package com.spiderman4u.hyperdex.services;

import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DeXNotificationListener extends NotificationListenerService {

    public static final String NOTIF_FILE_PATH = "/data/local/tmp/hyperdex_notifications.json";
    public static final String ACTION_CLEAR_NOTIFICATIONS = "com.spiderman4u.hyperdex.ACTION_CLEAR_NOTIFICATIONS";

    private final SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());

    private final BroadcastReceiver clearReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && ACTION_CLEAR_NOTIFICATIONS.equals(intent.getAction())) {
                cancelAllNotifications();
                updateNotificationCache();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        IntentFilter filter = new IntentFilter(ACTION_CLEAR_NOTIFICATIONS);
        registerReceiver(clearReceiver, filter, Context.RECEIVER_EXPORTED);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(clearReceiver);
        } catch (Throwable ignored) {}
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        updateNotificationCache();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        updateNotificationCache();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        updateNotificationCache();
    }

    private void updateNotificationCache() {
        try {
            StatusBarNotification[] sbns = getActiveNotifications();
            JSONArray array = new JSONArray();

            if (sbns != null) {
                for (StatusBarNotification sbn : sbns) {
                    if (sbn == null || sbn.getNotification() == null) continue;
                    Notification n = sbn.getNotification();
                    if ((n.flags & Notification.FLAG_ONGOING_EVENT) != 0) continue;

                    Bundle extras = n.extras;
                    CharSequence titleCs = extras != null ? extras.getCharSequence(Notification.EXTRA_TITLE) : null;
                    CharSequence textCs = extras != null ? extras.getCharSequence(Notification.EXTRA_TEXT) : null;
                    String title = titleCs != null ? titleCs.toString().trim() : "";
                    String text = textCs != null ? textCs.toString().trim() : "";
                    if (title.isEmpty() && text.isEmpty()) continue;

                    JSONObject obj = new JSONObject();
                    obj.put("pkg", sbn.getPackageName());
                    obj.put("title", title);
                    obj.put("text", text);
                    obj.put("time", timeFormat.format(new Date(sbn.getPostTime())));
                    array.put(obj);
                }
            }

            File file = new File(NOTIF_FILE_PATH);
            FileWriter writer = new FileWriter(file);
            writer.write(array.toString());
            writer.flush();
            writer.close();
            file.setReadable(true, false);
            file.setWritable(true, false);
        } catch (Throwable ignored) {}
    }
}
