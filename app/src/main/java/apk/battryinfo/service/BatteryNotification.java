package apk.battryinfo.service;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import apk.battryinfo.MainActivity;
import apk.battryinfo.model.BatteryData;

import java.util.Locale;

public final class BatteryNotification {
    private static final String CHANNEL_ID = "battery_live";
    private static final int NOTIFICATION_ID = 4101;

    private final Context context;
    private final NotificationManager manager;

    public BatteryNotification(Context context) {
        this.context = context.getApplicationContext();
        manager = (NotificationManager)
                this.context.getSystemService(Context.NOTIFICATION_SERVICE);
        createChannel();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26 && manager != null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Live BMS battery",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Live battery percentage from the connected BMS");
            channel.setShowBadge(false);
            manager.createNotificationChannel(channel);
        }
    }

    public void showConnected(BatteryData data) {
        update(data);
    }

    public void update(BatteryData data) {
        if (manager == null || !canNotify()) return;

        boolean available = data != null && data.reportedSocAvailable;
        int percent = available
                ? Math.max(0, Math.min(100, (int) Math.round(data.reportedSoc)))
                : 0;

        String text = available
                ? String.format(Locale.US, "BMS remaining charge: %d%%", percent)
                : "Connected — waiting for BMS charge data";

        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP |
                        Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 4101, intent,
                PendingIntent.FLAG_UPDATE_CURRENT |
                (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);

        builder.setSmallIcon(android.R.drawable.ic_lock_idle_charging)
                .setContentTitle("BatteryINFO • BMS connected")
                .setContentText(text)
                .setSubText("Live BMS-reported SOC")
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_STATUS);

        if (available) {
            builder.setProgress(100, percent, false);
        } else {
            builder.setProgress(100, 0, true);
        }

        try {
            manager.notify(NOTIFICATION_ID, builder.build());
        } catch (SecurityException ignored) {
        }
    }

    private boolean canNotify() {
        return Build.VERSION.SDK_INT < 33 ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        == PackageManager.PERMISSION_GRANTED;
    }

    public void stop() {
        if (manager != null) {
            manager.cancel(NOTIFICATION_ID);
        }
    }
}
