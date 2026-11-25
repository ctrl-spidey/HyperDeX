package com.spiderman4u.hyperdex.views;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.spiderman4u.hyperdex.config.ConfigManager;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.robv.android.xposed.XposedHelpers;

public class DeXDockAppsView extends HorizontalScrollView {

    private LinearLayout appsContainer;
    private Object mHotSeatsInstance = null;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    public DeXDockAppsView(Context context) {
        super(context);
        init(context);
    }

    public DeXDockAppsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_IF_CONTENT_SCROLLS);
        setFillViewport(false);
        setClipChildren(true);
        setClipToPadding(true);

        appsContainer = new LinearLayout(context);
        appsContainer.setOrientation(LinearLayout.HORIZONTAL);
        appsContainer.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        appsContainer.setClipChildren(true);
        appsContainer.setClipToPadding(true);
        addView(appsContainer, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        reloadApps(context);
    }

    public void setHotSeatsInstance(Object hotSeats) {
        this.mHotSeatsInstance = hotSeats;
        mHandler.post(() -> reloadApps(getContext()));
    }

    public void reloadApps(Context context) {
        if (appsContainer == null || context == null) return;
        appsContainer.removeAllViews();

        PackageManager pm = context.getPackageManager();
        int barHeightPx = dp(context, ConfigManager.getTaskbarHeight(context));
        int iconSizePx = dp(context, ConfigManager.getIconSize(context));
        int maxSafeIconPx = Math.max(dp(context, 18), barHeightPx - dp(context, 8));
        iconSizePx = Math.min(iconSizePx, maxSafeIconPx);
        int spacingPx = dp(context, ConfigManager.getIconSpacing(context));

        // 1. Get running processes for active indicator
        Set<String> runningPkgs = new HashSet<>();
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                List<ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
                if (procs != null) {
                    for (ActivityManager.RunningAppProcessInfo p : procs) {
                        if (p.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE) {
                            runningPkgs.add(p.processName);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 2. Collect pinned apps from HotSeats
        List<String> appPackages = new ArrayList<>();

        if (mHotSeatsInstance != null) {
            try {
                List<?> items = (List<?>) XposedHelpers.callMethod(mHotSeatsInstance, "getHotSeatsItemsIncludeDragging");
                if (items != null) {
                    for (Object item : items) {
                        if (item == null) continue;
                        String pkg = extractPackageFromItem(item);
                        if (pkg != null && !appPackages.contains(pkg) && !pkg.equals("com.miui.home")) {
                            if (pm.getLaunchIntentForPackage(pkg) != null) {
                                appPackages.add(pkg);
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        // 3. Fallback: discover verified installed launcher apps
        if (appPackages.isEmpty()) {
            try {
                Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
                mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> launchables = pm.queryIntentActivities(mainIntent, 0);
                if (launchables != null) {
                    for (ResolveInfo ri : launchables) {
                        if (ri.activityInfo != null && !ri.activityInfo.packageName.equals("com.miui.home") && !ri.activityInfo.packageName.contains("systemui")) {
                            String pkg = ri.activityInfo.packageName;
                            if (!appPackages.contains(pkg)) {
                                appPackages.add(pkg);
                                if (appPackages.size() >= 10) break;
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        // 4. Also add running foreground apps
        for (String rPkg : runningPkgs) {
            if (!appPackages.contains(rPkg) && !rPkg.startsWith("android") && !rPkg.contains("systemui") && !rPkg.equals("com.miui.home")) {
                try {
                    Intent launchIntent = pm.getLaunchIntentForPackage(rPkg);
                    if (launchIntent != null && appPackages.size() < 16) {
                        appPackages.add(rPkg);
                    }
                } catch (Throwable ignored) {}
            }
        }

        // 5. Render icons with exact spacing and DeX active running dot indicator
        for (String pkg : appPackages) {
            Drawable iconDrawable = null;
            try {
                iconDrawable = pm.getApplicationIcon(pkg);
            } catch (Throwable ignored) {}
            if (iconDrawable == null) continue;

            View appItem = createAppIconView(context, pkg, iconDrawable, iconSizePx, barHeightPx, spacingPx, runningPkgs.contains(pkg));
            appsContainer.addView(appItem);
        }
    }

    private View createAppIconView(Context context, String pkg, Drawable icon, int iconSizePx, int barHeightPx, int spacingPx, boolean isRunning) {
        FrameLayout itemContainer = new FrameLayout(context);
        LinearLayout.LayoutParams containerLp = new LinearLayout.LayoutParams(
                iconSizePx + spacingPx * 2,
                barHeightPx
        );
        itemContainer.setLayoutParams(containerLp);

        // Hover & tap ripple
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(context, 8));

        ColorStateList rippleColor = ColorStateList.valueOf(Color.parseColor("#33FFFFFF"));
        RippleDrawable ripple = new RippleDrawable(rippleColor, null, mask);
        itemContainer.setBackground(ripple);

        // App Icon
        ImageView iv = new ImageView(context);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setImageDrawable(icon);
        FrameLayout.LayoutParams lpIcon = new FrameLayout.LayoutParams(iconSizePx, iconSizePx);
        lpIcon.gravity = Gravity.CENTER;
        itemContainer.addView(iv, lpIcon);

        // Samsung DeX Running Indicator (DeX blue active dot below icon)
        if (isRunning) {
            View dot = new View(context);
            GradientDrawable dotBg = new GradientDrawable();
            dotBg.setColor(Color.parseColor("#0A84FF"));
            dotBg.setCornerRadius(dp(context, 2));
            dot.setBackground(dotBg);

            FrameLayout.LayoutParams lpDot = new FrameLayout.LayoutParams(dp(context, 4), dp(context, 4));
            lpDot.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            lpDot.bottomMargin = dp(context, 2);
            itemContainer.addView(dot, lpDot);
        }

        // Click launches or switches to app
        itemContainer.setOnClickListener(v -> {
            try {
                PackageManager pm = context.getPackageManager();
                Intent launchIntent = pm.getLaunchIntentForPackage(pkg);
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                    context.startActivity(launchIntent);
                }
            } catch (Throwable ignored) {}
        });

        return itemContainer;
    }

    private String extractPackageFromItem(Object item) {
        if (item == null) return null;
        try {
            Method m = item.getClass().getMethod("getPackageName");
            Object res = m.invoke(item);
            if (res instanceof String && !((String) res).isEmpty()) {
                return (String) res;
            }
        } catch (Throwable ignored) {}

        try {
            Method m = item.getClass().getMethod("getTargetComponent");
            Object comp = m.invoke(item);
            if (comp instanceof ComponentName) {
                return ((ComponentName) comp).getPackageName();
            }
        } catch (Throwable ignored) {}

        try {
            Method m = item.getClass().getMethod("getComponentName");
            Object comp = m.invoke(item);
            if (comp instanceof ComponentName) {
                return ((ComponentName) comp).getPackageName();
            }
        } catch (Throwable ignored) {}

        try {
            Intent intent = (Intent) XposedHelpers.getObjectField(item, "mIntent");
            if (intent != null && intent.getComponent() != null) {
                return intent.getComponent().getPackageName();
            }
            if (intent != null && intent.getPackage() != null) {
                return intent.getPackage();
            }
        } catch (Throwable ignored) {}

        try {
            Intent intent = (Intent) XposedHelpers.callMethod(item, "getIntent");
            if (intent != null && intent.getComponent() != null) {
                return intent.getComponent().getPackageName();
            }
            if (intent != null && intent.getPackage() != null) {
                return intent.getPackage();
            }
        } catch (Throwable ignored) {}

        try {
            ComponentName cn = (ComponentName) XposedHelpers.getObjectField(item, "componentName");
            if (cn != null) {
                return cn.getPackageName();
            }
        } catch (Throwable ignored) {}

        return null;
    }

    private int dp(Context context, float val) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, val, context.getResources().getDisplayMetrics());
    }
}
