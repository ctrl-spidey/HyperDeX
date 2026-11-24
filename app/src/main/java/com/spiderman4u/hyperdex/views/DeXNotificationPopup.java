package com.spiderman4u.hyperdex.views;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import com.spiderman4u.hyperdex.config.ConfigManager;
import com.spiderman4u.hyperdex.services.DeXNotificationListener;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class DeXNotificationPopup {

    private static PopupWindow sCurrentPopup = null;

    public static class SimpleNotificationItem {
        public String packageName;
        public String title;
        public String text;
        public String time;
    }

    public static void show(Context context, View anchorView) {
        if (sCurrentPopup != null && sCurrentPopup.isShowing()) {
            sCurrentPopup.dismiss();
            sCurrentPopup = null;
            return;
        }

        int width = dp(context, 360);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 14));

        // Acrylic Dark Card Background
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#F2181A20"));
        bg.setCornerRadius(dp(context, 16));
        bg.setStroke(dp(context, 1), Color.parseColor("#33FFFFFF"));
        root.setBackground(bg);

        // 1. Header (Notifications title, Notification settings, Clear button)
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvTitle = new TextView(context);
        tvTitle.setText("Notifications");
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(tvTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Settings Button
        TextView btnSettings = new TextView(context);
        btnSettings.setText("Settings");
        btnSettings.setTextColor(Color.parseColor("#A0A2AA"));
        btnSettings.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btnSettings.setPadding(dp(context, 8), dp(context, 4), dp(context, 8), dp(context, 4));
        btnSettings.setOnClickListener(v -> {
            dismiss();
            try {
                Intent intent = new Intent(Settings.ACTION_ALL_APPS_NOTIFICATION_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Throwable ignored) {}
        });
        header.addView(btnSettings);

        // Clear Button
        TextView btnClear = new TextView(context);
        btnClear.setText("Clear");
        btnClear.setTextColor(Color.WHITE);
        btnClear.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btnClear.setPadding(dp(context, 12), dp(context, 4), dp(context, 12), dp(context, 4));

        GradientDrawable clearBg = new GradientDrawable();
        clearBg.setColor(Color.parseColor("#33FFFFFF"));
        clearBg.setCornerRadius(dp(context, 12));
        btnClear.setBackground(clearBg);
        btnClear.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(DeXNotificationListener.ACTION_CLEAR_NOTIFICATIONS);
                context.sendBroadcast(intent);
            } catch (Throwable ignored) {}
            dismiss();
        });
        header.addView(btnClear);

        root.addView(header);

        addVerticalSpace(root, context, 12);

        // 2. Notification List / ScrollView
        ScrollView scrollView = new ScrollView(context);
        LinearLayout listContainer = new LinearLayout(context);
        listContainer.setOrientation(LinearLayout.VERTICAL);

        // Initial loading/empty state
        populateEmptyState(context, listContainer);

        // Asynchronously load notifications
        new Thread(() -> {
            List<SimpleNotificationItem> items = loadRecentNotifications(context);
            new Handler(Looper.getMainLooper()).post(() -> {
                if (sCurrentPopup != null && sCurrentPopup.isShowing()) {
                    listContainer.removeAllViews();
                    if (items.isEmpty()) {
                        populateEmptyState(context, listContainer);
                    } else {
                        for (SimpleNotificationItem item : items) {
                            View card = createNotificationCard(context, item);
                            listContainer.addView(card);
                            addVerticalSpace(listContainer, context, 8);
                        }
                    }
                }
            });
        }).start();

        scrollView.addView(listContainer);
        LinearLayout.LayoutParams lpScroll = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(scrollView, lpScroll);

        // Max height constraint
        int maxHeight = dp(context, 420);

        // Create PopupWindow
        PopupWindow popup = new PopupWindow(root, width, maxHeight, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setFocusable(true);
        popup.setElevation(dp(context, 16));

        int xOffset = dp(context, 12);
        int yOffset = dp(context, ConfigManager.getTaskbarHeight(context) + 6);
        popup.showAtLocation(anchorView, Gravity.BOTTOM | Gravity.END, xOffset, yOffset);
        sCurrentPopup = popup;
    }

    private static void populateEmptyState(Context context, LinearLayout listContainer) {
        LinearLayout emptyLayout = new LinearLayout(context);
        emptyLayout.setOrientation(LinearLayout.VERTICAL);
        emptyLayout.setGravity(Gravity.CENTER);
        emptyLayout.setPadding(0, dp(context, 32), 0, dp(context, 32));

        ImageView ivEmpty = new ImageView(context);
        ivEmpty.setImageDrawable(DeXVectorDrawables.createBellDrawable(Color.parseColor("#60636D")));
        emptyLayout.addView(ivEmpty, new LinearLayout.LayoutParams(dp(context, 36), dp(context, 36)));

        TextView tvEmpty = new TextView(context);
        tvEmpty.setText("No new notifications");
        tvEmpty.setTextColor(Color.parseColor("#80838D"));
        tvEmpty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        LinearLayout.LayoutParams lpEmptyText = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpEmptyText.topMargin = dp(context, 8);
        emptyLayout.addView(tvEmpty, lpEmptyText);

        listContainer.addView(emptyLayout);
    }

    public static void dismiss() {
        if (sCurrentPopup != null) {
            try {
                sCurrentPopup.dismiss();
            } catch (Throwable ignored) {}
            sCurrentPopup = null;
        }
    }

    private static View createNotificationCard(Context context, SimpleNotificationItem item) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10));

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor("#262830"));
        cardBg.setCornerRadius(dp(context, 12));
        card.setBackground(cardBg);

        // App Header Row
        LinearLayout appHeader = new LinearLayout(context);
        appHeader.setOrientation(LinearLayout.HORIZONTAL);
        appHeader.setGravity(Gravity.CENTER_VERTICAL);

        ImageView ivAppIcon = new ImageView(context);
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(item.packageName, 0);
            Drawable appIcon = pm.getApplicationIcon(ai);
            ivAppIcon.setImageDrawable(appIcon);
        } catch (Throwable t) {
            ivAppIcon.setImageDrawable(DeXVectorDrawables.createBellDrawable(Color.parseColor("#0A84FF")));
        }
        appHeader.addView(ivAppIcon, new LinearLayout.LayoutParams(dp(context, 16), dp(context, 16)));

        TextView tvAppName = new TextView(context);
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(item.packageName, 0);
            tvAppName.setText(pm.getApplicationLabel(ai));
        } catch (Throwable t) {
            tvAppName.setText(item.packageName);
        }
        tvAppName.setTextColor(Color.parseColor("#90939E"));
        tvAppName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        LinearLayout.LayoutParams lpAppName = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lpAppName.setMarginStart(dp(context, 6));
        appHeader.addView(tvAppName, lpAppName);

        if (item.time != null && !item.time.isEmpty()) {
            TextView tvTime = new TextView(context);
            tvTime.setText(item.time);
            tvTime.setTextColor(Color.parseColor("#70737E"));
            tvTime.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            appHeader.addView(tvTime);
        }

        card.addView(appHeader);

        // Title
        if (item.title != null && !item.title.isEmpty()) {
            TextView tvTitle = new TextView(context);
            tvTitle.setText(item.title);
            tvTitle.setTextColor(Color.WHITE);
            tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams lpTitle = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lpTitle.topMargin = dp(context, 4);
            card.addView(tvTitle, lpTitle);
        }

        // Text
        if (item.text != null && !item.text.isEmpty()) {
            TextView tvContent = new TextView(context);
            tvContent.setText(item.text);
            tvContent.setTextColor(Color.parseColor("#C0C3CC"));
            tvContent.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            LinearLayout.LayoutParams lpText = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lpText.topMargin = dp(context, 2);
            card.addView(tvContent, lpText);
        }

        // Tap on card launches app
        card.setOnClickListener(v -> {
            dismiss();
            try {
                Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(item.packageName);
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(launchIntent);
                }
            } catch (Throwable ignored) {}
        });

        return card;
    }

    private static List<SimpleNotificationItem> loadRecentNotifications(Context context) {
        List<SimpleNotificationItem> list = new ArrayList<>();
        File file = new File(DeXNotificationListener.NOTIF_FILE_PATH);
        if (file.exists() && file.canRead()) {
            try {
                FileInputStream fis = new FileInputStream(file);
                byte[] data = new byte[(int) file.length()];
                fis.read(data);
                fis.close();

                String jsonStr = new String(data, StandardCharsets.UTF_8);
                JSONArray array = new JSONArray(jsonStr);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    SimpleNotificationItem item = new SimpleNotificationItem();
                    item.packageName = obj.optString("pkg");
                    item.title = obj.optString("title");
                    item.text = obj.optString("text");
                    item.time = obj.optString("time");
                    list.add(item);
                }
            } catch (Throwable ignored) {}
        }
        return list;
    }

    private static void addVerticalSpace(LinearLayout parent, Context context, int dpVal) {
        View space = new View(context);
        parent.addView(space, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, dpVal)));
    }

    private static int dp(Context context, float val) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, val, context.getResources().getDisplayMetrics());
    }
}
