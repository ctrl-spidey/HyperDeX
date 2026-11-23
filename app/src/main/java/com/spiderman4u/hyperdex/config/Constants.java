package com.spiderman4u.hyperdex.config;

public final class Constants {
    private Constants() {}

    public static final String PACKAGE_NAME = "com.spiderman4u.hyperdex";
    public static final String PKG_LAUNCHER = "com.miui.home";
    public static final String PKG_SYSTEMUI = "com.android.systemui";
    public static final String PKG_ANDROID = "android";

    public static final String PREF_FILE = "hyperdex_prefs";
    public static final String PREF_DEX_MODE_ENABLED = "pref_dex_mode_enabled";
    public static final String PREF_EDGE_TO_EDGE = "pref_edge_to_edge";
    public static final String PREF_START_MENU = "pref_start_menu";
    public static final String PREF_DEX_BUTTONS = "pref_dex_buttons";

    public static final String ACTION_MODE_CHANGED = "com.spiderman4u.hyperdex.ACTION_MODE_CHANGED";
    public static final String ACTION_TOGGLE_MODE = "com.spiderman4u.hyperdex.ACTION_TOGGLE_MODE";
    public static final String ACTION_SET_MODE = "com.spiderman4u.hyperdex.ACTION_SET_MODE";
    public static final String EXTRA_DEX_MODE = "extra_dex_mode";

    public static final String AUTHORITY = "com.spiderman4u.hyperdex.provider";
    public static final String METHOD_GET_PREFS = "get_prefs";
    public static final String METHOD_SET_PREF = "set_pref";
}
