package apk.battryinfo.ui;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LogUI {
    private static class Entry {
        String time;
        String direction;
        String code;
        String payload;
        String message;

        Entry(String direction, String code, String payload, String message) {
            this.time = new SimpleDateFormat(
                    "HH:mm:ss.SSS", Locale.US).format(new Date());
            this.direction = direction;
            this.code = code;
            this.payload = payload;
            this.message = message;
        }
    }

    private final Activity activity;
    private final UI ui;
    private final List<Entry> entries = new ArrayList<>();
    private int format;

    public LogUI(Activity activity, UI ui) {
        this.activity = activity;
        this.ui = ui;

        ui.logFormat.setOnItemSelectedListener(
                new android.widget.AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            android.widget.AdapterView<?> parent,
                            android.view.View view,
                            int position,
                            long id) {
                        format = position;
                        render();
                    }

                    @Override
                    public void onNothingSelected(
                            android.widget.AdapterView<?> parent) {}
                });

        ui.copyLog.setOnClickListener(v -> copyAll());
        ui.clearLog.setOnClickListener(v -> clear());
    }

    public void add(String message) {
        entries.add(new Entry("EVENT", "", "", message));
        trim();
        render();
    }

    public void addPacket(String direction, String code, String payload) {
        entries.add(new Entry(direction, code, payload, ""));
        trim();
        render();
    }

    public void clear() {
        entries.clear();
        render();
    }

    private void trim() {
        while (entries.size() > 500) {
            entries.remove(0);
        }
    }

    private void render() {
        StringBuilder out = new StringBuilder();

        for (Entry entry : entries) {
            out.append(entry.time).append(" ");

            if ("EVENT".equals(entry.direction)) {
                out.append("[EVENT] ").append(entry.message).append("\n");
                continue;
            }

            out.append("[").append(entry.direction).append("] ");

            if (format == 0) {
                out.append(entry.code)
                        .append(" ")
                        .append(entry.payload.length() / 2)
                        .append(" bytes\n")
                        .append(entry.payload)
                        .append("\n\n");
            } else if (format == 1) {
                out.append(entry.code).append(" HEX\n");
                out.append(groupHex(entry.payload)).append("\n\n");
            } else if (format == 2) {
                out.append(entry.code).append(" BINARY\n");
                out.append(toBinary(entry.payload)).append("\n\n");
            } else if (format == 3) {
                out.append(entry.code).append(" ASCII\n");
                out.append(toAscii(entry.payload)).append("\n\n");
            } else {
                out.append("Response code: ").append(entry.code).append("\n");
                out.append("Payload bytes: ")
                        .append(entry.payload.length() / 2).append("\n");
                out.append("Raw HEX: ").append(entry.payload).append("\n\n");
            }
        }

        if (out.length() == 0) {
            out.append("No debug events yet.");
        }

        ui.log.setText(out.toString());
    }

    private String groupHex(String hex) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i + 1 < hex.length(); i += 2) {
            out.append(hex, i, i + 2).append(' ');
            if ((i / 2 + 1) % 16 == 0) out.append('\n');
        }
        return out.toString();
    }

    private String toBinary(String hex) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i + 1 < hex.length(); i += 2) {
            int value = Integer.parseInt(hex.substring(i, i + 2), 16);
            String bits = String.format(Locale.US, "%8s",
                    Integer.toBinaryString(value)).replace(' ', '0');
            out.append(bits).append(' ');
            if ((i / 2 + 1) % 8 == 0) out.append('\n');
        }
        return out.toString();
    }

    private String toAscii(String hex) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i + 1 < hex.length(); i += 2) {
            int value = Integer.parseInt(hex.substring(i, i + 2), 16);
            out.append(value >= 32 && value <= 126
                    ? (char) value : '.');
        }
        return out.toString();
    }

    private void copyAll() {
        ClipboardManager clipboard = (ClipboardManager)
                activity.getSystemService(Context.CLIPBOARD_SERVICE);

        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(
                    "BatteryINFO Debug Log",
                    ui.log.getText().toString()));
            android.widget.Toast.makeText(activity,
                    "Log copied", android.widget.Toast.LENGTH_SHORT).show();
        }
    }
}
