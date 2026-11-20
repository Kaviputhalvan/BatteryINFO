package apk.battryinfo.service;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class DeviceHistoryStore {
    private static final String PREFS = "battery_device_history";
    private static final String KEY_DEVICES = "devices";
    private static final String KEY_AUTO = "auto_connect_address";

    public static class DeviceRecord {
        public final String address;
        public final String name;
        public final long lastConnectedAt;
        public final int connectionCount;

        public DeviceRecord(String address, String name,
                            long lastConnectedAt, int connectionCount) {
            this.address = address;
            this.name = name;
            this.lastConnectedAt = lastConnectedAt;
            this.connectionCount = connectionCount;
        }
    }

    private final SharedPreferences prefs;

    public DeviceHistoryStore(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized List<DeviceRecord> getDevices() {
        List<DeviceRecord> result = new ArrayList<>();
        String raw = prefs.getString(KEY_DEVICES, "[]");

        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;

                String address = item.optString("address", "");
                if (address.isEmpty()) continue;

                result.add(new DeviceRecord(
                        address,
                        item.optString("name", "Unknown BMS"),
                        item.optLong("lastConnectedAt", 0),
                        item.optInt("connectionCount", 1)));
            }
        } catch (Exception ignored) {}

        return result;
    }

    public synchronized void saveConnectedDevice(
            String address, String name) {
        if (address == null || address.trim().isEmpty()) return;

        List<DeviceRecord> devices = getDevices();
        long now = System.currentTimeMillis();
        int count = 1;

        List<DeviceRecord> updated = new ArrayList<>();
        for (DeviceRecord record : devices) {
            if (record.address.equalsIgnoreCase(address)) {
                count = record.connectionCount + 1;
            } else {
                updated.add(record);
            }
        }

        updated.add(0, new DeviceRecord(
                address,
                name == null || name.trim().isEmpty()
                        ? "Unknown BMS" : name,
                now,
                count));

        JSONArray array = new JSONArray();
        try {
            for (DeviceRecord record : updated) {
                JSONObject item = new JSONObject();
                item.put("address", record.address);
                item.put("name", record.name);
                item.put("lastConnectedAt", record.lastConnectedAt);
                item.put("connectionCount", record.connectionCount);
                array.put(item);
            }
        } catch (Exception ignored) {
            return;
        }

        prefs.edit().putString(KEY_DEVICES, array.toString()).apply();
    }

    public synchronized String getAutoConnectAddress() {
        return prefs.getString(KEY_AUTO, "");
    }

    public synchronized void setAutoConnectAddress(String address) {
        prefs.edit().putString(
                KEY_AUTO, address == null ? "" : address).apply();
    }
}
