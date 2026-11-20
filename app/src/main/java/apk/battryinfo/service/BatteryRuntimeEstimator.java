package apk.battryinfo.service;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import apk.battryinfo.model.BatteryData;

public class BatteryRuntimeEstimator {

    private static final String PREFS = "battery_runtime_history";
    private static final String HISTORY = "discharge_sessions";

    private final SharedPreferences prefs;

    private double smoothedCurrent;
    private boolean currentInitialized;

    private double previousSoc = -1;
    private long previousSampleTime;

    private int direction;
    private long directionUpdated;
    private long sessionStart;
    private double sessionStartSoc;
    private double sessionCurrentSum;
    private int sessionSamples;

    public BatteryRuntimeEstimator(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(
                PREFS, Context.MODE_PRIVATE);
    }

    public synchronized void update(BatteryData data) {
        if (data == null || !data.liveDataAvailable ||
                !data.reportedSocAvailable ||
                data.remainingCapacity <= 0 ||
                data.reportedSoc < 0 || data.reportedSoc > 100) {
            return;
        }

        long now = System.currentTimeMillis();
        double soc = data.reportedSoc;
        double current = Math.abs(data.current);

        if (!currentInitialized) {
            smoothedCurrent = current;
            currentInitialized = true;
        } else {
            smoothedCurrent = smoothedCurrent * 0.80 + current * 0.20;
        }

        // Infer direction from SOC movement, not an undocumented status code.
        if (previousSoc >= 0 && previousSampleTime > 0) {
            long elapsed = now - previousSampleTime;

            if (elapsed >= 3000 && elapsed <= 120000) {
                double delta = soc - previousSoc;
                int detected = 0;

                if (delta >= 0.15 && current >= 0.15) {
                    detected = 1; // Charging
                } else if (delta <= -0.15 && current >= 0.15) {
                    detected = -1; // Discharging
                }

                if (detected != 0) {
                    if (direction != detected) {
                        finishSession(now);
                        startSession(detected, soc, now);
                    }

                    directionUpdated = now;
                    sessionCurrentSum += current;
                    sessionSamples++;
                }
            }
        }

        // Only report a direction supported by recent SOC movement
        // and non-trivial current. Do not interpret undocumented BMS codes.
        if (direction != 0 &&
                directionUpdated > 0 &&
                now - directionUpdated <= 120000 &&
                current >= 0.15) {
            data.flowDirection = direction;
        } else {
            data.flowDirection = 0;
        }

        previousSoc = soc;
        previousSampleTime = now;

        // Infer full capacity from BMS remaining Ah and BMS-reported SOC.
        double fullCapacity = data.remainingCapacity / (soc / 100.0);

        if (fullCapacity > 0 &&
                fullCapacity < 2000 &&
                smoothedCurrent >= 0.15) {

            double dischargeHours =
                    data.remainingCapacity / smoothedCurrent;

            double chargeAh = Math.max(
                    0, fullCapacity - data.remainingCapacity);

            double chargeHours = chargeAh / smoothedCurrent;

            data.estimatedDischargeMinutes =
                    safeMinutes(dischargeHours);

            data.estimatedChargeMinutes =
                    safeMinutes(chargeHours);

            data.runtimeEstimateAvailable = true;
            data.inferredFullCapacity = fullCapacity;
            data.averageLoadCurrent = smoothedCurrent;
        }

        updateHistoryStats(data);
    }

    private int safeMinutes(double hours) {
        if (Double.isNaN(hours) || Double.isInfinite(hours) ||
                hours < 0 || hours > 10000) {
            return 0;
        }
        return (int) Math.round(hours * 60.0);
    }

    private void startSession(int newDirection, double soc, long now) {
        direction = newDirection;
        sessionStart = now;
        sessionStartSoc = soc;
        sessionCurrentSum = 0;
        sessionSamples = 0;
    }

    private void finishSession(long now) {
        if (direction == -1 && sessionStart > 0) {
            long duration = now - sessionStart;
            double socDrop = sessionStartSoc - previousSoc;

            // Save only meaningful completed discharge sessions.
            if (duration >= 10 * 60 * 1000L && socDrop >= 5) {
                double averageCurrent = sessionSamples > 0
                        ? sessionCurrentSum / sessionSamples : 0;

                try {
                    JSONArray history = getHistory();

                    JSONObject entry = new JSONObject();
                    entry.put("durationMinutes", duration / 60000.0);
                    entry.put("socDrop", socDrop);
                    entry.put("averageCurrent", averageCurrent);
                    entry.put("timestamp", now);

                    history.put(entry);

                    // Keep the most recent 100 completed sessions.
                    JSONArray limited = new JSONArray();
                    int start = Math.max(0, history.length() - 100);

                    for (int i = start; i < history.length(); i++) {
                        limited.put(history.getJSONObject(i));
                    }

                    prefs.edit().putString(
                            HISTORY, limited.toString()).apply();

                } catch (Exception ignored) {
                }
            }
        }

        direction = 0;
        directionUpdated = 0;
        sessionStart = 0;
        sessionCurrentSum = 0;
        sessionSamples = 0;
    }

    private JSONArray getHistory() {
        try {
            return new JSONArray(prefs.getString(HISTORY, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private void updateHistoryStats(BatteryData data) {
        JSONArray history = getHistory();
        int count = history.length();

        data.completedDischargeSessions = count;
        data.averageDischargeMinutes = 0;
        data.dischargeRuntimeTrendPercent = 0;

        if (count == 0) return;

        try {
            double total = 0;
            double first = history.getJSONObject(0)
                    .optDouble("durationMinutes", 0);

            double latest = history.getJSONObject(count - 1)
                    .optDouble("durationMinutes", 0);

            int valid = 0;
            for (int i = 0; i < count; i++) {
                double duration = history.getJSONObject(i)
                        .optDouble("durationMinutes", 0);

                if (duration > 0) {
                    total += duration;
                    valid++;
                }
            }

            if (valid > 0) {
                data.averageDischargeMinutes = total / valid;
            }

            if (first > 0 && latest > 0) {
                data.dischargeRuntimeTrendPercent =
                        ((latest - first) / first) * 100.0;
            }
        } catch (Exception ignored) {
        }
    }

    public synchronized void disconnect(BatteryData data) {
        finishSession(System.currentTimeMillis());

        if (data != null) {
            updateHistoryStats(data);
        }

        previousSoc = -1;
        previousSampleTime = 0;
        currentInitialized = false;
        direction = 0;
        directionUpdated = 0;
        if (data != null) data.flowDirection = 0;
    }
}
