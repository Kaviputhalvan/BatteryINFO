package apk.battryinfo.service;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.speech.tts.TextToSpeech;

import java.util.Locale;

public final class BikeBatteryAlerts implements TextToSpeech.OnInitListener {

    private static final String CHANNEL_ID = "bike_battery_alerts";
    private static final String PREFS = "bike_battery_alert_state";

    private final Activity activity;
    private final SharedPreferences preferences;
    private final NotificationManager notifications;

    private TextToSpeech tts;
    private boolean ttsReady;

    public BikeBatteryAlerts(Activity activity) {
        this.activity = activity;
        preferences = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        notifications = (NotificationManager)
                activity.getSystemService(Context.NOTIFICATION_SERVICE);

        createChannel();
        tts = new TextToSpeech(activity.getApplicationContext(), this);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26 && notifications != null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Bike Battery Alerts",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Bike battery charge and low-charge warnings");
            notifications.createNotificationChannel(channel);
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS && tts != null) {
            int result = tts.setLanguage(Locale.forLanguageTag("en-IN"));
            ttsReady = result != TextToSpeech.LANG_MISSING_DATA
                    && result != TextToSpeech.LANG_NOT_SUPPORTED;
        }
    }

    public void evaluate(double soc) {
        if (!Double.isFinite(soc) || soc < 0.0 || soc > 100.0) return;

        // Reset the low warning only after the bike battery recovers to 20%.
        if (soc >= 20.0 &&
                preferences.getBoolean("low_alerted", false)) {
            preferences.edit().putBoolean("low_alerted", false).apply();
        }

        // Reset high-charge alerts after the battery drops below 95%.
        // This allows alerts again during a later charging cycle.
        if (soc < 95.0) {
            preferences.edit()
                    .putBoolean("alert_98", false)
                    .putBoolean("alert_100", false)
                    .apply();
        }

        if (soc < 20.0 &&
                !preferences.getBoolean("low_alerted", false)) {
            preferences.edit().putBoolean("low_alerted", true).apply();

            alert(
                    20,
                    "Bike battery below 20%",
                    "Your bike battery is below 20 percentage. Please charge soon.",
                    "Ungal bike battery 20 percentage-ku keezha irukku. Seekiram charge pannunga."
            );
        }

        if (soc >= 98.0 &&
                !preferences.getBoolean("alert_98", false)) {
            preferences.edit().putBoolean("alert_98", true).apply();

            alert(
                    98,
                    "Bike battery 98% charged",
                    "Your bike battery is charged 98 percentage. It will soon hit full.",
                    "Ungal bike battery 98 percentage full aaga pogirathu."
            );
        }

        if (soc >= 100.0 &&
                !preferences.getBoolean("alert_100", false)) {
            preferences.edit().putBoolean("alert_100", true).apply();

            alert(
                    100,
                    "Bike battery fully charged",
                    "Your bike battery is 100 percentage, fully charged.",
                    "Ungal bike battery 100 percentage full aagivittathu."
            );
        }
    }

    private void alert(int id, String title, String english, String tamilEnglish) {
        postNotification(id, title, english);
        speak(english + " " + tamilEnglish);
    }

    private void postNotification(int id, String title, String message) {
        if (notifications == null) return;

        if (Build.VERSION.SDK_INT >= 33 &&
                activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        try {
            Notification.Builder builder;

            if (Build.VERSION.SDK_INT >= 26) {
                builder = new Notification.Builder(activity, CHANNEL_ID);
            } else {
                builder = new Notification.Builder(activity);
            }

            Notification notification = builder
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setStyle(new Notification.BigTextStyle().bigText(message))
                    .setAutoCancel(true)
                    .setPriority(Notification.PRIORITY_DEFAULT)
                    .build();

            notifications.notify(id, notification);
        } catch (SecurityException ignored) {
            // Notification permission may have been denied.
        }
    }

    private void speak(String message) {
        if (ttsReady && tts != null) {
            tts.speak(message, TextToSpeech.QUEUE_ADD, null, "bike-alert-" +
                    System.currentTimeMillis());
        }
    }

    public void close() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        ttsReady = false;
    }
}
