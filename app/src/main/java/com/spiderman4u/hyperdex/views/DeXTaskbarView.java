package com.spiderman4u.hyperdex.views;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.spiderman4u.hyperdex.R;
import com.spiderman4u.hyperdex.config.ConfigManager;

public class DeXTaskbarView extends FrameLayout {

    private DeXNavButtonsView navButtonsView;
    private DeXDockAppsView dockAppsView;
    private DeXSystemTrayView systemTrayView;

    public DeXTaskbarView(Context context) {
        super(context);
        init(context);
    }

    public DeXTaskbarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        setId(R.id.dex_taskbar_view);
        setClipChildren(false);
        setClipToPadding(false);

        int barHeightDp = ConfigManager.getTaskbarHeight(context);
        int barHeightPx = dp(context, barHeightDp);
        int opacity = ConfigManager.getTaskbarOpacity(context);
        int alpha = (int) (opacity * 255 / 100f);
        int bgColor = Color.argb(alpha, 0x14, 0x15, 0x18);

        // Solid edge-to-edge acrylic taskbar background with crisp 1px top border
        GradientDrawable topBorder = new GradientDrawable();
        topBorder.setColor(Color.parseColor("#33FFFFFF"));

        GradientDrawable dexFill = new GradientDrawable();
        dexFill.setColor(bgColor);
        dexFill.setCornerRadius(0);

        LayerDrawable layerDrawable = new LayerDrawable(new GradientDrawable[]{topBorder, dexFill});
        layerDrawable.setLayerInset(1, 0, 1, 0, 0);
        setBackground(layerDrawable);

        // 1. Far-Left Navigation Buttons [ |||  ◯  <  Q  ::: ]
        navButtonsView = new DeXNavButtonsView(context);
        navButtonsView.setId(R.id.dex_nav_buttons);
        LayoutParams lpNav = new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                barHeightPx
        );
        lpNav.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        lpNav.leftMargin = dp(context, 4);
        addView(navButtonsView, lpNav);

        // 2. Center-Left Apps Dock (Tightly packed, zero flexbox gaps)
        dockAppsView = new DeXDockAppsView(context);
        dockAppsView.setId(R.id.dex_dock_apps);
        int navWidth = calculateNavWidth(context);
        LayoutParams lpApps = new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                barHeightPx
        );
        lpApps.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        lpApps.leftMargin = navWidth + dp(context, 8);
        lpApps.rightMargin = dp(context, 260);
        addView(dockAppsView, lpApps);

        // 3. Far-Right Unified System Tray
        systemTrayView = new DeXSystemTrayView(context);
        systemTrayView.setId(R.id.dex_system_tray);
        LayoutParams lpTray = new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                barHeightPx
        );
        lpTray.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        lpTray.rightMargin = dp(context, 4);
        addView(systemTrayView, lpTray);
    }

    public void updateStyleAndDimensions(Context context) {
        int barHeightDp = ConfigManager.getTaskbarHeight(context);
        int barHeightPx = dp(context, barHeightDp);
        int opacity = ConfigManager.getTaskbarOpacity(context);
        int alpha = (int) (opacity * 255 / 100f);
        int bgColor = Color.argb(alpha, 0x14, 0x15, 0x18);

        GradientDrawable topBorder = new GradientDrawable();
        topBorder.setColor(Color.parseColor("#33FFFFFF"));

        GradientDrawable dexFill = new GradientDrawable();
        dexFill.setColor(bgColor);
        dexFill.setCornerRadius(0);

        LayerDrawable layerDrawable = new LayerDrawable(new GradientDrawable[]{topBorder, dexFill});
        layerDrawable.setLayerInset(1, 0, 1, 0, 0);
        setBackground(layerDrawable);

        if (navButtonsView != null) {
            navButtonsView.updateVisibilityConfig(context);
            ViewGroup.LayoutParams lp = navButtonsView.getLayoutParams();
            if (lp instanceof LayoutParams) {
                ((LayoutParams) lp).height = barHeightPx;
                navButtonsView.setLayoutParams(lp);
            }
        }

        if (dockAppsView != null) {
            int navWidth = calculateNavWidth(context);
            ViewGroup.LayoutParams lp = dockAppsView.getLayoutParams();
            if (lp instanceof LayoutParams) {
                LayoutParams flp = (LayoutParams) lp;
                flp.height = barHeightPx;
                flp.leftMargin = navWidth + dp(context, 8);
                flp.rightMargin = dp(context, 260);
                dockAppsView.setLayoutParams(flp);
            }
            dockAppsView.reloadApps(context);
        }

        if (systemTrayView != null) {
            ViewGroup.LayoutParams lp = systemTrayView.getLayoutParams();
            if (lp instanceof LayoutParams) {
                ((LayoutParams) lp).height = barHeightPx;
                systemTrayView.setLayoutParams(lp);
            }
        }
    }

    public DeXDockAppsView getDockAppsView() {
        return dockAppsView;
    }

    public DeXNavButtonsView getNavButtonsView() {
        return navButtonsView;
    }

    public DeXSystemTrayView getSystemTrayView() {
        return systemTrayView;
    }

    private int calculateNavWidth(Context context) {
        boolean showNav = ConfigManager.isShowNavButtons(context);
        boolean showSearch = ConfigManager.isShowSearchButton(context);
        boolean showApps = ConfigManager.isStartMenuEnabled(context);

        int count = 0;
        if (showNav) count += 3;
        if (showSearch) count += 1;
        if (showApps) count += 1;

        if (count == 0) return dp(context, 4);
        return dp(context, count * 36 + 8);
    }

    private int dp(Context context, float val) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, val, context.getResources().getDisplayMetrics());
    }
}
