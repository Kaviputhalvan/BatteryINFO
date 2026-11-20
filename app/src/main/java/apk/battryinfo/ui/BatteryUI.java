package apk.battryinfo.ui;

import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

import apk.battryinfo.model.BatteryData;
import apk.battryinfo.service.BmsAlarmDecoder;

public class BatteryUI {
    private final UI ui;
    private boolean cardMode = true;

    public BatteryUI(UI ui) {
        this.ui = ui;
        renderEmpty();
    }

    public void show(BatteryData battery) {
        LinearLayout root = ui.batteryContent;
        root.removeAllViews();

        int cells = Math.max(0, Math.min(16, battery.cellCount));
        if (cells == 0) {
            for (int i = 0; i < 16; i++) {
                if (battery.cellVoltages[i] > 0) cells = i + 1;
            }
        }

        double high = -1;
        double low = Double.MAX_VALUE;
        int highIndex = -1;
        int lowIndex = -1;

        for (int i = 0; i < cells; i++) {
            double v = battery.cellVoltages[i];
            if (v <= 0) continue;
            if (v > high) { high = v; highIndex = i; }
            if (v < low) { low = v; lowIndex = i; }
        }

        LinearLayout hero = ui.card();
        add(root, hero, 0, 0, 0, 12);

        TextView packTitle = ui.label("BMS REPORTED STATE OF CHARGE");
        hero.addView(packTitle);

        TextView soc = new TextView(ui.activity);
        soc.setText(battery.reportedSocAvailable
                ? String.format(Locale.US, "%.1f%%", battery.reportedSoc)
                : "--.-%");
        soc.setTextColor(ui.accent);
        soc.setTextSize(42);
        soc.setTypeface(null, Typeface.BOLD);
        soc.setPadding(0, ui.dp(5), 0, ui.dp(3));
        hero.addView(soc);

        TextView charging = ui.value(
                "⚡  Charge/discharge status code: " +
                (battery.fcTelemetryAvailable
                        ? battery.chargeDischargeStatus : "Unavailable"));
        charging.setTextSize(13);
        charging.setTextSize(13);
        hero.addView(charging);

        hero.addView(progress(
                battery.reportedSocAvailable ? battery.reportedSoc : 0,
                ui.accent));

        TextView estimated = ui.value(
                battery.liveDataAvailable && battery.soc >= 0
                        ? String.format(Locale.US,
                                "Voltage-estimated SOC: %.1f%%", battery.soc)
                        : "Voltage-estimated SOC: Unavailable");
        estimated.setTextSize(14);
        hero.addView(estimated);

        hero.addView(progress(battery.soc >= 0 ? battery.soc : 0, ui.good));

        TextView estimateNote = ui.label(
                "BMS SOC is read from live register 42. Estimated SOC is calculated separately from cell voltages.");
        estimateNote.setPadding(0, ui.dp(9), 0, 0);
        hero.addView(estimateNote);

        LinearLayout runtime = ui.card();
        add(root, runtime, 0, 0, 0, 12);
        runtime.addView(ui.heading("LIVE RUNTIME ESTIMATION"));

        if (battery.runtimeEstimateAvailable) {
            addPair(runtime, "Estimated discharge time",
                    formatDuration(battery.estimatedDischargeMinutes));
            addPair(runtime, "Estimated charging time",
                    formatDuration(battery.estimatedChargeMinutes));
            addPair(runtime, "Average load current",
                    String.format(Locale.US, "%.2f A",
                            battery.averageLoadCurrent));
            addPair(runtime, "Inferred full capacity",
                    String.format(Locale.US, "%.1f Ah",
                            battery.inferredFullCapacity));
        } else {
            runtime.addView(ui.label(
                    "Waiting for valid BMS capacity, SOC and current readings."));
        }

        runtime.addView(ui.heading("DISCHARGE HISTORY"));
        addPair(runtime, "Completed discharge sessions",
                String.valueOf(battery.completedDischargeSessions));

        if (battery.averageDischargeMinutes > 0) {
            addPair(runtime, "Average discharge duration",
                    formatDuration((int) battery.averageDischargeMinutes));
        }

        if (battery.completedDischargeSessions >= 2) {
            addPair(runtime, "Runtime change since first session",
                    String.format(Locale.US, "%+.1f%%",
                            battery.dischargeRuntimeTrendPercent));
        }

        TextView historyNote = ui.label(
                "History is saved once per completed discharge session. Runtime comparisons are affected by load, temperature and usage.");
        historyNote.setPadding(0, ui.dp(8), 0, 0);
        runtime.addView(historyNote);

        LinearLayout metrics = new LinearLayout(ui.activity);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        add(root, metrics, 0, 0, 0, 12);

        metric(metrics, "PACK VOLTAGE",
                battery.fcTelemetryAvailable
                        ? String.format(Locale.US, "%.2f V", battery.totalVoltage)
                        : "00.0 V");
        metric(metrics, "CURRENT",
                battery.fcTelemetryAvailable
                        ? String.format(Locale.US, "%.1f A", battery.current)
                        : "00.0 A");

        LinearLayout metrics2 = new LinearLayout(ui.activity);
        metrics2.setOrientation(LinearLayout.HORIZONTAL);
        add(root, metrics2, 0, 0, 0, 12);

        metric(metrics2, "REMAINING",
                battery.fcTelemetryAvailable
                        ? String.format(Locale.US, "%.1f Ah", battery.remainingCapacity)
                        : "00.0 Ah");
        metric(metrics2, "POWER",
                battery.fcTelemetryAvailable
                        ? String.format(Locale.US, "%d W", battery.power)
                        : "00 W");

        LinearLayout analysis = ui.card();
        add(root, analysis, 0, 0, 0, 12);
        analysis.addView(ui.heading("Cell balance"));

        String highText = highIndex >= 0
                ? String.format(Locale.US, "C%d  %.3f V", highIndex + 1, high)
                : "Unavailable";
        String lowText = lowIndex >= 0
                ? String.format(Locale.US, "C%d  %.3f V", lowIndex + 1, low)
                : "Unavailable";

        addPair(analysis, "Highest cell", highText);
        addPair(analysis, "Lowest cell", lowText);

        String delta = highIndex >= 0 && lowIndex >= 0
                ? String.format(Locale.US, "%.0f mV", (high - low) * 1000)
                : "Unavailable";
        addPair(analysis, "Voltage difference", delta);
        addPair(analysis, "Active cells", cells + " / 16");
        addPair(analysis, "Balancing code",
                battery.fcTelemetryAvailable
                        ? String.valueOf(battery.balancingState) : "Unavailable");

        addPair(analysis, "Balancing",
                battery.balancingAvailable
                        ? (battery.balancingEnabled ? "ACTIVE" : "INACTIVE")
                        : "Unavailable");

        addPair(analysis, "Balancing current",
                battery.balancingAvailable
                        ? String.format(Locale.US, "%.1f A", battery.balancingCurrent)
                        : "Unavailable");

        addPair(analysis, "Balancing cycle",
                battery.balancingAvailable
                        ? String.valueOf(battery.balancingCycle)
                        : "Unavailable");

        addPair(analysis, "Balancing position",
                battery.balancingAvailable
                        ? String.valueOf(battery.balancingPosition)
                        : "Unavailable");

        addPair(analysis, "Charging MOS code",
                battery.fcTelemetryAvailable
                        ? String.valueOf(battery.chargingMos) : "Unavailable");
        addPair(analysis, "Discharge MOS code",
                battery.fcTelemetryAvailable
                        ? String.valueOf(battery.dischargeMos) : "Unavailable");
        addPair(analysis, "Cycle count",
                battery.fcTelemetryAvailable
                        ? String.valueOf(battery.cycles) : "Unavailable");

        addPair(analysis, "Last FC update",
                battery.fcTelemetryAvailable && battery.lastFcReceivedAt > 0
                        ? Math.max(0, (System.currentTimeMillis()
                                - battery.lastFcReceivedAt) / 1000) + " sec ago"
                        : "Unavailable");

        addAlarmPanel(root, battery);

        LinearLayout cellHeader = new LinearLayout(ui.activity);
        cellHeader.setGravity(Gravity.CENTER_VERTICAL);
        cellHeader.addView(ui.heading("Individual cells"),
                new LinearLayout.LayoutParams(0, -2, 1));

        Button toggle = ui.smallButton(cardMode ? "LIST VIEW" : "CARD VIEW");
        cellHeader.addView(toggle);
        toggle.setOnClickListener(v -> {
            cardMode = !cardMode;
            show(battery);
        });
        root.addView(cellHeader);

        TextView note = ui.label(
                "Estimated SOC uses a generic NMC/NCA voltage curve; it is not a BMS measurement.");
        note.setPadding(ui.dp(2), 0, ui.dp(2), ui.dp(10));
        root.addView(note);

        if (cardMode) {
            LinearLayout row = null;
            for (int i = 0; i < cells; i++) {
                if (i % 2 == 0) {
                    row = new LinearLayout(ui.activity);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    add(root, row, 0, 0, 0, 8);
                }
                cellCard(row, battery, i);
            }
        } else {
            for (int i = 0; i < cells; i++) {
                cellListItem(root, battery, i);
            }
        }

        LinearLayout temperature = ui.card();
        add(root, temperature, 0, 0, 0, 12);
        temperature.addView(ui.heading("Temperature"));

        addPair(temperature, "MOS temperature",
                battery.mosTemperatureAvailable
                        ? battery.mosTemperature + " °C"
                        : "Unavailable");

        addPair(temperature, "Maximum",
                battery.fcTelemetryAvailable
                        ? String.format(Locale.US, "%.1f °C", battery.maximumTemperature)
                        : "00.0 °C");
        addPair(temperature, "Minimum",
                battery.fcTelemetryAvailable
                        ? String.format(Locale.US, "%.1f °C", battery.minimumTemperature)
                        : "00.0 °C");

        for (int i = 0; i < Math.min(8, battery.temperatureSensorCount); i++) {
            addPair(temperature, "Sensor " + (i + 1),
                    battery.cellTemperatures[i] + " °C");
        }

        TextView foot = ui.label("Bike BatteryINFO • Live BMS telemetry");
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, ui.dp(12), 0, ui.dp(12));
        root.addView(foot);
    }


    private void addAlarmPanel(LinearLayout root, BatteryData battery) {
        LinearLayout panel = ui.card();
        add(root, panel, 0, 0, 0, 12);

        panel.addView(ui.heading("BMS PROTECTION & ALARMS"));

        if (!battery.liveDataAvailable) {
            panel.addView(ui.label("Waiting for BMS alarm registers."));
            return;
        }

        int[] registers = battery.faultStates.clone();
        java.util.List<BmsAlarmDecoder.Alarm> alarms =
                BmsAlarmDecoder.decode(registers);

        for (int i = 0; i < 4; i++) {
            addPair(panel,
                    "Register " + (i + 1) + " • Word " + (58 + i),
                    String.format(Locale.US, "0x%04X", registers[i]));
        }

        panel.addView(ui.heading("Active alarms"));

        if (alarms.isEmpty()) {
            TextView clear = ui.value("✓  No mapped active alarms");
            clear.setTextColor(ui.good);
            panel.addView(clear);
        } else {
            for (BmsAlarmDecoder.Alarm alarm : alarms) {
                LinearLayout row = new LinearLayout(ui.activity);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, ui.dp(7), 0, ui.dp(7));

                TextView code = ui.value(alarm.code());
                code.setTextSize(12);
                code.setTextColor(ui.warning);
                row.addView(code);

                LinearLayout details = new LinearLayout(ui.activity);
                details.setOrientation(LinearLayout.VERTICAL);
                details.setPadding(ui.dp(12), 0, 0, 0);

                TextView title = ui.value(alarm.title);
                title.setTextSize(13);
                details.addView(title);

                if (!alarm.level.isEmpty()) {
                    TextView level = ui.label(alarm.level);
                    details.addView(level);
                }

                row.addView(details,
                        new LinearLayout.LayoutParams(0, -2, 1));

                panel.addView(row);
            }
        }

        TextView note = ui.label(
                "Decoded from words 58–61. Only mapped active bits are displayed. " +
                "Reserved and unmapped bits are ignored. " +
                "R4.B5 wording is provisional pending original resource translation.");
        note.setPadding(0, ui.dp(10), 0, 0);
        panel.addView(note);
    }

    private String formatDuration(int minutes) {
        if (minutes <= 0) return "Calculating";

        int hours = minutes / 60;
        int mins = minutes % 60;

        if (hours == 0) return mins + " min";
        return hours + " hr " + mins + " min";
    }

    private void cellCard(LinearLayout row, BatteryData data, int index) {
        LinearLayout card = ui.card();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(ui.dp(3), 0, ui.dp(3), 0);
        row.addView(card, lp);

        double voltage = data.cellVoltages[index];
        double soc = data.cellSoc[index];

        card.addView(ui.label(String.format(Locale.US, "CELL %02d", index + 1)));

        TextView v = ui.value(voltage > 0
                ? String.format(Locale.US, "%.3f V", voltage) : "--");
        v.setTextSize(18);
        card.addView(v);

        card.addView(ui.label(data.liveDataAvailable && soc >= 0
                ? String.format(Locale.US, "%.1f%% estimated", soc)
                : "00.0%"));

        card.addView(progress(soc, cellColor(voltage)));
    }

    private void cellListItem(LinearLayout parent, BatteryData data, int index) {
        LinearLayout card = ui.card();
        add(parent, card, 0, 0, 0, 7);

        double voltage = data.cellVoltages[index];
        double soc = data.cellSoc[index];

        LinearLayout line = new LinearLayout(ui.activity);
        line.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(line);

        TextView name = ui.value(String.format(Locale.US, "CELL %02d", index + 1));
        line.addView(name, new LinearLayout.LayoutParams(0, -2, 1));

        TextView v = ui.value(voltage > 0
                ? String.format(Locale.US, "%.3f V", voltage) : "--");
        line.addView(v);

        TextView percent = ui.label(data.liveDataAvailable && soc >= 0
                ? String.format(Locale.US, "  %.1f%%", soc) : "  00.0%");
        line.addView(percent);

        card.addView(progress(soc, cellColor(voltage)));
    }

    private int cellColor(double voltage) {
        if (voltage <= 0) return ui.secondaryText;
        if (voltage < 3.20 || voltage > 4.15) return ui.danger;
        if (voltage < 3.40 || voltage > 4.05) return ui.warning;
        return ui.good;
    }

    private View progress(double value, int color) {
        LinearLayout track = new LinearLayout(ui.activity);
        track.setOrientation(LinearLayout.HORIZONTAL);
        track.setBackground(ui.rounded(ui.surface2, 8));
        track.setClipToOutline(true);
        track.setMinimumHeight(ui.dp(9));

        track.setLayoutParams(new LinearLayout.LayoutParams(-1, ui.dp(9)));

        float fraction = (float) (Math.max(0, Math.min(100, value)) / 100.0);

        View fill = new View(ui.activity);
        fill.setBackground(ui.rounded(color, 8));

        View remaining = new View(ui.activity);

        track.addView(fill,
                new LinearLayout.LayoutParams(0, ui.dp(9), fraction));
        track.addView(remaining,
                new LinearLayout.LayoutParams(0, ui.dp(9), 1.0f - fraction));

        return track;
    }

    private void metric(LinearLayout parent, String title, String value) {
        LinearLayout card = ui.card();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(ui.dp(3), 0, ui.dp(3), 0);
        parent.addView(card, lp);
        card.addView(ui.label(title));
        TextView v = ui.value(value);
        v.setTextSize(19);
        v.setPadding(0, ui.dp(5), 0, 0);
        card.addView(v);
    }

    private void addPair(LinearLayout parent, String title, String value) {
        LinearLayout row = new LinearLayout(ui.activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, ui.dp(5), 0, ui.dp(5));

        TextView left = ui.label(title);
        row.addView(left, new LinearLayout.LayoutParams(0, -2, 1));

        TextView right = ui.value(value);
        right.setTextSize(13);
        row.addView(right);
        parent.addView(row);
    }

    private void add(LinearLayout parent, View child,
                     int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(ui.dp(left), ui.dp(top), ui.dp(right), ui.dp(bottom));
        parent.addView(child, lp);
    }

    private void renderEmpty() {
        BatteryData placeholder = new BatteryData();

        for (int i = 0; i < placeholder.cellSoc.length; i++) {
            placeholder.cellSoc[i] = -1;
        }

        show(placeholder);
    }
}
