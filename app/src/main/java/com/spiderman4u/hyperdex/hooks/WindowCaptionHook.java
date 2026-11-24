package com.spiderman4u.hyperdex.hooks;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.spiderman4u.hyperdex.R;
import com.spiderman4u.hyperdex.config.ConfigManager;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class WindowCaptionHook {

    private static final String TAG = "[HyperDeX-Caption] ";
    private static final List<WeakReference<View>> sStatusBarViews = new ArrayList<>();
    private static boolean sStatusBarObserverRegistered = false;

    private static class TaskData {
        final int taskId;
        final String appName;
        final Object dotInstance;

        TaskData(int taskId, String appName, Object dotInstance) {
            this.taskId = taskId;
            this.appName = appName;
            this.dotInstance = dotInstance;
        }
    }

    private static final Map<View, TaskData> sTaskDataMap = new WeakHashMap<>();
    private static final Map<Object, Object> sHostToDotMap = new WeakHashMap<>();
    private static final Map<Object, TaskData> sDotToTaskDataMap = new WeakHashMap<>();

    public static void init(XC_LoadPackage.LoadPackageParam lpparam) {
        hookMiuiDecorationDot(lpparam);
        hookMiuiDecorationRootViewHost(lpparam);
        hookMiuiDecorationDotView(lpparam);
        hookMiuiDecorationTouchListener(lpparam);
        hookMiuiCaptionContainerView(lpparam);
        hookAppHeaderViewHolder(lpparam);
        hookStatusBar(lpparam);
    }

    /**
     * Hook MiuiDecorationDot to capture TaskInfo, TaskBounds, and surface layout
     */
    private static void hookMiuiDecorationDot(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> dotCls = XposedHelpers.findClassIfExists(
                    "com.android.wm.shell.multitasking.miuimultiwinswitch.miuiwindowdecor.decoration.MiuiDecorationDot",
                    lpparam.classLoader
            );
            if (dotCls != null) {
                // 1. relayout: link TaskInfo and ViewHost
                XposedBridge.hookAllMethods(dotCls, "relayout", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object dotObj = param.thisObject;
                        int taskId = -1;
                        String appName = null;

                        Object taskInfo = null;
                        try {
                            taskInfo = XposedHelpers.getObjectField(dotObj, "mRunningTaskInfo");
                        } catch (Throwable ignored) {}
                        if (taskInfo == null) {
                            try {
                                taskInfo = XposedHelpers.getObjectField(dotObj, "mTaskInfo");
                            } catch (Throwable ignored) {}
                        }
                        if (taskInfo == null && param.args.length > 0) {
                            for (Object arg : param.args) {
                                if (arg != null && arg.getClass().getName().contains("TaskInfo")) {
                                    taskInfo = arg;
                                    break;
                                }
                            }
                        }

                        if (taskInfo != null) {
                            try {
                                taskId = XposedHelpers.getIntField(taskInfo, "taskId");
                            } catch (Throwable ignored) {}
                            try {
                                ComponentName topActivity = (ComponentName) XposedHelpers.getObjectField(taskInfo, "topActivity");
                                if (topActivity != null) {
                                    appName = topActivity.getPackageName();
                                }
                            } catch (Throwable ignored) {}
                        }

                        TaskData taskData = new TaskData(taskId, appName, dotObj);
                        sDotToTaskDataMap.put(dotObj, taskData);

                        // Associate host with dot controller
                        try {
                            Object host = XposedHelpers.getObjectField(dotObj, "mMiuiDecorationRootViewHost");
                            if (host != null) {
                                sHostToDotMap.put(host, dotObj);
                                try {
                                    View dotView = (View) XposedHelpers.callMethod(host, "getRootView");
                                    if (dotView != null) {
                                        sTaskDataMap.put(dotView, taskData);
                                        dotView.invalidate();
                                    }
                                } catch (Throwable ignored) {}
                            }
                        } catch (Throwable ignored) {}

                        // Secondary discovery of DotView
                        for (Field f : dotObj.getClass().getDeclaredFields()) {
                            try {
                                f.setAccessible(true);
                                Object v = f.get(dotObj);
                                if (v instanceof View && v.getClass().getName().contains("MiuiDecorationDotView")) {
                                    sTaskDataMap.put((View) v, taskData);
                                    ((View) v).invalidate();
                                    break;
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
                });

                // 2. updateTopCaptionSurface: position at (0, 0) and crop to task bounds width
                XposedBridge.hookAllMethods(dotCls, "updateTopCaptionSurface", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object dotObj = param.thisObject;
                        Context context = null;
                        try {
                            context = (Context) XposedHelpers.getObjectField(dotObj, "mContext");
                        } catch (Throwable ignored) {}

                        if (context == null || !ConfigManager.isClassicWindowControlsEnabled(context)) {
                            return;
                        }

                        if (param.args.length >= 2 && param.args[1] instanceof Rect) {
                            Object t = param.args[0]; // SurfaceControl.Transaction
                            Rect taskBounds = (Rect) param.args[1];
                            Object surface = null;
                            try {
                                surface = XposedHelpers.getObjectField(dotObj, "mCaptionContainerSurface");
                            } catch (Throwable ignored) {}

                            if (t != null && surface != null && taskBounds.width() > 0) {
                                int h = dp(context, 32);
                                XposedHelpers.callMethod(t, "setPosition", surface, 0f, 0f);
                                XposedHelpers.callMethod(t, "setWindowCrop", surface, taskBounds.width(), h);
                                XposedHelpers.callMethod(t, "apply");
                            }
                        }
                    }
                });

                XposedBridge.log(TAG + "MiuiDecorationDot hooked successfully");
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed hooking MiuiDecorationDot: " + t);
        }
    }

    /**
     * Hook MiuiDecorationRootViewHost / MiuiDecorationDotViewHost to report full window width and titlebar height
     */
    private static void hookMiuiDecorationRootViewHost(XC_LoadPackage.LoadPackageParam lpparam) {
        String[] hostClasses = new String[] {
                "com.android.wm.shell.multitasking.miuimultiwinswitch.miuiwindowdecor.decoration.MiuiDecorationDotViewHost",
                "com.android.wm.shell.multitasking.miuimultiwinswitch.miuiwindowdecor.decoration.MiuiDecorationRootViewHost"
        };

        for (String className : hostClasses) {
            try {
                Class<?> cls = XposedHelpers.findClassIfExists(className, lpparam.classLoader);
                if (cls == null) continue;

                // getSurfaceWidth: return taskBounds.width() instead of narrow pill width
                XposedBridge.hookAllMethods(cls, "getSurfaceWidth", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object host = param.thisObject;
                        Object dotObj = sHostToDotMap.get(host);
                        if (dotObj != null) {
                            try {
                                Context context = (Context) XposedHelpers.getObjectField(dotObj, "mContext");
                                if (ConfigManager.isClassicWindowControlsEnabled(context)) {
                                    Rect bounds = (Rect) XposedHelpers.getObjectField(dotObj, "mTaskBounds");
                                    if (bounds != null && bounds.width() > 0) {
                                        param.setResult((float) bounds.width());
                                    }
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
                });

                // getSurfaceHeight: return DeX titlebar height (32dp)
                XposedBridge.hookAllMethods(cls, "getSurfaceHeight", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object host = param.thisObject;
                        Object dotObj = sHostToDotMap.get(host);
                        if (dotObj != null) {
                            try {
                                Context context = (Context) XposedHelpers.getObjectField(dotObj, "mContext");
                                if (ConfigManager.isClassicWindowControlsEnabled(context)) {
                                    param.setResult((float) dp(context, 32));
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
                });

                XposedBridge.log(TAG + "Host class hooked: " + className);
            } catch (Throwable t) {
                XposedBridge.log(TAG + "Failed hooking host class " + className + ": " + t);
            }
        }
    }

    /**
     * Hook MiuiDecorationDotView to persistently render DeX classic window titlebar controls [-] [□] [X]
     */
    private static void hookMiuiDecorationDotView(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> dotViewCls = XposedHelpers.findClassIfExists(
                    "com.android.wm.shell.multitasking.miuimultiwinswitch.miuiwindowdecor.decoration.MiuiDecorationDotView",
                    lpparam.classLoader
            );
            if (dotViewCls == null) {
                XposedBridge.log(TAG + "MiuiDecorationDotView class not found");
                return;
            }

            // 1. onDraw: persistently paint Samsung DeX window titlebar and buttons [-] [□] [X]
            XposedBridge.hookAllMethods(dotViewCls, "onDraw", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (!(param.thisObject instanceof View) || param.args.length == 0 || !(param.args[0] instanceof Canvas)) {
                        return;
                    }
                    View view = (View) param.thisObject;
                    Context context = view.getContext();
                    if (!ConfigManager.isClassicWindowControlsEnabled(context)) {
                        return;
                    }

                    Canvas canvas = (Canvas) param.args[0];
                    int w = view.getWidth();
                    int h = view.getHeight();
                    if (w <= 0 || h <= 0) return;

                    float cy = h / 2f;

                    // A. Dark sleek acrylic background header
                    Paint bgPaint = new Paint();
                    bgPaint.setColor(Color.parseColor("#DE141518"));
                    bgPaint.setStyle(Paint.Style.FILL);
                    bgPaint.setAntiAlias(true);
                    canvas.drawRect(0, 0, w, h, bgPaint);

                    // B. Crisp 1px bottom accent border
                    Paint strokePaint = new Paint();
                    strokePaint.setColor(Color.parseColor("#33FFFFFF"));
                    strokePaint.setStrokeWidth(1f);
                    canvas.drawLine(0, h - 1, w, h - 1, strokePaint);

                    // C. App Icon & Title on left
                    TaskData data = sTaskDataMap.get(view);
                    int textLeft = dp(context, 14);
                    if (data != null && data.appName != null && !data.appName.isEmpty()) {
                        try {
                            PackageManager pm = context.getPackageManager();
                            ApplicationInfo appInfo = pm.getApplicationInfo(data.appName, 0);
                            Drawable icon = pm.getApplicationIcon(appInfo);
                            if (icon != null) {
                                int iconSize = dp(context, 16);
                                int iconTop = (int) (cy - iconSize / 2f);
                                icon.setBounds(dp(context, 12), iconTop, dp(context, 12) + iconSize, iconTop + iconSize);
                                icon.draw(canvas);
                                textLeft = dp(context, 34);
                            }
                            CharSequence label = pm.getApplicationLabel(appInfo);
                            if (label != null && label.length() > 0) {
                                Paint textPaint = new Paint();
                                textPaint.setColor(Color.WHITE);
                                textPaint.setTextSize(dp(context, 12));
                                textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                                textPaint.setAntiAlias(true);
                                canvas.drawText(label.toString(), textLeft, cy + dp(context, 4), textPaint);
                            }
                        } catch (Throwable ignored) {}
                    }

                    // D. Subtle center drag handle pill
                    Paint pillPaint = new Paint();
                    pillPaint.setColor(Color.parseColor("#55FFFFFF"));
                    pillPaint.setStyle(Paint.Style.FILL);
                    pillPaint.setAntiAlias(true);
                    canvas.drawRoundRect(w / 2f - dp(context, 18), cy - dp(context, 2), w / 2f + dp(context, 18), cy + dp(context, 2), dp(context, 2), dp(context, 2), pillPaint);

                    // E. Classic Samsung DeX Titlebar Buttons [-] [□] [X]
                    Paint btnPaint = new Paint();
                    btnPaint.setColor(Color.parseColor("#E1E2E8"));
                    btnPaint.setStyle(Paint.Style.STROKE);
                    btnPaint.setStrokeWidth(dp(context, 1.8f));
                    btnPaint.setAntiAlias(true);
                    btnPaint.setStrokeCap(Paint.Cap.ROUND);
                    btnPaint.setStrokeJoin(Paint.Join.ROUND);

                    // 1. [-] Minimize button: center at w - 100dp
                    float minCx = w - dp(context, 100);
                    float minHalfW = dp(context, 5.5f);
                    canvas.drawLine(minCx - minHalfW, cy, minCx + minHalfW, cy, btnPaint);

                    // 2. [□] Maximize button: center at w - 65dp
                    float maxCx = w - dp(context, 65);
                    float maxHalfSize = dp(context, 5f);
                    canvas.drawRect(maxCx - maxHalfSize, cy - maxHalfSize, maxCx + maxHalfSize, cy + maxHalfSize, btnPaint);

                    // 3. [X] Close button: center at w - 28dp
                    float closeCx = w - dp(context, 28);
                    float closeHalfSize = dp(context, 4.5f);
                    canvas.drawLine(closeCx - closeHalfSize, cy - closeHalfSize, closeCx + closeHalfSize, cy + closeHalfSize, btnPaint);
                    canvas.drawLine(closeCx + closeHalfSize, cy - closeHalfSize, closeCx - closeHalfSize, cy + closeHalfSize, btnPaint);
                }
            });

            // 2. dispatchTouchEvent on View as safety net
            XposedBridge.hookAllMethods(dotViewCls, "dispatchTouchEvent", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (!(param.thisObject instanceof View) || param.args.length == 0 || !(param.args[0] instanceof MotionEvent)) {
                        return;
                    }
                    View view = (View) param.thisObject;
                    Context context = view.getContext();
                    if (!ConfigManager.isClassicWindowControlsEnabled(context)) {
                        return;
                    }

                    MotionEvent event = (MotionEvent) param.args[0];
                    float x = event.getX();
                    float y = event.getY();
                    int w = view.getWidth();
                    int h = view.getHeight();

                    float minBtnX = w - dp(context, 120);
                    if (x >= minBtnX && y >= 0 && y <= h) {
                        if (event.getAction() == MotionEvent.ACTION_UP) {
                            TaskData data = sTaskDataMap.get(view);
                            int taskId = (data != null) ? data.taskId : -1;
                            Object dotObj = (data != null) ? data.dotInstance : null;

                            if (x >= w - dp(context, 46)) {
                                performClose(context, taskId, dotObj);
                            } else if (x >= w - dp(context, 82)) {
                                performMaximize(context, taskId, dotObj);
                            } else {
                                performMinimize(context, taskId, dotObj);
                            }
                        }
                        param.setResult(true);
                    }
                }
            });

            XposedBridge.log(TAG + "MiuiDecorationDotView hooked with persistent DeX titlebar controls");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed hooking MiuiDecorationDotView: " + t);
        }
    }

    /**
     * Hook MiuiDecorationTouchListener to intercept touch on DeX buttons [-] [□] [X] while preserving window dragging
     */
    private static void hookMiuiDecorationTouchListener(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> touchListenerCls = XposedHelpers.findClassIfExists(
                    "com.android.wm.shell.multitasking.miuimultiwinswitch.miuiwindowdecor.decoration.MiuiDecorationTouchListener",
                    lpparam.classLoader
            );
            if (touchListenerCls == null) {
                XposedBridge.log(TAG + "MiuiDecorationTouchListener not found");
                return;
            }

            XposedBridge.hookAllMethods(touchListenerCls, "onTouch", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args.length < 2 || !(param.args[0] instanceof View) || !(param.args[1] instanceof MotionEvent)) {
                        return;
                    }
                    View view = (View) param.args[0];
                    MotionEvent event = (MotionEvent) param.args[1];
                    Context context = view.getContext();
                    if (!ConfigManager.isClassicWindowControlsEnabled(context)) return;

                    int w = view.getWidth();
                    int h = view.getHeight();
                    float x = event.getX();
                    float y = event.getY();

                    // If touching the right-side button cluster [-] [□] [X]
                    if (x >= w - dp(context, 120) && y >= 0 && y <= h) {
                        if (event.getAction() == MotionEvent.ACTION_UP) {
                            int taskId = -1;
                            try {
                                taskId = XposedHelpers.getIntField(param.thisObject, "mTaskId");
                            } catch (Throwable ignored) {}
                            if (taskId <= 0) {
                                TaskData data = sTaskDataMap.get(view);
                                if (data != null) taskId = data.taskId;
                            }

                            if (x >= w - dp(context, 46)) {
                                performClose(context, taskId, null);
                            } else if (x >= w - dp(context, 82)) {
                                performMaximize(context, taskId, null);
                            } else {
                                performMinimize(context, taskId, null);
                            }
                        }
                        param.setResult(true); // Consume touch so dragging isn't triggered
                    }
                    // Else: allow default onTouch to execute normally (smooth window dragging!)
                }
            });

            XposedBridge.log(TAG + "MiuiDecorationTouchListener hooked successfully");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed hooking MiuiDecorationTouchListener: " + t);
        }
    }

    public static void performClose(Context context, int taskId, Object dotInstance) {
        try {
            if (taskId > 0) {
                Class<?> atmCls = Class.forName("android.app.ActivityTaskManager");
                Method getService = atmCls.getMethod("getService");
                Object atm = getService.invoke(null);
                Method removeTask = atm.getClass().getMethod("removeTask", int.class);
                removeTask.invoke(atm, taskId);
                return;
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "removeTask via ATM failed: " + t);
        }

        if (dotInstance != null) {
            try {
                Method m = XposedHelpers.findMethodBestMatch(dotInstance.getClass(), "close");
                if (m != null) {
                    m.invoke(dotInstance);
                    return;
                }
            } catch (Throwable ignored) {}
        }
    }

    public static void performMinimize(Context context, int taskId, Object dotInstance) {
        try {
            if (taskId > 0) {
                Class<?> atmCls = Class.forName("android.app.ActivityTaskManager");
                Method getService = atmCls.getMethod("getService");
                Object atm = getService.invoke(null);
                Method moveTaskToBack = atm.getClass().getMethod("moveTaskToBack", int.class);
                moveTaskToBack.invoke(atm, taskId);
                return;
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "moveTaskToBack via ATM failed: " + t);
        }
    }

    public static void performMaximize(Context context, int taskId, Object dotInstance) {
        try {
            if (taskId > 0) {
                Class<?> atmCls = Class.forName("android.app.ActivityTaskManager");
                Method getService = atmCls.getMethod("getService");
                Object atm = getService.invoke(null);
                try {
                    Method setWindowingMode = atm.getClass().getMethod("setTaskWindowingMode", int.class, int.class, boolean.class);
                    setWindowingMode.invoke(atm, taskId, 1, true);
                    return;
                } catch (Throwable t1) {
                    Method setWindowingMode = atm.getClass().getMethod("setTaskWindowingMode", int.class, int.class);
                    setWindowingMode.invoke(atm, taskId, 1);
                    return;
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "setTaskWindowingMode failed: " + t);
        }
    }

    private static void hookMiuiCaptionContainerView(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> captionViewCls = XposedHelpers.findClassIfExists(
                    "com.android.wm.shell.multitasking.miuimultiwinswitch.miuiwindowdecor.handlemenu.MiuiCaptionContainerView",
                    lpparam.classLoader
            );

            if (captionViewCls == null) {
                XposedBridge.log(TAG + "Class MiuiCaptionContainerView not found");
                return;
            }

            XposedBridge.hookAllMethods(captionViewCls, "initButtonAndBg", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (!(param.thisObject instanceof View)) return;
                    View captionView = (View) param.thisObject;
                    Context context = captionView.getContext();

                    if (!ConfigManager.shouldApplyDeXTaskbar(context)) {
                        return;
                    }

                    // 1. Dark sleek PC titlebar background
                    GradientDrawable bg = new GradientDrawable();
                    bg.setColor(Color.parseColor("#DE18191C"));
                    bg.setCornerRadii(new float[]{14, 14, 14, 14, 0, 0, 0, 0});
                    captionView.setBackground(bg);

                    // 2. Add App Title text on the left if not present
                    if (captionView instanceof ViewGroup) {
                        ViewGroup group = (ViewGroup) captionView;
                        View existingTitle = group.findViewById(R.id.dex_window_title);
                        if (existingTitle == null) {
                            try {
                                CharSequence appName = (CharSequence) XposedHelpers.getObjectField(param.thisObject, "mAppName");
                                if (appName != null && appName.length() > 0) {
                                    TextView tvTitle = new TextView(context);
                                    tvTitle.setId(R.id.dex_window_title);
                                    tvTitle.setText(appName);
                                    tvTitle.setTextColor(Color.WHITE);
                                    tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                                    tvTitle.setTypeface(null, Typeface.BOLD);
                                    tvTitle.setPadding(dp(context, 12), 0, 0, 0);

                                    LinearLayout.LayoutParams lpTitle = new LinearLayout.LayoutParams(
                                            ViewGroup.LayoutParams.WRAP_CONTENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                    );
                                    lpTitle.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
                                    group.addView(tvTitle, 0, lpTitle);
                                }
                            } catch (Throwable ignored) {}
                        }
                    }

                    // 3. Ensure button container is visible and styled
                    LinearLayout container = (LinearLayout) XposedHelpers.getObjectField(param.thisObject, "mCaptionButtonContainer");
                    if (container != null) {
                        container.setVisibility(View.VISIBLE);
                        for (int i = 0; i < container.getChildCount(); i++) {
                            View child = container.getChildAt(i);
                            if (child != null) {
                                child.setVisibility(View.VISIBLE);
                                child.setAlpha(1.0f);
                            }
                        }
                    }
                }
            });

            XposedBridge.log(TAG + "MiuiCaptionContainerView hooked successfully");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed hooking MiuiCaptionContainerView: " + t);
        }
    }

    private static void hookStatusBar(XC_LoadPackage.LoadPackageParam lpparam) {
        String[] statusBarClassNames = new String[] {
            "com.android.systemui.statusbar.phone.PhoneStatusBarView",
            "com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView",
            "com.android.systemui.statusbar.window.StatusBarWindowView"
        };

        for (String className : statusBarClassNames) {
            try {
                Class<?> cls = XposedHelpers.findClassIfExists(className, lpparam.classLoader);
                if (cls == null) continue;

                XposedBridge.hookAllMethods(cls, "onAttachedToWindow", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.thisObject instanceof View) {
                            View v = (View) param.thisObject;
                            registerStatusBarView(v);
                            registerStatusBarObserver(v.getContext());
                            updateStatusBarView(v);
                        }
                    }
                });

                XposedBridge.hookAllMethods(cls, "onMeasure", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.thisObject instanceof View) {
                            View v = (View) param.thisObject;
                            if (ConfigManager.shouldApplyDeXTaskbar(v.getContext())) {
                                v.setVisibility(View.GONE);
                                v.setAlpha(0f);
                                XposedHelpers.callMethod(param.thisObject, "setMeasuredDimension", 0, 0);
                                param.setResult(null);
                            }
                        }
                    }
                });

                XposedBridge.hookAllMethods(cls, "draw", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.thisObject instanceof View) {
                            View v = (View) param.thisObject;
                            if (ConfigManager.shouldApplyDeXTaskbar(v.getContext())) {
                                param.setResult(null);
                            }
                        }
                    }
                });

                XposedBridge.hookAllMethods(cls, "onDraw", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.thisObject instanceof View) {
                            View v = (View) param.thisObject;
                            if (ConfigManager.shouldApplyDeXTaskbar(v.getContext())) {
                                param.setResult(null);
                            }
                        }
                    }
                });

                XposedBridge.log(TAG + "StatusBar class hooked: " + className);
            } catch (Throwable t) {
                XposedBridge.log(TAG + "Failed hooking statusBar class " + className + ": " + t);
            }
        }

        try {
            Class<?> ctrlCls = XposedHelpers.findClassIfExists("com.android.systemui.statusbar.window.StatusBarWindowControllerImpl", lpparam.classLoader);
            if (ctrlCls != null) {
                XposedBridge.hookAllMethods(ctrlCls, "apply", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            View sbView = (View) XposedHelpers.getObjectField(param.thisObject, "mStatusBarView");
                            if (sbView != null && ConfigManager.shouldApplyDeXTaskbar(sbView.getContext())) {
                                sbView.setVisibility(View.GONE);
                                sbView.setAlpha(0f);
                            }
                        } catch (Throwable ignored) {}
                    }
                });
                XposedBridge.log(TAG + "StatusBarWindowControllerImpl.apply hooked");
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed hooking StatusBarWindowControllerImpl: " + t);
        }
    }

    private static synchronized void registerStatusBarView(View v) {
        if (v == null) return;
        boolean exists = false;
        for (WeakReference<View> ref : sStatusBarViews) {
            if (ref.get() == v) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            sStatusBarViews.add(new WeakReference<>(v));
        }
    }

    private static void updateStatusBarView(View v) {
        if (v == null) return;
        boolean shouldHide = ConfigManager.shouldApplyDeXTaskbar(v.getContext());
        v.setVisibility(shouldHide ? View.GONE : View.VISIBLE);
        v.setAlpha(shouldHide ? 0f : 1.0f);
    }

    private static synchronized void updateAllStatusBarViews() {
        Iterator<WeakReference<View>> it = sStatusBarViews.iterator();
        while (it.hasNext()) {
            View v = it.next().get();
            if (v == null) {
                it.remove();
            } else {
                updateStatusBarView(v);
                v.requestLayout();
                v.invalidate();
            }
        }
    }

    private static void registerStatusBarObserver(Context context) {
        if (sStatusBarObserverRegistered || context == null) return;
        try {
            Uri uriDkt = Settings.System.getUriFor("miui_dkt_mode");
            context.getContentResolver().registerContentObserver(uriDkt, true, new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override
                public void onChange(boolean selfChange) {
                    updateAllStatusBarViews();
                }
            });

            Uri uriGlobal = Settings.Global.getUriFor("hyperdex_mode");
            context.getContentResolver().registerContentObserver(uriGlobal, true, new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override
                public void onChange(boolean selfChange) {
                    updateAllStatusBarViews();
                }
            });

            sStatusBarObserverRegistered = true;
            XposedBridge.log(TAG + "StatusBar observers registered");
        } catch (Throwable ignored) {}
    }

    private static void hookAppHeaderViewHolder(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> holderCls = XposedHelpers.findClassIfExists(
                    "com.android.wm.shell.windowdecor.viewholder.AppHeaderViewHolder",
                    lpparam.classLoader
            );

            if (holderCls == null) return;

            XposedBridge.hookAllMethods(holderCls, "bindData", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Object holder = param.thisObject;
                    View captionView = getField(holder, "captionView", View.class);
                    Context context = captionView != null ? captionView.getContext() : null;

                    if (!ConfigManager.shouldApplyDeXTaskbar(context)) {
                        return;
                    }

                    ImageButton minBtn = getField(holder, "minimizeWindowButton", ImageButton.class);
                    if (minBtn != null) {
                        minBtn.setVisibility(View.VISIBLE);
                        minBtn.setAlpha(1.0f);
                    }

                    ImageButton maxBtn = getField(holder, "maximizeWindowButton", ImageButton.class);
                    if (maxBtn != null) {
                        maxBtn.setVisibility(View.VISIBLE);
                        maxBtn.setAlpha(1.0f);
                    }

                    ImageButton closeBtn = getField(holder, "closeWindowButton", ImageButton.class);
                    if (closeBtn != null) {
                        closeBtn.setVisibility(View.VISIBLE);
                        closeBtn.setAlpha(1.0f);
                    }
                }
            });

            XposedBridge.log(TAG + "AppHeaderViewHolder hooked successfully");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed hooking AppHeaderViewHolder: " + t);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T getField(Object obj, String fieldName, Class<T> type) {
        if (obj == null) return null;
        try {
            Field f = obj.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            Object v = f.get(obj);
            if (type.isInstance(v)) {
                return (T) v;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static int dp(Context context, float val) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, val, context.getResources().getDisplayMetrics());
    }
}
