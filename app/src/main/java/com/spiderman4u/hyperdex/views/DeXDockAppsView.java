package com.spiderman4u.hyperdex.views;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.ActivityOptions;
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
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;
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

import java.lang.reflect.Method;
import java.util.ArrayList;
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
    private final Set<String> mVisibleTaskPkgs = ConcurrentHashMap.newKeySet();
    private volatile String mFocusedTaskPkg = null;
    private final Set<String> mLastDisplayedRunningPkgs = new HashSet<>();
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
                boolean unpinnedChanged = updateRunningTasksCache(getContext());
                mMainHandler.post(() -> {
                    if (unpinnedChanged) {
                        reloadApps(getContext());
                    } else {
                        updateRunningDotsUi();
                    }
                });
            } catch (Throwable ignored) {}
        }, 400, 1200, TimeUnit.MILLISECONDS);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mTaskScheduler != null) {
            mTaskScheduler.shutdownNow();
            mTaskScheduler = null;
        }
    }

    // 1. Asynchronous Background Task Query (strictly filters truly running & visible tasks)
    @SuppressLint("BlockedPrivateApi")
    private boolean updateRunningTasksCache(Context context) {
        if (context == null) return false;
        Map<String, Integer> newMap = new ConcurrentHashMap<>();
        Set<String> newVisible = new HashSet<>();
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
                        boolean isRunning = true;
                        try {
                            isRunning = XposedHelpers.getBooleanField(task, "isRunning");
                        } catch (Throwable ignored) {}

                        int numActivities = 1;
                        try {
                            numActivities = XposedHelpers.getIntField(task, "numActivities");
                        } catch (Throwable ignored) {}

                        int topActivityType = 1;
                        try {
                            topActivityType = XposedHelpers.getIntField(task, "topActivityType");
                        } catch (Throwable ignored) {}

                        // Skip dead, empty, launcher/recents/assistant tasks
                        if (!isRunning || numActivities <= 0 || topActivityType > 1) {
                            continue;
                        }

                        ComponentName comp = (ComponentName) XposedHelpers.getObjectField(task, "realActivity");
                        if (comp == null) {
                            comp = (ComponentName) XposedHelpers.getObjectField(task, "topActivity");
                        }
                        if (comp == null) {
                            comp = (ComponentName) XposedHelpers.getObjectField(task, "baseActivity");
                        }
                        if (comp != null) {
                            String pkg = comp.getPackageName();
                            if (!pkg.equals("com.miui.home") && !pkg.contains("systemui") && !pkg.equals("android") && !pkg.equals(Constants.PACKAGE_NAME)) {
                                int taskId = XposedHelpers.getIntField(task, "taskId");
                                boolean isFocused = false;
                                boolean isVisible = false;
                                try {
                                    isFocused = XposedHelpers.getBooleanField(task, "isFocused");
                                } catch (Throwable ignored) {}
                                try {
                                    isVisible = XposedHelpers.getBooleanField(task, "isVisible");
                                } catch (Throwable ignored) {}

                                if (!newMap.containsKey(pkg)) {
                                    newMap.put(pkg, taskId);
                                }
                                if (isVisible) {
                                    newVisible.add(pkg);
                                }
                                if ((isFocused || (isVisible && topFocusedPkg == null)) && topFocusedPkg == null) {
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
                            if (info.numRunning <= 0) continue;
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
        mVisibleTaskPkgs.clear();
        mVisibleTaskPkgs.addAll(newVisible);
        mFocusedTaskPkg = topFocusedPkg;

        boolean changed = !mLastDisplayedRunningPkgs.equals(newMap.keySet());
        if (changed) {
            mLastDisplayedRunningPkgs.clear();
            mLastDisplayedRunningPkgs.addAll(newMap.keySet());
        }
        return changed;
    }

    // 2. Lightweight UI Update: updates active capsule highlight & running/focused underline bars cleanly
    private void updateRunningDotsUi() {
        if (appsContainer == null) return;
        Context context = getContext();
        for (int i = 0; i < appsContainer.getChildCount(); i++) {
            View itemContainer = appsContainer.getChildAt(i);
            if (itemContainer instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) itemContainer;
                Object tag = vg.getTag();
                if (tag instanceof String) {
                    String pkg = (String) tag;
                    boolean isRunning = mRunningTaskMap.containsKey(pkg);
                    boolean isFocused = pkg.equals(mFocusedTaskPkg);
                    boolean isVisible = mVisibleTaskPkgs.contains(pkg);

                    View highlight = vg.findViewById(R.id.dex_app_highlight);
                    if (highlight != null) {
                        highlight.setVisibility(isFocused ? View.VISIBLE : View.INVISIBLE);
                    }

                    View dot = vg.findViewById(R.id.dex_running_dot);
                    if (dot != null) {
                        if (!isRunning) {
                            dot.setVisibility(View.GONE);
                        } else {
                            dot.setVisibility(View.VISIBLE);
                            ViewGroup.LayoutParams lp = dot.getLayoutParams();
                            if (lp != null) {
                                lp.width = dp(context, isFocused ? 16 : (isVisible ? 10 : 6));
                                lp.height = dp(context, 3);
                                dot.setLayoutParams(lp);
                            }
                            if (dot.getBackground() instanceof GradientDrawable) {
                                GradientDrawable gd = (GradientDrawable) dot.getBackground();
                                gd.setColor(isFocused ? Color.parseColor("#0A84FF") : Color.parseColor("#99E1E2E8"));
                            }
                        }
                    }
                }
            }
        }
    }

    // 3. Full App Dock Reload (Called on config changes, pin/unpin/reorder, or when running unpinned apps change)
    public void reloadApps(Context context) {
        if (appsContainer == null || context == null) return;
        appsContainer.removeAllViews();

        PackageManager pm = context.getPackageManager();
        int barHeightPx = dp(context, ConfigManager.getTaskbarHeight(context));
        int iconSizePx = dp(context, ConfigManager.getIconSize(context));
        int maxSafeIconPx = Math.max(dp(context, 18), barHeightPx - dp(context, 8));
        iconSizePx = Math.min(iconSizePx, maxSafeIconPx);
        int spacingPx = dp(context, ConfigManager.getIconSpacing(context));

        List<String> pinnedList = ConfigManager.getPinnedPackages(context);
        if (pinnedList.isEmpty()) {
            pinnedList = collectDefaultHotSeatPackages(context, pm);
            if (!pinnedList.isEmpty()) {
                ConfigManager.setPinnedPackages(context, pinnedList);
            }
        }

        List<String> displayList = new ArrayList<>(pinnedList);
        for (String rPkg : mRunningTaskMap.keySet()) {
            if (!displayList.contains(rPkg) && displayList.size() < 16) {
                if (pm.getLaunchIntentForPackage(rPkg) != null) {
                    displayList.add(rPkg);
                }
            }
        }

        for (String pkg : displayList) {
            Drawable iconDrawable = null;
            try {
                iconDrawable = pm.getApplicationIcon(pkg);
            } catch (Throwable ignored) {}
            if (iconDrawable == null) continue;

            boolean isPinned = pinnedList.contains(pkg);
            boolean isRunning = mRunningTaskMap.containsKey(pkg);
            boolean isFocused = pkg.equals(mFocusedTaskPkg);
            boolean isVisible = mVisibleTaskPkgs.contains(pkg);

            View appItem = createAppIconView(context, pkg, iconDrawable, iconSizePx, barHeightPx, spacingPx, isPinned, isRunning, isFocused, isVisible);
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

    private View createAppIconView(Context context, String pkg, Drawable icon, int iconSizePx, int barHeightPx, int spacingPx, boolean isPinned, boolean isRunning, boolean isFocused, boolean isVisible) {
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

        // Subtle Active App Glass Capsule Highlight
        View activeHighlight = new View(context);
        activeHighlight.setId(R.id.dex_app_highlight);
        GradientDrawable hiBg = new GradientDrawable();
        hiBg.setColor(Color.parseColor("#1FFFFFFF"));
        hiBg.setCornerRadius(dp(context, 8));
        activeHighlight.setBackground(hiBg);
        activeHighlight.setVisibility(isFocused ? View.VISIBLE : View.INVISIBLE);
        FrameLayout.LayoutParams lpHighlight = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        lpHighlight.setMargins(dp(context, 2), dp(context, 4), dp(context, 2), dp(context, 4));
        itemContainer.addView(activeHighlight, lpHighlight);

        // App Icon
        ImageView iv = new ImageView(context);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setImageDrawable(icon);
        FrameLayout.LayoutParams lpIcon = new FrameLayout.LayoutParams(iconSizePx, iconSizePx);
        lpIcon.gravity = Gravity.CENTER;
        itemContainer.addView(iv, lpIcon);

        // Samsung DeX Active Running Bar / Dot
        View dot = new View(context);
        dot.setId(R.id.dex_running_dot);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setColor(isFocused ? Color.parseColor("#0A84FF") : Color.parseColor("#99E1E2E8"));
        dotBg.setCornerRadius(dp(context, 1.5f));
        dot.setBackground(dotBg);

        FrameLayout.LayoutParams lpDot = new FrameLayout.LayoutParams(
                dp(context, isFocused ? 16 : (isVisible ? 10 : 6)),
                dp(context, 3)
        );
        lpDot.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lpDot.bottomMargin = dp(context, 2);
        dot.setVisibility(isRunning ? View.VISIBLE : View.GONE);
        itemContainer.addView(dot, lpDot);

        // Click: Instant 0ms response, macOS spring bounce + toggle minimize/restore
        itemContainer.setOnClickListener(v -> {
            v.animate().cancel();
            v.animate()
                    .scaleX(0.88f)
                    .scaleY(0.88f)
                    .setDuration(85)
                    .withEndAction(() -> v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(160).setInterpolator(new OvershootInterpolator(1.6f)).start())
                    .start();

            handleAppClickInstant(context, pkg, v);
        });

        // Long Press: Dynamic Context Menu (Pin / Unpin / Move Left-Right / Close / App Info)
        itemContainer.setOnLongClickListener(v -> {
            showAppContextMenu(context, v, pkg, isPinned);
            return true;
        });

        return itemContainer;
    }

    // 4. Instant 0ms Minimize & Restore Toggle
    private void handleAppClickInstant(Context context, String pkg, View anchorView) {
        if (context == null || TextUtils.isEmpty(pkg)) return;

        LauncherDockHook.resetDockToBottom();

        Integer taskId = mRunningTaskMap.get(pkg);

        if (taskId != null && taskId > 0) {
            if (pkg.equals(mFocusedTaskPkg)) {
                minimizeTask(context, taskId, pkg);
            } else {
                restoreTask(context, taskId, pkg, anchorView);
            }
        } else {
            launchAppDirectly(context, pkg, anchorView);
        }
    }

    private void minimizeTask(Context context, int taskId, String pkg) {
        mFocusedTaskPkg = null;
        if (pkg != null) {
            mVisibleTaskPkgs.remove(pkg);
        }
        updateRunningDotsUi();

        Intent intent = new Intent(Constants.ACTION_NAV_TRIGGER);
        intent.setPackage(Constants.PKG_SYSTEMUI);
        intent.putExtra(Constants.EXTRA_NAV_ACTION, "minimize_task");
        intent.putExtra("task_id", taskId);
        context.sendBroadcast(intent);
    }

    public static void hideTaskDirectly(Context context, int taskId) {
        if (context == null || taskId <= 0) return;
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
    }

    private void restoreTask(Context context, int taskId, String pkg, View anchorView) {
        mFocusedTaskPkg = pkg;
        mVisibleTaskPkgs.add(pkg);
        updateRunningDotsUi();

        // 1. Un-hide if eluded by workstation manager
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

        // 2. Bring existing task to front cleanly with smooth scale-up from dock icon (without MOVE_TASK_WITH_HOME)
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                ActivityOptions opts = (anchorView != null)
                        ? ActivityOptions.makeScaleUpAnimation(anchorView, 0, 0, anchorView.getWidth(), anchorView.getHeight())
                        : ActivityOptions.makeBasic();
                am.moveTaskToFront(taskId, ActivityManager.MOVE_TASK_NO_USER_ACTION, opts.toBundle());
            }
        } catch (Throwable t) {
            XposedBridge.log("[HyperDeX] moveTaskToFront failed: " + t);
        }

        LauncherDockHook.resetDockToBottom();
    }

    private void closeTask(Context context, int taskId, String pkg) {
        mRunningTaskMap.remove(pkg);
        mVisibleTaskPkgs.remove(pkg);
        if (pkg.equals(mFocusedTaskPkg)) {
            mFocusedTaskPkg = null;
        }
        try {
            Intent intent = new Intent(Constants.ACTION_NAV_TRIGGER);
            intent.setPackage("com.android.systemui");
            intent.putExtra(Constants.EXTRA_NAV_ACTION, "close_task");
            intent.putExtra("task_id", taskId);
            context.sendBroadcast(intent);
        } catch (Throwable ignored) {}
        reloadApps(context);
    }

    private void launchAppDirectly(Context context, String pkg, View anchorView) {
        try {
            PackageManager pm = context.getPackageManager();
            Intent launchIntent = pm.getLaunchIntentForPackage(pkg);
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                if (anchorView != null) {
                    ActivityOptions opts = ActivityOptions.makeScaleUpAnimation(anchorView, 0, 0, anchorView.getWidth(), anchorView.getHeight());
                    context.startActivity(launchIntent, opts.toBundle());
                } else {
                    context.startActivity(launchIntent);
                }
            }
        } catch (Throwable t) {
            XposedBridge.log("[HyperDeX] launchAppDirectly failed: " + t);
        }
    }

    // 5. Desktop-Grade Long-Press Context Menu (Pin / Unpin / Move / Close / Info)
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

        int popupWidth = dp(context, 210);
        final PopupWindow popup = new PopupWindow(root, popupWidth, ViewGroup.LayoutParams.WRAP_CONTENT, true);
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

        // Action 2: Reorder Pinned Icons (Move Left / Move Right)
        if (isPinned) {
            LinearLayout moveRow = new LinearLayout(context);
            moveRow.setOrientation(LinearLayout.HORIZONTAL);
            View btnLeft = createMenuItem(context, "← Move Left", Color.parseColor("#D0D3DC"));
            btnLeft.setOnClickListener(v -> {
                popup.dismiss();
                if (ConfigManager.movePinnedPackage(context, pkg, -1)) {
                    reloadApps(context);
                }
            });
            View btnRight = createMenuItem(context, "Move Right →", Color.parseColor("#D0D3DC"));
            btnRight.setOnClickListener(v -> {
                popup.dismiss();
                if (ConfigManager.movePinnedPackage(context, pkg, 1)) {
                    reloadApps(context);
                }
            });
            moveRow.addView(btnLeft, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            moveRow.addView(btnRight, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            root.addView(moveRow);
        }

        // Action 3: Close Window (if running)
        final Integer taskId = mRunningTaskMap.get(pkg);
        if (taskId != null && taskId > 0) {
            View btnClose = createMenuItem(context, "Close App", Color.parseColor("#FF7675"));
            btnClose.setOnClickListener(v -> {
                popup.dismiss();
                closeTask(context, taskId, pkg);
            });
            root.addView(btnClose);
        }

        // Action 4: App Info
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

        // Measure dynamically so popup aligns flush above the taskbar icon
        root.measure(
                MeasureSpec.makeMeasureSpec(popupWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        );
        int menuHeight = root.getMeasuredHeight();

        int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        popup.showAtLocation(anchor, Gravity.NO_GRAVITY, location[0], Math.max(dp(context, 12), location[1] - menuHeight - dp(context, 8)));

        root.setAlpha(0f);
        root.setScaleX(0.92f);
        root.setScaleY(0.92f);
        root.setTranslationY(dp(context, 10));
        root.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(200)
                .setInterpolator(new PathInterpolator(0.22f, 1.0f, 0.36f, 1.0f))
                .start();
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
