package com.spiderman4u.hyperdex.config;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.robv.android.xposed.XposedBridge;

public class ConfigManager {

    private static final String SETTING_DEX_MODE = "hyperdex_mode";
    private static final String SETTING_START_MENU = "hyperdex_start_menu";
    private static final String SETTING_SHOW_NAV_BUTTONS = "hyperdex_show_nav_buttons";
    private static final String SETTING_SHOW_SEARCH_BUTTON = "hyperdex_show_search_button";
    private static final String SETTING_FLOATING_POPUPS = "hyperdex_floating_popups";
    private static final String SETTING_TASKBAR_HEIGHT = "hyperdex_taskbar_height";
    private static final String SETTING_ICON_SIZE = "hyperdex_icon_size";
    private static final String SETTING_ICON_SPACING = "hyperdex_icon_spacing";
    private static final String SETTING_TASKBAR_OPACITY = "hyperdex_taskbar_opacity";
    private static final String SETTING_PINNED_PACKAGES = "hyperdex_pinned_packages";

    public static boolean isDeXModeEnabled(Context context) {
        return getGlobalBoolean(context, SETTING_DEX_MODE, true);
    }

    public static boolean isStartMenuEnabled(Context context) {
        return getGlobalBoolean(context, SETTING_START_MENU, true);
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
        if (!isDeXModeEnabled(context)) {
            return false;
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

    // Pinned Packages Customization
    public static List<String> getPinnedPackages(Context context) {
        List<String> list = new ArrayList<>();
        if (context == null) return list;
        try {
            String raw = Settings.Global.getString(context.getContentResolver(), SETTING_PINNED_PACKAGES);
            if (!TextUtils.isEmpty(raw)) {
                String[] items = raw.split(",");
                for (String item : items) {
                    String trimmed = item.trim();
                    if (!trimmed.isEmpty() && !list.contains(trimmed)) {
                        list.add(trimmed);
                    }
                }
            }
        } catch (Throwable t) {
            XposedBridge.log("[HyperDeX] Error reading pinned packages: " + t);
        }
        return list;
    }

    public static void setPinnedPackages(Context context, List<String> packages) {
        if (context == null) return;
        try {
            String joined = packages != null ? TextUtils.join(",", packages) : "";
            Settings.Global.putString(context.getContentResolver(), SETTING_PINNED_PACKAGES, joined);
        } catch (Throwable t) {
            try {
                String joined = packages != null ? TextUtils.join(",", packages) : "";
                String cmd = "settings put global " + SETTING_PINNED_PACKAGES + " '" + joined + "'";
                Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            } catch (Throwable ignored) {}
        }
        notifyChanged(context);
    }

    public static void addPinnedPackage(Context context, String pkg) {
        if (context == null || TextUtils.isEmpty(pkg)) return;
        List<String> current = getPinnedPackages(context);
        if (!current.contains(pkg)) {
            current.add(pkg);
            setPinnedPackages(context, current);
        }
    }

    public static void removePinnedPackage(Context context, String pkg) {
        if (context == null || TextUtils.isEmpty(pkg)) return;
        List<String> current = getPinnedPackages(context);
        if (current.remove(pkg)) {
            setPinnedPackages(context, current);
        }
    }

    public static boolean isPinnedPackage(Context context, String pkg) {
        if (context == null || TextUtils.isEmpty(pkg)) return false;
        return getPinnedPackages(context).contains(pkg);
    }

    public static void resetTaskbarDefaults(Context context) {
        setInt(context, Constants.PREF_TASKBAR_HEIGHT, Constants.DEFAULT_TASKBAR_HEIGHT);
        setInt(context, Constants.PREF_ICON_SIZE, Constants.DEFAULT_ICON_SIZE);
        setInt(context, Constants.PREF_ICON_SPACING, Constants.DEFAULT_ICON_SPACING);
        setInt(context, Constants.PREF_TASKBAR_OPACITY, Constants.DEFAULT_TASKBAR_OPACITY);
        setBoolean(context, Constants.PREF_SHOW_NAV_BUTTONS, true);
        setBoolean(context, Constants.PREF_SHOW_SEARCH_BUTTON, true);
        setBoolean(context, Constants.PREF_FLOATING_POPUPS, true);
        try {
            Settings.Global.putString(context.getContentResolver(), SETTING_PINNED_PACKAGES, "");
        } catch (Throwable ignored) {}
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
        if (Constants.PREF_START_MENU.equals(prefKey)) return SETTING_START_MENU;
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
