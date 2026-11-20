package apk.battryinfo.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import apk.battryinfo.service.DeviceHistoryStore;

public class DeviceHistoryUI {
    public interface Listener {
        void onAutoConnectSelected(String address);
    }

    private final Context context;
    private final UI ui;
    private final DeviceHistoryStore store;
    private final Listener listener;

    public DeviceHistoryUI(Context context, UI ui,
                           DeviceHistoryStore store, Listener listener) {
        this.context = context;
        this.ui = ui;
        this.store = store;
        this.listener = listener;
    }

    public void show() {
        List<DeviceHistoryStore.DeviceRecord> devices = store.getDevices();
        String selected = store.getAutoConnectAddress();

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(ui.dp(18), ui.dp(8), ui.dp(18), ui.dp(8));

        TextView explanation = ui.label(
                "Choose one saved BMS as the automatic connection target. " +
                "Selecting a device starts a Bluetooth scan for its address.");
        explanation.setPadding(0, 0, 0, ui.dp(12));
        content.addView(explanation);

        if (devices.isEmpty()) {
            TextView empty = ui.value("No successfully connected devices yet");
            empty.setPadding(0, ui.dp(16), 0, ui.dp(16));
            content.addView(empty);
        } else {
            for (DeviceHistoryStore.DeviceRecord record : devices) {
                LinearLayout row = ui.card();
                LinearLayout.LayoutParams rowParams =
                        new LinearLayout.LayoutParams(-1, -2);
                rowParams.setMargins(0, ui.dp(4), 0, ui.dp(4));
                content.addView(row, rowParams);

                CheckBox check = new CheckBox(context);
                check.setText(record.name);
                check.setTextColor(ui.textColor);
                check.setTextSize(15);
                check.setChecked(record.address.equalsIgnoreCase(selected));
                row.addView(check);

                TextView address = ui.label(record.address);
                row.addView(address);

                TextView details = ui.label(
                        "Connections: " + record.connectionCount +
                        "  •  Last connected: " +
                        (record.lastConnectedAt > 0
                                ? new SimpleDateFormat(
                                        "dd MMM yyyy, HH:mm",
                                        Locale.getDefault()).format(
                                                new Date(record.lastConnectedAt))
                                : "Unknown"));
                details.setPadding(0, ui.dp(5), 0, 0);
                row.addView(details);

                check.setOnCheckedChangeListener((button, checked) -> {
                    if (!checked) return;

                    store.setAutoConnectAddress(record.address);
                    for (int i = 0; i < content.getChildCount(); i++) {
                        View child = content.getChildAt(i);
                        if (!(child instanceof LinearLayout)) continue;
                        LinearLayout item = (LinearLayout) child;
                        for (int j = 0; j < item.getChildCount(); j++) {
                            View nested = item.getChildAt(j);
                            if (nested instanceof CheckBox &&
                                    nested != check) {
                                ((CheckBox) nested).setChecked(false);
                            }
                        }
                    }
                    listener.onAutoConnectSelected(record.address);
                });

                row.setOnClickListener(v -> check.setChecked(!check.isChecked()));
            }
        }

        android.widget.Button clear = ui.smallButton("CLEAR AUTO-CONNECT SELECTION");
        clear.setOnClickListener(v -> {
            store.setAutoConnectAddress("");
            listener.onAutoConnectSelected("");
            for (int i = 0; i < content.getChildCount(); i++) {
                View child = content.getChildAt(i);
                if (!(child instanceof LinearLayout)) continue;
                LinearLayout item = (LinearLayout) child;
                for (int j = 0; j < item.getChildCount(); j++) {
                    View nested = item.getChildAt(j);
                    if (nested instanceof CheckBox) {
                        ((CheckBox) nested).setChecked(false);
                    }
                }
            }
        });
        content.addView(clear);

        new AlertDialog.Builder(context)
                .setTitle("Bluetooth Device History")
                .setView(content)
                .setPositiveButton("CLOSE", null)
                .show();
    }
}
