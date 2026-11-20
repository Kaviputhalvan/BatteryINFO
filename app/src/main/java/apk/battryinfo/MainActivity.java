package apk.battryinfo;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;

import apk.battryinfo.model.BatteryData;
import apk.battryinfo.service.BatteryService;
import apk.battryinfo.service.BleService;
import apk.battryinfo.ui.Activities;
import apk.battryinfo.ui.BatteryUI;
import apk.battryinfo.ui.DeviceUI;
import apk.battryinfo.ui.LogUI;
import apk.battryinfo.ui.UI;

public class MainActivity extends Activity implements
        BleService.Listener,
        Activities.Listener,
        DeviceUI.Listener {

    private UI ui;
    private BatteryUI batteryUI;
    private DeviceUI deviceUI;
    private LogUI logUI;

    private BatteryService batteryService;
    private apk.battryinfo.service.BikeBatteryAlerts bikeBatteryAlerts;
    private apk.battryinfo.service.BatteryNotification batteryNotification;
    private apk.battryinfo.service.BatteryRuntimeEstimator runtimeEstimator;
    private apk.battryinfo.service.BatteryHistoryStore historyStore;
    private apk.battryinfo.ui.HistoryUI historyUI;
    private apk.battryinfo.service.DeviceHistoryStore deviceHistoryStore;
    private apk.battryinfo.ui.DeviceHistoryUI deviceHistoryUI;
    private String connectedAddress = "";
    private String connectedDeviceName = "";
    private boolean autoConnectScanActive;
    private boolean startupAutoConnectStarted;
    private boolean monitorReceiverRegistered;
    private boolean bleConnected;
    private boolean controlSessionActive;
    private int currentPage = UI.BATTERY;

    private static final String CONTROL_PASSWORD = "kavi@88388";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        ui = new UI(this);
        batteryUI = new BatteryUI(ui);
        deviceUI = new DeviceUI(this, ui, this);
        logUI = new LogUI(this, ui);

        batteryService = new BatteryService();
        bikeBatteryAlerts = new apk.battryinfo.service.BikeBatteryAlerts(this);
        batteryNotification = new apk.battryinfo.service.BatteryNotification(this);
        runtimeEstimator = new apk.battryinfo.service.BatteryRuntimeEstimator(this);
        historyStore = new apk.battryinfo.service.BatteryHistoryStore(this);
        historyUI = new apk.battryinfo.ui.HistoryUI(ui, historyStore);
        deviceHistoryStore =
                new apk.battryinfo.service.DeviceHistoryStore(this);
        deviceHistoryUI = new apk.battryinfo.ui.DeviceHistoryUI(
                this, ui, deviceHistoryStore, this::onAutoConnectSelected);

        new Activities(ui, this);

        ui.batteryTab.setOnClickListener(v ->
                showPage(UI.BATTERY));

        ui.pairTab.setOnClickListener(v ->
                showPage(UI.PAIR));

        ui.debugTab.setOnClickListener(v ->
                showPage(UI.DEBUG));

        ui.controlTab.setOnClickListener(v -> showControlWarning());

        ui.historyButton.setOnClickListener(v -> {
            historyUI.show();
            showPage(UI.HISTORY);
        });

        ui.historyBack.setOnClickListener(v ->
                showPage(UI.BATTERY));

        ui.deviceHistoryButton.setOnClickListener(v ->
                deviceHistoryUI.show());

        updateDeviceCards();
        registerMonitorReceiver();

        showPage(UI.BATTERY);
        requestBlePermissions();
    }


    private final BroadcastReceiver monitorReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null ||
                    !apk.battryinfo.service.BatteryMonitorService.ACTION_EVENT
                            .equals(intent.getAction())) return;

            String type = intent.getStringExtra(
                    apk.battryinfo.service.BatteryMonitorService.EXTRA_TYPE);

            if ("status".equals(type)) {
                onStatus(intent.getStringExtra(
                        apk.battryinfo.service.BatteryMonitorService.EXTRA_STATUS));
            } else if ("error".equals(type)) {
                onError(intent.getStringExtra(
                        apk.battryinfo.service.BatteryMonitorService.EXTRA_STATUS));
            } else if ("device".equals(type) || "ready".equals(type)) {
                BluetoothDevice device;
                if (Build.VERSION.SDK_INT >= 33) {
                    device = intent.getParcelableExtra(
                            apk.battryinfo.service.BatteryMonitorService.EXTRA_DEVICE,
                            BluetoothDevice.class);
                } else {
                    device = intent.getParcelableExtra(
                            apk.battryinfo.service.BatteryMonitorService.EXTRA_DEVICE);
                }

                if ("ready".equals(type)) {
                    onReady(device);
                } else if (device != null) {
                    onDevice(device, intent.getIntExtra(
                            apk.battryinfo.service.BatteryMonitorService.EXTRA_RSSI, 0));
                }
            } else if ("packet".equals(type)) {
                onPacket(
                        intent.getStringExtra(
                                apk.battryinfo.service.BatteryMonitorService.EXTRA_CODE),
                        intent.getStringExtra(
                                apk.battryinfo.service.BatteryMonitorService.EXTRA_PAYLOAD));
            }
        }
    };

    private void registerMonitorReceiver() {
        if (monitorReceiverRegistered) return;

        IntentFilter filter = new IntentFilter(
                apk.battryinfo.service.BatteryMonitorService.ACTION_EVENT);

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(monitorReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(monitorReceiver, filter);
        }
        monitorReceiverRegistered = true;
    }

    private void startMonitorService(String action) {
        Intent intent = new Intent(this,
                apk.battryinfo.service.BatteryMonitorService.class);
        intent.setAction(action);

        try {
            boolean stopping =
                    apk.battryinfo.service.BatteryMonitorService.ACTION_DISABLE.equals(action)
                    || apk.battryinfo.service.BatteryMonitorService.ACTION_DISCONNECT.equals(action);

            if (Build.VERSION.SDK_INT >= 26 && !stopping) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } catch (RuntimeException e) {
            if (ui != null) {
                ui.status.setText("Background monitor could not start: " +
                        e.getMessage());
            }
        }
    }

    private void connectThroughMonitor(BluetoothDevice device) {
        if (device == null) return;

        try {
            String address = device.getAddress();
            Intent intent = new Intent(this,
                    apk.battryinfo.service.BatteryMonitorService.class);
            intent.setAction(
                    apk.battryinfo.service.BatteryMonitorService.ACTION_CONNECT);
            intent.putExtra(
                    apk.battryinfo.service.BatteryMonitorService.EXTRA_ADDRESS,
                    address);

            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } catch (SecurityException e) {
            onError("Bluetooth permission missing: " + e.getMessage());
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerMonitorReceiver();
        startAutoConnectIfConfigured();
    }

    @Override
    protected void onStop() {
        if (monitorReceiverRegistered) {
            try {
                unregisterReceiver(monitorReceiver);
            } catch (RuntimeException ignored) {}
            monitorReceiverRegistered = false;
        }
        super.onStop();
    }

    private void showControlWarning() {
        if (isFinishing() || isDestroyed()) return;

        new AlertDialog.Builder(this)
                .setTitle("BMS CONTROL WARNING")
                .setMessage(
                        "This section is intended for commands that can modify " +
                        "your BMS. Incorrect settings may disable charging or " +
                        "discharge protection and may damage the battery. " +
                        "Continue only if you understand the risk. A password " +
                        "is required. No unverified write command will be sent.")
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("I UNDERSTAND",
                        (dialog, which) -> showControlPassword())
                .show();
    }

    private void showControlPassword() {
        final EditText passwordInput = new EditText(this);
        passwordInput.setSingleLine(true);
        passwordInput.setInputType(
                InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordInput.setHint("Enter Control password");

        int padding = (int) (20 *
                getResources().getDisplayMetrics().density + 0.5f);
        passwordInput.setPadding(padding, padding, padding, padding);

        new AlertDialog.Builder(this)
                .setTitle("Unlock BMS Control")
                .setMessage("Enter the Control password to continue.")
                .setView(passwordInput)
                .setNegativeButton("Cancel",
                        (dialog, which) -> dialog.dismiss())
                .setPositiveButton("UNLOCK", (dialog, which) -> {
                    String entered = passwordInput.getText().toString();

                    if (CONTROL_PASSWORD.equals(entered)) {
                        controlSessionActive = bleConnected;
                        showPage(UI.CONTROL);
                    } else {
                        Toast.makeText(this,
                                "Incorrect Control password",
                                Toast.LENGTH_SHORT).show();
                        showPage(UI.BATTERY);
                    }
                })
                .show();
    }

    private void requestBlePermissions() {
        String[] permissions;

        if (Build.VERSION.SDK_INT >= 31) {
            permissions = new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
            };
        } else if (Build.VERSION.SDK_INT >= 23) {
            permissions = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
        } else {
            return;
        }

        boolean missing = false;
        for (String permission : permissions) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                missing = true;
                break;
            }
        }

        if (missing) {
            requestPermissions(permissions, 100);
        } else {
            requestNotificationPermission();
            startAutoConnectIfConfigured();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);

        if (requestCode == 100) {
            boolean granted = results.length > 0;
            for (int result : results) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                }
            }

            onStatus(granted
                    ? "Bluetooth permissions granted"
                    : "Bluetooth permissions are required");
            requestNotificationPermission();
            if (granted) startAutoConnectIfConfigured();
        }
    }


    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
        }
    }

    @Override
    public void onScan() {
        autoConnectScanActive = false;
        deviceUI.clear();
        showPage(UI.PAIR);
        startMonitorService(apk.battryinfo.service.BatteryMonitorService.ACTION_SCAN);
    }

    @Override
    public void onDisconnect() {
        startMonitorService(apk.battryinfo.service.BatteryMonitorService.ACTION_DISCONNECT);
        showPage(UI.PAIR);
    }

    @Override
    public void onConnect(BluetoothDevice device) {
        connectThroughMonitor(device);
    }

    @Override
    public void onDeviceSelected(BluetoothDevice device) {
        autoConnectScanActive = false;
        connectThroughMonitor(device);
    }

    private boolean hasBlePermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
                    == PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED;
        }
        if (Build.VERSION.SDK_INT >= 23) {
            return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void startAutoConnectIfConfigured() {
        if (bleConnected || deviceHistoryStore == null) return;

        String address = deviceHistoryStore.getAutoConnectAddress();
        if (address == null || address.trim().isEmpty()) return;

        startupAutoConnectStarted = true;
        startAutoConnectForAddress(address);
    }

    private void startAutoConnectForAddress(String address) {
        if (address == null || address.trim().isEmpty()) {
            autoConnectScanActive = false;
            updateDeviceCards();
            return;
        }

        if (address.equalsIgnoreCase(connectedAddress) && bleConnected) {
            autoConnectScanActive = false;
            updateDeviceCards();
            return;
        }

        if (!hasBlePermissions()) {
            autoConnectScanActive = false;
            ui.status.setText("Bluetooth permissions are required for auto-connect");
            return;
        }

        autoConnectScanActive = true;
        ui.status.setText("Searching for saved device " + address);
        startMonitorService(apk.battryinfo.service.BatteryMonitorService.ACTION_ENABLE);
    }

    private void onAutoConnectSelected(String address) {
        updateDeviceCards();
        if (address == null || address.trim().isEmpty()) {
            autoConnectScanActive = false;
            startMonitorService(apk.battryinfo.service.BatteryMonitorService.ACTION_DISABLE);
            return;
        }
        startAutoConnectForAddress(address);
    }

    private void updateDeviceCards() {
        if (ui == null) return;

        String connected = bleConnected
                ? (connectedDeviceName.isEmpty()
                    ? "Connected BMS" : connectedDeviceName)
                    + "\\n" + connectedAddress
                : "Not connected";

        String autoAddress = deviceHistoryStore == null
                ? "" : deviceHistoryStore.getAutoConnectAddress();

        String autoName = "Not selected";
        if (deviceHistoryStore != null && !autoAddress.isEmpty()) {
            for (apk.battryinfo.service.DeviceHistoryStore.DeviceRecord record
                    : deviceHistoryStore.getDevices()) {
                if (record.address.equalsIgnoreCase(autoAddress)) {
                    autoName = record.name + "\\n" + record.address;
                    break;
                }
            }
            if ("Not selected".equals(autoName)) autoName = autoAddress;
        }

        ui.updateDeviceCards(connected, autoName);
    }

    private String safeDeviceName(BluetoothDevice device) {
        try {
            String name = device == null ? null : device.getName();
            return name == null || name.trim().isEmpty()
                    ? "Unknown BMS" : name;
        } catch (SecurityException e) {
            return "Unknown BMS";
        }
    }

    private String safeDeviceAddress(BluetoothDevice device) {
        try {
            return device == null ? "" : device.getAddress();
        } catch (SecurityException e) {
            return "";
        }
    }

    @Override
    public void onReady(BluetoothDevice device) {
        runOnUiThread(() -> {
            String address = safeDeviceAddress(device);
            if (address.isEmpty()) return;

            connectedAddress = address;
            connectedDeviceName = safeDeviceName(device);

            if (deviceHistoryStore != null) {
                deviceHistoryStore.saveConnectedDevice(
                        connectedAddress, connectedDeviceName);
            }

            autoConnectScanActive = false;
            updateDeviceCards();
        });
    }

    @Override
    public void onStatus(String message) {
        runOnUiThread(() -> {
            ui.status.setText(message);
            logUI.add(message);

            if (message.contains("BLE ready")) {
                bleConnected = true;
                autoConnectScanActive = false;
            } else if (message.equals("Disconnected")) {
                bleConnected = false;
                connectedAddress = "";
                connectedDeviceName = "";
                controlSessionActive = false;
                autoConnectScanActive = false;
            }

            updateDeviceCards();
            updateControlStatus();

            if (message.contains("BLE ready")) {
                showPage(UI.BATTERY);
                if (batteryNotification != null) {
                    batteryNotification.showConnected(batteryService.getData());
                }
            } else if (message.equals("Disconnected")) {
                showPage(UI.BATTERY);
                if (runtimeEstimator != null && batteryService != null) {
                    runtimeEstimator.disconnect(batteryService.getData());
                }
                if (batteryNotification != null) {
                    batteryNotification.stop();
                }
            }
        });
    }

    private void showPage(int page) {
        if (page != UI.CONTROL) {
            controlSessionActive = false;
        }

        currentPage = page;
        ui.showPage(page);
    }

    public boolean canWriteBms() {
        return controlSessionActive
                && bleConnected
                && currentPage == UI.CONTROL
                && !isFinishing()
                && !isDestroyed();
    }

    private void updateControlStatus() {
        if (ui != null && batteryService != null) {
            ui.updateControlStatus(
                    batteryService.getData(), bleConnected);
        }
    }

    @Override
    public void onDevice(BluetoothDevice device, int rssi) {
        runOnUiThread(() -> deviceUI.add(device, rssi));
    }

    @Override
    public void onPacket(String code, String payload) {
        onPacket("D2", code, payload);
    }

    @Override
    public void onPacket(
            String deviceAddress, String code, String payload) {
        runOnUiThread(() -> {
            boolean handled = batteryService.receive(code, payload);

            logUI.addPacket("RX " + deviceAddress, code, payload);

            BatteryData data = batteryService.getData();
            ui.updateControlStatus(data, bleConnected);

            if (historyStore != null &&
                    "FC".equalsIgnoreCase(code)) {
                historyStore.record(data);
            }

            if (runtimeEstimator != null &&
                    "FC".equalsIgnoreCase(code)) {
                runtimeEstimator.update(data);
            }

            if (batteryNotification != null &&
                    data.reportedSocAvailable &&
                    ("FC".equalsIgnoreCase(code) ||
                     "7C".equalsIgnoreCase(code))) {
                batteryNotification.update(data);
            }

            if (data.liveDataAvailable) {
                batteryUI.show(data);
                if (bikeBatteryAlerts != null &&
                        ("FC".equalsIgnoreCase(code) ||
                         "7C".equalsIgnoreCase(code))) {
                    bikeBatteryAlerts.evaluate(data.soc);
                }
            }
        });
    }

    @Override
    public void onError(String error) {
        runOnUiThread(() -> {
            if (autoConnectScanActive) {
                autoConnectScanActive = false;
                ui.status.setText(
                        "Auto-connect unavailable: " + error);
            }
            logUI.add("ERROR: " + error);
            updateDeviceCards();
        });
    }

    @Override
    protected void onDestroy() {
        controlSessionActive = false;
        currentPage = UI.BATTERY;
        if (ui != null) ui.stopClock();
        if (historyStore != null) historyStore.close();
        if (bikeBatteryAlerts != null) bikeBatteryAlerts.close();
        if (batteryNotification != null) batteryNotification.stop();
        super.onDestroy();
    }
}
