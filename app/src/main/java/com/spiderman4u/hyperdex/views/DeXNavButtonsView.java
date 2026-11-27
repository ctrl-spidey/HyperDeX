package com.spiderman4u.hyperdex.views;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.spiderman4u.hyperdex.R;
import com.spiderman4u.hyperdex.config.ConfigManager;
import com.spiderman4u.hyperdex.hooks.SystemUIHook;

import java.lang.reflect.Method;

public class DeXNavButtonsView extends LinearLayout {

    private ImageView btnRecents;
    private ImageView btnHome;
    private ImageView btnBack;
    private ImageView btnSearch;
    private ImageView btnApps;

    public DeXNavButtonsView(Context context) {
        super(context);
        init(context);
    }

    public DeXNavButtonsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        setPadding(dp(4), 0, dp(4), 0);

        int iconColor = Color.parseColor("#E1E2E8");
        int btnSize = dp(34);
        int iconPadding = dp(7);

        // 1. Recents Button [ ||| ]
        btnRecents = createIconButton(context, btnSize, iconPadding);
        btnRecents.setId(R.id.dex_btn_recents);
        btnRecents.setImageDrawable(DeXVectorDrawables.createRecentsDrawable(iconColor));
        btnRecents.setOnClickListener(v -> sendNavAction(context, "recents"));
        addView(btnRecents);

        // 2. Home Button [ ◯ ]
        btnHome = createIconButton(context, btnSize, iconPadding);
        btnHome.setId(R.id.dex_btn_home);
        btnHome.setImageDrawable(DeXVectorDrawables.createHomeDrawable(iconColor));
        btnHome.setOnClickListener(v -> {
            sendNavAction(context, "home");
            triggerHome(context);
        });
        addView(btnHome);

        // 3. Back Button [ < ]
        btnBack = createIconButton(context, btnSize, iconPadding);
        btnBack.setId(R.id.dex_btn_back);
        btnBack.setImageDrawable(DeXVectorDrawables.createBackDrawable(iconColor));
        btnBack.setOnClickListener(v -> sendNavAction(context, "back"));
        addView(btnBack);

        // 4. Search Button [ Q ]
        btnSearch = createIconButton(context, btnSize, iconPadding);
        btnSearch.setId(R.id.dex_btn_search);
        btnSearch.setImageDrawable(DeXVectorDrawables.createSearchDrawable(iconColor));
        btnSearch.setOnClickListener(v -> triggerSearch(context));
        addView(btnSearch);

        // 5. Apps Drawer Button [ ::: ]
        btnApps = createIconButton(context, btnSize, iconPadding);
        btnApps.setId(R.id.dex_btn_apps);
        btnApps.setImageDrawable(DeXVectorDrawables.createAppsDrawable(iconColor));
        btnApps.setOnClickListener(v -> toggleAppDrawer(context));
        addView(btnApps);

        updateVisibilityConfig(context);
    }

    public void updateVisibilityConfig(Context context) {
        boolean showNav = ConfigManager.isShowNavButtons(context);
        boolean showSearch = ConfigManager.isShowSearchButton(context);
        boolean showApps = ConfigManager.isStartMenuEnabled(context);

        if (btnRecents != null) btnRecents.setVisibility(showNav ? VISIBLE : GONE);
        if (btnHome != null) btnHome.setVisibility(showNav ? VISIBLE : GONE);
        if (btnBack != null) btnBack.setVisibility(showNav ? VISIBLE : GONE);
        if (btnSearch != null) btnSearch.setVisibility(showSearch ? VISIBLE : GONE);
        if (btnApps != null) btnApps.setVisibility(showApps ? VISIBLE : GONE);
    }

    private ImageView createIconButton(Context context, int size, int padding) {
        ImageView iv = new ImageView(context);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setPadding(padding, padding, padding, padding);

        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(8));

        GradientDrawable content = new GradientDrawable();
        content.setColor(Color.TRANSPARENT);
        content.setCornerRadius(dp(8));

        ColorStateList rippleColor = ColorStateList.valueOf(Color.parseColor("#33FFFFFF"));
        RippleDrawable ripple = new RippleDrawable(rippleColor, content, mask);
        iv.setBackground(ripple);

        LayoutParams lp = new LayoutParams(size, size);
        lp.setMargins(dp(2), 0, dp(2), 0);
        iv.setLayoutParams(lp);
        return iv;
    }

    private void triggerHome(Context context) {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            context.startActivity(intent);
        } catch (Throwable ignored) {}
    }

    private Object getLauncher(Context context) {
        Context ctx = context;
        while (ctx instanceof android.content.ContextWrapper) {
            if (ctx.getClass().getName().contains("Launcher")) {
                return ctx;
            }
            ctx = ((android.content.ContextWrapper) ctx).getBaseContext();
        }
        try {
            Class<?> appClass = Class.forName("com.miui.home.launcher.Application");
            Method getLauncherMethod = appClass.getMethod("getLauncher");
            Object launcher = getLauncherMethod.invoke(null);
            if (launcher != null) return launcher;
        } catch (Throwable ignored) {}
        try {
            Class<?> lasCls = Class.forName("com.miui.home.launcher.LauncherAppState");
            Object appState = lasCls.getMethod("getInstance").invoke(null);
            if (appState != null) {
                return appState.getClass().getMethod("getLauncher").invoke(appState);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private void toggleAppDrawer(Context context) {
        Object launcher = getLauncher(context);
        if (launcher != null) {
            try {
                Method showMethod = launcher.getClass().getMethod("showOrHideAllAppsWindow");
                showMethod.invoke(launcher);
                return;
            } catch (Throwable ignored) {}
            try {
                Method showMethod = launcher.getClass().getMethod("setAllAppsSheetShowOrHide");
                showMethod.invoke(launcher);
                return;
            } catch (Throwable ignored) {}
            try {
                Method showMethod = launcher.getClass().getMethod("showAllApps", boolean.class);
                showMethod.invoke(launcher, true);
                return;
            } catch (Throwable ignored) {}
            try {
                Method showMethod = launcher.getClass().getMethod("showAllApps");
                showMethod.invoke(launcher);
                return;
            } catch (Throwable ignored) {}
        }
    }

    private void triggerSearch(Context context) {
        Object launcher = getLauncher(context);
        if (launcher != null) {
            try {
                Method getLaptopCtrl = launcher.getClass().getMethod("getLaptopAllAppsController");
                Object laptopCtrl = getLaptopCtrl.invoke(launcher);
                if (laptopCtrl != null) {
                    Method showSearch = laptopCtrl.getClass().getMethod("showSearchApps");
                    showSearch.invoke(laptopCtrl);
                    return;
                }
            } catch (Throwable ignored) {}
            try {
                Method showSearchDialog = launcher.getClass().getMethod("showHomeSearchBarDialog");
                showSearchDialog.invoke(launcher);
                return;
            } catch (Throwable ignored) {}
            try {
                Method showMethod = launcher.getClass().getMethod("showOrHideAllAppsWindow");
                showMethod.invoke(launcher);
                return;
            } catch (Throwable ignored) {}
        }
    }

    public static void sendNavAction(Context context, String action) {
        try {
            Intent intent = new Intent(SystemUIHook.ACTION_NAV_TRIGGER);
            intent.setPackage("com.android.systemui");
            intent.putExtra(SystemUIHook.EXTRA_NAV_ACTION, action);
            context.sendBroadcast(intent);
        } catch (Throwable ignored) {}
    }

    private int dp(float val) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, val, getResources().getDisplayMetrics());
    }
}
