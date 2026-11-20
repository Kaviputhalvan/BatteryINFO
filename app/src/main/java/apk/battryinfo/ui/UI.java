package apk.battryinfo.ui;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import apk.battryinfo.model.BatteryData;

public class UI {
    public static final int BATTERY = 0;
    public static final int PAIR = 1;
    public static final int DEBUG = 2;
    public static final int HISTORY = 3;
    public static final int CONTROL = 4;

    public final LinearLayout root;
    public final LinearLayout deviceList;
    public final LinearLayout batteryContent;
    public final LinearLayout historyContent;
    public final Button historyButton;
    public final Button deviceHistoryButton;
    public final TextView currentDeviceValue;
    public final TextView autoConnectValue;
    public final Button historyBack;
    public final TextView status;
    public final TextView log;

    public final Button scan;
    public final Button disconnect;
    public final Button copyLog;
    public final Button clearLog;

    public final Spinner logFormat;

    public final Button batteryTab;
    public final Button pairTab;
    public final Button debugTab;
    public final Button controlTab;

    public final Activity activity;
    public final boolean dark;

    public final int background;
    public final int surface;
    public final int surface2;
    public final int textColor;
    public final int secondaryText;
    public final int accent;
    public final int border;
    public final int good;
    public final int warning;
    public final int danger;

    private final TextView pageTitle;
    private final TextView clock;
    private final FrameLayout pageHost;
    private final LinearLayout batteryPage;
    private final LinearLayout pairPage;
    private final FrameLayout pairFrame;
    private final LinearLayout debugPage;
    private final LinearLayout historyPage;
    private final LinearLayout controlPage;

    private TextView controlBluetoothValue;
    private TextView controlChargeMosValue;
    private TextView controlDischargeMosValue;
    private TextView controlBalancingValue;
    private TextView controlMosTemperatureValue;
    private TextView controlLastUpdateValue;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable clockTask = new Runnable() {
        @Override
        public void run() {
            clock.setText(new SimpleDateFormat(
                    "EEE, dd MMM  •  HH:mm:ss",
                    Locale.getDefault()).format(new Date()));
            handler.postDelayed(this, 1000);
        }
    };

