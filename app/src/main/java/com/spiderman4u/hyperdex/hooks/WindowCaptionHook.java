package com.spiderman4u.hyperdex.hooks;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;

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
    private static final int PILL_WIDTH_DP = 112;
    private static final int PILL_HEIGHT_DP = 20;

    private static final List<WeakReference<View>> sStatusBarViews = new ArrayList<>();
    private static boolean sStatusBarObserverRegistered = false;

    private static class TaskData {
        final int taskId;
        final String appName;
        final Object dotInstance;
        volatile boolean isFullscreen;

        TaskData(int taskId, String appName, Object dotInstance, boolean isFullscreen) {
            this.taskId = taskId;
            this.appName = appName;
            this.dotInstance = dotInstance;
            this.isFullscreen = isFullscreen;
        }
    }

    private static final Map<View, TaskData> sTaskDataMap = new WeakHashMap<>();
    private static final Map<Object, Object> sHostToDotMap = new WeakHashMap<>();
    private static final Map<Object, TaskData> sDotToTaskDataMap = new WeakHashMap<>();
    private static final Map<Object, Object> sTouchListenerToDotMap = new WeakHashMap<>();
    private static final Map<View, float[]> sTouchTrackMap = new WeakHashMap<>();
    private static volatile Object sLastActiveDotObj = null;
    private static volatile long sLastActionTimestamp = 0L;
    private static volatile float sLastTouchRawX = -1f;
    private static volatile float sLastTouchRawY = -1f;

    private static boolean shouldShowWindowsControls(Context context) {
        return context != null
                && ConfigManager.shouldApplyDeXTaskbar(context)
                && ConfigManager.isClassicWindowControlsEnabled(context);
    }

    public static void init(XC_LoadPackage.LoadPackageParam lpparam) {
        hookMulWinSwitchEventHandler(lpparam);
        hookMiuiDecorationDot(lpparam);
        hookMiuiDecorationRootViewHost(lpparam);
        hookMiuiDecorationDotView(lpparam);
        hookMiuiDecorationTouchListener(lpparam);
        hookAppHeaderViewHolder(lpparam);
        hookSmoothMinimizeAnimator(lpparam);
        hookStatusBar(lpparam);
    }

    private static void hookMulWinSwitchEventHandler(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> handlerCls = XposedHelpers.findClassIfExists(
                    "com.android.wm.shell.multitasking.miuimultiwinswitch.miuiwindowdecor.MulWinSwitchEventHandler",
                    lpparam.classLoader
            );
            if (handlerCls != null) {
                XposedBridge.hookAllMethods(handlerCls, "onEvent", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.args.length > 0 && param.args[0] instanceof MotionEvent) {
                            MotionEvent ev = (MotionEvent) param.args[0];
                            sLastTouchRawX = ev.getRawX();
                            sLastTouchRawY = ev.getRawY();
                        }
                    }
                });
                XposedBridge.log(TAG + "MulWinSwitchEventHandler hooked for coordinate tracking");
            }
        } catch (Throwable ignored) {}
    }

    private static void hookSmoothMinimizeAnimator(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> minAnimCls = XposedHelpers.findClassIfExists(
                    "com.android.wm.shell.shared.animation.MinimizeAnimator",
                    lpparam.classLoader
            );
            if (minAnimCls != null) {
                XposedBridge.hookAllMethods(minAnimCls, "create", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object res = param.getResult();
                        if (res instanceof android.animation.Animator) {
                            android.animation.Animator anim = (android.animation.Animator) res;
                            anim.setDuration(280);
                            anim.setInterpolator(new android.view.animation.PathInterpolator(0.22f, 1.0f, 0.36f, 1.0f));
                        }
                    }
                });
                XposedBridge.log(TAG + "MinimizeAnimator hooked for macOS-smooth curve");
            }
        } catch (Throwable ignored) {}
    }

    private static void hookMiuiDecorationDot(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> dotCls = XposedHelpers.findClassIfExists(
                    "com.android.wm.shell.multitasking.miuimultiwinswitch.miuiwindowdecor.decoration.MiuiDecorationDot",
                    lpparam.classLoader
            );
            if (dotCls == null) return;

            XposedBridge.hookAllConstructors(dotCls, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    linkDotAndHost(param.thisObject, param.args);
                }
            });

            XposedBridge.hookAllMethods(dotCls, "relayout", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    linkDotAndHost(param.thisObject, param.args);
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    linkDotAndHost(param.thisObject, param.args);
                    enforcePillRelayoutResult(param.thisObject);
                }
            });

            XposedBridge.hookAllMethods(dotCls, "updateRootView", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    enforcePillRelayoutResult(param.thisObject);
                }
            });

            XposedBridge.hookAllMethods(dotCls, "updateViews", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    linkDotAndHost(param.thisObject, null);
                    enforcePillRelayoutResult(param.thisObject);
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    applySurfacePositionAndCrop(param.thisObject, null);
                }
            });

            XposedBridge.hookAllMethods(dotCls, "updateTopCaptionSurface", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    applySurfacePositionAndCrop(param.thisObject, param.args);
                }
            });

            XposedBridge.hookAllMethods(dotCls, "isTouchedInCaption", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args.length == 0 || !(param.args[0] instanceof MotionEvent)) return;
                    MotionEvent ev = (MotionEvent) param.args[0];
                    sLastTouchRawX = ev.getRawX();
                    sLastTouchRawY = ev.getRawY();

                    if (Boolean.TRUE.equals(param.getResult())) return;
                    Context context = getDotContext(param.thisObject);
                    if (!shouldShowWindowsControls(context)) return;

                    Rect bounds = getTaskBounds(param.thisObject);
                    int taskW = (bounds != null && bounds.width() > 0) ? bounds.width() : getDisplayBounds(context).width();
                    float halfHitW = dp(context, PILL_WIDTH_DP / 2f + 12);
                    float maxHitH = dp(context, PILL_HEIGHT_DP + 16);

                    float x = ev.getX();
                    float y = ev.getY();

                    boolean inLocal = Math.abs(x - taskW / 2f) <= halfHitW && y >= 0 && y <= maxHitH;
                    boolean inScreen = bounds != null && Math.abs(x - (bounds.left + taskW / 2f)) <= halfHitW
                            && y >= bounds.top - dp(context, 8) && y <= bounds.top + maxHitH;

                    if (inLocal || inScreen) {
                        param.setResult(true);
                    }
                }
            });

            // Intercept handleCaptionClicked (called by MulWinSwitchDecorViewModel on top pill tap!)
            XposedBridge.hookAllMethods(dotCls, "handleCaptionClicked", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Object dotObj = param.thisObject;
                    Context context = getDotContext(dotObj);
                    if (!shouldShowWindowsControls(context)) return;

                    long now = SystemClock.uptimeMillis();
                    if (now - sLastActionTimestamp < 220) {
                        param.setResult(null);
                        return;
                    }
                    sLastActionTimestamp = now;

                    TaskData data = sDotToTaskDataMap.get(dotObj);
                    int taskId = (data != null) ? data.taskId : -1;
                    Rect bounds = getTaskBounds(dotObj);
                    float pillW = dp(context, PILL_WIDTH_DP);
                    float centerX;
                    if (bounds != null && bounds.width() > 0) {
                        float scale = 1.0f;
                        try {
                            float s = XposedHelpers.getFloatField(dotObj, "mScale");
                            if (s > 0.2f && s <= 1.5f) scale = s;
                        } catch (Throwable ignored) {}
                        centerX = bounds.left + (bounds.width() * scale) / 2f;
                    } else {
                        centerX = getDisplayBounds(context).width() / 2f;
                    }

                    float pillLeft = centerX - pillW / 2f;
                    float ratio = (sLastTouchRawX >= 0f) ? ((sLastTouchRawX - pillLeft) / pillW) : 0.5f;
                    ratio = Math.max(0f, Math.min(1f, ratio));

                    XposedBridge.log(TAG + "handleCaptionClicked rawX=" + sLastTouchRawX + " pillLeft=" + pillLeft + " ratio=" + ratio + " taskId=" + taskId);

                    View dotView = null;
                    try {
                        Object host = XposedHelpers.getObjectField(dotObj, "mMiuiDecorationRootViewHost");
                        if (host != null) {
                            dotView = (View) XposedHelpers.callMethod(host, "getRootView");
                        }
                    } catch (Throwable ignored) {}

                    if (ratio < 0.34f) {
                        performMinimize(context, taskId, dotObj);
                    } else if (ratio < 0.67f) {
                        performMaximize(context, taskId, dotObj, dotView);
                    } else {
                        performClose(context, taskId, dotObj);
                    }
                    param.setResult(null); // Suppress Xiaomi 3-dot popup menu
                }
            });

            // Keep scale fixed while dragging/hovering when Windows controls are active
            try {
                Class<?> scaleAnimCls = XposedHelpers.findClassIfExists(
                        "com.android.wm.shell.multitasking.miuimultiwinswitch.miuiwindowdecor.decoration.MiuiDecorationDotScaleAnim",
                        lpparam.classLoader
                );
                if (scaleAnimCls != null) {
                    XposedBridge.hookAllMethods(scaleAnimCls, "startMenuAnimation", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                Object dot = XposedHelpers.getObjectField(param.thisObject, "mDecorationDot");
                                if (dot != null && shouldShowWindowsControls(getDotContext(dot))) {
                                    param.setResult(null);
                                }
                            } catch (Throwable ignored) {}
                        }
                    });
                }
            } catch (Throwable ignored) {}

            XposedBridge.log(TAG + "MiuiDecorationDot hooked for Windows controls");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed hooking MiuiDecorationDot: " + t);
        }
    }

    private static Context getDotContext(Object dotObj) {
        if (dotObj == null) return null;
        try {
            return (Context) XposedHelpers.getObjectField(dotObj, "mContext");
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Rect getTaskBounds(Object dotObj) {
        if (dotObj == null) return null;
        Rect bounds = null;
        try {
            bounds = (Rect) XposedHelpers.getObjectField(dotObj, "mTaskBounds");
        } catch (Throwable ignored) {}
        if (bounds == null || bounds.width() <= 0) {
            try {
                Object taskInfo = XposedHelpers.getObjectField(dotObj, "mRunningTaskInfo");
                if (taskInfo != null) {
                    Object cfg = XposedHelpers.getObjectField(taskInfo, "configuration");
                    if (cfg != null) {
                        Object winCfg = XposedHelpers.getObjectField(cfg, "windowConfiguration");
                        if (winCfg != null) {
                            bounds = (Rect) XposedHelpers.callMethod(winCfg, "getBounds");
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
        return bounds;
    }

    private static int getTaskWidth(Object dotObj, Context context) {
        Rect bounds = getTaskBounds(dotObj);
        if (bounds != null && bounds.width() > 0) {
            return bounds.width();
        }
        return getDisplayBounds(context).width();
    }

    private static boolean isTaskFullscreen(Object dotObj) {
        if (dotObj == null) return false;
        try {
            Object taskInfo = XposedHelpers.getObjectField(dotObj, "mRunningTaskInfo");
            if (taskInfo != null) {
                try {
                    int mode = (int) XposedHelpers.callMethod(taskInfo, "getWindowingMode");
                    if (mode > 0) return mode == 1;
                } catch (Throwable ignored) {}
                Object cfg = XposedHelpers.getObjectField(taskInfo, "configuration");
                if (cfg != null) {
                    Object winCfg = XposedHelpers.getObjectField(cfg, "windowConfiguration");
                    if (winCfg != null) {
                        int mode = (int) XposedHelpers.callMethod(winCfg, "getWindowingMode");
                        if (mode > 0) return mode == 1;
                    }
                }
            }
        } catch (Throwable ignored) {}
        try {
            int mode = XposedHelpers.getIntField(dotObj, "mWindowingMode");
            return mode == 1;
        } catch (Throwable ignored) {}
        return false;
    }

    private static void enforcePillRelayoutResult(Object dotObj) {
        Context context = getDotContext(dotObj);
        if (!shouldShowWindowsControls(context)) return;

        int pillW = dp(context, PILL_WIDTH_DP);
        int pillH = dp(context, PILL_HEIGHT_DP);
        int taskW = getTaskWidth(dotObj, context);
        int capX = Math.max(0, (taskW - pillW) / 2);
        int capY = dp(context, 2);

        try {
            Object result = XposedHelpers.getObjectField(dotObj, "mResult");
            if (result != null) {
                XposedHelpers.setFloatField(result, "mCaptionWidth", (float) pillW);
                XposedHelpers.setFloatField(result, "mCaptionHeight", (float) pillH);
                XposedHelpers.setIntField(result, "mCaptionX", capX);
                XposedHelpers.setIntField(result, "mCaptionY", capY);
            }
        } catch (Throwable ignored) {}

        try {
            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) XposedHelpers.getObjectField(dotObj, "mDecorationLayoutParams");
            if (lp != null) {
                lp.width = pillW;
                lp.height = pillH;
            }
        } catch (Throwable ignored) {}
    }

    private static void applySurfacePositionAndCrop(Object dotObj, Object[] args) {
        Context context = getDotContext(dotObj);
        if (!shouldShowWindowsControls(context)) return;

        Object surface = null;
        try {
            surface = XposedHelpers.getObjectField(dotObj, "mCaptionContainerSurface");
        } catch (Throwable ignored) {}
        if (surface == null) return;

        Object t = null;
        Rect surfaceBounds = null;
        float scale = 1.0f;
        if (args != null) {
            for (Object arg : args) {
                if (arg == null) continue;
                if (arg.getClass().getName().contains("Transaction")) t = arg;
                else if (arg instanceof Rect) surfaceBounds = (Rect) arg;
            }
            if (args.length >= 3 && args[2] instanceof Float) {
                float s = (Float) args[2];
                if (s > 0.1f && s <= 2.0f) scale = s;
            }
        }
        if (t == null) return;

        int pillW = dp(context, PILL_WIDTH_DP);
        int pillH = dp(context, PILL_HEIGHT_DP);
        int taskW = (surfaceBounds != null && surfaceBounds.width() > 0)
                ? surfaceBounds.width()
                : getTaskWidth(dotObj, context);

        try {
            float posX = Math.max(0f, (taskW - pillW * scale) / 2f);
            float posY = dp(context, 2) * scale;
            XposedHelpers.callMethod(t, "setWindowCrop", surface, pillW, pillH);
            XposedHelpers.callMethod(t, "setPosition", surface, posX, posY);
            XposedHelpers.callMethod(t, "show", surface);
        } catch (Throwable ignored) {}
    }

    private static void linkDotAndHost(Object dotObj, Object[] args) {
        if (dotObj == null) return;
        sLastActiveDotObj = dotObj;

        int taskId = -1;
        String appName = null;

        Object taskInfo = null;
        try {
            taskInfo = XposedHelpers.getObjectField(dotObj, "mRunningTaskInfo");
        } catch (Throwable ignored) {}
        if (taskInfo == null && args != null) {
            for (Object arg : args) {
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

        TaskData taskData = new TaskData(taskId, appName, dotObj, isTaskFullscreen(dotObj));
        sDotToTaskDataMap.put(dotObj, taskData);

        try {
            Object host = XposedHelpers.getObjectField(dotObj, "mMiuiDecorationRootViewHost");
            if (host != null) {
                sHostToDotMap.put(host, dotObj);
                View dotView = (View) XposedHelpers.callMethod(host, "getRootView");
                if (dotView != null) {
                    sTaskDataMap.put(dotView, taskData);
                    dotView.postInvalidate();
                }
            }
        } catch (Throwable ignored) {}

        try {
            Object touchListener = XposedHelpers.getObjectField(dotObj, "mOnCaptionTouchListener");
            if (touchListener != null) {
                sTouchListenerToDotMap.put(touchListener, dotObj);
            }
        } catch (Throwable ignored) {}
    }

    private static void hookMiuiDecorationRootViewHost(XC_LoadPackage.LoadPackageParam lpparam) {
        String[] hostClasses = new String[] {
                "com.android.wm.shell.multitasking.miuimultiwinswitch.miuiwindowdecor.decoration.MiuiDecorationDotViewHost",
                "com.android.wm.shell.multitasking.miuimultiwinswitch.miuiwindowdecor.decoration.MiuiDecorationRootViewHost"
        };

        for (String className : hostClasses) {
            try {
                Class<?> cls = XposedHelpers.findClassIfExists(className, lpparam.classLoader);
                if (cls == null) continue;

                if (className.endsWith("MiuiDecorationDotViewHost")) {
                    XposedBridge.hookAllMethods(cls, "getSurfaceWidth", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            Context ctx = getHostContext(param.thisObject);
                            if (shouldShowWindowsControls(ctx)) {
                                param.setResult((float) dp(ctx, PILL_WIDTH_DP));
                            }
                        }
                    });

                    XposedBridge.hookAllMethods(cls, "getSurfaceHeight", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            Context ctx = getHostContext(param.thisObject);
                            if (shouldShowWindowsControls(ctx)) {
                                param.setResult((float) dp(ctx, PILL_HEIGHT_DP));
                            }
                        }
                    });
                }

                XposedBridge.hookAllMethods(cls, "createOrRelayoutView", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        enforceHostLayoutParams(param.thisObject, param.args);
                    }
                });

                XposedBridge.hookAllMethods(cls, "relayoutView", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        enforceHostLayoutParams(param.thisObject, param.args);
                    }
                });
            } catch (Throwable ignored) {}
        }
    }

    private static Context getHostContext(Object host) {
        if (host == null) return null;
        try {
            return (Context) XposedHelpers.getObjectField(host, "mContext");
        } catch (Throwable ignored) {}
        Object dotObj = sHostToDotMap.get(host);
        return getDotContext(dotObj != null ? dotObj : sLastActiveDotObj);
    }

    private static void enforceHostLayoutParams(Object host, Object[] args) {
        if (args == null || host == null) return;
        // Only resize top dot caption host, never bottom resize pill!
        if (!host.getClass().getName().contains("MiuiDecorationDotViewHost")) return;
        Context ctx = getHostContext(host);
        if (!shouldShowWindowsControls(ctx)) return;
        for (Object arg : args) {
            if (arg instanceof WindowManager.LayoutParams) {
                WindowManager.LayoutParams lp = (WindowManager.LayoutParams) arg;
                lp.width = dp(ctx, PILL_WIDTH_DP);
                lp.height = dp(ctx, PILL_HEIGHT_DP);
                break;
            }
        }
    }

    private static void hookMiuiDecorationDotView(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> dotViewCls = XposedHelpers.findClassIfExists(
                    "com.android.wm.shell.multitasking.miuimultiwinswitch.miuiwindowdecor.decoration.MiuiDecorationDotView",
                    lpparam.classLoader
            );
            if (dotViewCls == null) return;

            XposedBridge.hookAllMethods(dotViewCls, "onDraw", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (!(param.thisObject instanceof View) || param.args.length == 0 || !(param.args[0] instanceof Canvas)) {
                        return;
                    }
                    View view = (View) param.thisObject;
                    Context context = view.getContext();
                    if (!shouldShowWindowsControls(context)) {
                        return;
                    }

                    Canvas canvas = (Canvas) param.args[0];
                    int rawW = view.getWidth();
                    int rawH = view.getHeight();
                    if (rawW <= 0 || rawH <= 0) return;

                    boolean isRotated = rawW < rawH;
                    float barLen = isRotated ? rawH : rawW;
                    float barThick = isRotated ? rawW : rawH;

                    TaskData data = sTaskDataMap.get(view);
                    boolean isFs = false;
                    if (data != null && data.dotInstance != null) {
                        isFs = isTaskFullscreen(data.dotInstance);
                        if (!isFs) {
                            Rect b = getTaskBounds(data.dotInstance);
                            if (b != null && b.width() >= getDisplayBounds(context).width() - dp(context, 40)) {
                                isFs = true;
                            }
                        }
                    } else if (data != null) {
                        isFs = data.isFullscreen;
                    }

                    canvas.save();
                    if (isRotated) {
                        canvas.translate(0f, (float) rawH);
                        canvas.rotate(-90f);
                    }

                    float radius = barThick / 2f;
                    RectF pillRect = new RectF(1f, 1f, barLen - 1f, barThick - 1f);

                    // 1. Sleek Acrylic Windows Control Pill
                    Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    bgPaint.setColor(Color.parseColor("#E316181E"));
                    bgPaint.setStyle(Paint.Style.FILL);
                    canvas.drawRoundRect(pillRect, radius, radius, bgPaint);

                    // 2. Subtle hairline border
                    Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    borderPaint.setColor(Color.parseColor("#47FFFFFF"));
                    borderPaint.setStyle(Paint.Style.STROKE);
                    borderPaint.setStrokeWidth(dp(context, 0.8f));
                    canvas.drawRoundRect(pillRect, radius, radius, borderPaint);

                    // 3. Subtle section dividers
                    Paint divPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    divPaint.setColor(Color.parseColor("#24FFFFFF"));
                    divPaint.setStrokeWidth(1f);
                    float divPadY = barThick * 0.28f;
                    canvas.drawLine(barLen / 3f, divPadY, barLen / 3f, barThick - divPadY, divPaint);
                    canvas.drawLine((barLen * 2f) / 3f, divPadY, (barLen * 2f) / 3f, barThick - divPadY, divPaint);

                    float cy = barThick / 2f;
                    Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    iconPaint.setColor(Color.parseColor("#EAECEF"));
                    iconPaint.setStyle(Paint.Style.STROKE);
                    iconPaint.setStrokeWidth(dp(context, 1.5f));
                    iconPaint.setStrokeCap(Paint.Cap.ROUND);
                    iconPaint.setStrokeJoin(Paint.Join.ROUND);

                    // A. Minimize [ ─ ] (Left Third)
                    float minCx = barLen / 6f;
                    float minHalf = dp(context, 4.8f);
                    canvas.drawLine(minCx - minHalf, cy, minCx + minHalf, cy, iconPaint);

                    // B. Maximize / Restore [ ▢ ] (Middle Third)
                    float maxCx = barLen / 2f;
                    float sqHalf = dp(context, 4.0f);
                    if (isFs) {
                        float off = dp(context, 1.5f);
                        canvas.drawRect(maxCx - sqHalf + off, cy - sqHalf - off, maxCx + sqHalf + off, cy + sqHalf - off, iconPaint);
                        canvas.drawRect(maxCx - sqHalf - off, cy - sqHalf + off, maxCx + sqHalf - off, cy + sqHalf + off, iconPaint);
                    } else {
                        canvas.drawRoundRect(new RectF(maxCx - sqHalf, cy - sqHalf, maxCx + sqHalf, cy + sqHalf),
                                dp(context, 1.2f), dp(context, 1.2f), iconPaint);
                    }

                    // C. Close [ ✕ ] (Right Third)
                    float closeCx = (barLen * 5f) / 6f;
                    float xHalf = dp(context, 3.8f);
                    Paint closePaint = new Paint(iconPaint);
                    closePaint.setColor(Color.parseColor("#FF7675"));
                    canvas.drawLine(closeCx - xHalf, cy - xHalf, closeCx + xHalf, cy + xHalf, closePaint);
                    canvas.drawLine(closeCx + xHalf, cy - xHalf, closeCx - xHalf, cy + xHalf, closePaint);

                    canvas.restore();
                    param.setResult(null);
                }
            });

            XposedBridge.hookAllMethods(dotViewCls, "dispatchTouchEvent", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (!(param.thisObject instanceof View) || param.args.length == 0 || !(param.args[0] instanceof MotionEvent)) {
                        return;
                    }
                    View view = (View) param.thisObject;
                    if (!shouldShowWindowsControls(view.getContext())) return;

                    if (handlePillTouch(view, (MotionEvent) param.args[0], sTaskDataMap.get(view))) {
                        param.setResult(true);
                    }
                }
            });
        } catch (Throwable ignored) {}
    }

    private static void hookMiuiDecorationTouchListener(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> touchListenerCls = XposedHelpers.findClassIfExists(
                    "com.android.wm.shell.multitasking.miuimultiwinswitch.miuiwindowdecor.decoration.MiuiDecorationTouchListener",
                    lpparam.classLoader
            );
            if (touchListenerCls == null) return;

            XposedBridge.hookAllMethods(touchListenerCls, "onTouch", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args.length < 2 || !(param.args[0] instanceof View) || !(param.args[1] instanceof MotionEvent)) {
                        return;
                    }
                    View view = (View) param.args[0];
                    if (!shouldShowWindowsControls(view.getContext())) return;

                    TaskData data = sTaskDataMap.get(view);
                    if (data == null) {
                        Object dotObj = sTouchListenerToDotMap.get(param.thisObject);
                        if (dotObj != null) data = sDotToTaskDataMap.get(dotObj);
                    }

                    if (handlePillTouch(view, (MotionEvent) param.args[1], data)) {
                        param.setResult(true);
                    }
                }
            });
        } catch (Throwable ignored) {}
    }

    private static boolean handlePillTouch(View view, MotionEvent event, TaskData data) {
        Context context = view.getContext();
        int action = event.getActionMasked();
        float rawX = event.getRawX();
        float rawY = event.getRawY();
        sLastTouchRawX = rawX;
        sLastTouchRawY = rawY;

        if (action == MotionEvent.ACTION_DOWN) {
            sTouchTrackMap.put(view, new float[]{rawX, rawY, 0f});
            return false;
        }

        float[] track = sTouchTrackMap.get(view);
        if (action == MotionEvent.ACTION_MOVE) {
            if (track != null && track[2] == 0f) {
                if (Math.hypot(rawX - track[0], rawY - track[1]) > dp(context, 8)) {
                    track[2] = 1f;
                }
            }
            return false;
        }

        if (action == MotionEvent.ACTION_UP) {
            boolean wasDrag = track != null && track[2] > 0.5f;
            sTouchTrackMap.remove(view);
            if (wasDrag) {
                return false;
            }

            long now = SystemClock.uptimeMillis();
            if (now - sLastActionTimestamp < 220) {
                return true;
            }
            sLastActionTimestamp = now;

            int rawW = view.getWidth();
            int rawH = view.getHeight();
            if (rawW <= 0 || rawH <= 0) return false;

            boolean isRotated = rawW < rawH;
            float barLen = isRotated ? rawH : rawW;
            float touchAlong = isRotated ? (rawH - event.getY()) : event.getX();
            float ratio = touchAlong / barLen;

            int taskId = (data != null) ? data.taskId : -1;
            Object dotObj = (data != null) ? data.dotInstance : sLastActiveDotObj;
            XposedBridge.log(TAG + "handlePillTouch UP x=" + touchAlong + "/" + barLen + " ratio=" + ratio + " taskId=" + taskId);

            if (ratio < 0.34f) {
                performMinimize(context, taskId, dotObj);
            } else if (ratio < 0.67f) {
                performMaximize(context, taskId, dotObj, view);
            } else {
                performClose(context, taskId, dotObj);
            }
            return true;
        }

        if (action == MotionEvent.ACTION_CANCEL) {
            sTouchTrackMap.remove(view);
        }
        return false;
    }

    private static final Map<Integer, Rect> sSavedWindowBounds = new java.util.concurrent.ConcurrentHashMap<>();

    private static Rect getDisplayBounds(Context context) {
        if (context != null) {
            try {
                WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
                if (wm != null) {
                    android.view.WindowMetrics metrics = wm.getCurrentWindowMetrics();
                    if (metrics != null && metrics.getBounds() != null) {
                        return new Rect(metrics.getBounds());
                    }
                }
            } catch (Throwable ignored) {}
            try {
                android.util.DisplayMetrics dm = context.getResources().getDisplayMetrics();
                return new Rect(0, 0, dm.widthPixels, dm.heightPixels);
            } catch (Throwable ignored) {}
        }
        return new Rect(0, 0, 2560, 1600);
    }

    public static void performClose(Context context, int taskId, Object dotInstance) {
        if (dotInstance != null) {
            try {
                Object clickListener = XposedHelpers.getObjectField(dotInstance, "mOnMiuiCaptionClickListener");
                Object controller = XposedHelpers.getObjectField(dotInstance, "mMiuiDecorationController");
                if (clickListener != null && controller != null) {
                    XposedHelpers.callMethod(clickListener, "handleCloseClicked", controller);
                    return;
                }
            } catch (Throwable t) {
                XposedBridge.log(TAG + "handleCloseClicked failed: " + t);
            }
        }

        try {
            if (taskId > 0) {
                Class<?> atmCls = Class.forName("android.app.ActivityTaskManager");
                Object atm = atmCls.getMethod("getService").invoke(null);
                atm.getClass().getMethod("removeTask", int.class).invoke(atm, taskId);
                return;
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "removeTask via ATM failed: " + t);
        }
    }

    public static void performMinimize(Context context, int taskId, Object dotInstance) {
        if (dotInstance != null) {
            try {
                Object taskInfo = XposedHelpers.getObjectField(dotInstance, "mRunningTaskInfo");
                Object taskOrg = XposedHelpers.getObjectField(dotInstance, "mTaskOrganizer");
                if (taskInfo != null && taskOrg != null) {
                    Object token = XposedHelpers.getObjectField(taskInfo, "token");
                    Class<?> wctCls = Class.forName("android.window.WindowContainerTransaction");
                    Object wct = wctCls.newInstance();
                    XposedHelpers.callMethod(wct, "reorder", token, false);
                    XposedHelpers.callMethod(taskOrg, "applyTransaction", wct);
                    return;
                }
            } catch (Throwable t) {
                XposedBridge.log(TAG + "performMinimize via WCT failed: " + t);
            }
        }

        try {
            if (taskId > 0) {
                Class<?> atmCls = Class.forName("android.app.ActivityTaskManager");
                Object atm = atmCls.getMethod("getService").invoke(null);
                try {
                    Method moveTaskToRootTask = atm.getClass().getMethod("moveTaskToRootTask", int.class, int.class, boolean.class);
                    moveTaskToRootTask.invoke(atm, taskId, -1, false);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    public static void performMinimizeAll(Context context) {
        try {
            Class<?> atmCls = Class.forName("android.app.ActivityTaskManager");
            Object atm = atmCls.getMethod("getService").invoke(null);
            List<?> tasks = (List<?>) XposedHelpers.callMethod(atm, "getTasks", 20, false, false, 0);
            if (tasks == null) return;
            for (Object task : tasks) {
                try {
                    boolean isVisible = XposedHelpers.getBooleanField(task, "isVisible");
                    int topType = XposedHelpers.getIntField(task, "topActivityType");
                    if (!isVisible || topType > 1) continue;
                    int tid = XposedHelpers.getIntField(task, "taskId");
                    performMinimize(context, tid, null);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    public static void performMaximize(Context context, int taskId, Object dotInstance, View captionView) {
        if (dotInstance == null) return;
        boolean currentlyFullscreen = isTaskFullscreen(dotInstance);
        XposedBridge.log(TAG + "performMaximize taskId=" + taskId + " currentlyFullscreen=" + currentlyFullscreen);

        try {
            Object clickListener = XposedHelpers.getObjectField(dotInstance, "mOnMiuiCaptionClickListener");
            Object controller = XposedHelpers.getObjectField(dotInstance, "mMiuiDecorationController");
            if (clickListener != null && controller != null) {
                if (currentlyFullscreen) {
                    // Use direct AnimStarter first, or construct a MiuiCaptionStateButton so cast succeeds!
                    try {
                        Object animStarter = XposedHelpers.getObjectField(clickListener, "mMulWinSwitchAnimStarter");
                        Object taskOrg = XposedHelpers.getObjectField(clickListener, "mShellTaskOrganizer");
                        Object taskInfo = XposedHelpers.getObjectField(dotInstance, "mRunningTaskInfo");
                        if (animStarter != null && taskOrg != null && taskInfo != null) {
                            XposedHelpers.callMethod(animStarter, "switchFullscreenToFreeform", taskOrg, taskInfo);
                            return;
                        }
                    } catch (Throwable ignored) {}

                    Class<?> stateBtnCls = XposedHelpers.findClassIfExists(
                            "com.android.wm.shell.multitasking.miuimultiwinswitch.miuiwindowdecor.handlemenu.MiuiCaptionStateButton",
                            dotInstance.getClass().getClassLoader()
                    );
                    if (stateBtnCls != null && context != null) {
                        Object stateBtn = XposedHelpers.newInstance(stateBtnCls, context);
                        XposedHelpers.callMethod(stateBtn, "setSupported", true);
                        XposedHelpers.callMethod(clickListener, "handleFreeformClicked", stateBtn, controller);
                        return;
                    }
                } else {
                    XposedHelpers.callMethod(clickListener, "handleFullscreenClicked", controller);
                    return;
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "handleFullscreenClicked/Freeform failed: " + t);
        }

        try {
            Object taskInfo = XposedHelpers.getObjectField(dotInstance, "mRunningTaskInfo");
            Object taskOrg = XposedHelpers.getObjectField(dotInstance, "mTaskOrganizer");
            if (taskInfo == null || taskOrg == null) return;

            Object token = XposedHelpers.getObjectField(taskInfo, "token");
            Class<?> wctCls = Class.forName("android.window.WindowContainerTransaction");
            Object wct = wctCls.newInstance();

            Rect currentBounds = getTaskBounds(dotInstance);
            Rect displayBounds = getDisplayBounds(context);
            int barHeight = dp(context, ConfigManager.getTaskbarHeight(context));
            Rect desktopMaxBounds = new Rect(0, 0, displayBounds.width(), displayBounds.height() - barHeight);

            boolean isAlreadyMaximized = currentlyFullscreen || (currentBounds != null &&
                    Math.abs(currentBounds.width() - desktopMaxBounds.width()) <= dp(context, 20) &&
                    Math.abs(currentBounds.height() - desktopMaxBounds.height()) <= dp(context, 40));

            if (isAlreadyMaximized) {
                Rect restoreBounds = (taskId > 0) ? sSavedWindowBounds.get(taskId) : null;
                if (restoreBounds == null) {
                    int w = (int) (displayBounds.width() * 0.7f);
                    int h = (int) (displayBounds.height() * 0.7f);
                    int x = (displayBounds.width() - w) / 2;
                    int y = (displayBounds.height() - barHeight - h) / 2;
                    restoreBounds = new Rect(x, y, x + w, y + h);
                }
                XposedHelpers.callMethod(wct, "setBounds", token, restoreBounds);
            } else {
                if (currentBounds != null && currentBounds.width() > 0 && taskId > 0) {
                    sSavedWindowBounds.put(taskId, new Rect(currentBounds));
                }
                XposedHelpers.callMethod(wct, "setBounds", token, desktopMaxBounds);
            }
            XposedHelpers.callMethod(taskOrg, "applyTransaction", wct);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "performMaximize failed: " + t);
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
                            } else {
                                v.setVisibility(View.VISIBLE);
                                v.setAlpha(1.0f);
                            }
                        }
                    }
                });

                XposedBridge.hookAllMethods(cls, "draw", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.thisObject instanceof View && ConfigManager.shouldApplyDeXTaskbar(((View) param.thisObject).getContext())) {
                            param.setResult(null);
                        }
                    }
                });

                XposedBridge.hookAllMethods(cls, "onDraw", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.thisObject instanceof View && ConfigManager.shouldApplyDeXTaskbar(((View) param.thisObject).getContext())) {
                            param.setResult(null);
                        }
                    }
                });
            } catch (Throwable ignored) {}
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
            }
        } catch (Throwable ignored) {}
    }

    private static synchronized void registerStatusBarView(View v) {
        if (v == null) return;
        for (WeakReference<View> ref : sStatusBarViews) {
            if (ref.get() == v) return;
        }
        sStatusBarViews.add(new WeakReference<>(v));
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
                    boolean show = shouldShowWindowsControls(context);

                    for (String fname : new String[]{"minimizeWindowButton", "maximizeWindowButton", "closeWindowButton"}) {
                        ImageButton btn = getField(holder, fname, ImageButton.class);
                        if (btn != null) {
                            btn.setVisibility(show ? View.VISIBLE : View.GONE);
                            btn.setAlpha(show ? 1.0f : 0f);
                        }
                    }
                }
            });
        } catch (Throwable ignored) {}
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
