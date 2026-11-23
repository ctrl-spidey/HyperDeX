package com.spiderman4u.hyperdex.config;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

import de.robv.android.xposed.XposedBridge;

public class ConfigManager {

    private static final String SETTING_DEX_MODE = "hyperdex_mode";
    private static final String SETTING_EDGE_TO_EDGE = "hyperdex_edge_to_edge";
    private static final String SETTING_START_MENU = "hyperdex_start_menu";
    private static final String SETTING_DEX_BUTTONS = "hyperdex_dex_buttons";

    public static boolean isDeXModeEnabled(Context context) {
        return getGlobalSetting(context, SETTING_DEX_MODE, true);
    }

    public static boolean isEdgeToEdgeEnabled(Context context) {
        return getGlobalSetting(context, SETTING_EDGE_TO_EDGE, true);
    }

    public static boolean isStartMenuEnabled(Context context) {
        return getGlobalSetting(context, SETTING_START_MENU, true);
    }

    public static boolean isDeXButtonsEnabled(Context context) {
        return getGlobalSetting(context, SETTING_DEX_BUTTONS, true);
    }

    public static void setDeXModeEnabled(Context context, boolean enabled) {
        setGlobalSetting(context, SETTING_DEX_MODE, enabled);
        if (context != null) {
            Intent intent = new Intent(Constants.ACTION_MODE_CHANGED);
            intent.putExtra(Constants.EXTRA_DEX_MODE, enabled);
            context.sendBroadcast(intent);
        }
    }

    public static void setBoolean(Context context, String key, boolean value) {
        String settingKey = mapKeyToSetting(key);
        setGlobalSetting(context, settingKey, value);
    }

    private static String mapKeyToSetting(String prefKey) {
        if (Constants.PREF_EDGE_TO_EDGE.equals(prefKey)) return SETTING_EDGE_TO_EDGE;
        if (Constants.PREF_START_MENU.equals(prefKey)) return SETTING_START_MENU;
        if (Constants.PREF_DEX_BUTTONS.equals(prefKey)) return SETTING_DEX_BUTTONS;
        return SETTING_DEX_MODE;
    }

    private static boolean getGlobalSetting(Context context, String key, boolean defValue) {
        if (context != null) {
            try {
                int val = Settings.Global.getInt(context.getContentResolver(), key, defValue ? 1 : 0);
                return val == 1;
            } catch (Throwable t) {
                XposedBridge.log("[HyperDeX] Error reading Settings.Global: " + t.getMessage());
            }
        }
        return defValue;
    }

    private static void setGlobalSetting(Context context, String key, boolean value) {
        if (context != null) {
            try {
                Settings.Global.putInt(context.getContentResolver(), key, value ? 1 : 0);
            } catch (Throwable t) {
                // If direct write fails, write via root
                try {
                    String cmd = "settings put global " + key + " " + (value ? "1" : "0");
                    Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
