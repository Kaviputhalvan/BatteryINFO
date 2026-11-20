package apk.battryinfo.ble;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

import apk.battryinfo.protocol.BmsProtocol;

public class BleManager {

    public interface Listener {
        void onStatus(String status);
        void onReady();
        void onDevice(BluetoothDevice device, int rssi);
        void onPacket(String responseCode, String payload);

        default void onPacket(
                String deviceAddress, String responseCode, String payload) {
            onPacket(responseCode, payload);
        }

        void onError(String error);
    }

    private static final UUID SERVICE =
            UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb");
    private static final UUID READ =
            UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb");
    private static final UUID WRITE =
            UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb");
    private static final UUID CCCD =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private final Context context;
    private final Listener listener;
    private BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private BluetoothDevice connectingDevice;
    private BluetoothGattCharacteristic writeCharacteristic;
    private final Queue<byte[]> writeQueue = new ArrayDeque<>();
    private boolean writing;
    private boolean notificationsReady;
    private String receiveBuffer = "";

    public BleManager(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;

        BluetoothManager manager =
                (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (manager != null) adapter = manager.getAdapter();
    }

    private boolean hasPermission(String permission) {
        return Build.VERSION.SDK_INT < 23 ||
                context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasConnectPermission() {
        return Build.VERSION.SDK_INT < 31 ||
                hasPermission(Manifest.permission.BLUETOOTH_CONNECT);
    }

    public void startScan() {
        startScan(null);
    }

    public void startScan(String targetAddress) {
        if (adapter == null) {
            listener.onError("Bluetooth is unavailable");
            return;
        }
        if (!hasConnectPermission()) {
            listener.onError("Bluetooth connect permission is missing");
            return;
        }
        if (!adapter.isEnabled()) {
            listener.onError("Please turn on Bluetooth");
            return;
        }
        if (Build.VERSION.SDK_INT <= 30 &&
                !hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            listener.onError("Location permission is required for BLE scanning");
            return;
        }

        try {
            scanner = adapter.getBluetoothLeScanner();
            if (scanner == null) {
                listener.onError("BLE scanner unavailable");
                return;
            }
            listener.onStatus(targetAddress == null
                    ? "Scanning for BMS devices..."
                    : "Searching for saved BMS device...");

            if (targetAddress == null) {
                scanner.startScan(scanCallback);
            } else {
                List<ScanFilter> filters = new ArrayList<>();
                filters.add(new ScanFilter.Builder()
                        .setDeviceAddress(targetAddress)
                        .build());

                ScanSettings settings = new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                        .build();

                scanner.startScan(filters, settings, scanCallback);
            }
        } catch (SecurityException e) {
            listener.onError("Bluetooth permission denied");
        }
    }

    public void stopScan() {
        try {
            if (scanner != null) scanner.stopScan(scanCallback);
        } catch (SecurityException ignored) {}
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            listener.onDevice(device, result.getRssi());
        }

        @Override
        public void onScanFailed(int errorCode) {
            listener.onError("BLE scan failed: " + errorCode);
        }
    };

    public void connect(BluetoothDevice device) {
        stopScan();
        closeGatt();

        if (!hasConnectPermission()) {
            listener.onError("Bluetooth connect permission is missing");
            return;
        }

        try {
            connectingDevice = device;
            listener.onStatus("Connecting to " + device.getAddress());
            if (Build.VERSION.SDK_INT >= 23) {
                gatt = device.connectGatt(context, false, gattCallback,
                        BluetoothDevice.TRANSPORT_LE);
            } else {
                gatt = device.connectGatt(context, false, gattCallback);
            }
        } catch (SecurityException e) {
            listener.onError("Bluetooth permission denied");
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt bluetoothGatt,
                                            int status, int newState) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                listener.onError("GATT connection error: " + status);
                closeGatt();
                return;
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                listener.onStatus("Connected. Discovering services...");
                try {
                    bluetoothGatt.discoverServices();
                } catch (SecurityException e) {
                    listener.onError("Bluetooth permission denied");
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                notificationsReady = false;
                writeCharacteristic = null;
                connectingDevice = null;
                listener.onStatus("Disconnected");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt bluetoothGatt, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                listener.onError("Service discovery failed: " + status);
                return;
            }

            BluetoothGattService service = bluetoothGatt.getService(SERVICE);
            if (service == null) {
                listener.onError("BMS service FFF0 not found");
                return;
            }

            BluetoothGattCharacteristic notifyCharacteristic =
                    service.getCharacteristic(READ);
            writeCharacteristic = service.getCharacteristic(WRITE);

            if (notifyCharacteristic == null || writeCharacteristic == null) {
                listener.onError("FFF1 or FFF2 characteristic not found");
                return;
            }

            try {
                boolean enabled =
                        bluetoothGatt.setCharacteristicNotification(
                                notifyCharacteristic, true);

                BluetoothGattDescriptor descriptor =
                        notifyCharacteristic.getDescriptor(CCCD);

                if (!enabled || descriptor == null) {
                    listener.onError("Could not enable FFF1 notifications");
                    return;
                }

                if (Build.VERSION.SDK_INT >= 33) {
                    int result = bluetoothGatt.writeDescriptor(
                            descriptor,
                            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    if (result != BluetoothStatusCodes.SUCCESS) {
                        listener.onError("Notification descriptor write failed: " + result);
                    }
                } else {
                    descriptor.setValue(
                            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    bluetoothGatt.writeDescriptor(descriptor);
                }
            } catch (SecurityException e) {
                listener.onError("Bluetooth permission denied");
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt bluetoothGatt,
                                      BluetoothGattDescriptor descriptor, int status) {
            if (!CCCD.equals(descriptor.getUuid())) return;

            if (status != BluetoothGatt.GATT_SUCCESS) {
                listener.onError("FFF1 notification setup failed: " + status);
                return;
            }

            notificationsReady = true;
            listener.onStatus("BLE ready. Starting read-only register requests...");
            listener.onReady();
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt bluetoothGatt,
                                             BluetoothGattCharacteristic characteristic) {
            if (READ.equals(characteristic.getUuid())) {
                byte[] value = characteristic.getValue();
                if (value != null) receive(value);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt bluetoothGatt,
                                             BluetoothGattCharacteristic characteristic,
                                             byte[] value) {
            if (READ.equals(characteristic.getUuid()) && value != null) {
                receive(value);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt bluetoothGatt,
                                          BluetoothGattCharacteristic characteristic,
                                          int status) {
            writing = false;
            if (status != BluetoothGatt.GATT_SUCCESS) {
                writing = false;
                listener.onError("BLE write failed: " + status);
                return;
            }
            sendNext();
        }
    };

    private int findFrameHeader(String buffer) {
        for (int i = 0; i + 4 <= buffer.length(); i += 2) {
            String address = buffer.substring(i, i + 2);
            String function = buffer.substring(i + 2, i + 4);

            if (address.matches("(?i)D[2-7]") &&
                    "03".equalsIgnoreCase(function)) {
                return i;
            }
        }
        return -1;
    }

    private synchronized void receive(byte[] bytes) {
        StringBuilder hex = new StringBuilder();

        for (byte b : bytes) {
            hex.append(String.format("%02X", b & 0xFF));
        }

        receiveBuffer += hex;

        while (receiveBuffer.length() >= 10) {
            int header = findFrameHeader(receiveBuffer);

            if (header < 0) {
                receiveBuffer = receiveBuffer.substring(
                        Math.max(0, receiveBuffer.length() - 3));
                return;
            }

            if (header > 0) {
                receiveBuffer = receiveBuffer.substring(header);
            }

            if (receiveBuffer.length() < 10) return;

            int payloadBytes;

            try {
                payloadBytes = Integer.parseInt(
                        receiveBuffer.substring(4, 6), 16);
            } catch (NumberFormatException e) {
                receiveBuffer = receiveBuffer.substring(2);
                continue;
            }

            int frameLength = 10 + payloadBytes * 2;

            if (payloadBytes == 0 || frameLength > 4096) {
                receiveBuffer = receiveBuffer.substring(2);
                continue;
            }

            if (receiveBuffer.length() < frameLength) return;

            String frame = receiveBuffer.substring(0, frameLength);
            String body = frame.substring(0, frame.length() - 4);
            String receivedCrc = frame.substring(frame.length() - 4);
            String expectedCrc = BmsProtocol.crc16(body);

            if (!receivedCrc.equalsIgnoreCase(expectedCrc)) {
                listener.onError("Invalid packet CRC; resynchronizing");
                receiveBuffer = receiveBuffer.substring(2);
                continue;
            }

            String responseCode = frame.substring(4, 6).toUpperCase();
            String payload = frame.substring(6, frame.length() - 4);

            String deviceAddress = frame.substring(0, 2).toUpperCase();
            listener.onPacket(deviceAddress, responseCode, payload);

            receiveBuffer = receiveBuffer.substring(frameLength);
        }
    }

    public synchronized void sendRead(int start, int count) {
        sendRead(BmsProtocol.DEFAULT_ADDRESS, start, count);
    }

    public synchronized void sendRead(
            String deviceAddress, int start, int count) {
        enqueue(BmsProtocol.hexToBytes(
                BmsProtocol.buildReadRequest(
                        deviceAddress, start, count)));
    }

    public synchronized void sendReadPlan() {
        for (BmsProtocol.ReadRequest request : BmsProtocol.getReadPlan()) {
            enqueue(BmsProtocol.hexToBytes(request.getFrame()));
        }
    }

    private synchronized void enqueue(byte[] bytes) {
        writeQueue.offer(bytes);
        sendNext();
    }

    private synchronized void sendNext() {
        if (!notificationsReady || writing || writeQueue.isEmpty() ||
                gatt == null || writeCharacteristic == null) return;

        byte[] next = writeQueue.poll();
        try {
            writeCharacteristic.setWriteType(
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);

            if (Build.VERSION.SDK_INT >= 33) {
                int result = gatt.writeCharacteristic(
                        writeCharacteristic, next,
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                if (result != BluetoothStatusCodes.SUCCESS) {
                    listener.onError("GATT write request failed: " + result);
                    return;
                }
            } else {
                writeCharacteristic.setValue(next);
                boolean accepted = gatt.writeCharacteristic(writeCharacteristic);
                if (!accepted) {
                    listener.onError("GATT write request rejected");
                    return;
                }
            }
            writing = true;
        } catch (SecurityException e) {
            listener.onError("Bluetooth permission denied");
        }
    }

    public boolean isReady() {
        return notificationsReady;
    }

    public BluetoothDevice getConnectedDevice() {
        return notificationsReady ? connectingDevice : null;
    }

    public void disconnect() {
        stopScan();
        try {
            if (gatt != null) gatt.disconnect();
        } catch (SecurityException ignored) {}
    }

    public void close() {
        stopScan();
        closeGatt();
    }

    private void closeGatt() {
        try {
            if (gatt != null) {
                gatt.disconnect();
                gatt.close();
            }
        } catch (SecurityException ignored) {}
        gatt = null;
        notificationsReady = false;
        writing = false;
        writeCharacteristic = null;
        connectingDevice = null;
        writeQueue.clear();
        receiveBuffer = "";
    }
}
