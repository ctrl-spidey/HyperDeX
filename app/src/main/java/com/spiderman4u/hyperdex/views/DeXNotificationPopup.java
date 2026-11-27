package com.spiderman4u.hyperdex.views;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import com.spiderman4u.hyperdex.config.ConfigManager;
import com.spiderman4u.hyperdex.config.Constants;
import com.spiderman4u.hyperdex.services.DeXNotificationListener;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DeXNotificationPopup {

    private static PopupWindow sCurrentPopup = null;
    private static final List<SimpleNotificationItem> sRecentNotifications = Collections.synchronizedList(new ArrayList<>());

    public static class SimpleNotificationItem {
        public String packageName;
        public String title;
        public String text;
        public String time;
        public String key;
    }

    public static int getCount() {
        synchronized (sRecentNotifications) {
            return sRecentNotifications.size();
        }
    }

    public static void addNotification(String pkg, String title, String text, String time) {
        addNotification(pkg, title, text, time, null);
    }

    public static void addNotification(String pkg, String title, String text, String time, String key) {
        if (TextUtils.isEmpty(pkg)) return;
        SimpleNotificationItem item = new SimpleNotificationItem();
        item.packageName = pkg;
        item.title = title != null ? title : "";
        item.text = text != null ? text : "";
        item.time = time != null ? time : "now";
        item.key = key;

        synchronized (sRecentNotifications) {
            for (int i = sRecentNotifications.size() - 1; i >= 0; i--) {
                SimpleNotificationItem existing = sRecentNotifications.get(i);
                boolean sameKey = key != null && !key.isEmpty() && key.equals(existing.key);
                boolean sameContent = pkg.equals(existing.packageName) && item.title.equals(existing.title);
                if (sameKey || sameContent) {
                    sRecentNotifications.remove(i);
                }
            }
            sRecentNotifications.add(0, item);
            if (sRecentNotifications.size() > 25) {
                sRecentNotifications.remove(sRecentNotifications.size() - 1);
            }
        }
    }

    public static void removeNotification(String key, String pkg) {
        synchronized (sRecentNotifications) {
            for (int i = sRecentNotifications.size() - 1; i >= 0; i--) {
                SimpleNotificationItem existing = sRecentNotifications.get(i);
                if (key != null && !key.isEmpty() && key.equals(existing.key)) {
                    sRecentNotifications.remove(i);
                } else if ((key == null || key.isEmpty()) && pkg != null && pkg.equals(existing.packageName)) {
                    sRecentNotifications.remove(i);
                }
            }
        }
    }

    public static void clearAll() {
        synchronized (sRecentNotifications) {
            sRecentNotifications.clear();
        }
    }

    private static void syncFromCacheFileIfEmpty() {
        synchronized (sRecentNotifications) {
            if (!sRecentNotifications.isEmpty()) return;
            try {
                File file = new File(DeXNotificationListener.NOTIF_FILE_PATH);
                if (!file.exists() || !file.canRead()) return;
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                }
                JSONArray array = new JSONArray(sb.toString());
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.optJSONObject(i);
                    if (obj == null) continue;
                    SimpleNotificationItem item = new SimpleNotificationItem();
                    item.packageName = obj.optString("pkg", "");
                    item.title = obj.optString("title", "");
                    item.text = obj.optString("text", "");
                    item.time = obj.optString("time", "now");
                    if (!TextUtils.isEmpty(item.packageName)) {
                        sRecentNotifications.add(item);
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    public static void show(Context context, View anchorView) {
        show(context, anchorView, null);
    }

    public static void show(Context context, View anchorView, DeXSystemTrayView trayView) {
        if (sCurrentPopup != null && sCurrentPopup.isShowing()) {
            sCurrentPopup.dismiss();
            sCurrentPopup = null;
            return;
        }

        syncFromCacheFileIfEmpty();
        if (trayView != null) {
            trayView.updateNotificationBadge(getCount());
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

        // 1. Header (Notifications title, Settings, Clear button)
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

        final LinearLayout listContainer = new LinearLayout(context);
        listContainer.setOrientation(LinearLayout.VERTICAL);

        btnClear.setOnClickListener(v -> {
            clearAll();
            try {
                Intent sysIntent = new Intent(Constants.ACTION_CLEAR_NOTIFICATIONS);
                sysIntent.setPackage("com.android.systemui");
                context.sendBroadcast(sysIntent);

                Intent svcIntent = new Intent(Constants.ACTION_CLEAR_NOTIFICATIONS);
                svcIntent.setPackage(Constants.PACKAGE_NAME);
                context.sendBroadcast(svcIntent);
            } catch (Throwable ignored) {}
            if (trayView != null) {
                trayView.updateNotificationBadge(0);
            }
            listContainer.removeAllViews();
            populateEmptyState(context, listContainer);
        });
        header.addView(btnClear);

        root.addView(header);
        addVerticalSpace(root, context, 12);

        // 2. Notification List / ScrollView
        ScrollView scrollView = new ScrollView(context);

        synchronized (sRecentNotifications) {
            if (sRecentNotifications.isEmpty()) {
                populateEmptyState(context, listContainer);
            } else {
                for (SimpleNotificationItem item : new ArrayList<>(sRecentNotifications)) {
                    View card = createNotificationCard(context, item, listContainer, trayView);
                    listContainer.addView(card);
                }
            }
        }

        scrollView.addView(listContainer);
        LinearLayout.LayoutParams lpScroll = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(scrollView, lpScroll);

        int maxHeight = dp(context, 420);

        PopupWindow popup = new PopupWindow(root, width, maxHeight, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setFocusable(true);
        popup.setElevation(dp(context, 16));

        int xOffset = dp(context, 12);
        int yOffset = dp(context, ConfigManager.getTaskbarHeight(context) + Math.max(0, ConfigManager.getTaskbarBottomOffset(context)) + 8);
        DeXQuickSettingsPopup.dismiss();
        DeXClockPopup.dismiss();
        popup.showAtLocation(anchorView, Gravity.BOTTOM | Gravity.END, xOffset, yOffset);
        sCurrentPopup = popup;

        // Smooth macOS-like popup open animation
        root.setAlpha(0f);
        root.setScaleX(0.92f);
        root.setScaleY(0.92f);
        root.setTranslationY(dp(context, 14));
        root.animate()
                .alpha(1.0f)
                .scaleX(1.0f)
                .scaleY(1.0f)
                .translationY(0f)
                .setDuration(240)
                .setInterpolator(new PathInterpolator(0.22f, 1.0f, 0.36f, 1.0f))
                .start();
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

    private static View createNotificationCard(Context context, SimpleNotificationItem item, LinearLayout listContainer, DeXSystemTrayView trayView) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10));

        LinearLayout.LayoutParams lpCard = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpCard.bottomMargin = dp(context, 8);
        card.setLayoutParams(lpCard);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor("#262830"));
        cardBg.setCornerRadius(dp(context, 12));
        RippleDrawable rippleBg = new RippleDrawable(ColorStateList.valueOf(Color.parseColor("#22FFFFFF")), cardBg, null);
        card.setBackground(rippleBg);

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

        // Single Notification Dismiss [ × ] button
        TextView btnDismiss = new TextView(context);
        btnDismiss.setText("✕");
        btnDismiss.setTextColor(Color.parseColor("#8A8D98"));
        btnDismiss.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        btnDismiss.setPadding(dp(context, 8), dp(context, 2), dp(context, 4), dp(context, 2));
        btnDismiss.setOnClickListener(v -> {
            synchronized (sRecentNotifications) {
                sRecentNotifications.remove(item);
            }
            if (!TextUtils.isEmpty(item.key)) {
                try {
                    Intent dismissIntent = new Intent(Constants.ACTION_DISMISS_NOTIFICATION);
                    dismissIntent.setPackage("com.android.systemui");
                    dismissIntent.putExtra("key", item.key);
                    context.sendBroadcast(dismissIntent);
                } catch (Throwable ignored) {}
            }
            if (trayView != null) {
                trayView.updateNotificationBadge(getCount());
            }
            card.animate()
                    .alpha(0f)
                    .translationX(dp(context, 40))
                    .setDuration(180)
                    .withEndAction(() -> {
                        listContainer.removeView(card);
                        if (listContainer.getChildCount() == 0) {
                            populateEmptyState(context, listContainer);
                        }
                    })
                    .start();
        });
        appHeader.addView(btnDismiss);

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

    private static void addVerticalSpace(LinearLayout parent, Context context, int dpVal) {
        View space = new View(context);
        parent.addView(space, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, dpVal)));
    }

    private static int dp(Context context, float val) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, val, context.getResources().getDisplayMetrics());
    }
}
