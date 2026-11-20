package apk.battryinfo.service;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

import apk.battryinfo.model.BatteryData;

public final class BatteryHistoryStore extends SQLiteOpenHelper {

    private static final String DB_NAME = "battery_telemetry.db";
    private static final int DB_VERSION = 1;
    private static final long SAMPLE_INTERVAL = 5 * 60 * 1000L;
    private static final long RETENTION = 90L * 24 * 60 * 60 * 1000;

    public static final class Sample {
        public long timestamp;
        public double reportedSoc;
        public double estimatedSoc;
        public double voltage;
        public double current;
        public double maximumTemperature;
        public double mosTemperature;
        public double remainingCapacity;
        public int cycles;

        public boolean hasReportedSoc;
        public boolean hasEstimatedSoc;
        public boolean hasMosTemperature;
        public boolean hasMaximumTemperature;
    }

    public BatteryHistoryStore(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
            "CREATE TABLE samples (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "timestamp INTEGER NOT NULL," +
            "reported_soc REAL," +
            "estimated_soc REAL," +
            "voltage REAL," +
            "current REAL," +
            "max_temp REAL," +
            "mos_temp REAL," +
            "remaining_capacity REAL," +
            "cycles INTEGER)"
        );

        db.execSQL(
            "CREATE INDEX idx_samples_timestamp " +
            "ON samples(timestamp DESC)"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Preserve existing samples when schema versions change.
    }

    public synchronized boolean record(BatteryData data) {
        if (data == null || !data.liveDataAvailable ||
                !data.fcTelemetryAvailable ||
                data.totalVoltage <= 0) {
            return false;
        }

        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();

        try (Cursor cursor = db.rawQuery(
                "SELECT MAX(timestamp) FROM samples", null)) {
            if (cursor.moveToFirst() && !cursor.isNull(0)) {
                long previous = cursor.getLong(0);
                if (now - previous < SAMPLE_INTERVAL) {
                    return false;
                }
            }
        }

        ContentValues values = new ContentValues();
        values.put("timestamp", now);

        if (data.reportedSocAvailable &&
                data.reportedSoc >= 0 && data.reportedSoc <= 100) {
            values.put("reported_soc", data.reportedSoc);
        }

        if (data.soc >= 0 && data.soc <= 100) {
            values.put("estimated_soc", data.soc);
        }

        values.put("voltage", data.totalVoltage);
        values.put("current", data.current);

        if (data.maximumTemperature > -40 &&
                data.maximumTemperature < 200) {
            values.put("max_temp", data.maximumTemperature);
        }

        if (data.mosTemperatureAvailable) {
            values.put("mos_temp", data.mosTemperature);
        }

        values.put("remaining_capacity", data.remainingCapacity);
        values.put("cycles", data.cycles);

        long result = db.insert("samples", null, values);

        db.delete(
            "samples",
            "timestamp < ?",
            new String[]{String.valueOf(now - RETENTION)}
        );

        return result != -1;
    }

    public synchronized List<Sample> getLatest(long since, int limit) {
        List<Sample> result = new ArrayList<>();

        SQLiteDatabase db = getReadableDatabase();

        try (Cursor c = db.query(
                "samples",
                null,
                "timestamp >= ?",
                new String[]{String.valueOf(since)},
                null,
                null,
                "timestamp DESC",
                String.valueOf(Math.max(1, Math.min(limit, 500))))) {

            while (c.moveToNext()) {
                Sample s = new Sample();

                s.timestamp = c.getLong(c.getColumnIndexOrThrow("timestamp"));

                int index = c.getColumnIndexOrThrow("reported_soc");
                s.hasReportedSoc = !c.isNull(index);
                if (s.hasReportedSoc) s.reportedSoc = c.getDouble(index);

                index = c.getColumnIndexOrThrow("estimated_soc");
                s.hasEstimatedSoc = !c.isNull(index);
                if (s.hasEstimatedSoc) s.estimatedSoc = c.getDouble(index);

                s.voltage = c.getDouble(
                    c.getColumnIndexOrThrow("voltage"));

                s.current = c.getDouble(
                    c.getColumnIndexOrThrow("current"));

                index = c.getColumnIndexOrThrow("max_temp");
                s.hasMaximumTemperature = !c.isNull(index);
                if (s.hasMaximumTemperature) {
                    s.maximumTemperature = c.getDouble(index);
                }

                index = c.getColumnIndexOrThrow("mos_temp");
                s.hasMosTemperature = !c.isNull(index);
                if (s.hasMosTemperature) s.mosTemperature = c.getDouble(index);

                s.remainingCapacity = c.getDouble(
                    c.getColumnIndexOrThrow("remaining_capacity"));

                s.cycles = c.getInt(
                    c.getColumnIndexOrThrow("cycles"));

                result.add(s);
            }
        }

        return result;
    }

    public synchronized int getCount() {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM samples", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }
}
