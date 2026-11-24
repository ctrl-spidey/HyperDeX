package com.spiderman4u.hyperdex.views;

import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.BatteryManager;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.SeekBar;
import android.widget.TextView;

import com.spiderman4u.hyperdex.config.ConfigManager;

public class DeXQuickSettingsPopup {

    private static PopupWindow sCurrentPopup = null;
    private static boolean sTorchEnabled = false;

    public static void show(Context context, View anchorView) {
        if (sCurrentPopup != null && sCurrentPopup.isShowing()) {
            sCurrentPopup.dismiss();
            sCurrentPopup = null;
            return;
        }

        int width = dp(context, 330);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 16));

        // Acrylic Dark Card Background
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#F2181A20"));
        bg.setCornerRadius(dp(context, 16));
        bg.setStroke(dp(context, 1), Color.parseColor("#33FFFFFF"));
        root.setBackground(bg);

        // 1. Header (Quick Settings Title + Battery + Gear Icon)
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvTitle = new TextView(context);
        tvTitle.setText("Quick Settings");
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams lpTitle = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        header.addView(tvTitle, lpTitle);

        ImageView btnGear = createHeaderIconButton(context, DeXVectorDrawables.createGearDrawable(Color.parseColor("#E1E2E8")));
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

        // Spacer
        addVerticalSpace(root, context, 14);

        // 2. Toggles Row (Wi-Fi, Bluetooth, Torch, DND)
        LinearLayout togglesRow = new LinearLayout(context);
        togglesRow.setOrientation(LinearLayout.HORIZONTAL);
        togglesRow.setGravity(Gravity.CENTER);

        // Wi-Fi Toggle
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        boolean isWifiOn = wifiManager != null && wifiManager.isWifiEnabled();
        View wifiToggle = createTogglePill(context, "Wi-Fi", DeXVectorDrawables.createWifiDrawable(Color.WHITE), isWifiOn, v -> {
            try {
                Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                dismiss();
            } catch (Throwable ignored) {}
        });
        togglesRow.addView(wifiToggle, new LinearLayout.LayoutParams(0, dp(context, 54), 1f));

        addHorizontalSpace(togglesRow, context, 8);

        // Bluetooth Toggle
        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        boolean isBtOn = btAdapter != null && btAdapter.isEnabled();
        View btToggle = createTogglePill(context, "Bluetooth", DeXVectorDrawables.createBluetoothDrawable(Color.WHITE), isBtOn, v -> {
            try {
                Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                dismiss();
            } catch (Throwable ignored) {}
        });
        togglesRow.addView(btToggle, new LinearLayout.LayoutParams(0, dp(context, 54), 1f));

        addHorizontalSpace(togglesRow, context, 8);

        // Torch Toggle
        View torchToggle = createTogglePill(context, "Torch", DeXVectorDrawables.createTorchDrawable(Color.WHITE), sTorchEnabled, v -> {
            toggleTorch(context);
            dismiss();
        });
        togglesRow.addView(torchToggle, new LinearLayout.LayoutParams(0, dp(context, 54), 1f));

        addHorizontalSpace(togglesRow, context, 8);

        // DND Toggle
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        boolean isDnd = nm != null && nm.getCurrentInterruptionFilter() != NotificationManager.INTERRUPTION_FILTER_ALL;
        View dndToggle = createTogglePill(context, "DND", DeXVectorDrawables.createDndDrawable(Color.WHITE), isDnd, v -> {
            try {
                Intent intent = new Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                dismiss();
            } catch (Throwable ignored) {}
        });
        togglesRow.addView(dndToggle, new LinearLayout.LayoutParams(0, dp(context, 54), 1f));

        root.addView(togglesRow);

        addVerticalSpace(root, context, 14);

        // 3. Brightness Slider
        int curBrightness = 128;
        try {
            curBrightness = Settings.System.getInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 128);
        } catch (Throwable ignored) {}

        LinearLayout brightnessLayout = createSliderRow(
                context,
                DeXVectorDrawables.createBrightnessDrawable(Color.parseColor("#A0A2AA")),
                curBrightness,
                255,
                (seekBar, progress, fromUser) -> {
                    if (fromUser) {
                        try {
                            Settings.System.putInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, Math.max(10, progress));
                        } catch (Throwable t) {
                            try {
                                Runtime.getRuntime().exec(new String[]{"su", "-c", "settings put system screen_brightness " + Math.max(10, progress)});
                            } catch (Throwable ignored) {}
                        }
                    }
                }
        );
        root.addView(brightnessLayout);

        addVerticalSpace(root, context, 10);

        // 4. Volume Slider
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        int maxVol = audioManager != null ? audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) : 15;
        int curVol = audioManager != null ? audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) : 8;

        LinearLayout volumeLayout = createSliderRow(
                context,
                DeXVectorDrawables.createVolumeDrawable(Color.parseColor("#A0A2AA"), curVol == 0),
                curVol,
                maxVol,
                (seekBar, progress, fromUser) -> {
                    if (fromUser && audioManager != null) {
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0);
                    }
                }
        );
        root.addView(volumeLayout);

        // Create PopupWindow
        PopupWindow popup = new PopupWindow(root, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setFocusable(true);
        popup.setElevation(dp(context, 16));

        // Anchor above bottom-right
        int xOffset = dp(context, 12);
        int yOffset = dp(context, ConfigManager.getTaskbarHeight(context) + 6);
        popup.showAtLocation(anchorView, Gravity.BOTTOM | Gravity.END, xOffset, yOffset);
        sCurrentPopup = popup;
    }

    public static void dismiss() {
        if (sCurrentPopup != null) {
            try {
                sCurrentPopup.dismiss();
            } catch (Throwable ignored) {}
            sCurrentPopup = null;
        }
    }

    private static View createTogglePill(Context context, String label, DeXVectorDrawables.SvgPathDrawable icon, boolean isActive, View.OnClickListener listener) {
        LinearLayout pill = new LinearLayout(context);
        pill.setOrientation(LinearLayout.VERTICAL);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(context, 4), dp(context, 6), dp(context, 4), dp(context, 6));

        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setCornerRadius(dp(context, 12));
        if (isActive) {
            pillBg.setColor(Color.parseColor("#0A84FF")); // Active DeX Blue
        } else {
            pillBg.setColor(Color.parseColor("#282A32")); // Inactive Dark
        }
        pill.setBackground(pillBg);

        ImageView iv = new ImageView(context);
        icon.setColor(Color.WHITE);
        iv.setImageDrawable(icon);
        pill.addView(iv, new LinearLayout.LayoutParams(dp(context, 20), dp(context, 20)));

        TextView tv = new TextView(context);
        tv.setText(label);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        tv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lpText = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpText.topMargin = dp(context, 3);
        pill.addView(tv, lpText);

        pill.setOnClickListener(listener);
        return pill;
    }

    public interface OnSliderChangeListener {
        void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser);
    }

    private static LinearLayout createSliderRow(Context context, DeXVectorDrawables.SvgPathDrawable icon, int current, int max, OnSliderChangeListener listener) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 8), dp(context, 4), dp(context, 8), dp(context, 4));

        GradientDrawable rowBg = new GradientDrawable();
        rowBg.setColor(Color.parseColor("#202228"));
        rowBg.setCornerRadius(dp(context, 10));
        row.setBackground(rowBg);

        ImageView iv = new ImageView(context);
        iv.setImageDrawable(icon);
        row.addView(iv, new LinearLayout.LayoutParams(dp(context, 18), dp(context, 18)));

        SeekBar seekBar = new SeekBar(context);
        seekBar.setMax(max);
        seekBar.setProgress(current);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (listener != null) listener.onProgressChanged(sb, progress, fromUser);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        LinearLayout.LayoutParams lpSeek = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lpSeek.setMarginStart(dp(context, 6));
        row.addView(seekBar, lpSeek);

        return row;
    }

    private static ImageView createHeaderIconButton(Context context, DeXVectorDrawables.SvgPathDrawable icon) {
        ImageView iv = new ImageView(context);
        iv.setPadding(dp(context, 6), dp(context, 6), dp(context, 6), dp(context, 6));
        iv.setImageDrawable(icon);

        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(context, 8));

        ColorStateList rippleColor = ColorStateList.valueOf(Color.parseColor("#33FFFFFF"));
        RippleDrawable ripple = new RippleDrawable(rippleColor, null, mask);
        iv.setBackground(ripple);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(context, 30), dp(context, 30));
        iv.setLayoutParams(lp);
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
        } catch (Throwable t) {
            try {
                sTorchEnabled = !sTorchEnabled;
                Runtime.getRuntime().exec(new String[]{"su", "-c", "cmd camera set-torch-mode " + (sTorchEnabled ? "1" : "0")});
            } catch (Throwable ignored) {}
        }
    }

    private static void addVerticalSpace(LinearLayout parent, Context context, int dpVal) {
        View space = new View(context);
        parent.addView(space, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, dpVal)));
    }

    private static void addHorizontalSpace(LinearLayout parent, Context context, int dpVal) {
        View space = new View(context);
        parent.addView(space, new LinearLayout.LayoutParams(dp(context, dpVal), ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private static int dp(Context context, float val) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, val, context.getResources().getDisplayMetrics());
    }
}
