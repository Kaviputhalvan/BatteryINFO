package apk.battryinfo.ui;

import android.bluetooth.BluetoothDevice;

public class Activities {
    public interface Listener {
        void onScan();
        void onDisconnect();
        void onConnect(BluetoothDevice device);
    }

    public Activities(UI ui, Listener listener) {
        ui.scan.setOnClickListener(v -> listener.onScan());
        ui.disconnect.setOnClickListener(v -> listener.onDisconnect());
    }
}
