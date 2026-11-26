package com.spiderman4u.hyperdex.views;

import android.annotation.SuppressLint;
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

import de.robv.android.xposed.XposedBridge;
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

        // 1. Get actually running tasks (active activity windows, not background services/daemons)
        Set<String> runningPkgs = getActuallyRunningPackages(context);

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

        // Click launches, restores, or minimizes app (Samsung DeX & Windows taskbar toggle behavior)
        itemContainer.setOnClickListener(v -> {
            try {
                handleAppClick(context, pkg);
            } catch (Throwable t) {
                XposedBridge.log("[HyperDeX] itemContainer click error: " + t);
            }
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

    @SuppressLint("BlockedPrivateApi")
    private void handleAppClick(Context context, String pkg) {
        if (context == null || pkg == null) return;

        try {
            List<?> tasks = null;
            try {
                Class<?> atmCls = XposedHelpers.findClassIfExists("android.app.ActivityTaskManager", context.getClassLoader());
                if (atmCls == null) {
                    atmCls = Class.forName("android.app.ActivityTaskManager");
                }
                Object atm = XposedHelpers.callStaticMethod(atmCls, "getService");
                tasks = (List<?>) XposedHelpers.callMethod(atm, "getTasks", 50, false, false, 0);
            } catch (Throwable t) {
                try {
                    ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                    if (am != null) {
                        tasks = am.getRunningTasks(50);
                    }
                } catch (Throwable ignored) {}
            }

            int targetTaskId = -1;
            boolean isVisible = false;
            boolean isFocused = false;
            boolean isTopNonHomeTask = false;

            if (tasks != null) {
                boolean foundTopNonHome = false;
                for (Object task : tasks) {
                    try {
                        ComponentName comp = (ComponentName) XposedHelpers.getObjectField(task, "realActivity");
                        if (comp == null) {
                            comp = (ComponentName) XposedHelpers.getObjectField(task, "baseActivity");
                        }
                        if (comp != null) {
                            String taskPkg = comp.getPackageName();
                            if (!taskPkg.equals("com.miui.home") && !taskPkg.contains("systemui") && !taskPkg.equals("android")) {
                                if (!foundTopNonHome) {
                                    foundTopNonHome = true;
                                    if (pkg.equals(taskPkg)) {
                                        isTopNonHomeTask = true;
                                    }
                                }
                            }
                            if (pkg.equals(taskPkg)) {
                                targetTaskId = XposedHelpers.getIntField(task, "taskId");
                                try {
                                    isVisible = XposedHelpers.getBooleanField(task, "isVisible");
                                } catch (Throwable ignored) {}
                                try {
                                    isFocused = XposedHelpers.getBooleanField(task, "isFocused");
                                } catch (Throwable ignored) {}
                                break;
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            }

            // Samsung DeX & Windows taskbar toggle:
            // If the app is currently showing on display and active/focused:
            // -> MINIMIZE IT!
            // Else (app is minimized, backgrounded, behind other windows, or closed):
            // -> RESTORE / BRING TO FRONT!
            if (targetTaskId > 0 && (isFocused || (isVisible && isTopNonHomeTask))) {
                minimizeTask(context, targetTaskId);
            } else {
                restoreOrLaunchTask(context, pkg, targetTaskId);
            }
        } catch (Throwable t) {
            XposedBridge.log("[HyperDeX] handleAppClick error: " + t);
            restoreOrLaunchTask(context, pkg, -1);
        }
    }

    private void minimizeTask(Context context, int taskId) {
        boolean handled = false;
        // 1. Native Xiaomi Workstation Controller (LaptopSmallWindowProxyWrapper)
        try {
            Class<?> wrapperCls = XposedHelpers.findClassIfExists(
                    "com.miui.home.launcher.laptop.LaptopSmallWindowProxyWrapper",
                    context.getClassLoader()
            );
            if (wrapperCls != null) {
                Object wrapper = XposedHelpers.callStaticMethod(wrapperCls, "getInstance");
                if (wrapper != null) {
                    if (taskId > 0) {
                        try {
                            XposedHelpers.callMethod(wrapper, "hideDesktopApps", (Object) new int[]{ taskId });
                            handled = true;
                        } catch (Throwable ignored) {}
                    }
                    try {
                        XposedHelpers.callMethod(wrapper, "eludeSmallWindow");
                        handled = true;
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable t) {
            XposedBridge.log("[HyperDeX] LaptopSmallWindowProxyWrapper minimize error: " + t);
        }

        // 2. Broadcast fallback to SystemUIHook
        try {
            Intent intent = new Intent("com.spiderman4u.hyperdex.NAV_TRIGGER");
            intent.putExtra("nav_action", "minimize_task");
            intent.putExtra("task_id", taskId);
            context.sendBroadcast(intent);
        } catch (Throwable t) {
            XposedBridge.log("[HyperDeX] Broadcast minimize_task failed: " + t);
        }
    }

    private void restoreOrLaunchTask(Context context, String pkg, int taskId) {
        if (taskId > 0) {
            try {
                ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null) {
                    am.moveTaskToFront(taskId, ActivityManager.MOVE_TASK_WITH_HOME);
                }
            } catch (Throwable t) {
                XposedBridge.log("[HyperDeX] moveTaskToFront failed: " + t);
            }
        }

        try {
            Class<?> wrapperCls = XposedHelpers.findClassIfExists(
                    "com.miui.home.launcher.laptop.LaptopSmallWindowProxyWrapper",
                    context.getClassLoader()
            );
            if (wrapperCls != null) {
                Object wrapper = XposedHelpers.callStaticMethod(wrapperCls, "getInstance");
                if (wrapper != null) {
                    XposedHelpers.callMethod(wrapper, "showEludedSmallWindow", false);
                }
            }
        } catch (Throwable ignored) {}

        try {
            PackageManager pm = context.getPackageManager();
            Intent launchIntent = pm.getLaunchIntentForPackage(pkg);
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                context.startActivity(launchIntent);
            }
        } catch (Throwable t) {
            XposedBridge.log("[HyperDeX] startActivity failed: " + t);
        }
    }

    @SuppressLint("BlockedPrivateApi")
    private Set<String> getActuallyRunningPackages(Context context) {
        Set<String> running = new HashSet<>();
        try {
            Class<?> atmCls = XposedHelpers.findClassIfExists("android.app.ActivityTaskManager", context.getClassLoader());
            if (atmCls == null) {
                atmCls = Class.forName("android.app.ActivityTaskManager");
            }
            Object atm = XposedHelpers.callStaticMethod(atmCls, "getService");
            List<?> tasks = (List<?>) XposedHelpers.callMethod(atm, "getTasks", 50, false, false, 0);
            if (tasks != null) {
                for (Object task : tasks) {
                    try {
                        ComponentName comp = (ComponentName) XposedHelpers.getObjectField(task, "realActivity");
                        if (comp == null) {
                            comp = (ComponentName) XposedHelpers.getObjectField(task, "baseActivity");
                        }
                        if (comp != null) {
                            String pkg = comp.getPackageName();
                            if (!pkg.equals("com.miui.home") && !pkg.contains("systemui") && !pkg.equals("android")) {
                                running.add(pkg);
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable t) {
            try {
                ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null) {
                    List<ActivityManager.RunningTaskInfo> list = am.getRunningTasks(50);
                    if (list != null) {
                        for (ActivityManager.RunningTaskInfo info : list) {
                            try {
                                ComponentName comp = (ComponentName) XposedHelpers.getObjectField(info, "realActivity");
                                if (comp == null) {
                                    comp = (ComponentName) XposedHelpers.getObjectField(info, "baseActivity");
                                }
                                if (comp != null) {
                                    String pkg = comp.getPackageName();
                                    if (!pkg.equals("com.miui.home") && !pkg.contains("systemui") && !pkg.equals("android")) {
                                        running.add(pkg);
                                    }
                                }
                            } catch (Throwable ignored2) {}
                        }
                    }
                }
            } catch (Throwable ignored3) {}
        }
        return running;
    }

    private final Runnable mRunningAppsRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (isAttachedToWindow() && getVisibility() == View.VISIBLE) {
                reloadApps(getContext());
            }
            mHandler.postDelayed(this, 3000);
        }
    };

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mHandler.removeCallbacks(mRunningAppsRefreshRunnable);
        mHandler.postDelayed(mRunningAppsRefreshRunnable, 2000);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mHandler.removeCallbacks(mRunningAppsRefreshRunnable);
    }

    private int dp(Context context, float val) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, val, context.getResources().getDisplayMetrics());
    }
}
