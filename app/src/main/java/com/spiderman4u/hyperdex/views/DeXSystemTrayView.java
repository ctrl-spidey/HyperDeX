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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

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
        setPadding(dp(8), 0, dp(6), 0);

        int iconColor = Color.parseColor("#E1E2E8");

        // 1. Notification Bell Button
        ivNotification = new ImageView(context);
        ivNotification.setPadding(dp(4), dp(4), dp(4), dp(4));
        ivNotification.setImageDrawable(new DeXVectorDrawables.BellDrawable(iconColor));
        ivNotification.setOnClickListener(v -> expandNotifications(context));
        addView(ivNotification, new LayoutParams(dp(28), dp(28)));

        // 2. Wi-Fi Icon
        ivWifi = new ImageView(context);
        ivWifi.setPadding(dp(4), dp(4), dp(4), dp(4));
        ivWifi.setImageDrawable(new DeXVectorDrawables.WifiDrawable(iconColor));
        ivWifi.setOnClickListener(v -> openWifiSettings(context));
        LayoutParams lpWifi = new LayoutParams(dp(28), dp(28));
        lpWifi.setMarginStart(dp(4));
        addView(ivWifi, lpWifi);

        // 3. Battery Group (Battery Icon + Text)
        LinearLayout batteryLayout = new LinearLayout(context);
        batteryLayout.setOrientation(HORIZONTAL);
        batteryLayout.setGravity(Gravity.CENTER_VERTICAL);
        batteryLayout.setPadding(dp(6), 0, dp(4), 0);
        batteryLayout.setOnClickListener(v -> openBatterySettings(context));

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
        lpBattery.setMarginStart(dp(4));
        addView(batteryLayout, lpBattery);

        // 4. Clock and Date Group (2-line clean DeX typography)
        LinearLayout clockLayout = new LinearLayout(context);
        clockLayout.setOrientation(VERTICAL);
        clockLayout.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        clockLayout.setPadding(dp(8), 0, dp(8), 0);
        clockLayout.setOnClickListener(v -> openClock(context));

        tvTime = new TextView(context);
        tvTime.setTextColor(Color.WHITE);
        tvTime.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvTime.setTypeface(null, Typeface.BOLD);
        clockLayout.addView(tvTime);

        tvDate = new TextView(context);
        tvDate.setTextColor(Color.parseColor("#A0A2AA"));
        tvDate.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        clockLayout.addView(tvDate);

        addView(clockLayout);

        // 5. Far right "Show Desktop" vertical touch strip
        vDesktopStrip = new View(context);
        GradientDrawable stripBg = new GradientDrawable();
        stripBg.setColor(Color.parseColor("#4DFFFFFF")); // 30% translucent white
        stripBg.setCornerRadius(dp(1));
        vDesktopStrip.setBackground(stripBg);
        vDesktopStrip.setOnClickListener(v -> triggerHome(context));

        LayoutParams lpStrip = new LayoutParams(dp(2), dp(20));
        lpStrip.setMarginStart(dp(6));
        lpStrip.setMarginEnd(dp(4));
        addView(vDesktopStrip, lpStrip);

        updateClock();
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

    private void openBatterySettings(Context context) {
        try {
            Intent intent = new Intent(Intent.ACTION_POWER_USAGE_SUMMARY);
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

    private int dp(int val) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, val, getResources().getDisplayMetrics());
    }
}