    public UI(Activity activity) {
        this.activity = activity;

        String theme = activity.getPreferences(0)
                .getString("theme", "system");

        boolean systemDark = (activity.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;

        dark = "dark".equals(theme) || ("system".equals(theme) && systemDark);

        background = dark ? 0xFF101719 : 0xFFF3F6F5;
        surface = dark ? 0xFF1B2528 : Color.WHITE;
        surface2 = dark ? 0xFF263337 : 0xFFE8EFED;
        textColor = dark ? 0xFFEAF2F0 : 0xFF172321;
        secondaryText = dark ? 0xFFACBCB8 : 0xFF60716C;
        accent = dark ? 0xFF54D6B2 : 0xFF087F65;
        border = dark ? 0xFF344448 : 0xFFDCE5E1;
        good = 0xFF24B47E;
        warning = 0xFFE5A33B;
        danger = 0xFFE05D62;

        root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(background);
        activity.setContentView(root);

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(20), dp(12), dp(20), dp(12));
        header.setBackgroundColor(surface);
        root.addView(header);

        LinearLayout top = new LinearLayout(activity);
        top.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(top);

        TextView appName = new TextView(activity);
        appName.setText("BatteryINFO");
        appName.setTextColor(textColor);
        appName.setTextSize(22);
        appName.setTypeface(null, Typeface.BOLD);
        top.addView(appName, new LinearLayout.LayoutParams(0, -2, 1));

        Button themeButton = smallButton("◐");
        themeButton.setContentDescription("Change appearance");
        top.addView(themeButton);
        themeButton.setOnClickListener(v -> {
            String current = activity.getPreferences(0)
                    .getString("theme", "system");
            String next = "system".equals(current) ? "light"
                    : "light".equals(current) ? "dark" : "system";
            activity.getPreferences(0).edit()
                    .putString("theme", next).apply();
            activity.recreate();
        });

        clock = new TextView(activity);
        clock.setTextColor(secondaryText);
        clock.setTextSize(12);
        clock.setPadding(0, dp(7), 0, 0);
        header.addView(clock);
        handler.post(clockTask);

        pageTitle = new TextView(activity);
        pageTitle.setText("Bike Battery Details");
        pageTitle.setTextColor(textColor);
        pageTitle.setTextSize(17);
        pageTitle.setTypeface(null, Typeface.BOLD);
        pageTitle.setPadding(0, dp(10), 0, 0);
        header.addView(pageTitle);

        status = new TextView(activity);
        status.setText("Bluetooth not connected");
        status.setTextColor(secondaryText);
        status.setTextSize(12);
        status.setPadding(dp(20), dp(9), dp(20), dp(9));
        root.addView(status);

        pageHost = new FrameLayout(activity);
        root.addView(pageHost, new LinearLayout.LayoutParams(-1, 0, 1));

        batteryPage = new LinearLayout(activity);
        batteryPage.setOrientation(LinearLayout.VERTICAL);
        batteryPage.setBackgroundColor(background);

        FrameLayout batteryFrame = new FrameLayout(activity);

        ScrollView batteryScroll = new ScrollView(activity);
        batteryScroll.setFillViewport(true);
        batteryContent = new LinearLayout(activity);
        batteryContent.setOrientation(LinearLayout.VERTICAL);
        batteryContent.setPadding(dp(16), dp(8), dp(16), dp(24));
        batteryScroll.addView(batteryContent);

        batteryFrame.addView(batteryScroll,
                new FrameLayout.LayoutParams(-1, -1));

        historyButton = new Button(activity);
        historyButton.setText("◷");
        historyButton.setTextSize(27);
        historyButton.setTextColor(Color.WHITE);
        historyButton.setAllCaps(false);
        historyButton.setContentDescription("Open battery history");

        GradientDrawable historyCircle = new GradientDrawable();
        historyCircle.setShape(GradientDrawable.OVAL);
        historyCircle.setColor(accent);
        historyCircle.setStroke(dp(2), surface);
        historyButton.setBackground(historyCircle);
        historyButton.setElevation(dp(8));

        FrameLayout.LayoutParams historyButtonParams =
                new FrameLayout.LayoutParams(dp(58), dp(58),
                        Gravity.BOTTOM | Gravity.END);
        historyButtonParams.setMargins(0, 0, dp(18), dp(18));
        batteryFrame.addView(historyButton, historyButtonParams);

        batteryPage.addView(batteryFrame,
                new LinearLayout.LayoutParams(-1, -1));

        pairPage = new LinearLayout(activity);
        pairPage.setOrientation(LinearLayout.VERTICAL);
        pairPage.setPadding(dp(16), dp(8), dp(16), dp(12));
        pairPage.setBackgroundColor(background);

        TextView pairHeading = heading("Pair your BMS");
        pairPage.addView(pairHeading);

        TextView pairDescription = label(
                "Turn on your bike battery BMS Bluetooth, then scan for nearby devices.");
        pairDescription.setPadding(dp(2), 0, dp(2), dp(12));
        pairPage.addView(pairDescription);

        LinearLayout currentCard = card();
        currentCard.addView(label("CURRENTLY CONNECTED"));
        currentDeviceValue = value("Not connected");
        currentCard.addView(currentDeviceValue);
        pairPage.addView(currentCard);

        LinearLayout autoCard = card();
        LinearLayout.LayoutParams autoCardParams =
                new LinearLayout.LayoutParams(-1, -2);
        autoCardParams.setMargins(0, dp(7), 0, dp(7));
        autoCard.addView(label("AUTO-CONNECT DEVICE"));
        autoConnectValue = value("Not selected");
        autoCard.addView(autoConnectValue);
        pairPage.addView(autoCard, autoCardParams);

        scan = new Button(activity);
        scan.setText("SCAN FOR DEVICES");
        pairPage.addView(scan);

        disconnect = new Button(activity);
        disconnect.setText("DISCONNECT");
        pairPage.addView(disconnect);

        pairPage.addView(heading("Nearby devices"));

        ScrollView deviceScroll = new ScrollView(activity);
        deviceList = new LinearLayout(activity);
        deviceList.setOrientation(LinearLayout.VERTICAL);
        deviceScroll.addView(deviceList);
        pairPage.addView(deviceScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        debugPage = new LinearLayout(activity);
        debugPage.setOrientation(LinearLayout.VERTICAL);
        debugPage.setPadding(dp(12), dp(8), dp(12), dp(8));
        debugPage.setBackgroundColor(background);

        debugPage.addView(heading("Debug & connection log"));

        logFormat = new Spinner(activity);
        String[] formats = {"Readable", "HEX", "Binary", "ASCII", "Decoded"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                activity, android.R.layout.simple_spinner_item, formats);
        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        logFormat.setAdapter(adapter);
        debugPage.addView(logFormat);

        LinearLayout logActions = new LinearLayout(activity);
        copyLog = new Button(activity);
        copyLog.setText("COPY ALL");
        logActions.addView(copyLog, new LinearLayout.LayoutParams(0, -2, 1));

        clearLog = new Button(activity);
        clearLog.setText("CLEAR");
        logActions.addView(clearLog, new LinearLayout.LayoutParams(0, -2, 1));
        debugPage.addView(logActions);

        ScrollView logScroll = new ScrollView(activity);
        log = new TextView(activity);
        log.setTextColor(textColor);
        log.setTextSize(12);
        log.setTypeface(Typeface.MONOSPACE);
        log.setTextIsSelectable(true);
        log.setPadding(dp(10), dp(10), dp(10), dp(20));
        log.setText("No debug events yet.");
        logScroll.addView(log);
        debugPage.addView(logScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        controlPage = new LinearLayout(activity);
        controlPage.setOrientation(LinearLayout.VERTICAL);
        controlPage.setPadding(dp(16), dp(8), dp(16), dp(16));
        controlPage.setBackgroundColor(background);

        controlPage.addView(heading("Protected BMS Control"));

        TextView controlWarning = label(
                "WARNING: BMS commands can modify battery protection, charging, " +
                "and discharge behavior. Incorrect settings may damage the battery " +
                "or create a safety hazard.");
        controlWarning.setTextColor(danger);
        controlWarning.setTextSize(14);
        controlPage.addView(controlWarning);

        LinearLayout controlInfo = card();
        controlInfo.addView(value("Write controls are currently locked"));

        TextView controlReason = label(
                "BMS write registers and command payloads are not yet verified. " +
                "This version provides status information only.");
        controlReason.setPadding(0, dp(8), 0, 0);
        controlInfo.addView(controlReason);
        controlPage.addView(controlInfo);

        controlPage.addView(heading("Live BMS status"));

        controlBluetoothValue = addControlStatus(
                "Bluetooth", "DISCONNECTED");
        controlChargeMosValue = addControlStatus(
                "Charge MOS raw code", "UNKNOWN");
        controlDischargeMosValue = addControlStatus(
                "Discharge MOS raw code", "UNKNOWN");
        controlBalancingValue = addControlStatus(
                "Balancing", "UNKNOWN");
        controlMosTemperatureValue = addControlStatus(
                "MOS temperature", "UNKNOWN");
        controlLastUpdateValue = addControlStatus(
                "Last FC update", "Never");

        controlPage.addView(heading("Key Lock / Operating Mode"));

        TextView keyLockInfo = label(
                "Select a mode to view its setting. Changes are currently disabled " +
                "until the BMS register mapping and read-back protocol are verified.");
        keyLockInfo.setPadding(0, dp(4), 0, dp(8));
        controlPage.addView(keyLockInfo);

        String[][] keyLockModes = {
                {"Prohibit", "0055"},
                {"Discharge + Sleep", "00A5"},
                {"Discharge", "005A"},
                {"Charge + Discharge", "00AA"},
                {"Charge + Discharge + Sleep", "00A6"}
        };

        for (String[] mode : keyLockModes) {
            Button modeButton = new Button(activity);
            modeButton.setText(mode[0]);
            modeButton.setAllCaps(false);
            modeButton.setEnabled(true);
            modeButton.setOnClickListener(v ->
                    new android.app.AlertDialog.Builder(activity)
                            .setTitle(mode[0])
                            .setMessage(
                                    "BMS mode value: 0x" + mode[1] +
                                    "\n\nThis control is read-only for now. " +
                                    "No command has been sent to the BMS.")
                            .setPositiveButton("OK", null)
                            .show());
            controlPage.addView(modeButton,
                    new LinearLayout.LayoutParams(-1, -2));
        }

        Button unavailableControl = new Button(activity);
        unavailableControl.setText("WRITE CONTROLS — NOT VERIFIED");
        unavailableControl.setEnabled(false);
        controlPage.addView(unavailableControl);

        historyPage = new LinearLayout(activity);
        historyPage.setOrientation(LinearLayout.VERTICAL);
        historyPage.setPadding(dp(16), dp(8), dp(16), dp(12));
        historyPage.setBackgroundColor(background);

        LinearLayout historyHeader = new LinearLayout(activity);
        historyHeader.setGravity(Gravity.CENTER_VERTICAL);

        historyBack = smallButton("← Back");
        historyHeader.addView(historyBack);

        TextView historyTitle = heading("Battery History");
        historyHeader.addView(historyTitle,
                new LinearLayout.LayoutParams(0, -2, 1));
        historyPage.addView(historyHeader);

        ScrollView historyScroll = new ScrollView(activity);
        historyContent = new LinearLayout(activity);
        historyContent.setOrientation(LinearLayout.VERTICAL);
        historyScroll.addView(historyContent);
        historyPage.addView(historyScroll,
                new LinearLayout.LayoutParams(-1, 0, 1));

        pageHost.addView(batteryPage);

        pairFrame = new FrameLayout(activity);
        pairFrame.addView(pairPage,
                new FrameLayout.LayoutParams(-1, -1));

        deviceHistoryButton = new Button(activity);
        deviceHistoryButton.setText("◷");
        deviceHistoryButton.setTextSize(25);
        deviceHistoryButton.setTextColor(Color.WHITE);
        deviceHistoryButton.setAllCaps(false);
        deviceHistoryButton.setContentDescription("Open Bluetooth device history");

        GradientDrawable deviceHistoryCircle = new GradientDrawable();
        deviceHistoryCircle.setShape(GradientDrawable.OVAL);
        deviceHistoryCircle.setColor(accent);
        deviceHistoryCircle.setStroke(dp(2), surface);
        deviceHistoryButton.setBackground(deviceHistoryCircle);
        deviceHistoryButton.setElevation(dp(8));

        FrameLayout.LayoutParams deviceHistoryParams =
                new FrameLayout.LayoutParams(dp(56), dp(56),
                        Gravity.BOTTOM | Gravity.END);
        deviceHistoryParams.setMargins(0, 0, dp(18), dp(18));
        pairFrame.addView(deviceHistoryButton, deviceHistoryParams);

        pageHost.addView(pairFrame);
        pageHost.addView(debugPage);
        pageHost.addView(historyPage);
        pageHost.addView(controlPage);

        LinearLayout navigation = new LinearLayout(activity);
        navigation.setOrientation(LinearLayout.HORIZONTAL);
        navigation.setGravity(Gravity.CENTER);
        navigation.setBackgroundColor(surface);
        navigation.setElevation(dp(5));
        root.addView(navigation);

        batteryTab = navButton("▣\nBike Battery");
        pairTab = navButton("⌁\nPair");
        debugTab = navButton("≡\nLog");
        controlTab = navButton("⚙\nControl");

        navigation.addView(batteryTab, new LinearLayout.LayoutParams(0, dp(54), 1));
        navigation.addView(pairTab, new LinearLayout.LayoutParams(0, dp(54), 1));
        navigation.addView(debugTab, new LinearLayout.LayoutParams(0, dp(54), 1));
        navigation.addView(controlTab, new LinearLayout.LayoutParams(0, dp(54), 1));

        showPage(PAIR);
    }

    private TextView addControlStatus(String name, String initialValue) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(4), dp(7), dp(4), dp(7));

        TextView title = label(name);
        TextView result = value(initialValue);

        row.addView(title);
        row.addView(result);
        controlPage.addView(row);

        return result;
    }

