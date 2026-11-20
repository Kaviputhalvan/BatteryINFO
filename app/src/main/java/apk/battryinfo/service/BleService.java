package apk.battryinfo.service;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import apk.battryinfo.ble.BleManager;
import apk.battryinfo.protocol.BmsProtocol;

public class BleService implements BleManager.Listener {

    public interface Listener {
        void onStatus(String status);
        void onDevice(BluetoothDevice device, int rssi);
        void onPacket(String code, String payload);

        default void onPacket(
                String deviceAddress, String code, String payload) {
            onPacket(code, payload);
        }

        void onError(String error);

        default void onReady(BluetoothDevice device) {}
    }

    private static final int MAX_PACKS = 6;
    private static final long POLL_INTERVAL_MS = 5000;

    private final Listener listener;
    private final BleManager manager;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable packCountReader;
    private final Runnable mainDataReader;

    private final List<String> packAddresses = new ArrayList<>();

    private boolean initialReadsSent;
    private boolean packCountReceived;
    private boolean discoveringPacks;
    private boolean discoveryComplete;
    private boolean pollingInProgress;

    private int currentPackIndex;
    private String activePackAddress = BmsProtocol.DEFAULT_ADDRESS;

    public BleService(Context context, Listener listener) {
        this.listener = listener;
        this.manager = new BleManager(context, this);

        packCountReader = () -> {
            if (!manager.isReady()) return;

            listener.onStatus("Reading battery pack count");
            manager.sendRead(BmsProtocol.DEFAULT_ADDRESS, 67, 1);
        };

        mainDataReader = new Runnable() {
            @Override
            public void run() {
                if (!manager.isReady()) return;

                if (discoveryComplete && !pollingInProgress) {
                    pollingInProgress = true;
                    activePackAddress = BmsProtocol.DEFAULT_ADDRESS;

                    listener.onStatus(
                            "Polling main battery data: " + activePackAddress);

                    manager.sendRead(activePackAddress, 128, 112);
                }
            }
        };
    }

    public void scan() {
        manager.startScan();
    }

    public void scan(String address) {
        manager.startScan(address);
    }

    public void stopScan() {
        manager.stopScan();
    }

    public BluetoothDevice getConnectedDevice() {
        return manager.getConnectedDevice();
    }

    public void connect(BluetoothDevice device) {
        cancelScheduledReads();

        initialReadsSent = false;
        packCountReceived = false;
        discoveringPacks = false;
        discoveryComplete = false;
        pollingInProgress = false;
        currentPackIndex = 0;
        activePackAddress = BmsProtocol.DEFAULT_ADDRESS;
        packAddresses.clear();

        manager.connect(device);
    }

    public void disconnect() {
        cancelScheduledReads();

        initialReadsSent = false;
        packCountReceived = false;
        discoveringPacks = false;
        discoveryComplete = false;
        pollingInProgress = false;
        packAddresses.clear();

        manager.disconnect();
    }

    public void resetConnection() {
        cancelScheduledReads();

        initialReadsSent = false;
        packCountReceived = false;
        discoveringPacks = false;
        discoveryComplete = false;
        pollingInProgress = false;
        currentPackIndex = 0;
        activePackAddress = BmsProtocol.DEFAULT_ADDRESS;
        packAddresses.clear();

        manager.close();
    }

    public void close() {
        cancelScheduledReads();
        handler.removeCallbacksAndMessages(null);
        manager.close();
    }

    private void cancelScheduledReads() {
        handler.removeCallbacks(packCountReader);
        handler.removeCallbacks(mainDataReader);
    }

    @Override
    public void onStatus(String status) {
        listener.onStatus(status);
    }

    @Override
    public void onDevice(BluetoothDevice device, int rssi) {
        listener.onDevice(device, rssi);
    }

    @Override
    public void onReady() {
        if (initialReadsSent) return;

        initialReadsSent = true;

        handler.post(() -> {
            if (!manager.isReady()) return;

            listener.onStatus("BLE ready. Starting BMS initialization");
            listener.onReady(manager.getConnectedDevice());
            handler.postDelayed(packCountReader, 1000);
        });
    }

    @Override
    public void onPacket(String code, String payload) {
        onPacket(activePackAddress, code, payload);
    }

    @Override
    public void onPacket(
            String deviceAddress, String code, String payload) {
        listener.onPacket(deviceAddress, code, payload);

        handler.post(() ->
                handleProtocolPacket(deviceAddress, code, payload));
    }

    private void handleProtocolPacket(
            String deviceAddress, String code, String payload) {
        if (!manager.isReady()) return;

        if ("02".equalsIgnoreCase(code)) {
            handlePackCount(payload);
            return;
        }

        if ("E0".equalsIgnoreCase(code)) {
            listener.onStatus(
                    "Extended battery response received from " +
                            deviceAddress + "; reading pack data");

            manager.sendRead(deviceAddress, 0, 126);
            return;
        }

        if ("FC".equalsIgnoreCase(code)) {
            if (discoveringPacks) {
                advancePackDiscovery();
            } else if (discoveryComplete && pollingInProgress) {
                pollingInProgress = false;
                handler.removeCallbacks(mainDataReader);
                handler.postDelayed(mainDataReader, POLL_INTERVAL_MS);
                listener.onStatus("Live data updated; next poll in 5 seconds");
            }
        }
    }

    private void handlePackCount(String payload) {
        if (packCountReceived) return;

        int count;

        try {
            count = Integer.parseInt(payload.trim(), 16);
        } catch (RuntimeException e) {
            listener.onError("Invalid battery pack count: " + payload);
            count = 0;
        }

        if (count < 0 || count > MAX_PACKS) {
            listener.onError("Unsupported battery pack count: " + count);
            count = 0;
        }

        packCountReceived = true;
        packAddresses.clear();

        int effectiveCount = count == 0 ? 1 : count;

        for (int i = 0; i < effectiveCount; i++) {
            packAddresses.add(String.format(
                    Locale.US, "%02X", 0xD2 + i));
        }

        currentPackIndex = 0;
        discoveringPacks = true;
        discoveryComplete = false;

        listener.onStatus(
                "Detected " + effectiveCount + " battery pack(s): " +
                        packAddresses);

        readCurrentPack();
    }

    private void readCurrentPack() {
        if (!manager.isReady()) return;

        if (currentPackIndex >= packAddresses.size()) {
            finishPackDiscovery();
            return;
        }

        activePackAddress = packAddresses.get(currentPackIndex);

        listener.onStatus(
                "Reading pack " + (currentPackIndex + 1) +
                        "/" + packAddresses.size() +
                        " at " + activePackAddress);

        manager.sendRead(activePackAddress, 128, 112);
    }

    private void advancePackDiscovery() {
        if (!discoveringPacks) return;

        currentPackIndex++;

        if (currentPackIndex < packAddresses.size()) {
            readCurrentPack();
        } else {
            finishPackDiscovery();
        }
    }

    private void finishPackDiscovery() {
        discoveringPacks = false;
        discoveryComplete = true;
        pollingInProgress = false;
        activePackAddress = BmsProtocol.DEFAULT_ADDRESS;

        listener.onStatus(
                "Pack discovery complete. Starting 5-second live polling");

        handler.removeCallbacks(mainDataReader);
        handler.post(mainDataReader);
    }

    @Override
    public void onError(String error) {
        listener.onError(error);
    }
}
