package com.spiderman4u.hyperdex.hooks;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Region;
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
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.spiderman4u.hyperdex.R;
import com.spiderman4u.hyperdex.config.ConfigManager;
import com.spiderman4u.hyperdex.config.Constants;
import com.spiderman4u.hyperdex.views.DeXNavButtonsView;
import com.spiderman4u.hyperdex.views.DeXSystemTrayView;
import com.spiderman4u.hyperdex.views.DeXTaskbarView;

import java.lang.reflect.Field;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class LauncherDockHook {

    private static final String TAG = "[HyperDeX-Dock] ";
    private static Object sCurrentHotSeatsInstance = null;
    private static Object sCurrentDockContainerInstance = null;
    private static boolean sObserverRegistered = false;

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
                    Context context = null;
                    if (sCurrentHotSeatsInstance instanceof View) {
                        context = ((View) sCurrentHotSeatsInstance).getContext();
                    }
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
                    Context context = null;
                    if (sCurrentHotSeatsInstance instanceof View) {
                        context = ((View) sCurrentHotSeatsInstance).getContext();
                    }
                    if (context != null && ConfigManager.shouldApplyDeXTaskbar(context)) {
                        param.setResult(dp(context, ConfigManager.getTaskbarHeight(context)));
                    }
                }
            });

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

                // Suppress stock children during dock layouts
                XposedBridge.hookAllMethods(cls, "onLayout", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.thisObject instanceof ViewGroup) {
                            ViewGroup container = (ViewGroup) param.thisObject;
                            if (ConfigManager.shouldApplyDeXTaskbar(container.getContext())) {
                                for (int i = 0; i < container.getChildCount(); i++) {
                                    View child = container.getChildAt(i);
                                    if (child != null && child.getId() != R.id.dex_taskbar_view) {
                                        child.setVisibility(View.GONE);
                                        child.setAlpha(0f);
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
            "com.miui.home.launcher.hotseats.HotSeatsListContentBackground",
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
                                v.setVisibility(View.GONE);
                                v.setAlpha(0f);
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
                                v.setVisibility(View.GONE);
                                v.setAlpha(0f);
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
                                v.setVisibility(View.GONE);
                                v.setAlpha(0f);
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
                            v.setVisibility(View.GONE);
                            v.setAlpha(0f);
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
                            v.setVisibility(View.GONE);
                            v.setAlpha(0f);
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
                            v.setVisibility(View.GONE);
                            v.setAlpha(0f);
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
                            v.setVisibility(View.GONE);
                            v.setAlpha(0f);
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
            v.setVisibility(View.GONE);
            v.setAlpha(0f);
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
                    child.setVisibility(View.GONE);
                    child.setAlpha(0f);
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
            hotSeatsView.setVisibility(View.GONE);
            hotSeatsView.setAlpha(0f);

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
                if (isLaptopOverlay) {
                    dockContainer.setVisibility(View.GONE);
                    dockContainer.setAlpha(0f);
                    ViewGroup.LayoutParams lp = dockContainer.getLayoutParams();
                    if (lp instanceof android.view.WindowManager.LayoutParams) {
                        android.view.WindowManager.LayoutParams wlp = (android.view.WindowManager.LayoutParams) lp;
                        wlp.height = 0;
                        wlp.alpha = 0f;
                        android.view.WindowManager wm = (android.view.WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
                        if (wm != null) {
                            try {
                                wm.updateViewLayout(dockContainer, wlp);
                            } catch (Throwable ignored) {}
                        }
                    }
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

            dockContainer.setVisibility(View.VISIBLE);
            dockContainer.setAlpha(1.0f);
            dockContainer.setBackground(null);
            dockContainer.setElevation(0f);

            int barHeightDp = ConfigManager.getTaskbarHeight(context);
            int barHeightPx = dp(context, barHeightDp);

            // Dock Container layout params height & WindowManager update
            ViewGroup.LayoutParams containerLp = dockContainer.getLayoutParams();
            if (containerLp != null) {
                containerLp.height = barHeightPx;
                if (containerLp instanceof android.view.WindowManager.LayoutParams) {
                    android.view.WindowManager.LayoutParams wlp = (android.view.WindowManager.LayoutParams) containerLp;
                    wlp.height = barHeightPx;
                    wlp.alpha = 1.0f;
                    wlp.format = android.graphics.PixelFormat.TRANSLUCENT;
                    wlp.gravity = Gravity.BOTTOM | Gravity.FILL_HORIZONTAL;
                    android.view.WindowManager wm = (android.view.WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
                    if (wm != null) {
                        try {
                            wm.updateViewLayout(dockContainer, wlp);
                        } catch (Throwable ignored) {}
                    }
                } else {
                    dockContainer.setLayoutParams(containerLp);
                }
            }

            dockContainer.setBackground(null);
            dockContainer.setClipChildren(false);
            dockContainer.setClipToPadding(false);

            // 1. Hide ALL stock children in DockContainerView so only DeXTaskbarView is visible!
            for (int i = 0; i < dockContainer.getChildCount(); i++) {
                View child = dockContainer.getChildAt(i);
                if (child != null && child.getId() != R.id.dex_taskbar_view) {
                    child.setVisibility(View.GONE);
                    child.setAlpha(0f);
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
            if (hotSeatsView.getVisibility() == View.GONE) {
                hotSeatsView.setVisibility(View.VISIBLE);
                hotSeatsView.setAlpha(1.0f);
            }

            ViewParent parent = hotSeatsView.getParent();
            if (parent instanceof ViewGroup) {
                ViewGroup dockContainer = (ViewGroup) parent;

                View existingTaskbar = dockContainer.findViewById(R.id.dex_taskbar_view);
                if (existingTaskbar != null) {
                    dockContainer.removeView(existingTaskbar);
                }

                if (dockContainer.getClass().getName().contains("laptop.dockbar.DockContainerView")) {
                    dockContainer.setVisibility(View.GONE);
                } else {
                    // Restore stock children
                    for (int i = 0; i < dockContainer.getChildCount(); i++) {
                        View child = dockContainer.getChildAt(i);
                        if (child != null && child.getVisibility() != View.VISIBLE) {
                            child.setVisibility(View.VISIBLE);
                            child.setAlpha(1.0f);
                        }
                    }
                }
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
                "hyperdex_show_nav_buttons",
                "hyperdex_show_search_button",
                "hyperdex_floating_popups"
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
                dockContainer.animate().translationY(0f).setDuration(220)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
                ViewGroup.LayoutParams lp = dockContainer.getLayoutParams();
                if (lp instanceof android.view.WindowManager.LayoutParams) {
                    android.view.WindowManager.LayoutParams wlp = (android.view.WindowManager.LayoutParams) lp;
                    wlp.gravity = Gravity.BOTTOM | Gravity.FILL_HORIZONTAL;
                    wlp.y = 0;
                    android.view.WindowManager wm = (android.view.WindowManager) dockContainer.getContext().getSystemService(Context.WINDOW_SERVICE);
                    if (wm != null) {
                        try {
                            wm.updateViewLayout(dockContainer, wlp);
                        } catch (Throwable ignored) {}
                    }
                }
            } catch (Throwable ignored) {}
        });
    }

    private static void hookLauncherAppLaunchAndResume(XC_LoadPackage.LoadPackageParam lpparam) {
        // 1. Hook AllAppsController (drawer / spotlight in workstation mode)
        try {
            Class<?> allAppsCtrl = XposedHelpers.findClassIfExists("com.miui.home.launcher.laptop.AllAppsController", lpparam.classLoader);
            if (allAppsCtrl != null) {
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
                            v.setBackgroundColor(Color.TRANSPARENT);
                        }
                    }
                });
                XposedBridge.hookAllMethods(normalDockCls, "setBackground", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        param.args[0] = null;
                    }
                });
            }
        } catch (Throwable ignored) {}

        try {
            Class<?> dockWmCls = XposedHelpers.findClassIfExists("com.miui.home.launcher.dock.v3.view.DockWindowManager", lpparam.classLoader);
            if (dockWmCls != null) {
                XposedBridge.hookAllMethods(dockWmCls, "createWindow", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            View root = (View) XposedHelpers.getObjectField(param.thisObject, "dockRootView");
                            if (root != null) {
                                root.setBackgroundColor(Color.TRANSPARENT);
                            }
                        } catch (Throwable ignored) {}
                    }
                });
            }
        } catch (Throwable ignored) {}
    }

    private static int dp(Context context, int val) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, val, context.getResources().getDisplayMetrics());
    }
}
