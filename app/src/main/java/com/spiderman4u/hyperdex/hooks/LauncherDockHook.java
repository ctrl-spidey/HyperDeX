package com.spiderman4u.hyperdex.hooks;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.spiderman4u.hyperdex.R;
import com.spiderman4u.hyperdex.config.ConfigManager;
import com.spiderman4u.hyperdex.config.Constants;
import com.spiderman4u.hyperdex.views.DeXNavButtonsView;
import com.spiderman4u.hyperdex.views.DeXSystemTrayView;
import com.spiderman4u.hyperdex.views.DeXTaskbarView;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class LauncherDockHook {

    private static final String TAG = "[HyperDeX-Dock] ";
    private static Object sCurrentHotSeatsInstance = null;
    private static Object sCurrentDockContainerInstance = null;
    private static View sLaptopOverlayRootView = null;
    private static boolean sObserverRegistered = false;
    private static boolean sAllAppsOverlayExpanded;

    private static final Map<View, float[]> sStockVisibility = new WeakHashMap<>();
    private static final Map<View, WindowManager.LayoutParams> sStockWindowParams = new WeakHashMap<>();
    private static final Map<View, int[]> sStockPadding = new WeakHashMap<>();
    private static final Map<View, android.graphics.drawable.Drawable> sStockBackgrounds = new WeakHashMap<>();

    private static void hideStockView(View view) {
        sStockVisibility.putIfAbsent(view, new float[]{view.getVisibility(), view.getAlpha()});
        view.setVisibility(View.GONE);
        view.setAlpha(0f);
    }

    private static void saveStockWindowParams(View view, WindowManager.LayoutParams params) {
        if (sStockWindowParams.containsKey(view)) return;
        WindowManager.LayoutParams original = new WindowManager.LayoutParams();
        original.copyFrom(params);
        sStockWindowParams.put(view, original);
    }

    private static void saveStockPadding(View view) {
        sStockPadding.putIfAbsent(view, new int[]{view.getPaddingLeft(), view.getPaddingTop(),
                view.getPaddingRight(), view.getPaddingBottom()});
    }

    private static void restoreStockDock(View view) {
        float[] visibility = sStockVisibility.remove(view);
        if (visibility != null) {
            view.setVisibility((int) visibility[0]);
            view.setAlpha(visibility[1]);
        }
        int[] padding = sStockPadding.remove(view);
        if (padding != null) view.setPadding(padding[0], padding[1], padding[2], padding[3]);
        if (sStockBackgrounds.containsKey(view)) view.setBackground(sStockBackgrounds.remove(view));

        WindowManager.LayoutParams params = sStockWindowParams.remove(view);
        if (params != null && view.isAttachedToWindow()) {
            WindowManager manager = view.getContext().getSystemService(WindowManager.class);
            if (manager != null) manager.updateViewLayout(view, params);
        }
    }

    public static void init(XC_LoadPackage.LoadPackageParam lpparam) {
        hookHotSeats(lpparam);
        hookHotSeatsListContent(lpparam);
        hookDeviceConfig(lpparam);
        hookLaptopDockController(lpparam);
        hookLaptopModeController(lpparam);
        hookDockContainerViews(lpparam);
        hookHotSeatsLayoutManager(lpparam);
        hookBackgroundPillClasses(lpparam);
        hookIconContainers(lpparam);
        hookCellBackground(lpparam);
        hookDockWindowManagerParams();
        hookLauncherAppLaunchAndResume(lpparam);
        hookNormalModeDock(lpparam);
    }

    private static void hookHotSeats(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> hotSeatsCls = XposedHelpers.findClassIfExists("com.miui.home.launcher.hotseats.HotSeats", lpparam.classLoader);
            if (hotSeatsCls == null) {
                XposedBridge.log(TAG + "Class com.miui.home.launcher.hotseats.HotSeats not found");
                return;
            }

            // Hook isOpaque
            XposedBridge.hookAllMethods(hotSeatsCls, "isOpaque", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    param.setResult(false);
                }
            });

            // Hook onAttachedToWindow
            XposedBridge.hookAllMethods(hotSeatsCls, "onAttachedToWindow", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    sCurrentHotSeatsInstance = param.thisObject;
                    if (param.thisObject instanceof View) {
                        View hotSeatsView = (View) param.thisObject;
                        Context context = hotSeatsView.getContext();
                        registerObserver(context);
                        if (ConfigManager.shouldApplyDeXTaskbar(context)) {
                            applyDockLayout(param.thisObject, context);
                        } else {
                            hotSeatsView.setBackground(new ColorDrawable(Color.TRANSPARENT));
                        }
                    }
                }
            });

            // Enforce DeX taskbar height in onMeasure
            XposedBridge.hookAllMethods(hotSeatsCls, "onMeasure", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.thisObject instanceof View) {
                        View v = (View) param.thisObject;
                        Context context = v.getContext();
                        if (ConfigManager.shouldApplyDeXTaskbar(context)) {
                            int targetH = dp(context, ConfigManager.getTaskbarHeight(context));
                            param.args[1] = View.MeasureSpec.makeMeasureSpec(targetH, View.MeasureSpec.EXACTLY);
                        }
                    }
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.thisObject instanceof View) {
                        View v = (View) param.thisObject;
                        Context context = v.getContext();
                        if (ConfigManager.shouldApplyDeXTaskbar(context)) {
                            applyDockLayout(param.thisObject, context);
                        }
                    }
                }
            });

            // Hook initBackground
            XposedBridge.hookAllMethods(hotSeatsCls, "initBackground", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.thisObject instanceof View) {
                        View v = (View) param.thisObject;
                        Context context = v.getContext();
                        if (ConfigManager.shouldApplyDeXTaskbar(context)) {
                            applyDockLayout(param.thisObject, context);
                        }
                    }
                }
            });

            // Hook onLayout
            XposedBridge.hookAllMethods(hotSeatsCls, "onLayout", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.thisObject instanceof View) {
                        View v = (View) param.thisObject;
                        Context context = v.getContext();
                        if (ConfigManager.shouldApplyDeXTaskbar(context)) {
                            int navOffset = calculateNavOffset(context);
                            ViewGroup.LayoutParams lp = v.getLayoutParams();
                            if (lp instanceof FrameLayout.LayoutParams) {
                                FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) lp;
                                if ((flp.gravity & Gravity.HORIZONTAL_GRAVITY_MASK) != Gravity.START || flp.leftMargin != navOffset) {
                                    flp.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
                                    flp.leftMargin = navOffset;
                                    v.setLayoutParams(flp);
                                }
                            }
                            int left = v.getLeft();
                            if (left > navOffset) {
                                v.setTranslationX(navOffset - left);
                            } else {
                                v.setTranslationX(0f);
                            }
                        } else {
                            v.setTranslationX(0f);
                        }
                    }
                }
            });

            // Hook updatePadding
            XposedBridge.hookAllMethods(hotSeatsCls, "updatePadding", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.thisObject instanceof View) {
                        View v = (View) param.thisObject;
                        Context context = v.getContext();
                        if (ConfigManager.shouldApplyDeXTaskbar(context)) {
                            v.setPadding(dp(context, 4), 0, dp(context, 4), 0);
                        }
                    }
                }
            });

            XposedBridge.log(TAG + "HotSeats hooks applied successfully");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed hooking HotSeats: " + t);
        }
    }

    private static void hookHotSeatsListContent(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> listContentCls = XposedHelpers.findClassIfExists("com.miui.home.launcher.hotseats.HotSeatsListContent", lpparam.classLoader);
            if (listContentCls == null) return;

            XposedBridge.hookAllMethods(listContentCls, "onLayout", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.thisObject instanceof View) {
                        View lc = (View) param.thisObject;
                        Context context = lc.getContext();
                        if (ConfigManager.shouldApplyDeXTaskbar(context)) {
                            int curLeft = lc.getLeft();
                            if (curLeft > 0) {
                                lc.setTranslationX(-curLeft);
                            } else {
                                lc.setTranslationX(0f);
                            }
                        } else {
                            lc.setTranslationX(0f);
                        }
                    }
                }
            });

            // Suppress the wide dark grey background pill in HotSeatsListContent
            XposedBridge.hookAllMethods(listContentCls, "setContentBackgroundVisible", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.thisObject instanceof View && ConfigManager.shouldApplyDeXTaskbar(((View) param.thisObject).getContext())) {
                        param.args[0] = false;
                    }
                }
            });

            XposedBridge.hookAllMethods(listContentCls, "animContentBackgroundAlpha", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.thisObject instanceof View && ConfigManager.shouldApplyDeXTaskbar(((View) param.thisObject).getContext())) {
                        param.setResult(null);
                    }
                }
            });

            XposedBridge.hookAllMethods(listContentCls, "ignoreShowContentBackground", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.thisObject instanceof View && ConfigManager.shouldApplyDeXTaskbar(((View) param.thisObject).getContext())) {
                        param.setResult(true);
                    }
                }
            });
            XposedBridge.log(TAG + "HotSeatsListContent hooked");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed hooking HotSeatsListContent: " + t);
        }
    }

    private static void hookDeviceConfig(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> devConfigCls = XposedHelpers.findClassIfExists("com.miui.home.launcher.DeviceConfig", lpparam.classLoader);
            if (devConfigCls == null) return;

            XposedBridge.hookAllMethods(devConfigCls, "getHotSeatsPaddingLeft", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Context context = null;
                    if (sCurrentHotSeatsInstance instanceof View) {
                        context = ((View) sCurrentHotSeatsInstance).getContext();
                    }
                    if (context != null && ConfigManager.shouldApplyDeXTaskbar(context)) {
                        param.setResult(0);
                    }
                }
            });
            XposedBridge.log(TAG + "DeviceConfig hooked");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed hooking DeviceConfig: " + t);
        }
    }

    private static void hookLaptopDockController(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> dockCtrlCls = XposedHelpers.findClassIfExists("com.miui.home.launcher.laptop.dockbar.LaptopDockController", lpparam.classLoader);
            if (dockCtrlCls == null) return;

            XposedBridge.hookAllMethods(dockCtrlCls, "showDockbar", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    refreshDock();
                }
            });

            XposedBridge.hookAllMethods(dockCtrlCls, "updateDockWindowHeight", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Context context = resolveControllerContext(param.thisObject);
                    if (context != null && ConfigManager.shouldApplyDeXTaskbar(context)) {
                        try {
                            int targetH = dp(context, ConfigManager.getTaskbarHeight(context));
                            XposedHelpers.setIntField(param.thisObject, "mDockWindowHeight", targetH);
                        } catch (Throwable ignored) {}
                    }
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    refreshDock();
                }
            });

            XposedBridge.hookAllMethods(dockCtrlCls, "getDockWindowHeight", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Context context = resolveControllerContext(param.thisObject);
                    if (context != null && ConfigManager.shouldApplyDeXTaskbar(context)) {
                        param.setResult(dp(context, ConfigManager.getTaskbarHeight(context)));
                    }
                }
            });

            Class<?> defaultImplsCls = XposedHelpers.findClassIfExists("com.miui.home.launcher.dock.DockController$DefaultImpls", lpparam.classLoader);
            if (defaultImplsCls != null) {
                XposedBridge.hookAllMethods(defaultImplsCls, "updateDockWindowHeight", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        refreshDock();
                    }
                });
            }

            XposedBridge.log(TAG + "LaptopDockController hooks applied");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed hooking LaptopDockController: " + t);
        }
    }

    private static void hookLaptopModeController(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> ctrlCls = XposedHelpers.findClassIfExists("com.miui.home.launcher.laptop.LaptopModeController", lpparam.classLoader);
            if (ctrlCls == null) return;

            XposedBridge.hookAllMethods(ctrlCls, "realExecuteLaptopMode", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    refreshDock();
                }
            });

            XposedBridge.log(TAG + "LaptopModeController hooks applied");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed hooking LaptopModeController: " + t);
        }
    }

    private static void hookDockContainerViews(XC_LoadPackage.LoadPackageParam lpparam) {
        String[] containerClasses = new String[] {
            "com.miui.home.launcher.laptop.dockbar.DockContainerView"
        };

        for (String className : containerClasses) {
            try {
                Class<?> cls = XposedHelpers.findClassIfExists(className, lpparam.classLoader);
                if (cls == null) continue;

                XposedBridge.hookAllMethods(cls, "attachHotSeats", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.args.length > 0 && param.args[0] instanceof View) {
                            View hotSeats = (View) param.args[0];
                            Context context = hotSeats.getContext();
                            if (ConfigManager.shouldApplyDeXTaskbar(context)) {
                                applyDockLayout(hotSeats, context);
                            }
                        }
                    }
                });

                XposedBridge.hookAllMethods(cls, "onAttachedToWindow", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.thisObject instanceof ViewGroup) {
                            ViewGroup container = (ViewGroup) param.thisObject;
                            sCurrentDockContainerInstance = container;
                            Context context = container.getContext();
                            if (ConfigManager.shouldApplyDeXTaskbar(context)) {
                                applyDockContainerDeXStyle(container, context);
                            }
                        }
                    }
                });

                // Strip bottom system bar insets padding on FitSystemWindowView subclass
                XposedBridge.hookAllMethods(cls, "setPadding", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.thisObject instanceof View && param.args.length >= 4) {
                            Context ctx = ((View) param.thisObject).getContext();
                            if (ConfigManager.shouldApplyDeXTaskbar(ctx)) {
                                param.args[1] = 0;
                                param.args[3] = 0;
                            }
                        }
                    }
                });

                XposedBridge.hookAllMethods(cls, "onApplyWindowInsets", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.thisObject instanceof ViewGroup) {
                            ViewGroup container = (ViewGroup) param.thisObject;
                            Context ctx = container.getContext();
                            if (ConfigManager.shouldApplyDeXTaskbar(ctx)) {
                                if (container.getPaddingBottom() != 0 || container.getPaddingTop() != 0) {
                                    saveStockPadding(container);
                                    container.setPadding(0, 0, 0, 0);
                                }
                                container.post(() -> applyDockContainerDeXStyle(container, ctx));
                            }
                        }
                    }
                });

                // Suppress stock children and keep container flush during dock layouts
                XposedBridge.hookAllMethods(cls, "onLayout", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.thisObject instanceof ViewGroup) {
                            ViewGroup container = (ViewGroup) param.thisObject;
                            Context ctx = container.getContext();
                            if (ConfigManager.shouldApplyDeXTaskbar(ctx)) {
                                if (container.getPaddingBottom() != 0 || container.getPaddingTop() != 0) {
                                    saveStockPadding(container);
                                    container.setPadding(0, 0, 0, 0);
                                }
                                int targetH = dp(ctx, ConfigManager.getTaskbarHeight(ctx));
                                int targetY = dp(ctx, ConfigManager.getTaskbarBottomOffset(ctx));
                                ViewGroup.LayoutParams lp = container.getLayoutParams();
                                if (lp instanceof android.view.WindowManager.LayoutParams) {
                                    android.view.WindowManager.LayoutParams wlp = (android.view.WindowManager.LayoutParams) lp;
                                    if (wlp.height != targetH || wlp.y != targetY) {
                                        container.post(() -> applyDockContainerDeXStyle(container, ctx));
                                    }
                                }
                                for (int i = 0; i < container.getChildCount(); i++) {
                                    View child = container.getChildAt(i);
                                    if (child != null && child.getId() != R.id.dex_taskbar_view) {
                                        hideStockView(child);
                                    }
                                }
                            }
                        }
                    }
                });

                // Enforce Taskbar Height on DockContainerView measure
                XposedBridge.hookAllMethods(cls, "onMeasure", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.thisObject instanceof View) {
                            View v = (View) param.thisObject;
                            Context context = v.getContext();
                            if (ConfigManager.shouldApplyDeXTaskbar(context)) {
                                int targetH = dp(context, ConfigManager.getTaskbarHeight(context));
                                param.args[1] = View.MeasureSpec.makeMeasureSpec(targetH, View.MeasureSpec.EXACTLY);
                            }
                        }
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.thisObject instanceof View) {
                            View v = (View) param.thisObject;
                            Context context = v.getContext();
                            if (ConfigManager.shouldApplyDeXTaskbar(context)) {
                                int targetH = dp(context, ConfigManager.getTaskbarHeight(context));
                                if (v.getMeasuredHeight() != targetH && v.getMeasuredWidth() > 0) {
                                    XposedHelpers.callMethod(v, "setMeasuredDimension", v.getMeasuredWidth(), targetH);
                                }
                                if (v.getPaddingBottom() != 0 || v.getPaddingTop() != 0) {
                                    v.setPadding(0, 0, 0, 0);
                                }
                            }
                        }
                    }
                });

                // Full-width touchable region constrained to taskbar height
                XposedBridge.hookAllMethods(cls, "computeTouchableRegion", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.thisObject instanceof View && param.args.length > 0 && param.args[0] instanceof Region) {
                            View view = (View) param.thisObject;
                            if (ConfigManager.shouldApplyDeXTaskbar(view.getContext())) {
                                Region region = (Region) param.args[0];
                                int w = view.getWidth() > 0 ? view.getWidth() : 2560;
                                int h = dp(view.getContext(), ConfigManager.getTaskbarHeight(view.getContext()));
                                region.set(0, 0, w, h);
                                param.setResult(true);
                            }
                        }
                    }
                });

                XposedBridge.hookAllMethods(cls, "access$getMTouchableRegion$p", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.args.length > 0 && param.args[0] instanceof View) {
                            View view = (View) param.args[0];
                            if (ConfigManager.shouldApplyDeXTaskbar(view.getContext())) {
                                Region region = (Region) param.getResult();
                                if (region != null) {
                                    int w = view.getWidth() > 0 ? view.getWidth() : 2560;
                                    int h = dp(view.getContext(), ConfigManager.getTaskbarHeight(view.getContext()));
                                    region.set(0, 0, w, h);
                                }
                            }
                        }
                    }
                });

                XposedBridge.log(TAG + "DockContainerView hooked: " + className);
            } catch (Throwable t) {
                XposedBridge.log(TAG + "Failed hooking " + className + ": " + t);
            }
        }
    }

    private static void hookHotSeatsLayoutManager(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> layoutMgrCls = XposedHelpers.findClassIfExists(
                    "com.miui.home.launcher.hotseats.HotSeatsListContentLayoutManager",
                    lpparam.classLoader
            );
            if (layoutMgrCls != null) {
                XposedBridge.hookAllMethods(layoutMgrCls, "updateOffsetIfNeed", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object layoutMgr = param.thisObject;
                        try {
                            Object content = XposedHelpers.getObjectField(layoutMgr, "mContent");
                            if (content instanceof View && ConfigManager.shouldApplyDeXTaskbar(((View) content).getContext())) {
                                XposedHelpers.setIntField(layoutMgr, "mTotalOffset", 0);
                            }
                        } catch (Throwable ignored) {}
                    }
                });

                XposedBridge.hookAllMethods(layoutMgrCls, "getItemOffset", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.args.length > 0 && param.args[0] instanceof View) {
                            View v = (View) param.args[0];
                            if (ConfigManager.shouldApplyDeXTaskbar(v.getContext())) {
                                param.setResult(0f);
                            }
                        }
                    }
                });

                XposedBridge.log(TAG + "HotSeatsListContentLayoutManager hooked");
            }

            Class<?> decorCls = XposedHelpers.findClassIfExists(
                    "com.miui.home.launcher.hotseats.HotSeatsListContentLayoutManager$OffsetDecoration",
                    lpparam.classLoader
            );
            if (decorCls != null) {
                XposedBridge.hookAllMethods(decorCls, "getItemOffsets", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.args.length > 1 && param.args[1] instanceof View) {
                            View v = (View) param.args[1];
                            if (ConfigManager.shouldApplyDeXTaskbar(v.getContext())) {
                                if (param.args[0] instanceof Rect) {
                                    Rect outRect = (Rect) param.args[0];
                                    outRect.set(0, 0, 0, 0);
                                }
                            }
                        }
                    }
                });
                XposedBridge.log(TAG + "OffsetDecoration hooked");
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed hooking HotSeatsListContentLayoutManager: " + t);
        }
    }

    private static void hookBackgroundPillClasses(XC_LoadPackage.LoadPackageParam lpparam) {
        String[] bgClasses = new String[] {
            "com.miui.home.launcher.hotseats.HotSeatsListContentMiuiXBlurBackground",
            "com.miui.home.launcher.hotseats.HotSeatsListContentNormalBackground",
            "com.miui.home.launcher.hotseats.HotSeatsListContentBlurBackground",
            "com.miui.home.launcher.hotseats.HotSeatsListContentBlurBackground2",
            "com.miui.home.launcher.dock.IconDraggingBackgroundView",
            "com.miui.home.launcher.dock.SplitWindowBackgroundView"
        };

        for (String className : bgClasses) {
            try {
                Class<?> cls = XposedHelpers.findClassIfExists(className, lpparam.classLoader);
                if (cls == null) continue;

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

                XposedBridge.hookAllMethods(cls, "setVisibility", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.thisObject instanceof View) {
                            View v = (View) param.thisObject;
                            if (ConfigManager.shouldApplyDeXTaskbar(v.getContext())) {
                                param.args[0] = View.GONE;
                            }
                        }
                    }
                });

                XposedBridge.hookAllMethods(cls, "setAlpha", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.thisObject instanceof View) {
                            View v = (View) param.thisObject;
                            if (ConfigManager.shouldApplyDeXTaskbar(v.getContext())) {
                                param.args[0] = 0f;
                            }
                        }
                    }
                });

                XposedBridge.hookAllMethods(cls, "updateRoundRect", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.thisObject instanceof View) {
                            View v = (View) param.thisObject;
                            if (ConfigManager.shouldApplyDeXTaskbar(v.getContext())) {
                                hideStockView(v);
                                v.setBackground(null);
                            }
                        }
                    }
                });

                XposedBridge.hookAllMethods(cls, "updateBackgroundSize", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.thisObject instanceof View) {
                            View v = (View) param.thisObject;
                            if (ConfigManager.shouldApplyDeXTaskbar(v.getContext())) {
                                hideStockView(v);
                                v.setBackground(null);
                            }
                        }
                    }
                });

                XposedBridge.hookAllMethods(cls, "onMeasure", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.thisObject instanceof View) {
                            View v = (View) param.thisObject;
                            if (ConfigManager.shouldApplyDeXTaskbar(v.getContext())) {
                                hideStockView(v);
                                v.setBackground(null);
                            }
                        }
                    }
                });

            } catch (Throwable t) {
                XposedBridge.log(TAG + "Failed hooking background class " + className + ": " + t);
            }
        }
    }

    private static void hookIconContainers(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> flexboxCls = XposedHelpers.findClassIfExists("com.miui.home.launcher.hotseats.FlexboxItemContainer", lpparam.classLoader);
            if (flexboxCls != null) {
                XposedBridge.hookAllMethods(flexboxCls, "onAttachedToWindow", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.thisObject instanceof View) {
                            View v = (View) param.thisObject;
                            if (ConfigManager.shouldApplyDeXTaskbar(v.getContext())) {
                                stripBackgrounds(v);
                            }
                        }
                    }
                });

                XposedBridge.hookAllMethods(flexboxCls, "setBackground", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.thisObject instanceof View) {
                            View v = (View) param.thisObject;
                            if (ConfigManager.shouldApplyDeXTaskbar(v.getContext())) {
                                param.args[0] = null;
                            }
                        }
                    }
                });

                XposedBridge.log(TAG + "FlexboxItemContainer hooked");
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed hooking FlexboxItemContainer: " + t);
        }

        // Hook HotSeatsListContentAdapter to resize icons and strip item backgrounds
        try {
            Class<?> adapterCls = XposedHelpers.findClassIfExists("com.miui.home.launcher.hotseats.HotSeatsListContentAdapter", lpparam.classLoader);
            if (adapterCls != null) {
                XposedBridge.hookAllMethods(adapterCls, "onBindViewHolder", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object holder = param.args[0];
                        if (holder != null) {
                            View itemView = (View) XposedHelpers.getObjectField(holder, "itemView");
                            if (itemView != null && ConfigManager.shouldApplyDeXTaskbar(itemView.getContext())) {
                                Context context = itemView.getContext();
                                stripBackgrounds(itemView);

                                int barHeight = dp(context, ConfigManager.getTaskbarHeight(context));
                                int rawIconSize = dp(context, ConfigManager.getIconSize(context));
                                int maxSafeIconPx = Math.max(dp(context, 18), barHeight - dp(context, 8));
                                int iconSize = Math.min(rawIconSize, maxSafeIconPx);
                                int spacing = dp(context, ConfigManager.getIconSpacing(context));

                                ViewGroup.LayoutParams lp = itemView.getLayoutParams();
                                if (lp != null) {
                                    lp.width = iconSize + spacing * 2;
                                    lp.height = barHeight;
                                    itemView.setLayoutParams(lp);
                                }
                                itemView.setPadding(spacing, 0, spacing, 0);

                                int baseSize = dp(context, 60);
                                float scale = Math.min(1.0f, (float) iconSize / (float) baseSize);
                                itemView.setScaleX(scale);
                                itemView.setScaleY(scale);
                                itemView.setPivotX((iconSize + spacing * 2) / 2f);
                                itemView.setPivotY(barHeight / 2f);

                                if (itemView.getParent() instanceof ViewGroup) {
                                    ViewGroup parentVg = (ViewGroup) itemView.getParent();
                                    parentVg.setClipChildren(false);
                                    parentVg.setClipToPadding(false);
                                }
                            }
                        }
                    }
                });

                XposedBridge.hookAllMethods(adapterCls, "onViewAttachedToWindow", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object holder = param.args[0];
                        if (holder != null) {
                            View itemView = (View) XposedHelpers.getObjectField(holder, "itemView");
                            if (itemView != null && ConfigManager.shouldApplyDeXTaskbar(itemView.getContext())) {
                                Context context = itemView.getContext();
                                stripBackgrounds(itemView);

                                int barHeight = dp(context, ConfigManager.getTaskbarHeight(context));
                                int rawIconSize = dp(context, ConfigManager.getIconSize(context));
                                int maxSafeIconPx = Math.max(dp(context, 18), barHeight - dp(context, 8));
                                int iconSize = Math.min(rawIconSize, maxSafeIconPx);
                                int spacing = dp(context, ConfigManager.getIconSpacing(context));

                                ViewGroup.LayoutParams lp = itemView.getLayoutParams();
                                if (lp != null) {
                                    lp.width = iconSize + spacing * 2;
                                    lp.height = barHeight;
                                    itemView.setLayoutParams(lp);
                                }
                                itemView.setPadding(spacing, 0, spacing, 0);

                                int baseSize = dp(context, 60);
                                float scale = Math.min(1.0f, (float) iconSize / (float) baseSize);
                                itemView.setScaleX(scale);
                                itemView.setScaleY(scale);
                                itemView.setPivotX((iconSize + spacing * 2) / 2f);
                                itemView.setPivotY(barHeight / 2f);

                                if (itemView.getParent() instanceof ViewGroup) {
                                    ViewGroup parentVg = (ViewGroup) itemView.getParent();
                                    parentVg.setClipChildren(false);
                                    parentVg.setClipToPadding(false);
                                }
                            }
                        }
                    }
                });

                XposedBridge.log(TAG + "HotSeatsListContentAdapter hooked");
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed hooking HotSeatsListContentAdapter: " + t);
        }
    }

    private static void resizeIconViews(View v, int iconSizePx) {
        if (v == null) return;
        String name = v.getClass().getName();
        if ((v instanceof ImageView || name.contains("Icon") || name.contains("ImageView")) && !name.contains("CellBackground")) {
            ViewGroup.LayoutParams lp = v.getLayoutParams();
            if (lp != null) {
                lp.width = iconSizePx;
                lp.height = iconSizePx;
                v.setLayoutParams(lp);
            }
            if (v instanceof ImageView) {
                ((ImageView) v).setScaleType(ImageView.ScaleType.FIT_CENTER);
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                resizeIconViews(vg.getChildAt(i), iconSizePx);
            }
        }
    }

    private static void hookCellBackground(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> cellBgCls = XposedHelpers.findClassIfExists("com.miui.home.launcher.CellBackground", lpparam.classLoader);
            if (cellBgCls == null) return;

            XposedBridge.hookAllConstructors(cellBgCls, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.thisObject instanceof View) {
                        View v = (View) param.thisObject;
                        if (ConfigManager.shouldApplyDeXTaskbar(v.getContext())) {
                            hideStockView(v);
                            if (v instanceof ImageView) {
                                ((ImageView) v).setImageDrawable(null);
                            }
                        }
                    }
                }
            });

            XposedBridge.hookAllMethods(cellBgCls, "setFrame", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.thisObject instanceof View) {
                        View v = (View) param.thisObject;
                        if (ConfigManager.shouldApplyDeXTaskbar(v.getContext())) {
                            hideStockView(v);
                            if (v instanceof ImageView) {
                                ((ImageView) v).setImageDrawable(null);
                            }
                            if (param.args.length >= 4) {
                                param.args[0] = 0;
                                param.args[1] = 0;
                                param.args[2] = 0;
                                param.args[3] = 0;
                            }
                        }
                    }
                }
            });

            XposedBridge.hookAllMethods(cellBgCls, "setCellTranslate", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.thisObject instanceof View) {
                        View v = (View) param.thisObject;
                        if (ConfigManager.shouldApplyDeXTaskbar(v.getContext())) {
                            hideStockView(v);
                            if (v instanceof ImageView) {
                                ((ImageView) v).setImageDrawable(null);
                            }
                            if (param.args.length > 0) param.args[0] = 0;
                        }
                    }
                }
            });

            XposedBridge.hookAllMethods(cellBgCls, "setImageTranslation", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.thisObject instanceof View) {
                        View v = (View) param.thisObject;
                        if (ConfigManager.shouldApplyDeXTaskbar(v.getContext())) {
                            hideStockView(v);
                            if (v instanceof ImageView) {
                                ((ImageView) v).setImageDrawable(null);
                            }
                            if (param.args.length >= 2) {
                                param.args[0] = 0;
                                param.args[1] = 0;
                            }
                        }
                    }
                }
            });

            XposedBridge.log(TAG + "CellBackground hooked");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed hooking CellBackground: " + t);
        }
    }

    private static void stripBackgrounds(View v) {
        if (v == null) return;
        String clsName = v.getClass().getName();
        if (clsName.contains("CellBackground")) {
            hideStockView(v);
            if (v instanceof ImageView) {
                ((ImageView) v).setImageDrawable(null);
            }
            return;
        }
        v.setBackground(null);
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                View child = vg.getChildAt(i);
                if (child.getClass().getName().contains("CellBackground")) {
                    hideStockView(child);
                    if (child instanceof ImageView) {
                        ((ImageView) child).setImageDrawable(null);
                    }
                } else if (!(child instanceof ImageView)) {
                    child.setBackground(null);
                }
                if (child instanceof ViewGroup) {
                    stripBackgrounds(child);
                }
            }
        }
    }

    private static void applyDockLayout(Object hotSeatsObj, Context context) {
        try {
            if (hotSeatsObj == null || context == null) return;

            boolean shouldDeX = ConfigManager.shouldApplyDeXTaskbar(context);

            if (shouldDeX) {
                applyDeXStyle(hotSeatsObj);
            } else {
                restoreWorkstationStyle(hotSeatsObj);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error applying dock layout: " + t.getMessage());
        }
    }

    private static int calculateNavOffset(Context context) {
        boolean showNav = ConfigManager.isShowNavButtons(context);
        boolean showSearch = ConfigManager.isShowSearchButton(context);
        boolean showApps = ConfigManager.isStartMenuEnabled(context);

        int count = 0;
        if (showNav) count += 3;
        if (showSearch) count += 1;
        if (showApps) count += 1;

        if (count == 0) return dp(context, 8);
        return dp(context, count * 36 + 12);
    }

    private static void applyDeXStyle(Object hotSeatsObj) {
        try {
            if (!(hotSeatsObj instanceof View)) return;
            View hotSeatsView = (View) hotSeatsObj;
            Context context = hotSeatsView.getContext();

            // Option 1: Completely suppress Xiaomi's native HotSeats dock to eliminate
            // flexbox item stretching and duplicate drawer buttons!
            hideStockView(hotSeatsView);

            // Set standalone edge-to-edge DeX taskbar on the parent dock container
            ViewParent parent = hotSeatsView.getParent();
            if (parent instanceof ViewGroup) {
                applyDockContainerDeXStyle((ViewGroup) parent, context);
            }

        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error in applyDeXStyle: " + t.getMessage());
        }
    }

    private static void applyDockContainerDeXStyle(ViewGroup dockContainer, Context context) {
        if (dockContainer == null || context == null) return;
        try {
            boolean isLaptopOverlay = dockContainer.getClass().getName().contains("laptop.dockbar.DockContainerView");

            if (!ConfigManager.shouldApplyDeXTaskbar(context)) {
                View existingTaskbar = dockContainer.findViewById(R.id.dex_taskbar_view);
                if (existingTaskbar != null) {
                    dockContainer.removeView(existingTaskbar);
                }
                restoreStockDock(dockContainer);
                for (int i = 0; i < dockContainer.getChildCount(); i++) {
                    restoreStockDock(dockContainer.getChildAt(i));
                }
                if (sLaptopOverlayRootView != null && sLaptopOverlayRootView != dockContainer) {
                    restoreStockDock(sLaptopOverlayRootView);
                }
                return;
            }

            if (!isLaptopOverlay) {
                View existingTaskbar = dockContainer.findViewById(R.id.dex_taskbar_view);
                if (existingTaskbar != null) {
                    dockContainer.removeView(existingTaskbar);
                }
                return;
            }

            sStockVisibility.putIfAbsent(dockContainer, new float[]{dockContainer.getVisibility(), dockContainer.getAlpha()});
            if (!sStockBackgrounds.containsKey(dockContainer)) {
                sStockBackgrounds.put(dockContainer, dockContainer.getBackground());
            }
            saveStockPadding(dockContainer);
            dockContainer.setVisibility(View.VISIBLE);
            dockContainer.setAlpha(1.0f);
            dockContainer.setBackground(null);
            dockContainer.setElevation(0f);
            dockContainer.setPadding(0, 0, 0, 0);

            int barHeightDp = ConfigManager.getTaskbarHeight(context);
            int barHeightPx = dp(context, barHeightDp);
            int bottomOffsetPx = dp(context, ConfigManager.getTaskbarBottomOffset(context));

            // Dock Container layout params height, flush bottom inset removal, & WindowManager update
            applyWindowParamsIfNeeded(dockContainer, context, barHeightPx, bottomOffsetPx);
            if (sLaptopOverlayRootView != null && sLaptopOverlayRootView != dockContainer) {
                saveStockPadding(sLaptopOverlayRootView);
                sLaptopOverlayRootView.setPadding(0, 0, 0, 0);
                applyWindowParamsIfNeeded(sLaptopOverlayRootView, context, barHeightPx, bottomOffsetPx);
            }

            dockContainer.setBackground(null);
            dockContainer.setClipChildren(false);
            dockContainer.setClipToPadding(false);

            // 1. Hide ALL stock children in DockContainerView so only DeXTaskbarView is visible!
            for (int i = 0; i < dockContainer.getChildCount(); i++) {
                View child = dockContainer.getChildAt(i);
                if (child != null && child.getId() != R.id.dex_taskbar_view) {
                    hideStockView(child);
                }
            }

            // 2. Attach or update DeXTaskbarView edge-to-edge
            DeXTaskbarView taskbar = dockContainer.findViewById(R.id.dex_taskbar_view);
            if (taskbar == null) {
                taskbar = new DeXTaskbarView(context);
                taskbar.setId(R.id.dex_taskbar_view);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        barHeightPx
                );
                lp.gravity = Gravity.BOTTOM | Gravity.FILL_HORIZONTAL;
                dockContainer.addView(taskbar, lp);
            } else {
                taskbar.setVisibility(View.VISIBLE);
                taskbar.updateStyleAndDimensions(context);
                ViewGroup.LayoutParams lp = taskbar.getLayoutParams();
                if (lp instanceof FrameLayout.LayoutParams) {
                    FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) lp;
                    flp.height = barHeightPx;
                    flp.gravity = Gravity.BOTTOM | Gravity.FILL_HORIZONTAL;
                    taskbar.setLayoutParams(flp);
                }
            }

            // Connect HotSeats instance to dock apps view
            if (sCurrentHotSeatsInstance != null && taskbar.getDockAppsView() != null) {
                taskbar.getDockAppsView().setHotSeatsInstance(sCurrentHotSeatsInstance);
            }

        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error in applyDockContainerDeXStyle: " + t);
        }
    }

    private static void restoreWorkstationStyle(Object hotSeatsObj) {
        try {
            if (!(hotSeatsObj instanceof View)) return;
            View hotSeatsView = (View) hotSeatsObj;
            restoreStockDock(hotSeatsView);
            ViewParent parent = hotSeatsView.getParent();
            if (parent instanceof ViewGroup) {
                applyDockContainerDeXStyle((ViewGroup) parent, hotSeatsView.getContext());
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error in restoreWorkstationStyle: " + t.getMessage());
        }
    }

    private static void registerObserver(Context context) {
        if (sObserverRegistered || context == null) return;
        try {
            ContentObserver observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override
                public void onChange(boolean selfChange) {
                    refreshDock();
                }
            };

            String[] settingKeys = new String[] {
                "hyperdex_mode",
                "hyperdex_apply_normal",
                "hyperdex_taskbar_height",
                "hyperdex_icon_size",
                "hyperdex_icon_spacing",
                "hyperdex_taskbar_opacity",
                "hyperdex_taskbar_bottom_offset",
                "hyperdex_show_nav_buttons",
                "hyperdex_show_search_button",
                "hyperdex_floating_popups",
                "hyperdex_window_controls"
            };

            for (String key : settingKeys) {
                Uri uri = Settings.Global.getUriFor(key);
                context.getContentResolver().registerContentObserver(uri, true, observer);
            }

            Uri uriDkt = Settings.System.getUriFor("miui_dkt_mode");
            context.getContentResolver().registerContentObserver(uriDkt, true, observer);

            IntentFilter filter = new IntentFilter(Constants.ACTION_MODE_CHANGED);
            context.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    refreshDock();
                }
            }, filter, Context.RECEIVER_EXPORTED);

            IntentFilter hideFilter = new IntentFilter(Constants.ACTION_HIDE_TASK);
            context.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    if (intent == null) return;
                    int tid = intent.getIntExtra("task_id", -1);
                    if (tid > 0) {
                        com.spiderman4u.hyperdex.views.DeXDockAppsView.hideTaskDirectly(ctx, tid);
                    }
                }
            }, hideFilter, Context.RECEIVER_EXPORTED);

            sObserverRegistered = true;
            XposedBridge.log(TAG + "Observers and receiver registered successfully");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error registering observer: " + t);
        }
    }

    private static void refreshDock() {
        if (sCurrentHotSeatsInstance instanceof View) {
            View v = (View) sCurrentHotSeatsInstance;
            applyDockLayout(sCurrentHotSeatsInstance, v.getContext());
            v.requestLayout();
            v.invalidate();
        }
        if (sCurrentDockContainerInstance instanceof ViewGroup) {
            ViewGroup container = (ViewGroup) sCurrentDockContainerInstance;
            applyDockContainerDeXStyle(container, container.getContext());
            container.requestLayout();
            container.invalidate();
            if (!ConfigManager.shouldApplyDeXTaskbar(container.getContext())) {
                for (View view : new java.util.ArrayList<>(sStockVisibility.keySet())) {
                    if (view != null) restoreStockDock(view);
                }
            }
        }
    }

    public static void resetDockToBottom() {
        if (sCurrentDockContainerInstance instanceof View) {
            resetDockToBottom((View) sCurrentDockContainerInstance);
        }
    }

    public static void resetDockToBottom(View dockContainer) {
        if (dockContainer == null) return;
        dockContainer.post(() -> {
            try {
                dockContainer.animate().cancel();
                dockContainer.animate().translationY(0f).setDuration(280)
                        .setInterpolator(new android.view.animation.PathInterpolator(0.22f, 1.0f, 0.36f, 1.0f)).start();
                View targetRoot = (sLaptopOverlayRootView != null && sLaptopOverlayRootView.isAttachedToWindow())
                        ? sLaptopOverlayRootView : dockContainer;
                Context ctx = targetRoot.getContext();
                int barHeightPx = dp(ctx, ConfigManager.getTaskbarHeight(ctx));
                int bottomOffsetPx = dp(ctx, ConfigManager.getTaskbarBottomOffset(ctx));
                applyWindowParamsIfNeeded(targetRoot, ctx, barHeightPx, bottomOffsetPx);
            } catch (Throwable ignored) {}
        });
    }

    private static void hookLauncherAppLaunchAndResume(XC_LoadPackage.LoadPackageParam lpparam) {
        // 1. Hook AllAppsController (drawer / spotlight in workstation mode)
        try {
            Class<?> allAppsCtrl = XposedHelpers.findClassIfExists("com.miui.home.launcher.laptop.AllAppsController", lpparam.classLoader);
            if (allAppsCtrl != null) {
                XposedBridge.hookAllMethods(allAppsCtrl, "updateDockWindowSizeMode", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (param.args.length == 1 && param.args[0] instanceof Boolean) {
                            sAllAppsOverlayExpanded = (boolean) param.args[0];
                        }
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!sAllAppsOverlayExpanded) resetDockToBottom();
                    }
                });
                String[] resetMethods = new String[] {
                    "closeAllApps", "onHomeButtonPressed", "onBackPressed", "onScreenContentHide", "changeScreenContentOnClose"
                };
                for (String mName : resetMethods) {
                    try {
                        XposedBridge.hookAllMethods(allAppsCtrl, mName, new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                resetDockToBottom();
                            }
                        });
                    } catch (Throwable ignored) {}
                }
                XposedBridge.log(TAG + "AllAppsController hooks applied successfully");
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed hooking AllAppsController: " + t);
        }

        // 2. Hook LaptopDockController item clicks & gestures
        try {
            Class<?> dockCtrlCls = XposedHelpers.findClassIfExists("com.miui.home.launcher.laptop.dockbar.LaptopDockController", lpparam.classLoader);
            if (dockCtrlCls != null) {
                XposedBridge.hookAllMethods(dockCtrlCls, "onLauncherItemClicked", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        resetDockToBottom();
                    }
                });
                XposedBridge.hookAllMethods(dockCtrlCls, "slideDownDockEvent", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        resetDockToBottom();
                    }
                });
            }
        } catch (Throwable ignored) {}

        // 3. Hook Activity.startActivity for Launcher process
        try {
            Class<?> actCls = XposedHelpers.findClassIfExists("android.app.Activity", lpparam.classLoader);
            if (actCls != null) {
                XposedBridge.hookAllMethods(actCls, "startActivity", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.thisObject != null && param.thisObject.getClass().getName().contains("miui.home")) {
                            resetDockToBottom();
                        }
                    }
                });
            }
        } catch (Throwable ignored) {}
    }

    private static void hookNormalModeDock(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> normalDockCls = XposedHelpers.findClassIfExists("com.miui.home.launcher.dock.DockContainerView", lpparam.classLoader);
            if (normalDockCls != null) {
                XposedBridge.hookAllMethods(normalDockCls, "onAttachedToWindow", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.thisObject instanceof View) {
                            View v = (View) param.thisObject;
                            v.setBackground(new ColorDrawable(Color.TRANSPARENT));
                        }
                    }
                });
                XposedBridge.hookAllMethods(normalDockCls, "isOpaque", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        param.setResult(false);
                    }
                });
                XposedBridge.hookAllMethods(normalDockCls, "dispatchDraw", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.args.length > 0 && param.args[0] instanceof Canvas) {
                            ((Canvas) param.args[0]).drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                        }
                    }
                });
                XposedBridge.hookAllMethods(normalDockCls, "onDraw", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.args.length > 0 && param.args[0] instanceof Canvas) {
                            ((Canvas) param.args[0]).drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                        }
                    }
                });
                XposedBridge.hookAllMethods(normalDockCls, "setBackground", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        param.args[0] = new ColorDrawable(Color.TRANSPARENT);
                    }
                });
                XposedBridge.log(TAG + "Normal mode DockContainerView hooked with transparent clear");
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed hooking normal DockContainerView: " + t);
        }

        try {
            Class<?> dragLayerCls = XposedHelpers.findClassIfExists("com.miui.home.launcher.dock.DockContainerView$DockDragLayer", lpparam.classLoader);
            if (dragLayerCls != null) {
                XposedBridge.hookAllMethods(dragLayerCls, "onAttachedToWindow", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.thisObject instanceof View) {
                            View v = (View) param.thisObject;
                            v.setBackground(new ColorDrawable(Color.TRANSPARENT));
                        }
                    }
                });
                XposedBridge.hookAllMethods(dragLayerCls, "isOpaque", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        param.setResult(false);
                    }
                });
                XposedBridge.hookAllMethods(dragLayerCls, "dispatchDraw", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.args.length > 0 && param.args[0] instanceof Canvas) {
                            ((Canvas) param.args[0]).drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                        }
                    }
                });
                XposedBridge.hookAllMethods(dragLayerCls, "onDraw", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.args.length > 0 && param.args[0] instanceof Canvas) {
                            ((Canvas) param.args[0]).drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                        }
                    }
                });
                XposedBridge.hookAllMethods(dragLayerCls, "setBackground", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        param.args[0] = new ColorDrawable(Color.TRANSPARENT);
                    }
                });
                XposedBridge.log(TAG + "DockDragLayer hooked with transparent clear");
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed hooking DockDragLayer: " + t);
        }

        try {
            Class<?> dockWmCls = XposedHelpers.findClassIfExists("com.miui.home.launcher.dock.v3.view.DockWindowManager", lpparam.classLoader);
            if (dockWmCls != null) {
                XposedBridge.hookAllMethods(dockWmCls, "createWindow", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            View root = (View) XposedHelpers.getObjectField(param.thisObject, "dockRootView");
                            if (root != null) {
                                root.setBackground(new ColorDrawable(Color.TRANSPARENT));
                            }
                        } catch (Throwable ignored) {}
                    }
                });
                XposedBridge.log(TAG + "DockWindowManager hooked");
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed hooking DockWindowManager: " + t);
        }
    }

    private static void applyWindowParamsIfNeeded(View view, Context context, int barHeightPx, int bottomOffsetPx) {
        if (view == null || context == null || !ConfigManager.shouldApplyDeXTaskbar(context)
                || (sAllAppsOverlayExpanded && view == sLaptopOverlayRootView)) return;
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp instanceof android.view.WindowManager.LayoutParams) {
            android.view.WindowManager.LayoutParams wlp = (android.view.WindowManager.LayoutParams) lp;
            saveStockWindowParams(view, wlp);
            wlp.height = barHeightPx;
            wlp.alpha = 1.0f;
            wlp.format = android.graphics.PixelFormat.TRANSLUCENT;
            wlp.gravity = Gravity.BOTTOM | Gravity.FILL_HORIZONTAL;
            wlp.y = bottomOffsetPx;
            try {
                wlp.setFitInsetsTypes(0);
            } catch (Throwable ignored) {}
            if (bottomOffsetPx < 0) {
                wlp.flags |= android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
            } else {
                wlp.flags &= ~android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
            }
            android.view.WindowManager wm = (android.view.WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (wm != null && view.isAttachedToWindow()) {
                try {
                    wm.updateViewLayout(view, wlp);
                } catch (Throwable ignored) {}
            }
        } else if (lp != null) {
            if (lp.height != barHeightPx) {
                lp.height = barHeightPx;
                view.setLayoutParams(lp);
            }
        }
    }

    private static void hookDockWindowManagerParams() {
        try {
            Class<?> wmGlobalCls = Class.forName("android.view.WindowManagerGlobal");
            XC_MethodHook wmParamEnforcer = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args.length >= 2 && param.args[0] instanceof View && param.args[1] instanceof android.view.WindowManager.LayoutParams) {
                        View view = (View) param.args[0];
                        android.view.WindowManager.LayoutParams wlp = (android.view.WindowManager.LayoutParams) param.args[1];
                        String title = (wlp.getTitle() != null) ? wlp.getTitle().toString() : "";
                        boolean isLaptopDock = title.contains("Laptop overlay")
                                || view.getClass().getName().contains("laptop.dockbar.DockContainerView")
                                || view.findViewById(R.id.dex_taskbar_view) != null;
                        if (isLaptopDock) {
                            sLaptopOverlayRootView = view;
                            Context ctx = view.getContext();
                            if (ConfigManager.shouldApplyDeXTaskbar(ctx) && !sAllAppsOverlayExpanded) {
                                saveStockWindowParams(view, wlp);
                                saveStockPadding(view);
                                int targetH = dp(ctx, ConfigManager.getTaskbarHeight(ctx));
                                int bottomOffsetPx = dp(ctx, ConfigManager.getTaskbarBottomOffset(ctx));
                                wlp.height = targetH;
                                wlp.gravity = Gravity.BOTTOM | Gravity.FILL_HORIZONTAL;
                                wlp.y = bottomOffsetPx;
                                view.setPadding(0, 0, 0, 0);
                                try {
                                    wlp.setFitInsetsTypes(0);
                                } catch (Throwable ignored) {}
                                if (bottomOffsetPx < 0) {
                                    wlp.flags |= android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
                                } else {
                                    wlp.flags &= ~android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
                                }
                            }
                        }
                    }
                }
            };
            XposedBridge.hookAllMethods(wmGlobalCls, "addView", wmParamEnforcer);
            XposedBridge.hookAllMethods(wmGlobalCls, "updateViewLayout", wmParamEnforcer);
            XposedBridge.log(TAG + "WindowManagerGlobal DockContainerView/LaptopOverlay enforcer hooked");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed hooking WindowManagerGlobal: " + t);
        }
    }

    private static Context resolveControllerContext(Object controller) {
        if (sCurrentHotSeatsInstance instanceof View) {
            return ((View) sCurrentHotSeatsInstance).getContext();
        }
        if (sCurrentDockContainerInstance instanceof View) {
            return ((View) sCurrentDockContainerInstance).getContext();
        }
        if (controller != null) {
            try {
                return (Context) XposedHelpers.getObjectField(controller, "mContext");
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static int dp(Context context, int val) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, val, context.getResources().getDisplayMetrics());
    }
}
