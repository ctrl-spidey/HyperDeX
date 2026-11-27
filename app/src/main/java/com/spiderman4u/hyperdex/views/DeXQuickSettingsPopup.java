package com.spiderman4u.hyperdex.views;

import android.app.NotificationManager;
import android.app.UiModeManager;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.SeekBar;
import android.widget.TextView;

import com.spiderman4u.hyperdex.config.ConfigManager;

/**
 * macOS/DeX Control Center popup with live state toggles, hardware triggers, and capsule sliders.
 */
public class DeXQuickSettingsPopup {

    private static PopupWindow sCurrentPopup = null;
    private static boolean sTorchEnabled = false;

    public static void show(Context context, View anchorView) {
        if (sCurrentPopup != null && sCurrentPopup.isShowing()) {
            dismiss();
            return;
        }
        DeXNotificationPopup.dismiss();
        DeXClockPopup.dismiss();

        int width = dp(context, 368);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 16));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#F416181F"));
        bg.setCornerRadius(dp(context, 18));
        bg.setStroke(dp(context, 1), Color.parseColor("#33FFFFFF"));
        root.setBackground(bg);

        // 1. Header: Title + Live Battery Chip + Action Circle Buttons
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvTitle = new TextView(context);
        tvTitle.setText("Control Center");
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        header.addView(tvTitle);

        // Battery Chip
        LinearLayout batteryChip = createBatteryChip(context);
        LinearLayout.LayoutParams lpBat = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, 24));
        lpBat.setMarginStart(dp(context, 8));
        header.addView(batteryChip, lpBat);

        View spacer = new View(context);
        header.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        // Screenshot Icon Button
        ImageView btnShot = createHeaderCircleButton(context, DeXVectorDrawables.createScreenshotDrawable(Color.parseColor("#E6E9F2")));
        btnShot.setOnClickListener(v -> {
            dismiss();
            root.postDelayed(() -> DeXNavButtonsView.sendNavAction(context, "screenshot"), 180);
        });
        header.addView(btnShot);
        addHorizontalSpace(header, context, 6);

        // Lock Screen Icon Button
        ImageView btnLock = createHeaderCircleButton(context, DeXVectorDrawables.createLockDrawable(Color.parseColor("#E6E9F2")));
        btnLock.setOnClickListener(v -> {
            dismiss();
            DeXNavButtonsView.sendNavAction(context, "lock_screen");
        });
        header.addView(btnLock);
        addHorizontalSpace(header, context, 6);

        // Power Menu Icon Button
        ImageView btnPower = createHeaderCircleButton(context, DeXVectorDrawables.createPowerDrawable(Color.parseColor("#FF6B6B")));
        btnPower.setOnClickListener(v -> {
            dismiss();
            DeXNavButtonsView.sendNavAction(context, "power");
        });
        header.addView(btnPower);
        addHorizontalSpace(header, context, 6);

        // Settings Gear Icon Button
        ImageView btnGear = createHeaderCircleButton(context, DeXVectorDrawables.createGearDrawable(Color.parseColor("#E6E9F2")));
        btnGear.setOnClickListener(v -> {
            dismiss();
            try {
                Intent intent = new Intent(Settings.ACTION_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Throwable ignored) {}
        });
        header.addView(btnGear);

        root.addView(header);
        addVerticalSpace(root, context, 14);

        // 2. Connectivity Bento Row (Wi-Fi Card + Bluetooth Card)
        LinearLayout connRow = new LinearLayout(context);
        connRow.setOrientation(LinearLayout.HORIZONTAL);

        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        final boolean[] wifiState = new boolean[]{wifiManager != null && wifiManager.isWifiEnabled()};
        BentoCard wifiCard = createBentoCard(context, DeXVectorDrawables.createWifiDrawable(Color.WHITE),
                "Wi-Fi", wifiState[0] ? "Connected" : "Off", wifiState[0]);
        wifiCard.root.setOnClickListener(v -> {
            wifiState[0] = !wifiState[0];
            wifiCard.setState(wifiState[0], wifiState[0] ? "On" : "Off");
            DeXNavButtonsView.sendNavAction(context, "toggle_wifi");
        });
        wifiCard.root.setOnLongClickListener(v -> {
            dismiss();
            try {
                Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Throwable ignored) {}
            return true;
        });
        connRow.addView(wifiCard.root, new LinearLayout.LayoutParams(0, dp(context, 56), 1f));

        addHorizontalSpace(connRow, context, 10);

        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        final boolean[] btState = new boolean[]{btAdapter != null && btAdapter.isEnabled()};
        BentoCard btCard = createBentoCard(context, DeXVectorDrawables.createBluetoothDrawable(Color.WHITE),
                "Bluetooth", btState[0] ? "On" : "Off", btState[0]);
        btCard.root.setOnClickListener(v -> {
            btState[0] = !btState[0];
            btCard.setState(btState[0], btState[0] ? "On" : "Off");
            DeXNavButtonsView.sendNavAction(context, "toggle_bluetooth");
        });
        btCard.root.setOnLongClickListener(v -> {
            dismiss();
            try {
                Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Throwable ignored) {}
            return true;
        });
        connRow.addView(btCard.root, new LinearLayout.LayoutParams(0, dp(context, 56), 1f));

        root.addView(connRow);
        addVerticalSpace(root, context, 10);

        // 3. Compact 3x2 Tiles Grid (Live Toggles)
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        final int[] ringerState = new int[]{audioManager != null ? audioManager.getRingerMode() : AudioManager.RINGER_MODE_NORMAL};

        LinearLayout row1 = new LinearLayout(context);
        row1.setOrientation(LinearLayout.HORIZONTAL);

        // Tile 1: Sound / Vibrate / Mute
        ToggleTile soundTile = createToggleTile(context, getSoundLabel(ringerState[0]),
                getSoundIcon(ringerState[0]), ringerState[0] != AudioManager.RINGER_MODE_SILENT);
        soundTile.root.setOnClickListener(v -> {
            int nextMode;
            if (ringerState[0] == AudioManager.RINGER_MODE_NORMAL) nextMode = AudioManager.RINGER_MODE_VIBRATE;
            else if (ringerState[0] == AudioManager.RINGER_MODE_VIBRATE) nextMode = AudioManager.RINGER_MODE_SILENT;
            else nextMode = AudioManager.RINGER_MODE_NORMAL;
            ringerState[0] = nextMode;
            if (audioManager != null) {
                try {
                    audioManager.setRingerMode(nextMode);
                } catch (Throwable ignored) {}
            }
            soundTile.update(getSoundLabel(nextMode), getSoundIcon(nextMode), nextMode != AudioManager.RINGER_MODE_SILENT);
        });
        row1.addView(soundTile.root, new LinearLayout.LayoutParams(0, dp(context, 54), 1f));
        addHorizontalSpace(row1, context, 8);

        // Tile 2: Auto-Rotate
        final boolean[] rotState = new boolean[]{true};
        try {
            rotState[0] = Settings.System.getInt(context.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 1) == 1;
        } catch (Throwable ignored) {}
        ToggleTile rotateTile = createToggleTile(context, rotState[0] ? "Auto-Rotate" : "Locked",
                DeXVectorDrawables.createAutoRotateDrawable(Color.WHITE), rotState[0]);
        rotateTile.root.setOnClickListener(v -> {
            rotState[0] = !rotState[0];
            try {
                Settings.System.putInt(context.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, rotState[0] ? 1 : 0);
            } catch (Throwable ignored) {}
            rotateTile.update(rotState[0] ? "Auto-Rotate" : "Locked", null, rotState[0]);
        });
        row1.addView(rotateTile.root, new LinearLayout.LayoutParams(0, dp(context, 54), 1f));
        addHorizontalSpace(row1, context, 8);

        // Tile 3: Dark Mode
        int currentNight = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        final boolean[] darkState = new boolean[]{currentNight == Configuration.UI_MODE_NIGHT_YES};
        ToggleTile darkTile = createToggleTile(context, "Dark Mode",
                DeXVectorDrawables.createDarkModeDrawable(Color.WHITE), darkState[0]);
        darkTile.root.setOnClickListener(v -> {
            darkState[0] = !darkState[0];
            darkTile.update("Dark Mode", null, darkState[0]);
            try {
                UiModeManager um = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
                if (um != null) {
                    um.setNightMode(darkState[0] ? UiModeManager.MODE_NIGHT_YES : UiModeManager.MODE_NIGHT_NO);
                }
            } catch (Throwable ignored) {}
        });
        row1.addView(darkTile.root, new LinearLayout.LayoutParams(0, dp(context, 54), 1f));

        root.addView(row1);
        addVerticalSpace(root, context, 8);

        LinearLayout row2 = new LinearLayout(context);
        row2.setOrientation(LinearLayout.HORIZONTAL);

        // Tile 4: Torch
        ToggleTile torchTile = createToggleTile(context, "Torch",
                DeXVectorDrawables.createTorchDrawable(Color.WHITE), sTorchEnabled);
        torchTile.root.setOnClickListener(v -> {
            toggleTorch(context);
            torchTile.update("Torch", null, sTorchEnabled);
        });
        row2.addView(torchTile.root, new LinearLayout.LayoutParams(0, dp(context, 54), 1f));
        addHorizontalSpace(row2, context, 8);

        // Tile 5: DND
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        final boolean[] dndState = new boolean[]{nm != null && nm.getCurrentInterruptionFilter() != NotificationManager.INTERRUPTION_FILTER_ALL};
        ToggleTile dndTile = createToggleTile(context, "DND",
                DeXVectorDrawables.createDndDrawable(Color.WHITE), dndState[0]);
        dndTile.root.setOnClickListener(v -> {
            dndState[0] = !dndState[0];
            dndTile.update("DND", null, dndState[0]);
            DeXNavButtonsView.sendNavAction(context, "toggle_dnd");
        });
        dndTile.root.setOnLongClickListener(v -> {
            dismiss();
            try {
                Intent intent = new Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Throwable ignored) {}
            return true;
        });
        row2.addView(dndTile.root, new LinearLayout.LayoutParams(0, dp(context, 54), 1f));
        addHorizontalSpace(row2, context, 8);

        // Tile 6: Airplane
        boolean isAirplane = false;
        try {
            isAirplane = Settings.Global.getInt(context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) == 1;
        } catch (Throwable ignored) {}
        ToggleTile airTile = createToggleTile(context, "Airplane",
                DeXVectorDrawables.createAirplaneDrawable(Color.WHITE), isAirplane);
        airTile.root.setOnClickListener(v -> {
            dismiss();
            try {
                Intent intent = new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Throwable ignored) {}
        });
        row2.addView(airTile.root, new LinearLayout.LayoutParams(0, dp(context, 54), 1f));

        root.addView(row2);
        addVerticalSpace(root, context, 14);

        // 4. Sleek Capsule Sliders (Brightness & Volume)
        int curBrightness = 128;
        try {
            curBrightness = Settings.System.getInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 128);
        } catch (Throwable ignored) {}
        root.addView(createCapsuleSlider(
                context,
                DeXVectorDrawables.createBrightnessDrawable(Color.WHITE),
                curBrightness,
                255,
                (progress, fromUser) -> {
                    if (fromUser) {
                        try {
                            Settings.System.putInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, Math.max(10, progress));
                        } catch (Throwable ignored) {}
                    }
                }
        ));

        addVerticalSpace(root, context, 8);

        int maxVol = audioManager != null ? audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) : 15;
        int curVol = audioManager != null ? audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) : 8;
        root.addView(createCapsuleSlider(
                context,
                DeXVectorDrawables.createVolumeDrawable(Color.WHITE, curVol == 0),
                curVol,
                maxVol,
                (progress, fromUser) -> {
                    if (fromUser && audioManager != null) {
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0);
                    }
                }
        ));

        PopupWindow popup = new PopupWindow(root, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setFocusable(true);
        popup.setElevation(dp(context, 18));
        popup.setOnDismissListener(() -> {
            if (sCurrentPopup == popup) sCurrentPopup = null;
        });

        int xOffset = dp(context, 12);
        int yOffset = dp(context, ConfigManager.getTaskbarHeight(context) + Math.max(0, ConfigManager.getTaskbarBottomOffset(context)) + 8);
        popup.showAtLocation(anchorView, Gravity.BOTTOM | Gravity.END, xOffset, yOffset);
        sCurrentPopup = popup;

        // Smooth macOS entrance
        root.setAlpha(0f);
        root.setScaleX(0.92f);
        root.setScaleY(0.92f);
        root.setTranslationY(dp(context, 14));
        root.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(240)
                .setInterpolator(new android.view.animation.PathInterpolator(0.22f, 1.0f, 0.36f, 1.0f))
                .start();
    }

    public static void dismiss() {
        if (sCurrentPopup != null) {
            try {
                sCurrentPopup.dismiss();
            } catch (Throwable ignored) {}
            sCurrentPopup = null;
        }
    }

    private static String getSoundLabel(int ringerMode) {
        if (ringerMode == AudioManager.RINGER_MODE_VIBRATE) return "Vibrate";
        if (ringerMode == AudioManager.RINGER_MODE_SILENT) return "Mute";
        return "Sound";
    }

    private static DeXVectorDrawables.SvgPathDrawable getSoundIcon(int ringerMode) {
        if (ringerMode == AudioManager.RINGER_MODE_VIBRATE) return DeXVectorDrawables.createVibrateDrawable(Color.WHITE);
        return DeXVectorDrawables.createVolumeDrawable(Color.WHITE, ringerMode == AudioManager.RINGER_MODE_SILENT);
    }

    private static LinearLayout createBatteryChip(Context context) {
        LinearLayout chip = new LinearLayout(context);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(dp(context, 8), 0, dp(context, 8), 0);

        GradientDrawable chipBg = new GradientDrawable();
        chipBg.setColor(Color.parseColor("#242834"));
        chipBg.setCornerRadius(dp(context, 12));
        chip.setBackground(chipBg);

        int pct = 100;
        boolean charging = false;
        try {
            Intent bat = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (bat != null) {
                int lvl = bat.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scl = bat.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                int st = bat.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                if (lvl >= 0 && scl > 0) pct = (lvl * 100) / scl;
                charging = (st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL);
            }
        } catch (Throwable ignored) {}

        DeXVectorDrawables.BatteryDrawable bd = new DeXVectorDrawables.BatteryDrawable(Color.parseColor("#D8DCE6"));
        bd.setLevelAndCharging(pct, charging);
        ImageView iv = new ImageView(context);
        iv.setImageDrawable(bd);
        chip.addView(iv, new LinearLayout.LayoutParams(dp(context, 18), dp(context, 11)));

        TextView tv = new TextView(context);
        tv.setText(pct + "%");
        tv.setTextColor(Color.parseColor("#D8DCE6"));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        tv.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams lpTv = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpTv.setMarginStart(dp(context, 5));
        chip.addView(tv, lpTv);
        return chip;
    }

    private static class BentoCard {
        final LinearLayout root;
        final GradientDrawable cardBg;
        final GradientDrawable circleBg;
        final TextView tvSub;

        BentoCard(LinearLayout root, GradientDrawable cardBg, GradientDrawable circleBg, TextView tvSub) {
            this.root = root;
            this.cardBg = cardBg;
            this.circleBg = circleBg;
            this.tvSub = tvSub;
        }

        void setState(boolean active, String subtitle) {
            cardBg.setColor(Color.parseColor(active ? "#273A58" : "#222530"));
            circleBg.setColor(Color.parseColor(active ? "#0A84FF" : "#353946"));
            tvSub.setText(subtitle);
            tvSub.setTextColor(Color.parseColor(active ? "#8BC6FF" : "#8E93A2"));
        }
    }

    private static BentoCard createBentoCard(Context context, DeXVectorDrawables.SvgPathDrawable icon,
                                             String title, String subtitle, boolean active) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8));

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor(active ? "#273A58" : "#222530"));
        cardBg.setCornerRadius(dp(context, 14));
        card.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.parseColor("#33FFFFFF")), cardBg, null));

        FrameLayout iconCircle = new FrameLayout(context);
        GradientDrawable circleBg = new GradientDrawable();
        circleBg.setShape(GradientDrawable.OVAL);
        circleBg.setColor(Color.parseColor(active ? "#0A84FF" : "#353946"));
        iconCircle.setBackground(circleBg);

        ImageView iv = new ImageView(context);
        iv.setImageDrawable(icon);
        FrameLayout.LayoutParams lpIv = new FrameLayout.LayoutParams(dp(context, 17), dp(context, 17));
        lpIv.gravity = Gravity.CENTER;
        iconCircle.addView(iv, lpIv);
        card.addView(iconCircle, new LinearLayout.LayoutParams(dp(context, 34), dp(context, 34)));

        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lpCol = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lpCol.setMarginStart(dp(context, 10));

        TextView tvTitle = new TextView(context);
        tvTitle.setText(title);
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setMaxLines(1);
        textCol.addView(tvTitle);

        TextView tvSub = new TextView(context);
        tvSub.setText(subtitle);
        tvSub.setTextColor(Color.parseColor(active ? "#8BC6FF" : "#8E93A2"));
        tvSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        tvSub.setMaxLines(1);
        textCol.addView(tvSub);

        card.addView(textCol, lpCol);
        return new BentoCard(card, cardBg, circleBg, tvSub);
    }

    private static class ToggleTile {
        final LinearLayout root;
        final GradientDrawable bg;
        final ImageView iv;
        final TextView tv;

        ToggleTile(LinearLayout root, GradientDrawable bg, ImageView iv, TextView tv) {
            this.root = root;
            this.bg = bg;
            this.iv = iv;
            this.tv = tv;
        }

        void update(String label, DeXVectorDrawables.SvgPathDrawable newIcon, boolean active) {
            bg.setColor(Color.parseColor(active ? "#0A84FF" : "#252834"));
            if (newIcon != null) iv.setImageDrawable(newIcon);
            if (label != null) tv.setText(label);
        }
    }

    private static ToggleTile createToggleTile(Context context, String label, DeXVectorDrawables.SvgPathDrawable icon, boolean isActive) {
        LinearLayout tile = new LinearLayout(context);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(context, 4), dp(context, 6), dp(context, 4), dp(context, 6));

        GradientDrawable tileBg = new GradientDrawable();
        tileBg.setCornerRadius(dp(context, 13));
        tileBg.setColor(Color.parseColor(isActive ? "#0A84FF" : "#252834"));
        tile.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.parseColor("#33FFFFFF")), tileBg, null));

        ImageView iv = new ImageView(context);
        icon.setColor(Color.WHITE);
        iv.setImageDrawable(icon);
        tile.addView(iv, new LinearLayout.LayoutParams(dp(context, 19), dp(context, 19)));

        TextView tv = new TextView(context);
        tv.setText(label);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        tv.setGravity(Gravity.CENTER);
        tv.setMaxLines(1);
        LinearLayout.LayoutParams lpText = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpText.topMargin = dp(context, 3);
        tile.addView(tv, lpText);

        return new ToggleTile(tile, tileBg, iv, tv);
    }

    public interface OnCapsuleChangeListener {
        void onProgressChanged(int progress, boolean fromUser);
    }

    private static FrameLayout createCapsuleSlider(Context context, DeXVectorDrawables.SvgPathDrawable icon,
                                                   int current, int max, OnCapsuleChangeListener listener) {
        FrameLayout container = new FrameLayout(context);
        LinearLayout.LayoutParams lpContainer = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 40));
        container.setLayoutParams(lpContainer);

        // Capsule progress drawable
        GradientDrawable trackBg = new GradientDrawable();
        trackBg.setColor(Color.parseColor("#232632"));
        trackBg.setCornerRadius(dp(context, 13));

        GradientDrawable fillBg = new GradientDrawable();
        fillBg.setColor(Color.parseColor("#478DF7"));
        fillBg.setCornerRadius(dp(context, 13));
        ClipDrawable clipFill = new ClipDrawable(fillBg, Gravity.START, ClipDrawable.HORIZONTAL);

        LayerDrawable layers = new LayerDrawable(new android.graphics.drawable.Drawable[]{trackBg, clipFill});
        layers.setId(0, android.R.id.background);
        layers.setId(1, android.R.id.progress);

        SeekBar seekBar = new SeekBar(context);
        seekBar.setMax(Math.max(1, max));
        seekBar.setProgress(Math.max(0, Math.min(max, current)));
        seekBar.setProgressDrawable(layers);
        seekBar.setThumb(new ColorDrawable(Color.TRANSPARENT));
        seekBar.setPadding(0, 0, 0, 0);
        seekBar.setSplitTrack(false);
        container.addView(seekBar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ImageView iv = new ImageView(context);
        iv.setImageDrawable(icon);
        iv.setAlpha(0.92f);
        FrameLayout.LayoutParams lpIv = new FrameLayout.LayoutParams(dp(context, 17), dp(context, 17));
        lpIv.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
        lpIv.setMarginStart(dp(context, 12));
        container.addView(iv, lpIv);

        TextView tvPct = new TextView(context);
        int initPct = Math.round((current * 100f) / Math.max(1, max));
        tvPct.setText(initPct + "%");
        tvPct.setTextColor(Color.parseColor("#DCE2F0"));
        tvPct.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvPct.setTypeface(null, Typeface.BOLD);
        FrameLayout.LayoutParams lpPct = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpPct.gravity = Gravity.CENTER_VERTICAL | Gravity.END;
        lpPct.setMarginEnd(dp(context, 12));
        container.addView(tvPct, lpPct);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int pct = Math.round((progress * 100f) / Math.max(1, sb.getMax()));
                tvPct.setText(pct + "%");
                if (listener != null) listener.onProgressChanged(progress, fromUser);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        return container;
    }

    private static ImageView createHeaderCircleButton(Context context, DeXVectorDrawables.SvgPathDrawable icon) {
        ImageView iv = new ImageView(context);
        iv.setPadding(dp(context, 7), dp(context, 7), dp(context, 7), dp(context, 7));
        iv.setImageDrawable(icon);

        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Color.parseColor("#252834"));

        ColorStateList rippleColor = ColorStateList.valueOf(Color.parseColor("#33FFFFFF"));
        iv.setBackground(new RippleDrawable(rippleColor, circle, null));

        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(context, 30), dp(context, 30)));
        return iv;
    }

    private static void toggleTorch(Context context) {
        try {
            CameraManager cm = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (cm != null) {
                String cameraId = cm.getCameraIdList()[0];
                sTorchEnabled = !sTorchEnabled;
                cm.setTorchMode(cameraId, sTorchEnabled);
            }
        } catch (Throwable ignored) {}
    }

    private static void addHorizontalSpace(LinearLayout parent, Context context, int dpVal) {
        View space = new View(context);
        parent.addView(space, new LinearLayout.LayoutParams(dp(context, dpVal), 1));
    }

    private static void addVerticalSpace(LinearLayout parent, Context context, int dpVal) {
        View space = new View(context);
        parent.addView(space, new LinearLayout.LayoutParams(1, dp(context, dpVal)));
    }

    private static int dp(Context context, float dpVal) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dpVal, context.getResources().getDisplayMetrics()
        ));
    }
}
