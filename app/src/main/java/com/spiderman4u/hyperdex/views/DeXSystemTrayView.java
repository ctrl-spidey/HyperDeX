package com.spiderman4u.hyperdex.views;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.spiderman4u.hyperdex.config.ConfigManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DeXSystemTrayView extends LinearLayout {

    private TextView tvTime;
    private TextView tvDate;
    private TextView tvBattery;
    private ImageView ivBattery;
    private ImageView ivWifi;
    private ImageView ivNotification;
    private View vNotifBadge;
    private ImageView ivVolume;
    private ImageView ivScreenshot;
    private View vDesktopStrip;

    private DeXVectorDrawables.BatteryDrawable batteryDrawable;
    private boolean isReceiverRegistered = false;

    private final SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, MMM d", Locale.getDefault());

    private final BroadcastReceiver systemReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent != null ? intent.getAction() : null;
            if (Intent.ACTION_TIME_TICK.equals(action) || Intent.ACTION_TIME_CHANGED.equals(action) || Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
                updateClock();
            } else if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                boolean isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL);

                if (level >= 0 && scale > 0) {
                    int percent = (level * 100) / scale;
                    if (tvBattery != null) {
                        tvBattery.setText(percent + "%");
                    }
                    if (batteryDrawable != null) {
                        batteryDrawable.setLevelAndCharging(percent, isCharging);
                    }
                }
            }
        }
    };

    public DeXSystemTrayView(Context context) {
        super(context);
        init(context);
    }

    public DeXSystemTrayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        setPadding(dp(6), 0, dp(4), 0);

        int iconColor = Color.parseColor("#E1E2E8");
        int btnSize = dp(28);

        // 1. Screenshot Quick Capture Icon [ • ]
        ivScreenshot = new ImageView(context);
        ivScreenshot.setPadding(dp(4), dp(4), dp(4), dp(4));
        ivScreenshot.setImageDrawable(DeXVectorDrawables.createScreenshotDrawable(iconColor));
        ivScreenshot.setOnClickListener(v -> {
            animateTapBounce(v);
            DeXNavButtonsView.sendNavAction(context, "screenshot");
        });
        addView(ivScreenshot, new LayoutParams(btnSize, btnSize));

        // 2. Volume Speaker Icon
        ivVolume = new ImageView(context);
        ivVolume.setPadding(dp(4), dp(4), dp(4), dp(4));
        ivVolume.setImageDrawable(DeXVectorDrawables.createVolumeDrawable(iconColor, false));
        ivVolume.setOnClickListener(v -> {
            animateTapBounce(v);
            onQuickSettingsClicked(context, v);
        });
        LayoutParams lpVol = new LayoutParams(btnSize, btnSize);
        lpVol.setMarginStart(dp(2));
        addView(ivVolume, lpVol);

        // 3. Notification Bell Button + Unread Badge
        FrameLayout notifWrapper = new FrameLayout(context);
        notifWrapper.setId(com.spiderman4u.hyperdex.R.id.dex_tray_notification);
        ivNotification = new ImageView(context);
        ivNotification.setPadding(dp(4), dp(4), dp(4), dp(4));
        ivNotification.setImageDrawable(DeXVectorDrawables.createBellDrawable(iconColor));
        notifWrapper.addView(ivNotification, new FrameLayout.LayoutParams(btnSize, btnSize));

        vNotifBadge = new View(context);
        vNotifBadge.setId(com.spiderman4u.hyperdex.R.id.dex_tray_notif_badge);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setShape(GradientDrawable.OVAL);
        badgeBg.setColor(Color.parseColor("#0A84FF"));
        badgeBg.setStroke(dp(1), Color.parseColor("#FF141518"));
        vNotifBadge.setBackground(badgeBg);
        FrameLayout.LayoutParams lpBadge = new FrameLayout.LayoutParams(dp(7), dp(7));
        lpBadge.gravity = Gravity.TOP | Gravity.END;
        lpBadge.topMargin = dp(4);
        lpBadge.rightMargin = dp(4);
        vNotifBadge.setVisibility(DeXNotificationPopup.getCount() > 0 ? View.VISIBLE : View.GONE);
        notifWrapper.addView(vNotifBadge, lpBadge);

        notifWrapper.setOnClickListener(v -> {
            animateTapBounce(v);
            onNotificationClicked(context, v);
        });
        LayoutParams lpNotif = new LayoutParams(btnSize, btnSize);
        lpNotif.setMarginStart(dp(2));
        addView(notifWrapper, lpNotif);

        // 4. Wi-Fi Icon
        ivWifi = new ImageView(context);
        ivWifi.setPadding(dp(4), dp(4), dp(4), dp(4));
        ivWifi.setImageDrawable(DeXVectorDrawables.createWifiDrawable(iconColor));
        ivWifi.setOnClickListener(v -> onQuickSettingsClicked(context, v));
        LayoutParams lpWifi = new LayoutParams(btnSize, btnSize);
        lpWifi.setMarginStart(dp(2));
        addView(ivWifi, lpWifi);

        // 5. Battery Group (Horizontal Pill + Percentage)
        LinearLayout batteryLayout = new LinearLayout(context);
        batteryLayout.setOrientation(HORIZONTAL);
        batteryLayout.setGravity(Gravity.CENTER_VERTICAL);
        batteryLayout.setPadding(dp(4), 0, dp(2), 0);
        batteryLayout.setOnClickListener(v -> onQuickSettingsClicked(context, v));

        ivBattery = new ImageView(context);
        batteryDrawable = new DeXVectorDrawables.BatteryDrawable(iconColor);
        ivBattery.setImageDrawable(batteryDrawable);
        batteryLayout.addView(ivBattery, new LayoutParams(dp(22), dp(13)));

        tvBattery = new TextView(context);
        tvBattery.setTextColor(iconColor);
        tvBattery.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvBattery.setTypeface(null, Typeface.BOLD);
        tvBattery.setText("100%");
        tvBattery.setPadding(dp(4), 0, dp(2), 0);
        batteryLayout.addView(tvBattery);

        LayoutParams lpBattery = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        lpBattery.setMarginStart(dp(2));
        addView(batteryLayout, lpBattery);

        // 6. Clock and Date Group (2-line clean DeX typography without font padding clipping)
        LinearLayout clockLayout = new LinearLayout(context);
        clockLayout.setOrientation(VERTICAL);
        clockLayout.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        clockLayout.setPadding(dp(6), 0, dp(4), 0);
        clockLayout.setOnClickListener(v -> {
            animateTapBounce(v);
            openClock(context);
        });

        tvTime = new TextView(context);
        tvTime.setTextColor(Color.WHITE);
        tvTime.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        tvTime.setTypeface(null, Typeface.BOLD);
        tvTime.setIncludeFontPadding(false);
        clockLayout.addView(tvTime);

        tvDate = new TextView(context);
        tvDate.setTextColor(Color.parseColor("#A0A2AA"));
        tvDate.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f);
        tvDate.setIncludeFontPadding(false);
        LinearLayout.LayoutParams lpDate = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpDate.topMargin = dp(1);
        clockLayout.addView(tvDate, lpDate);

        addView(clockLayout, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 7. Far right "Show Desktop" touch zone + pill strip
        FrameLayout desktopStripBtn = new FrameLayout(context);
        desktopStripBtn.setPadding(dp(4), 0, dp(3), 0);
        desktopStripBtn.setOnClickListener(v -> {
            animateTapBounce(v);
            DeXNavButtonsView.sendNavAction(context, "minimize_all");
            triggerHome(context);
        });

        vDesktopStrip = new View(context);
        GradientDrawable stripBg = new GradientDrawable();
        stripBg.setColor(Color.parseColor("#59FFFFFF"));
        stripBg.setCornerRadius(dp(1.5f));
        vDesktopStrip.setBackground(stripBg);
        FrameLayout.LayoutParams lpPill = new FrameLayout.LayoutParams(dp(2.5f), dp(20));
        lpPill.gravity = Gravity.CENTER;
        desktopStripBtn.addView(vDesktopStrip, lpPill);

        addView(desktopStripBtn, new LayoutParams(dp(12), ViewGroup.LayoutParams.MATCH_PARENT));

        updateResponsiveLayout();
        updateClock();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        updateResponsiveLayout();
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    public void updateResponsiveLayout() {
        boolean isPortrait = getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT;
        if (ivScreenshot != null) {
            ivScreenshot.setVisibility(isPortrait ? View.GONE : View.VISIBLE);
        }
        if (ivVolume != null) {
            ivVolume.setVisibility(isPortrait ? View.GONE : View.VISIBLE);
        }
        if (tvDate != null) {
            tvDate.setVisibility(isPortrait ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    protected void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateResponsiveLayout();
    }

    private void updateClock() {
        Date now = new Date();
        if (tvTime != null) {
            tvTime.setText(timeFormat.format(now));
        }
        if (tvDate != null) {
            tvDate.setText(dateFormat.format(now));
        }
    }

    public View getNotificationIcon() {
        return ivNotification;
    }

    private void animateTapBounce(View v) {
        if (v == null) return;
        v.animate().cancel();
        v.animate().scaleX(0.88f).scaleY(0.88f).setDuration(80).withEndAction(() ->
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(140)
                        .setInterpolator(new OvershootInterpolator(1.6f)).start()
        ).start();
    }

    public void updateNotificationBadge(int count) {
        post(() -> {
            if (vNotifBadge != null) {
                vNotifBadge.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
            }
        });
    }

    public void pulseNotificationBell() {
        updateNotificationBadge(DeXNotificationPopup.getCount());
        if (ivNotification == null) return;
        ivNotification.animate()
                .scaleX(1.32f)
                .scaleY(1.32f)
                .setDuration(150)
                .withEndAction(() -> {
                    if (ivNotification != null) {
                        ivNotification.animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .setDuration(220)
                                .setInterpolator(new OvershootInterpolator(1.8f))
                                .start();
                    }
                })
                .start();
    }

    private void onNotificationClicked(Context context, View v) {
        if (ConfigManager.isFloatingPopupsEnabled(context)) {
            DeXNotificationPopup.show(context, v, this);
        } else {
            expandNotifications(context);
        }
    }

    private void onQuickSettingsClicked(Context context, View v) {
        if (ConfigManager.isFloatingPopupsEnabled(context)) {
            DeXQuickSettingsPopup.show(context, v);
        } else {
            openWifiSettings(context);
        }
    }

    private void expandNotifications(Context context) {
        try {
            Object sbservice = context.getSystemService("statusbar");
            if (sbservice != null) {
                sbservice.getClass().getMethod("expandNotificationsPanel").invoke(sbservice);
                return;
            }
        } catch (Throwable ignored) {}
        try {
            Runtime.getRuntime().exec(new String[]{"su", "-c", "cmd statusbar expand-notifications"});
        } catch (Throwable ignored) {}
    }

    private void openWifiSettings(Context context) {
        try {
            Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable ignored) {}
    }

    private void openClock(Context context) {
        try {
            Intent intent = new Intent(Intent.ACTION_QUICK_CLOCK);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable t) {
            try {
                Intent intent = new Intent(Settings.ACTION_DATE_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Throwable ignored) {}
        }
    }

    private void triggerScreenshot(Context context) {
        try {
            Runtime.getRuntime().exec(new String[]{"su", "-c", "input keyevent 120"}); // KEYCODE_SYSRQ (Take Screenshot)
        } catch (Throwable t) {
            try {
                Runtime.getRuntime().exec(new String[]{"su", "-c", "screencap -p /sdcard/Pictures/Screenshots/Screenshot_$(date +%Y%m%d_%H%M%S).png"});
            } catch (Throwable ignored) {}
        }
    }

    private void triggerHome(Context context) {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable t) {
            try {
                Runtime.getRuntime().exec(new String[]{"su", "-c", "input keyevent KEYCODE_HOME"});
            } catch (Throwable ignored) {}
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!isReceiverRegistered && getContext() != null) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_TIME_TICK);
            filter.addAction(Intent.ACTION_TIME_CHANGED);
            filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            filter.addAction(Intent.ACTION_BATTERY_CHANGED);
            getContext().registerReceiver(systemReceiver, filter);
            isReceiverRegistered = true;
            updateClock();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (isReceiverRegistered && getContext() != null) {
            try {
                getContext().unregisterReceiver(systemReceiver);
            } catch (Throwable ignored) {}
            isReceiverRegistered = false;
        }
    }

    private int dp(float val) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, val, getResources().getDisplayMetrics());
    }
}
