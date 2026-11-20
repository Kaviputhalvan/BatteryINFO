package apk.battryinfo.ui;

import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import apk.battryinfo.service.BatteryHistoryStore;

public final class HistoryUI {

    private final UI ui;
    private final BatteryHistoryStore store;

    private long selectedWindow = 24L * 60 * 60 * 1000;

    public HistoryUI(UI ui, BatteryHistoryStore store) {
        this.ui = ui;
        this.store = store;
    }

    public void show() {
        render();
    }

    private void render() {
        LinearLayout root = ui.historyContent;
        root.removeAllViews();

        LinearLayout filters = new LinearLayout(ui.activity);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        filters.setGravity(Gravity.CENTER);
        filters.setPadding(0, ui.dp(8), 0, ui.dp(12));

        addFilter(filters, "24 Hours", 24L * 60 * 60 * 1000);
        addFilter(filters, "7 Days", 7L * 24 * 60 * 60 * 1000);
        addFilter(filters, "30 Days", 30L * 24 * 60 * 60 * 1000);

        root.addView(filters);

        long since = System.currentTimeMillis() - selectedWindow;
        List<BatteryHistoryStore.Sample> samples =
                store.getLatest(since, 300);

        // Database order is newest first. Charts draw oldest to newest.
        List<BatteryHistoryStore.Sample> chronological =
                new java.util.ArrayList<>(samples);
        Collections.reverse(chronological);

        TextView summary = ui.label(
                samples.size() + " saved samples • Latest first");
        summary.setPadding(ui.dp(3), 0, 0, ui.dp(10));
        root.addView(summary);

        if (samples.isEmpty()) {
            LinearLayout empty = ui.card();
            TextView message = ui.value("No history yet");
            message.setTextSize(18);
            empty.addView(message);

            TextView hint = ui.label(
                    "Connect your BMS and keep BatteryINFO running. " +
                    "A telemetry sample is saved every five minutes.");
            hint.setPadding(0, ui.dp(8), 0, 0);
            empty.addView(hint);
            root.addView(empty);
            return;
        }

        addChart(root, chronological, TelemetryChartView.SOC);
        addChart(root, chronological, TelemetryChartView.VOLTAGE);
        addChart(root, chronological, TelemetryChartView.CURRENT);
        addChart(root, chronological, TelemetryChartView.TEMPERATURE);

        SimpleDateFormat dateFormat = new SimpleDateFormat(
                "dd MMM yyyy  •  HH:mm:ss",
                Locale.getDefault());

        for (BatteryHistoryStore.Sample sample : samples) {
            LinearLayout card = ui.card();

            TextView date = ui.label(
                    dateFormat.format(new Date(sample.timestamp)));
            date.setTextColor(ui.accent);
            date.setTypeface(null, Typeface.BOLD);
            card.addView(date);

            LinearLayout row1 = new LinearLayout(ui.activity);
            row1.setOrientation(LinearLayout.HORIZONTAL);
            row1.setPadding(0, ui.dp(10), 0, ui.dp(5));

            addMetric(row1, "BMS SOC",
                    sample.hasReportedSoc
                    ? String.format(Locale.getDefault(), "%.1f%%",
                            sample.reportedSoc) : "--");

            addMetric(row1, "Estimated SOC",
                    sample.hasEstimatedSoc
                    ? String.format(Locale.getDefault(), "%.1f%%",
                            sample.estimatedSoc) : "--");

            card.addView(row1);

            LinearLayout row2 = new LinearLayout(ui.activity);
            row2.setOrientation(LinearLayout.HORIZONTAL);

            addMetric(row2, "Voltage",
                    String.format(Locale.getDefault(), "%.2f V",
                            sample.voltage));

            addMetric(row2, "Current",
                    String.format(Locale.getDefault(), "%.2f A",
                            sample.current));

            card.addView(row2);

            LinearLayout row3 = new LinearLayout(ui.activity);
            row3.setOrientation(LinearLayout.HORIZONTAL);
            row3.setPadding(0, ui.dp(5), 0, 0);

            addMetric(row3, "Max temperature",
                    String.format(Locale.getDefault(), "%.1f °C",
                            sample.maximumTemperature));

            addMetric(row3, "MOS temperature",
                    sample.hasMosTemperature
                    ? String.format(Locale.getDefault(), "%.1f °C",
                            sample.mosTemperature) : "--");

            card.addView(row3);

            TextView extra = ui.label(
                    String.format(Locale.getDefault(),
                            "Remaining capacity: %.1f Ah   •   Cycles: %d",
                            sample.remainingCapacity, sample.cycles));
            extra.setPadding(0, ui.dp(8), 0, 0);
            card.addView(extra);

            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(-1, -2);
            params.bottomMargin = ui.dp(10);
            root.addView(card, params);
        }
    }

    private void addChart(
            LinearLayout parent,
            List<BatteryHistoryStore.Sample> samples,
            int mode) {

        LinearLayout card = ui.card();

        TelemetryChartView chart = new TelemetryChartView(
                ui.activity,
                samples,
                mode,
                ui.accent,
                0xFFFFB74D,
                ui.textColor,
                ui.border);

        card.addView(chart,
                new LinearLayout.LayoutParams(-1, ui.dp(190)));

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = ui.dp(10);
        parent.addView(card, params);
    }

    private void addFilter(LinearLayout parent,
                           String title, long window) {
        Button button = ui.smallButton(title);
        button.setTextSize(11);
        button.setAllCaps(false);
        button.setOnClickListener(v -> {
            selectedWindow = window;
            render();
        });

        parent.addView(button,
                new LinearLayout.LayoutParams(0, ui.dp(42), 1));
    }

    private void addMetric(LinearLayout parent,
                           String title, String value) {
        LinearLayout column = new LinearLayout(ui.activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(0, 0, ui.dp(4), 0);

        TextView label = ui.label(title);
        label.setTextSize(11);
        column.addView(label);

        TextView number = ui.value(value);
        number.setTextSize(15);
        column.addView(number);

        parent.addView(column,
                new LinearLayout.LayoutParams(0, -2, 1));
    }
}
