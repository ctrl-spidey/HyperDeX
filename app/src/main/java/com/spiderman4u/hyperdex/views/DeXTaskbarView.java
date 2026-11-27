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
        setClipChildren(true);
        setClipToPadding(true);

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

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int barHeightDp = ConfigManager.getTaskbarHeight(getContext());
        int height = dp(getContext(), barHeightDp);
        int heightSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);

        // 1. Measure Nav Buttons
        if (navButtonsView != null && navButtonsView.getVisibility() != GONE) {
            navButtonsView.measure(
                    MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
                    heightSpec
            );
        }
        int navW = (navButtonsView != null && navButtonsView.getVisibility() != GONE) ? navButtonsView.getMeasuredWidth() : 0;

        // 2. Measure System Tray
        if (systemTrayView != null && systemTrayView.getVisibility() != GONE) {
            systemTrayView.measure(
                    MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
                    heightSpec
            );
        }
        int trayW = (systemTrayView != null && systemTrayView.getVisibility() != GONE) ? systemTrayView.getMeasuredWidth() : 0;

        // 3. Constrain Dock Apps strictly to remaining space between Nav and Tray
        int spacing = dp(getContext(), 16);
        int maxDockW = Math.max(0, width - navW - trayW - spacing);
        if (dockAppsView != null && dockAppsView.getVisibility() != GONE) {
            dockAppsView.measure(
                    MeasureSpec.makeMeasureSpec(maxDockW, MeasureSpec.EXACTLY),
                    heightSpec
            );
        }

        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left;
        int height = bottom - top;

        int navW = (navButtonsView != null && navButtonsView.getVisibility() != GONE) ? navButtonsView.getMeasuredWidth() : 0;
        int trayW = (systemTrayView != null && systemTrayView.getVisibility() != GONE) ? systemTrayView.getMeasuredWidth() : 0;

        if (navButtonsView != null && navButtonsView.getVisibility() != GONE) {
            int navLeft = dp(getContext(), 4);
            navButtonsView.layout(navLeft, 0, navLeft + navW, height);
            navButtonsView.bringToFront();
        }

        if (systemTrayView != null && systemTrayView.getVisibility() != GONE) {
            int trayRight = width - dp(getContext(), 4);
            systemTrayView.layout(trayRight - trayW, 0, trayRight, height);
            systemTrayView.bringToFront();
        }

        if (dockAppsView != null && dockAppsView.getVisibility() != GONE) {
            int dockLeft = navW + dp(getContext(), 8);
            int dockRight = width - trayW - dp(getContext(), 8);
            if (dockRight < dockLeft) dockRight = dockLeft;
            dockAppsView.layout(dockLeft, 0, dockRight, height);
        }
    }

    @Override
    protected void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (systemTrayView != null) {
            systemTrayView.updateResponsiveLayout();
        }
        requestLayout();
    }

    private boolean isNotifReceiverRegistered = false;
    private final android.content.BroadcastReceiver mNotifReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(Context context, android.content.Intent intent) {
            if (intent == null || intent.getAction() == null) return;
            String action = intent.getAction();
            if (com.spiderman4u.hyperdex.config.Constants.ACTION_NEW_NOTIFICATION.equals(action)) {
                String pkg = intent.getStringExtra("pkg");
                String title = intent.getStringExtra("title");
                String text = intent.getStringExtra("text");
                String key = intent.getStringExtra("key");
                boolean syncOnly = intent.getBooleanExtra("sync_only", false);
                String formattedTime = "now";

                DeXNotificationPopup.addNotification(pkg, title, text, formattedTime, key);
                if (systemTrayView != null) {
                    systemTrayView.updateNotificationBadge(DeXNotificationPopup.getCount());
                }
                if (!syncOnly) {
                    DeXNotificationToastController.showNotificationToast(context, pkg, title, text, formattedTime, systemTrayView);
                }
            } else if (com.spiderman4u.hyperdex.config.Constants.ACTION_REMOVE_NOTIFICATION.equals(action)) {
                String key = intent.getStringExtra("key");
                String pkg = intent.getStringExtra("pkg");
                DeXNotificationPopup.removeNotification(key, pkg);
                if (systemTrayView != null) {
                    systemTrayView.updateNotificationBadge(DeXNotificationPopup.getCount());
                }
            } else if (com.spiderman4u.hyperdex.config.Constants.ACTION_CLEAR_NOTIFICATIONS.equals(action)) {
                DeXNotificationPopup.clearAll();
                if (systemTrayView != null) {
                    systemTrayView.updateNotificationBadge(0);
                }
            }
        }
    };

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!isNotifReceiverRegistered && getContext() != null) {
            try {
                android.content.IntentFilter filter = new android.content.IntentFilter();
                filter.addAction(com.spiderman4u.hyperdex.config.Constants.ACTION_NEW_NOTIFICATION);
                filter.addAction(com.spiderman4u.hyperdex.config.Constants.ACTION_REMOVE_NOTIFICATION);
                filter.addAction(com.spiderman4u.hyperdex.config.Constants.ACTION_CLEAR_NOTIFICATIONS);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    getContext().registerReceiver(mNotifReceiver, filter, Context.RECEIVER_EXPORTED);
                } else {
                    getContext().registerReceiver(mNotifReceiver, filter);
                }
                isNotifReceiverRegistered = true;
            } catch (Throwable ignored) {}
        }
        if (systemTrayView != null) {
            systemTrayView.updateNotificationBadge(DeXNotificationPopup.getCount());
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (isNotifReceiverRegistered && getContext() != null) {
            try {
                getContext().unregisterReceiver(mNotifReceiver);
            } catch (Throwable ignored) {}
            isNotifReceiverRegistered = false;
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
