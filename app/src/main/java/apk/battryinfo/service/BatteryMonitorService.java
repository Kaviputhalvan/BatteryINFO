package apk.battryinfo.service;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import apk.battryinfo.MainActivity;
import apk.battryinfo.R;
import apk.battryinfo.model.BatteryData;

public final class BatteryMonitorService extends Service
        implements BleService.Listener {

    public static final String ACTION_ENABLE =
            "apk.battryinfo.monitor.ENABLE";
    public static final String ACTION_DISABLE =
            "apk.battryinfo.monitor.DISABLE";
    public static final String ACTION_SCAN =
            "apk.battryinfo.monitor.SCAN";
    public static final String ACTION_CONNECT =
            "apk.battryinfo.monitor.CONNECT";
    public static final String ACTION_DISCONNECT =
            "apk.battryinfo.monitor.DISCONNECT";

    public static final String ACTION_EVENT =
            "apk.battryinfo.monitor.EVENT";
    public static final String EXTRA_TYPE = "type";
    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_DEVICE = "device";
    public static final String EXTRA_RSSI = "rssi";
    public static final String EXTRA_CODE = "code";
    public static final String EXTRA_PAYLOAD = "payload";
    public static final String EXTRA_ADDRESS = "address";

    private static final String PREFS = "battery_device_history";
    private static final String KEY_ADDRESS = "auto_connect_address";

    private static final String CHANNEL = "battery_monitor_service";
    private static final int NOTIFICATION_ID = 4101;

    private static final long[] RETRY_DELAYS = {
            5000, 10000, 20000, 40000, 60000
    };

    private final Handler handler = new Handler(Looper.getMainLooper());

    private SharedPreferences prefs;
    private BleService ble;
    private BatteryService batteryParser;
    private BatteryRuntimeEstimator runtimeEstimator;
    private NotificationManager notifications;

    private boolean autoEnabled;
    private boolean screenLocked;
    private boolean connected;
    private boolean scanning;
    private boolean autoConnectScanning;
    private boolean connecting;
    private boolean receiverRegistered;

    private int retryIndex;
    private String targetAddress;

    private final Runnable retryTask = this::connectSavedDevice;
    private final Runnable scanTimeout = () -> {
        if (scanning && !connected) {
            ble.stopScan();
            scanning = false;
            autoConnectScanning = false;
            scheduleRetry();
        }
    };

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                screenLocked = true;
                handler.removeCallbacks(retryTask);
                handler.removeCallbacks(scanTimeout);
                scanning = false;
                autoConnectScanning = false;
                connecting = false;
                ble.stopScan();

                if (connected) {
                    connected = false;
                    ble.disconnect();
                }

                updateNotification("Paused while screen is locked");
                emitStatus("Monitoring paused while screen is locked");

            } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                screenLocked = false;

                if (autoEnabled) {
                    emitStatus("Screen unlocked; resuming BMS monitoring");
                    connectSavedDevice();
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        notifications = (NotificationManager)
                getSystemService(Context.NOTIFICATION_SERVICE);

        batteryParser = new BatteryService();
        runtimeEstimator = new BatteryRuntimeEstimator(this);
        ble = new BleService(getApplicationContext(), this);

        targetAddress = prefs.getString(KEY_ADDRESS, "");
        autoEnabled = targetAddress != null && !targetAddress.trim().isEmpty();

        PowerManager pm = (PowerManager)
                getSystemService(Context.POWER_SERVICE);
        screenLocked = pm != null && !pm.isInteractive();

        createNotificationChannel();
        registerScreenReceiver();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_ENABLE : intent.getAction();

        if (ACTION_DISABLE.equals(action)) {
            autoEnabled = false;
            autoConnectScanning = false;

            handler.removeCallbacks(retryTask);
            handler.removeCallbacks(scanTimeout);
            ble.stopScan();
            scanning = false;

            if (!connected) {
                stopForeground(true);
                stopSelf();
                return START_NOT_STICKY;
            }

            updateNotification("Connected; automatic reconnect disabled");
            return START_STICKY;
        }

        if (ACTION_DISCONNECT.equals(action)) {
            autoEnabled = false;
            autoConnectScanning = false;

            handler.removeCallbacks(retryTask);
            handler.removeCallbacks(scanTimeout);
            ble.disconnect();
            connected = false;
            scanning = false;
            connecting = false;

            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_CONNECT.equals(action)) {
            String address = intent.getStringExtra(EXTRA_ADDRESS);
            if (address != null && BluetoothAdapter.checkBluetoothAddress(address)) {
                startForeground(NOTIFICATION_ID,
                        buildNotification("Connecting to BMS"));
                targetAddress = address;
                connecting = false;
                autoConnectScanning = false;
                connectAddress(address);
            }
            return START_STICKY;
        }

        if (ACTION_SCAN.equals(action)) {
            startForeground(NOTIFICATION_ID,
                    buildNotification("Scanning for nearby BMS devices"));

            scanning = true;
            autoConnectScanning = false;
            ble.scan();

            handler.removeCallbacks(scanTimeout);
            handler.postDelayed(scanTimeout, 15000);
            return START_STICKY;
        }

        targetAddress = prefs.getString(KEY_ADDRESS, "");
        autoEnabled = targetAddress != null && !targetAddress.trim().isEmpty();

        startForeground(NOTIFICATION_ID,
                buildNotification("Starting BMS monitor"));

        if (connected) {
            emitStatus("BLE ready. Existing connection retained");
            BluetoothDevice current = ble.getConnectedDevice();
            if (current != null) {
                Intent event = new Intent();
                event.putExtra(EXTRA_DEVICE, current);
                emit("ready", event);
            }
        } else if (autoEnabled && !screenLocked) {
            connectSavedDevice();
        }

        return START_STICKY;
    }

    private void registerScreenReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenReceiver, filter);
        }

        receiverRegistered = true;
    }

    private void connectSavedDevice() {
        if (!autoEnabled || screenLocked || connected ||
                scanning || connecting) return;

        targetAddress = prefs.getString(KEY_ADDRESS, "");

        if (targetAddress == null || targetAddress.trim().isEmpty() ||
                !BluetoothAdapter.checkBluetoothAddress(targetAddress)) {
            emitStatus("No saved BMS device. Pair a BMS first.");
            updateNotification("Waiting for BMS pairing");
            return;
        }

        BluetoothManager manager = (BluetoothManager)
                getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();

        if (adapter == null || !adapter.isEnabled()) {
            updateNotification("Bluetooth is unavailable");
            scheduleRetry();
            return;
        }

        if (Build.VERSION.SDK_INT >= 31 &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
                        != PackageManager.PERMISSION_GRANTED) {
            emitStatus("Bluetooth scan permission is required");
            updateNotification("Bluetooth permission required");
            return;
        }

        scanning = true;
        autoConnectScanning = true;
        updateNotification("Searching for saved BMS");
        ble.scan(targetAddress);

        handler.removeCallbacks(scanTimeout);
        handler.postDelayed(scanTimeout, 12000);
    }

    private void connectAddress(String address) {
        if (screenLocked) {
            emitStatus("Connection postponed until screen unlock");
            return;
        }

        handler.removeCallbacks(retryTask);
        handler.removeCallbacks(scanTimeout);
        ble.stopScan();

        scanning = false;
        autoConnectScanning = false;
        connecting = true;

        try {
            BluetoothManager manager = (BluetoothManager)
                    getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();

            if (adapter == null || !adapter.isEnabled()) {
                connecting = false;
                emitStatus("Bluetooth is unavailable");
                scheduleRetry();
                return;
            }

            BluetoothDevice device = adapter.getRemoteDevice(address);
            startForeground(NOTIFICATION_ID,
                    buildNotification("Connecting to saved BMS"));

            ble.connect(device);

        } catch (SecurityException | IllegalArgumentException e) {
            connecting = false;
            emitError("Cannot connect to saved BMS: " + e.getMessage());
            scheduleRetry();
        }
    }

    private void scheduleRetry() {
        if (!autoEnabled || screenLocked || connected) return;

        handler.removeCallbacks(retryTask);

        int index = Math.min(retryIndex, RETRY_DELAYS.length - 1);
        long delay = RETRY_DELAYS[index];
        retryIndex = Math.min(retryIndex + 1, RETRY_DELAYS.length - 1);

        updateNotification("Reconnecting in " + (delay / 1000) + " seconds");
        emitStatus("BMS reconnect scheduled in " + (delay / 1000) + " seconds");

        handler.postDelayed(retryTask, delay);
    }

    private void emit(String type, Intent extras) {
        Intent event = new Intent(ACTION_EVENT);
        event.setPackage(getPackageName());
        event.putExtra(EXTRA_TYPE, type);

        if (extras != null) {
            if (extras.hasExtra(EXTRA_STATUS))
                event.putExtra(EXTRA_STATUS, extras.getStringExtra(EXTRA_STATUS));
            if (extras.hasExtra(EXTRA_CODE))
                event.putExtra(EXTRA_CODE, extras.getStringExtra(EXTRA_CODE));
            if (extras.hasExtra(EXTRA_PAYLOAD))
                event.putExtra(EXTRA_PAYLOAD, extras.getStringExtra(EXTRA_PAYLOAD));
            if (extras.hasExtra(EXTRA_RSSI))
                event.putExtra(EXTRA_RSSI, extras.getIntExtra(EXTRA_RSSI, 0));
            if (Build.VERSION.SDK_INT >= 33 &&
                    extras.hasExtra(EXTRA_DEVICE)) {
                BluetoothDevice device =
                        extras.getParcelableExtra(EXTRA_DEVICE, BluetoothDevice.class);
                event.putExtra(EXTRA_DEVICE, device);
            } else if (extras.hasExtra(EXTRA_DEVICE)) {
                event.putExtra(EXTRA_DEVICE,
                        (BluetoothDevice) extras.getParcelableExtra(EXTRA_DEVICE));
            }
        }

        sendBroadcast(event);
    }

    private void emitStatus(String status) {
        Intent i = new Intent();
        i.putExtra(EXTRA_STATUS, status);
        emit("status", i);
    }

    private void emitError(String error) {
        Intent event = new Intent();
        event.putExtra(EXTRA_STATUS, error);
        emit("error", event);
    }

    @Override
    public void onReady(BluetoothDevice device) {
        Intent event = new Intent();
        event.putExtra(EXTRA_DEVICE, device);
        emit("ready", event);
    }

    @Override
    public void onStatus(String status) {
        emitStatus(status);

        if (status.contains("BLE ready")) {
            connected = true;
            connecting = false;
            scanning = false;
            autoConnectScanning = false;
            retryIndex = 0;
            handler.removeCallbacks(retryTask);
            handler.removeCallbacks(scanTimeout);
            updateNotification("BMS connected");
        } else if ("Disconnected".equals(status)) {
            connected = false;
            connecting = false;
            scanning = false;
            autoConnectScanning = false;
            updateNotification("BMS disconnected");

            if (autoEnabled && !screenLocked) {
                scheduleRetry();
            } else if (!autoEnabled) {
                stopForeground(true);
                stopSelf();
            }
        }
    }

    @Override
    public void onDevice(BluetoothDevice device, int rssi) {
        String address;

        try {
            address = device.getAddress();
        } catch (SecurityException e) {
            return;
        }

        if (autoConnectScanning && autoEnabled
                && targetAddress != null && !targetAddress.trim().isEmpty()) {
            if (!targetAddress.equalsIgnoreCase(address)) return;

            if (connected || connecting) return;

            ble.stopScan();
            scanning = false;
            autoConnectScanning = false;
            connecting = true;
            handler.removeCallbacks(scanTimeout);
            ble.connect(device);
            return;
        }

        Intent i = new Intent();
        i.putExtra(EXTRA_DEVICE, device);
        i.putExtra(EXTRA_RSSI, rssi);
        emit("device", i);
    }

    @Override
    public void onPacket(String code, String payload) {
        batteryParser.receive(code, payload);
        BatteryData data = batteryParser.getData();

        if ("FC".equalsIgnoreCase(code) ||
                "7C".equalsIgnoreCase(code)) {
            runtimeEstimator.update(data);
        }

        Intent i = new Intent();
        i.putExtra(EXTRA_CODE, code);
        i.putExtra(EXTRA_PAYLOAD, payload);
        emit("packet", i);

        if ("FC".equalsIgnoreCase(code) ||
                "7C".equalsIgnoreCase(code)) {
            updateNotification("BMS connected", data);
        }
    }

    private boolean isRecoverableBleError(String error) {
        if (error == null) return false;

        String value = error.toLowerCase(java.util.Locale.ROOT);

        return value.contains("ble write failed")
                || value.contains("gatt connection error")
                || value.contains("gatt write")
                || value.contains("service discovery failed")
                || value.contains("bms service")
                || value.contains("fff1")
                || value.contains("fff2")
                || value.contains("notification")
                || value.contains("ble scan failed");
    }

    @Override
    public void onError(String error) {
        emitError(error);

        if (!autoEnabled || screenLocked ||
                !isRecoverableBleError(error)) {
            return;
        }

        // A failed write/GATT callback invalidates the old connection.
        connected = false;
        connecting = false;
        scanning = false;
        autoConnectScanning = false;

        handler.removeCallbacks(retryTask);
        handler.removeCallbacks(scanTimeout);

        // Close the stale GATT and clear pending protocol reads/writes.
        ble.resetConnection();

        updateNotification("BMS connection failed; restarting scan");
        scheduleRetry();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26 && notifications != null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL,
                    "Battery monitor service",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Background BMS connection and battery monitoring");
            channel.setShowBadge(false);
            notifications.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String state) {
        return buildNotification(state, batteryParser.getData());
    }

    private String formatMinutes(int minutes) {
        if (minutes <= 0) return "Calculating...";

        int hours = minutes / 60;
        int mins = minutes % 60;

        if (hours > 0) {
            return String.format(java.util.Locale.US,
                    "%02dh %02dm", hours, mins);
        }

        return String.format(java.util.Locale.US, "%02dm", mins);
    }

    private Notification buildNotification(String state, BatteryData data) {
        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP |
                Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pending = PendingIntent.getActivity(
                this, 4101, open,
                PendingIntent.FLAG_UPDATE_CURRENT |
                        (Build.VERSION.SDK_INT >= 23
                                ? PendingIntent.FLAG_IMMUTABLE : 0));

        boolean hasSoc = data != null && data.reportedSocAvailable;
        double percent = hasSoc
                ? Math.max(0.0, Math.min(100.0, data.reportedSoc))
                : 0.0;

        String percentage = hasSoc
                ? String.format(java.util.Locale.US, "%.1f%%", percent)
                : "--.-%";

        String title;
        String detail;
        String expanded;

        int direction = data == null ? 0 : data.flowDirection;

        if (direction == 1) {
            title = "⚡ CHARGING  •  " + percentage;
            detail = data.runtimeEstimateAvailable
                    ? "Estimated time to full: " +
                        formatMinutes(data.estimatedChargeMinutes)
                    : "Charging • Calculating time to full";

            expanded = "⚡ CHARGING\n" +
                    "BMS battery level: " + percentage + "\n" +
                    "Estimated time to full: " +
                    (data.runtimeEstimateAvailable
                            ? formatMinutes(data.estimatedChargeMinutes)
                            : "Calculating...");

        } else if (direction == -1) {
            title = "↓ DISCHARGING  •  " + percentage;
            detail = data.runtimeEstimateAvailable
                    ? "Estimated time remaining: " +
                        formatMinutes(data.estimatedDischargeMinutes)
                    : "Discharging • Calculating remaining time";

            expanded = "↓ DISCHARGING\n" +
                    "BMS battery level: " + percentage + "\n" +
                    "Estimated time remaining: " +
                    (data.runtimeEstimateAvailable
                            ? formatMinutes(data.estimatedDischargeMinutes)
                            : "Calculating...");

        } else {
            title = "BatteryINFO  •  " + percentage;
            detail = hasSoc
                    ? "Direction unknown / battery idle"
                    : state + " • Waiting for BMS data";

            expanded = "BATTERY STATUS UNKNOWN / IDLE\n" +
                    "BMS battery level: " + percentage + "\n" +
                    "Waiting for confirmed charge/discharge trend.";
        }

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);

        builder.setSmallIcon(android.R.drawable.ic_lock_idle_charging)
                .setContentTitle(title)
                .setContentText(detail)
                .setStyle(new Notification.BigTextStyle()
                        .bigText(expanded)
                        .setBigContentTitle(title))
                .setSubText("BatteryINFO • Live BMS monitor")
                .setContentIntent(pending)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_STATUS)
                .setVisibility(Notification.VISIBILITY_PUBLIC);

        if (hasSoc) {
            builder.setProgress(1000,
                    (int) Math.round(percent * 10.0), false);
        }

        return builder.build();
    }

    private void updateNotification(String state) {
        updateNotification(state, batteryParser.getData());
    }

    private void updateNotification(String state, BatteryData data) {
        Notification notification = buildNotification(state, data);

        if (Build.VERSION.SDK_INT >= 26) {
            try {
                startForeground(NOTIFICATION_ID, notification);
            } catch (RuntimeException ignored) {
                if (notifications != null) {
                    notifications.notify(NOTIFICATION_ID, notification);
                }
            }
        } else if (notifications != null) {
            notifications.notify(NOTIFICATION_ID, notification);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);

        if (receiverRegistered) {
            try {
                unregisterReceiver(screenReceiver);
            } catch (RuntimeException ignored) {}
        }

        if (ble != null) ble.close();

        if (runtimeEstimator != null) {
            runtimeEstimator.disconnect(batteryParser.getData());
        }

        super.onDestroy();
    }
}