    private String formatMosStatus(int raw) {
        if (raw == 1) return "ON";
        if (raw == 0) return "OFF";
        return "UNKNOWN (raw: " + raw + ")";
    }

    public void updateControlStatus(BatteryData data, boolean connected) {
        if (controlBluetoothValue == null) return;

        controlBluetoothValue.setText(
                connected ? "CONNECTED" : "DISCONNECTED");

        if (data == null || !data.fcTelemetryAvailable) {
            controlChargeMosValue.setText("UNKNOWN");
            controlDischargeMosValue.setText("UNKNOWN");
            controlBalancingValue.setText("UNKNOWN");
            controlMosTemperatureValue.setText("UNKNOWN");
            controlLastUpdateValue.setText("No FC telemetry");
            return;
        }

        // Verified against the original app's explicit value == 1 checks.
        controlChargeMosValue.setText(
                formatMosStatus(data.chargingMos));
        controlDischargeMosValue.setText(
                formatMosStatus(data.dischargeMos));

        controlBalancingValue.setText(
                !data.balancingAvailable ? "UNKNOWN"
                : data.balancingEnabled ? "ACTIVE" : "INACTIVE");

        controlMosTemperatureValue.setText(
                data.mosTemperatureAvailable
                ? data.mosTemperature + " °C"
                : "UNKNOWN");

        controlLastUpdateValue.setText(
                data.lastFcReceivedAt > 0
                ? new SimpleDateFormat(
                        "dd MMM HH:mm:ss", Locale.getDefault())
                        .format(new Date(data.lastFcReceivedAt))
                : "UNKNOWN");
    }

