package com.spiderman4u.hyperdex.views;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.spiderman4u.hyperdex.R;
import com.spiderman4u.hyperdex.config.ConfigManager;
import com.spiderman4u.hyperdex.config.Constants;
import com.spiderman4u.hyperdex.hooks.LauncherDockHook;
import com.spiderman4u.hyperdex.hooks.SystemUIHook;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class DeXDockAppsView extends HorizontalScrollView {

    private LinearLayout appsContainer;
    private Object mHotSeatsInstance = null;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    // Asynchronous thread-safe running task cache (0ms main thread delay on click)
    private final Map<String, Integer> mRunningTaskMap = new ConcurrentHashMap<>();
    private volatile String mFocusedTaskPkg = null;
    private ScheduledExecutorService mTaskScheduler = null;

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
        startTaskMonitoring(context);
    }

    public void setHotSeatsInstance(Object hotSeats) {
        this.mHotSeatsInstance = hotSeats;
        mMainHandler.post(() -> reloadApps(getContext()));
    }

    private void startTaskMonitoring(Context context) {
        if (mTaskScheduler != null && !mTaskScheduler.isShutdown()) {
            return;
        }
        mTaskScheduler = Executors.newSingleThreadScheduledExecutor();
        mTaskScheduler.scheduleWithFixedDelay(() -> {
            try {
                updateRunningTasksCache(getContext());
                mMainHandler.post(this::updateRunningDotsUi);
            } catch (Throwable ignored) {}
        }, 1, 2, TimeUnit.SECONDS);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mTaskScheduler != null) {
            mTaskScheduler.shutdownNow();
            mTaskScheduler = null;
        }
    }

    // 1. Asynchronous Background Task Query (Never blocks UI thread)
    @SuppressLint("BlockedPrivateApi")
    private void updateRunningTasksCache(Context context) {
        if (context == null) return;
        Map<String, Integer> newMap = new ConcurrentHashMap<>();
        String topFocusedPkg = null;

        try {
            Class<?> atmCls = XposedHelpers.findClassIfExists("android.app.ActivityTaskManager", context.getClassLoader());
            if (atmCls == null) {
                atmCls = Class.forName("android.app.ActivityTaskManager");
            }
            Object atm = XposedHelpers.callStaticMethod(atmCls, "getService");
            List<?> tasks = (List<?>) XposedHelpers.callMethod(atm, "getTasks", 30, false, false, 0);

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
                                int taskId = XposedHelpers.getIntField(task, "taskId");
                                boolean isFocused = false;
                                try {
                                    isFocused = XposedHelpers.getBooleanField(task, "isFocused");
                                } catch (Throwable ignored) {}

                                if (!newMap.containsKey(pkg)) {
                                    newMap.put(pkg, taskId);
                                }
                                if (isFocused && topFocusedPkg == null) {
                                    topFocusedPkg = pkg;
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable t) {
            try {
                ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null) {
                    List<ActivityManager.RunningTaskInfo> list = am.getRunningTasks(30);
                    if (list != null) {
                        for (ActivityManager.RunningTaskInfo info : list) {
                            ComponentName comp = (ComponentName) XposedHelpers.getObjectField(info, "realActivity");
                            if (comp == null) {
                                comp = (ComponentName) XposedHelpers.getObjectField(info, "baseActivity");
                            }
                            if (comp != null) {
                                String pkg = comp.getPackageName();
                                if (!pkg.equals("com.miui.home") && !pkg.contains("systemui") && !pkg.equals("android")) {
                                    int taskId = XposedHelpers.getIntField(info, "taskId");
                                    if (!newMap.containsKey(pkg)) {
                                        newMap.put(pkg, taskId);
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        mRunningTaskMap.clear();
        mRunningTaskMap.putAll(newMap);
        mFocusedTaskPkg = topFocusedPkg;
    }

    // 2. Lightweight UI Update: ONLY toggles the blue indicator dot, never recreates views
    private void updateRunningDotsUi() {
        if (appsContainer == null) return;
        for (int i = 0; i < appsContainer.getChildCount(); i++) {
            View itemContainer = appsContainer.getChildAt(i);
            if (itemContainer instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) itemContainer;
                Object tag = vg.getTag();
                if (tag instanceof String) {
                    String pkg = (String) tag;
                    boolean isRunning = mRunningTaskMap.containsKey(pkg);
                    View dot = vg.findViewById(R.id.dex_running_dot);
                    if (dot != null) {
                        dot.setVisibility(isRunning ? View.VISIBLE : View.GONE);
                    }
                }
            }
        }
    }

    // 3. Full App Dock Reload (Called only on config changes or user pin/unpin)
    public void reloadApps(Context context) {
        if (appsContainer == null || context == null) return;
        appsContainer.removeAllViews();

        PackageManager pm = context.getPackageManager();
        int barHeightPx = dp(context, ConfigManager.getTaskbarHeight(context));
        int iconSizePx = dp(context, ConfigManager.getIconSize(context));
        int maxSafeIconPx = Math.max(dp(context, 18), barHeightPx - dp(context, 8));
        iconSizePx = Math.min(iconSizePx, maxSafeIconPx);
        int spacingPx = dp(context, ConfigManager.getIconSpacing(context));

        // Pinned packages customization
        List<String> pinnedList = ConfigManager.getPinnedPackages(context);

        // Fallback default initialization if user has never customized pins
        if (pinnedList.isEmpty()) {
            pinnedList = collectDefaultHotSeatPackages(context, pm);
            if (!pinnedList.isEmpty()) {
                ConfigManager.setPinnedPackages(context, pinnedList);
            }
        }

        List<String> displayList = new ArrayList<>(pinnedList);

        // Append currently running apps that are not pinned
        for (String rPkg : mRunningTaskMap.keySet()) {
            if (!displayList.contains(rPkg) && displayList.size() < 16) {
                if (pm.getLaunchIntentForPackage(rPkg) != null) {
                    displayList.add(rPkg);
                }
            }
        }

        // Render dock items
        for (String pkg : displayList) {
            Drawable iconDrawable = null;
            try {
                iconDrawable = pm.getApplicationIcon(pkg);
            } catch (Throwable ignored) {}
            if (iconDrawable == null) continue;

            boolean isPinned = pinnedList.contains(pkg);
            boolean isRunning = mRunningTaskMap.containsKey(pkg);

            View appItem = createAppIconView(context, pkg, iconDrawable, iconSizePx, barHeightPx, spacingPx, isPinned, isRunning);
            appsContainer.addView(appItem);
        }
    }

    private List<String> collectDefaultHotSeatPackages(Context context, PackageManager pm) {
        List<String> defaults = new ArrayList<>();
        if (mHotSeatsInstance != null) {
            try {
                List<?> items = (List<?>) XposedHelpers.callMethod(mHotSeatsInstance, "getHotSeatsItemsIncludeDragging");
                if (items != null) {
                    for (Object item : items) {
                        String pkg = extractPackageFromItem(item);
                        if (pkg != null && !defaults.contains(pkg) && !pkg.equals("com.miui.home") && !pkg.contains("systemui")) {
                            if (pm.getLaunchIntentForPackage(pkg) != null) {
                                defaults.add(pkg);
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        if (defaults.isEmpty()) {
            try {
                Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
                mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> launchables = pm.queryIntentActivities(mainIntent, 0);
                if (launchables != null) {
                    for (ResolveInfo ri : launchables) {
                        if (ri.activityInfo != null && !ri.activityInfo.packageName.equals("com.miui.home")) {
                            String pkg = ri.activityInfo.packageName;
                            if (!defaults.contains(pkg)) {
                                defaults.add(pkg);
                                if (defaults.size() >= 8) break;
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
        return defaults;
    }

    private View createAppIconView(Context context, String pkg, Drawable icon, int iconSizePx, int barHeightPx, int spacingPx, boolean isPinned, boolean isRunning) {
        FrameLayout itemContainer = new FrameLayout(context);
        itemContainer.setTag(pkg);
        LinearLayout.LayoutParams containerLp = new LinearLayout.LayoutParams(
                iconSizePx + spacingPx * 2,
                barHeightPx
        );
        itemContainer.setLayoutParams(containerLp);

        // macOS / DeX Hover & Ripple
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

        // Samsung DeX Active Running Dot
        View dot = new View(context);
        dot.setId(R.id.dex_running_dot);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setColor(Color.parseColor("#0A84FF"));
        dotBg.setCornerRadius(dp(context, 2));
        dot.setBackground(dotBg);

        FrameLayout.LayoutParams lpDot = new FrameLayout.LayoutParams(dp(context, 4), dp(context, 4));
        lpDot.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lpDot.bottomMargin = dp(context, 2);
        dot.setVisibility(isRunning ? View.VISIBLE : View.GONE);
        itemContainer.addView(dot, lpDot);

        // Click: Instant 0ms response, fluid toggle
        itemContainer.setOnClickListener(v -> {
            // macOS-style tactile feedback bounce
            v.animate()
                    .scaleX(0.92f)
                    .scaleY(0.92f)
                    .setDuration(90)
                    .withEndAction(() -> {
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(130).setInterpolator(new OvershootInterpolator(1.4f)).start();
                    })
                    .start();

            handleAppClickInstant(context, pkg);
        });

        // Long Press: Dynamic Context Menu (Pin / Unpin / Close / App Info)
        itemContainer.setOnLongClickListener(v -> {
            showAppContextMenu(context, v, pkg, isPinned);
            return true;
        });

        return itemContainer;
    }

    // 4. Instant 0ms Minimize & Restore Toggle
    private void handleAppClickInstant(Context context, String pkg) {
        if (context == null || TextUtils.isEmpty(pkg)) return;

        // Reset dock to bottom whenever an app is interacted with
        LauncherDockHook.resetDockToBottom();

        Integer taskId = mRunningTaskMap.get(pkg);

        if (taskId != null && taskId > 0) {
            // If the app is currently focused and in the foreground -> MINIMIZE IT
            if (pkg.equals(mFocusedTaskPkg)) {
                minimizeTask(context, taskId);
            } else {
                // Minimized or backgrounded -> RESTORE IT INSTANTLY
                restoreTask(context, taskId, pkg);
            }
        } else {
            // Not running -> Launch fresh
            launchAppDirectly(context, pkg);
        }
    }

    private void minimizeTask(Context context, int taskId) {
        // 1. Xiaomi native workstation minimize
        try {
            Class<?> wrapperCls = XposedHelpers.findClassIfExists(
                    "com.miui.home.launcher.laptop.LaptopSmallWindowProxyWrapper",
                    context.getClassLoader()
            );
            if (wrapperCls != null) {
                Object wrapper = XposedHelpers.callStaticMethod(wrapperCls, "getInstance");
                if (wrapper != null) {
                    XposedHelpers.callMethod(wrapper, "hideDesktopApps", (Object) new int[]{ taskId });
                }
            }
        } catch (Throwable ignored) {}

        // 2. Broadcast to SystemUIHook WindowCaptionHook
        try {
            Intent intent = new Intent(Constants.ACTION_NAV_TRIGGER);
            intent.putExtra(Constants.EXTRA_NAV_ACTION, "minimize_task");
            intent.putExtra("task_id", taskId);
            context.sendBroadcast(intent);
        } catch (Throwable ignored) {}

        mFocusedTaskPkg = null;
    }

    private void restoreTask(Context context, int taskId, String pkg) {
        // 1. Bring existing task to front
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                am.moveTaskToFront(taskId, ActivityManager.MOVE_TASK_WITH_HOME);
            }
        } catch (Throwable ignored) {}

        // 2. Un-hide if eluded by workstation manager
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

        mFocusedTaskPkg = pkg;
    }

    private void launchAppDirectly(Context context, String pkg) {
        try {
            PackageManager pm = context.getPackageManager();
            Intent launchIntent = pm.getLaunchIntentForPackage(pkg);
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                context.startActivity(launchIntent);
            }
        } catch (Throwable t) {
            XposedBridge.log("[HyperDeX] launchAppDirectly failed: " + t);
        }
    }

    // 5. Desktop-Grade Long-Press Context Menu (Pin / Unpin / Close / Info)
    private void showAppContextMenu(Context context, View anchor, String pkg, boolean isPinned) {
        PackageManager pm = context.getPackageManager();
        String appName = pkg;
        Drawable appIcon = null;
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            appName = pm.getApplicationLabel(ai).toString();
            appIcon = pm.getApplicationIcon(ai);
        } catch (Throwable ignored) {}

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(context, 12);
        root.setPadding(pad, pad, pad, pad);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#F21E2028"));
        bg.setCornerRadius(dp(context, 14));
        bg.setStroke(dp(context, 1), Color.parseColor("#33FFFFFF"));
        root.setBackground(bg);
        root.setElevation(dp(context, 16));

        // Header: Icon + App Name
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageView iv = new ImageView(context);
        if (appIcon != null) iv.setImageDrawable(appIcon);
        header.addView(iv, new LinearLayout.LayoutParams(dp(context, 20), dp(context, 20)));

        TextView tvName = new TextView(context);
        tvName.setText(appName);
        tvName.setTextColor(Color.WHITE);
        tvName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        tvName.setMaxLines(1);
        tvName.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lpName = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpName.setMarginStart(dp(context, 8));
        header.addView(tvName, lpName);

        root.addView(header);

        // Divider
        View divider = new View(context);
        divider.setBackgroundColor(Color.parseColor("#26FFFFFF"));
        LinearLayout.LayoutParams lpDiv = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 1));
        lpDiv.setMargins(0, dp(context, 8), 0, dp(context, 6));
        root.addView(divider, lpDiv);

        final PopupWindow popup = new PopupWindow(root, dp(context, 200), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);

        // Action 1: Pin / Unpin
        String pinText = isPinned ? "Unpin from Taskbar" : "Pin to Taskbar";
        int pinIconColor = isPinned ? Color.parseColor("#FF5C5C") : Color.parseColor("#0A84FF");
        View btnPin = createMenuItem(context, pinText, pinIconColor);
        btnPin.setOnClickListener(v -> {
            popup.dismiss();
            if (isPinned) {
                ConfigManager.removePinnedPackage(context, pkg);
            } else {
                ConfigManager.addPinnedPackage(context, pkg);
            }
            reloadApps(context);
        });
        root.addView(btnPin);

        // Action 2: Close Window (if running)
        final Integer taskId = mRunningTaskMap.get(pkg);
        if (taskId != null && taskId > 0) {
            View btnClose = createMenuItem(context, "Close App", Color.parseColor("#FF7675"));
            btnClose.setOnClickListener(v -> {
                popup.dismiss();
                minimizeTask(context, taskId);
                mRunningTaskMap.remove(pkg);
                updateRunningDotsUi();
            });
            root.addView(btnClose);
        }

        // Action 3: App Info
        View btnInfo = createMenuItem(context, "App Info", Color.parseColor("#A0A2AA"));
        btnInfo.setOnClickListener(v -> {
            popup.dismiss();
            try {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + pkg));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Throwable ignored) {}
        });
        root.addView(btnInfo);

        // Show popup cleanly above icon
        int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        popup.showAtLocation(anchor, Gravity.NO_GRAVITY, location[0], location[1] - dp(context, 130));
    }

    private View createMenuItem(Context context, String label, int textColor) {
        TextView tv = new TextView(context);
        tv.setText(label);
        tv.setTextColor(textColor);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tv.setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8));

        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(context, 6));

        ColorStateList rippleColor = ColorStateList.valueOf(Color.parseColor("#26FFFFFF"));
        RippleDrawable ripple = new RippleDrawable(rippleColor, null, mask);
        tv.setBackground(ripple);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(context, 2);
        tv.setLayoutParams(lp);
        return tv;
    }

    private String extractPackageFromItem(Object item) {
        if (item == null) return null;
        try {
            Method m = item.getClass().getMethod("getPackageName");
            Object res = m.invoke(item);
            if (res instanceof String && !((String) res).isEmpty()) return (String) res;
        } catch (Throwable ignored) {}
        try {
            Method m = item.getClass().getMethod("getTargetComponent");
            Object comp = m.invoke(item);
            if (comp instanceof ComponentName) return ((ComponentName) comp).getPackageName();
        } catch (Throwable ignored) {}
        try {
            Method m = item.getClass().getMethod("getComponentName");
            Object comp = m.invoke(item);
            if (comp instanceof ComponentName) return ((ComponentName) comp).getPackageName();
        } catch (Throwable ignored) {}
        return null;
    }

    private int dp(Context context, float val) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, val, context.getResources().getDisplayMetrics());
    }
}
