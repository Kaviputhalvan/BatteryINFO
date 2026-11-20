package apk.battryinfo.ui;

import android.bluetooth.BluetoothDevice;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.LinkedHashMap;
import java.util.Map;

public class DeviceUI {
    public interface Listener {
        void onDeviceSelected(BluetoothDevice device);
    }

    private final UI ui;
    private final Listener listener;
    private final Map<String, BluetoothDevice> devices = new LinkedHashMap<>();

    public DeviceUI(android.app.Activity activity, UI ui, Listener listener) {
        this.ui = ui;
        this.listener = listener;
    }

    public void clear() {
        devices.clear();
        ui.deviceList.removeAllViews();
    }

    public void add(BluetoothDevice device, int rssi) {
        String address;
        String name = "Unknown BMS";

        try {
            address = device.getAddress();
            if (device.getName() != null) name = device.getName();
        } catch (SecurityException e) {
            return;
        }

        if (devices.containsKey(address)) return;
        devices.put(address, device);

        LinearLayout card = ui.card();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, ui.dp(5), 0, ui.dp(5));
        ui.deviceList.addView(card, lp);

        TextView title = ui.value(name);
        title.setTextSize(17);
        card.addView(title);

        TextView detail = ui.label(address);
        detail.setPadding(0, ui.dp(4), 0, ui.dp(8));
        card.addView(detail);

        TextView signal = ui.label("Signal: " + rssi + " dBm");
        signal.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(signal);

        android.widget.Button connect = ui.smallButton("CONNECT");
        card.addView(connect);
        connect.setOnClickListener(v -> listener.onDeviceSelected(device));
        card.setOnClickListener(v -> listener.onDeviceSelected(device));
    }
}