    public void updateDeviceCards(String connected, String autoConnect) {
        if (currentDeviceValue != null) {
            currentDeviceValue.setText(
                    connected == null || connected.isEmpty()
                            ? "Not connected" : connected);
        }
        if (autoConnectValue != null) {
            autoConnectValue.setText(
                    autoConnect == null || autoConnect.isEmpty()
                            ? "Not selected" : autoConnect);
        }
    }

    public void stopClock() {
        handler.removeCallbacks(clockTask);
    }

    public int dp(float value) {
        return (int) (value * activity.getResources()
                .getDisplayMetrics().density + 0.5f);
    }

    public GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), border);
        return drawable;
    }

    public LinearLayout card() {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(rounded(surface, 20));
        return card;
    }

    public TextView heading(String value) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextColor(textColor);
        view.setTextSize(17);
        view.setTypeface(null, Typeface.BOLD);
        view.setPadding(dp(2), dp(14), dp(2), dp(9));
        return view;
    }

    public TextView label(String value) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextColor(secondaryText);
        view.setTextSize(13);
        return view;
    }

    public TextView value(String value) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextColor(textColor);
        view.setTextSize(16);
        view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    public Button smallButton(String value) {
        Button button = new Button(activity);
        button.setText(value);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setMinHeight(dp(40));
        button.setMinimumHeight(dp(40));
        button.setPadding(dp(8), 0, dp(8), 0);
        return button;
    }

    private Button navButton(String value) {
        Button button = smallButton(value);
        button.setTextSize(11);
        button.setMinHeight(dp(54));
        button.setMinimumHeight(dp(54));
        button.setBackgroundColor(Color.TRANSPARENT);
        return button;
    }

    public void showPage(int page) {
        batteryPage.setVisibility(page == BATTERY ? View.VISIBLE : View.GONE);
        pairFrame.setVisibility(page == PAIR ? View.VISIBLE : View.GONE);
        debugPage.setVisibility(page == DEBUG ? View.VISIBLE : View.GONE);
        historyPage.setVisibility(page == HISTORY ? View.VISIBLE : View.GONE);
        controlPage.setVisibility(page == CONTROL ? View.VISIBLE : View.GONE);

        pageTitle.setText(page == BATTERY ? "Bike Battery Details"
                : page == PAIR ? "Pair Device"
                : page == DEBUG ? "Connection Log"
                : page == CONTROL ? "Protected BMS Control"
                : "Battery History");

        batteryTab.setTextColor(page == BATTERY ? accent : secondaryText);
        pairTab.setTextColor(page == PAIR ? accent : secondaryText);
        debugTab.setTextColor(page == DEBUG ? accent : secondaryText);
        controlTab.setTextColor(page == CONTROL ? accent : secondaryText);
    }
}
