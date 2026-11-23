package com.spiderman4u.hyperdex.config;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class ConfigManager {

    private static XSharedPreferences sXSharedPrefs;

    public static boolean isDeXModeEnabled(Context context) {
        return getBoolean(context, Constants.PREF_DEX_MODE_ENABLED, true);
    }

    public static boolean isEdgeToEdgeEnabled(Context context) {
        return getBoolean(context, Constants.PREF_EDGE_TO_EDGE, true);
    }

    public static boolean isStartMenuEnabled(Context context) {
        return getBoolean(context, Constants.PREF_START_MENU, true);
    }

    public static boolean isDeXButtonsEnabled(Context context) {
        return getBoolean(context, Constants.PREF_DEX_BUTTONS, true);
    }

    public static void setDeXModeEnabled(Context context, boolean enabled) {
        setBoolean(context, Constants.PREF_DEX_MODE_ENABLED, enabled);
        // Broadcast mode change
        Intent intent = new Intent(Constants.ACTION_MODE_CHANGED);
        intent.putExtra(Constants.EXTRA_DEX_MODE, enabled);
        context.sendBroadcast(intent);
    }

    public static void setBoolean(Context context, String key, boolean value) {
        if (context != null) {
            SharedPreferences prefs = context.getSharedPreferences(Constants.PREF_FILE, Context.MODE_PRIVATE);
            prefs.edit().putBoolean(key, value).apply();
        }
    }

    public static boolean getBoolean(Context context, String key, boolean defValue) {
        // Try ContentProvider first if context is available (reliable cross-process)
        if (context != null) {
            try {
                Uri uri = Uri.parse("content://" + Constants.AUTHORITY);
                Bundle bundle = context.getContentResolver().call(uri, Constants.METHOD_GET_PREFS, null, null);
                if (bundle != null && bundle.containsKey(key)) {
                    return bundle.getBoolean(key, defValue);
                }
            } catch (Throwable ignored) {
            }

            try {
                SharedPreferences prefs = context.getSharedPreferences(Constants.PREF_FILE, Context.MODE_PRIVATE);
                if (prefs.contains(key)) {
                    return prefs.getBoolean(key, defValue);
                }
            } catch (Throwable ignored) {
            }
        }

        // Fallback to XSharedPreferences for Xposed hooks
        try {
            if (sXSharedPrefs == null) {
                sXSharedPrefs = new XSharedPreferences(Constants.PACKAGE_NAME, Constants.PREF_FILE);
                sXSharedPrefs.makeWorldReadable();
            } else {
                sXSharedPrefs.reload();
            }
            return sXSharedPrefs.getBoolean(key, defValue);
        } catch (Throwable t) {
            XposedBridge.log("[HyperDeX] Error reading XSharedPreferences: " + t.getMessage());
        }

        return defValue;
    }
}
