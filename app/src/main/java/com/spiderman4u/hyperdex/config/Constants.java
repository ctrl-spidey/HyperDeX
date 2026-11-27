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
    public static final String PREF_SHOW_NAV_BUTTONS = "pref_show_nav_buttons";
    public static final String PREF_SHOW_SEARCH_BUTTON = "pref_show_search_button";
    public static final String PREF_FLOATING_POPUPS = "pref_floating_popups";
    public static final String PREF_WINDOW_CONTROLS = "pref_window_controls";
    public static final String PREF_TASKBAR_HEIGHT = "pref_taskbar_height";
    public static final String PREF_ICON_SIZE = "pref_icon_size";
    public static final String PREF_ICON_SPACING = "pref_icon_spacing";
    public static final String PREF_TASKBAR_OPACITY = "pref_taskbar_opacity";
    public static final String PREF_TASKBAR_BOTTOM_OFFSET = "pref_taskbar_bottom_offset";
    public static final String PREF_PINNED_PACKAGES = "pref_pinned_packages";

    public static final String ACTION_MODE_CHANGED = "com.spiderman4u.hyperdex.ACTION_MODE_CHANGED";
    public static final String ACTION_TOGGLE_MODE = "com.spiderman4u.hyperdex.ACTION_TOGGLE_MODE";
    public static final String ACTION_SET_MODE = "com.spiderman4u.hyperdex.ACTION_SET_MODE";
    public static final String EXTRA_DEX_MODE = "extra_dex_mode";

    public static final String ACTION_NAV_TRIGGER = "com.spiderman4u.hyperdex.ACTION_NAV_TRIGGER";
    public static final String EXTRA_NAV_ACTION = "nav_action";
    public static final String ACTION_NEW_NOTIFICATION = "com.spiderman4u.hyperdex.ACTION_NEW_NOTIFICATION";
    public static final String ACTION_REMOVE_NOTIFICATION = "com.spiderman4u.hyperdex.ACTION_REMOVE_NOTIFICATION";
    public static final String ACTION_DISMISS_NOTIFICATION = "com.spiderman4u.hyperdex.ACTION_DISMISS_NOTIFICATION";
    public static final String ACTION_CLEAR_NOTIFICATIONS = "com.spiderman4u.hyperdex.ACTION_CLEAR_NOTIFICATIONS";
    public static final String ACTION_RESET_DOCK_BOTTOM = "com.spiderman4u.hyperdex.ACTION_RESET_DOCK_BOTTOM";
    public static final String ACTION_HIDE_TASK = "com.spiderman4u.hyperdex.ACTION_HIDE_TASK";

    public static final String AUTHORITY = "com.spiderman4u.hyperdex.provider";
    public static final String METHOD_GET_PREFS = "get_prefs";
    public static final String METHOD_SET_PREF = "set_pref";

    // Defaults matching Samsung DeX
    public static final int DEFAULT_TASKBAR_HEIGHT = 44; // dp
    public static final int DEFAULT_ICON_SIZE = 34;       // dp
    public static final int DEFAULT_ICON_SPACING = 6;      // dp
    public static final int DEFAULT_TASKBAR_OPACITY = 92;  // %
    public static final int DEFAULT_TASKBAR_BOTTOM_OFFSET = 0; // dp

    // Developer Branding
    public static final String DEVELOPER_TAG = "@spideyHyperOS";
    public static final String APP_VERSION_NAME = "1.1.22";
}
