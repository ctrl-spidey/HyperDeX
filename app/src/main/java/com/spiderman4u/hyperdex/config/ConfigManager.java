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
    private static final String SETTING_APPLY_NORMAL = "hyperdex_apply_normal";
    private static final String SETTING_SHOW_NAV_BUTTONS = "hyperdex_show_nav_buttons";
    private static final String SETTING_SHOW_SEARCH_BUTTON = "hyperdex_show_search_button";
    private static final String SETTING_FLOATING_POPUPS = "hyperdex_floating_popups";
    private static final String SETTING_TASKBAR_HEIGHT = "hyperdex_taskbar_height";
    private static final String SETTING_ICON_SIZE = "hyperdex_icon_size";
    private static final String SETTING_ICON_SPACING = "hyperdex_icon_spacing";
    private static final String SETTING_TASKBAR_OPACITY = "hyperdex_taskbar_opacity";

    public static boolean isDeXModeEnabled(Context context) {
        return getGlobalBoolean(context, SETTING_DEX_MODE, true);
    }

    public static boolean isEdgeToEdgeEnabled(Context context) {
        return getGlobalBoolean(context, SETTING_EDGE_TO_EDGE, true);
    }

    public static boolean isStartMenuEnabled(Context context) {
        return getGlobalBoolean(context, SETTING_START_MENU, true);
    }

    public static boolean isDeXButtonsEnabled(Context context) {
        return getGlobalBoolean(context, SETTING_DEX_BUTTONS, true);
    }

    public static boolean isClassicWindowControlsEnabled(Context context) {
        return isDeXButtonsEnabled(context);
    }

    public static boolean isApplyInNormalModeEnabled(Context context) {
        return getGlobalBoolean(context, SETTING_APPLY_NORMAL, false);
    }

    public static boolean isShowNavButtons(Context context) {
        return getGlobalBoolean(context, SETTING_SHOW_NAV_BUTTONS, true);
    }

    public static boolean isShowSearchButton(Context context) {
        return getGlobalBoolean(context, SETTING_SHOW_SEARCH_BUTTON, true);
    }

    public static boolean isFloatingPopupsEnabled(Context context) {
        return getGlobalBoolean(context, SETTING_FLOATING_POPUPS, true);
    }

    public static int getTaskbarHeight(Context context) {
        return getGlobalInt(context, SETTING_TASKBAR_HEIGHT, Constants.DEFAULT_TASKBAR_HEIGHT);
    }

    public static int getIconSize(Context context) {
        return getGlobalInt(context, SETTING_ICON_SIZE, Constants.DEFAULT_ICON_SIZE);
    }

    public static int getIconSpacing(Context context) {
        return getGlobalInt(context, SETTING_ICON_SPACING, Constants.DEFAULT_ICON_SPACING);
    }

    public static int getTaskbarOpacity(Context context) {
        return getGlobalInt(context, SETTING_TASKBAR_OPACITY, Constants.DEFAULT_TASKBAR_OPACITY);
    }

    public static boolean isWorkstationMode(Context context) {
        if (context == null) return false;
        try {
            return Settings.System.getInt(context.getContentResolver(), "miui_dkt_mode", 0) == 1;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean shouldApplyDeXTaskbar(Context context) {
        if (context == null) return false;
        if (!isDeXModeEnabled(context) || !isEdgeToEdgeEnabled(context)) {
            return false;
        }
        if (isApplyInNormalModeEnabled(context)) {
            return true;
        }
        return isWorkstationMode(context);
    }

    public static void setDeXModeEnabled(Context context, boolean enabled) {
        setGlobalBoolean(context, SETTING_DEX_MODE, enabled);
        notifyChanged(context);
    }

    public static void setBoolean(Context context, String key, boolean value) {
        String settingKey = mapKeyToSetting(key);
        setGlobalBoolean(context, settingKey, value);
        notifyChanged(context);
    }

    public static void setInt(Context context, String key, int value) {
        String settingKey = mapKeyToSetting(key);
        setGlobalInt(context, settingKey, value);
        notifyChanged(context);
    }

    public static void resetTaskbarDefaults(Context context) {
        setInt(context, Constants.PREF_TASKBAR_HEIGHT, Constants.DEFAULT_TASKBAR_HEIGHT);
        setInt(context, Constants.PREF_ICON_SIZE, Constants.DEFAULT_ICON_SIZE);
        setInt(context, Constants.PREF_ICON_SPACING, Constants.DEFAULT_ICON_SPACING);
        setInt(context, Constants.PREF_TASKBAR_OPACITY, Constants.DEFAULT_TASKBAR_OPACITY);
        setBoolean(context, Constants.PREF_SHOW_NAV_BUTTONS, true);
        setBoolean(context, Constants.PREF_SHOW_SEARCH_BUTTON, true);
        setBoolean(context, Constants.PREF_FLOATING_POPUPS, true);
        notifyChanged(context);
    }

    private static void notifyChanged(Context context) {
        if (context != null) {
            Intent intent = new Intent(Constants.ACTION_MODE_CHANGED);
            intent.putExtra(Constants.EXTRA_DEX_MODE, isDeXModeEnabled(context));
            context.sendBroadcast(intent);
        }
    }

    private static String mapKeyToSetting(String prefKey) {
        if (Constants.PREF_EDGE_TO_EDGE.equals(prefKey)) return SETTING_EDGE_TO_EDGE;
        if (Constants.PREF_START_MENU.equals(prefKey)) return SETTING_START_MENU;
        if (Constants.PREF_DEX_BUTTONS.equals(prefKey)) return SETTING_DEX_BUTTONS;
        if (Constants.PREF_APPLY_NORMAL.equals(prefKey)) return SETTING_APPLY_NORMAL;
        if (Constants.PREF_SHOW_NAV_BUTTONS.equals(prefKey)) return SETTING_SHOW_NAV_BUTTONS;
        if (Constants.PREF_SHOW_SEARCH_BUTTON.equals(prefKey)) return SETTING_SHOW_SEARCH_BUTTON;
        if (Constants.PREF_FLOATING_POPUPS.equals(prefKey)) return SETTING_FLOATING_POPUPS;
        if (Constants.PREF_TASKBAR_HEIGHT.equals(prefKey)) return SETTING_TASKBAR_HEIGHT;
        if (Constants.PREF_ICON_SIZE.equals(prefKey)) return SETTING_ICON_SIZE;
        if (Constants.PREF_ICON_SPACING.equals(prefKey)) return SETTING_ICON_SPACING;
        if (Constants.PREF_TASKBAR_OPACITY.equals(prefKey)) return SETTING_TASKBAR_OPACITY;
        return SETTING_DEX_MODE;
    }

    private static boolean getGlobalBoolean(Context context, String key, boolean defValue) {
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

    private static void setGlobalBoolean(Context context, String key, boolean value) {
        setGlobalInt(context, key, value ? 1 : 0);
    }

    private static int getGlobalInt(Context context, String key, int defValue) {
        if (context != null) {
            try {
                return Settings.Global.getInt(context.getContentResolver(), key, defValue);
            } catch (Throwable t) {
                XposedBridge.log("[HyperDeX] Error reading Settings.Global int: " + t.getMessage());
            }
        }
        return defValue;
    }

    private static void setGlobalInt(Context context, String key, int value) {
        if (context != null) {
            try {
                Settings.Global.putInt(context.getContentResolver(), key, value);
            } catch (Throwable t) {
                try {
                    String cmd = "settings put global " + key + " " + value;
                    Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
