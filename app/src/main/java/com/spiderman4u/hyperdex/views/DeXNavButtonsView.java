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
        btnRecents.setOnClickListener(v -> sendKeyEvent(187)); // KEYCODE_APP_SWITCH
        addView(btnRecents);

        // 2. Home Button [ ◯ ]
        btnHome = createIconButton(context, btnSize, iconPadding);
        btnHome.setId(R.id.dex_btn_home);
        btnHome.setImageDrawable(DeXVectorDrawables.createHomeDrawable(iconColor));
        btnHome.setOnClickListener(v -> triggerHome(context));
        addView(btnHome);

        // 3. Back Button [ < ]
        btnBack = createIconButton(context, btnSize, iconPadding);
        btnBack.setId(R.id.dex_btn_back);
        btnBack.setImageDrawable(DeXVectorDrawables.createBackDrawable(iconColor));
        btnBack.setOnClickListener(v -> sendKeyEvent(4)); // KEYCODE_BACK
        addView(btnBack);

        // 4. Search Button [ Q ]
        btnSearch = createIconButton(context, btnSize, iconPadding);
        btnSearch.setId(R.id.dex_btn_search);
        btnSearch.setImageDrawable(DeXVectorDrawables.createSearchDrawable(iconColor));
        btnSearch.setOnClickListener(v -> sendKeyEvent(84)); // KEYCODE_SEARCH
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

        // Subtle translucent hover/touch ripple background
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
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable t) {
            sendKeyEvent(3); // KEYCODE_HOME
        }
    }

    private void toggleAppDrawer(Context context) {
        try {
            // Xiaomi launcher app drawer key or intent
            Intent intent = new Intent("com.miui.home.action.ALL_APPS");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable t) {
            sendKeyEvent(82); // KEYCODE_MENU or toggle
        }
    }

    private void sendKeyEvent(int keyCode) {
        new Thread(() -> {
            try {
                Runtime.getRuntime().exec(new String[]{"su", "-c", "input keyevent " + keyCode});
            } catch (Throwable ignored) {}
        }).start();
    }

    private int dp(float val) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, val, getResources().getDisplayMetrics());
    }
}
