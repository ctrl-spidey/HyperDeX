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
                        applyDockLayout(param.thisObject, context);
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
                        applyDockLayout(param.thisObject, v.getContext());
                    }
                }
            });

            // Hook initBackground
            XposedBridge.hookAllMethods(hotSeatsCls, "initBackground", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.thisObject instanceof View) {
                        View v = (View) param.thisObject;
                        applyDockLayout(param.thisObject, v.getContext());
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
                    param.setResult(0);
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
            "com.miui.home.launcher.laptop.dockbar.DockContainerView",
            "com.miui.home.launcher.dock.DockContainerView"
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
                                int iconSize = dp(context, ConfigManager.getIconSize(context));
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

                                resizeIconViews(itemView, iconSize);
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
                                int iconSize = dp(context, ConfigManager.getIconSize(context));
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

                                resizeIconViews(itemView, iconSize);
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

            int taskbarHeightPx = dp(context, ConfigManager.getTaskbarHeight(context));
            int navOffset = calculateNavOffset(context);

            // 1. Force outer container layout params
            Field bgParamField = XposedHelpers.findFieldIfExists(hotSeatsObj.getClass(), "mBackgroundLayoutParam");
            if (bgParamField != null) {
                Object paramObj = bgParamField.get(hotSeatsObj);
                if (paramObj instanceof ViewGroup.MarginLayoutParams) {
                    ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) paramObj;
                    lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                    lp.height = taskbarHeightPx;
                    lp.leftMargin = 0;
                    lp.rightMargin = 0;
                    lp.bottomMargin = 0;
                }
            }

            // 2. Hide inner floating pill background views
            hideBackgroundField(hotSeatsObj, "mBlurBackground");
            hideBackgroundField(hotSeatsObj, "mBlurBackground2");
            hideBackgroundField(hotSeatsObj, "mNormalBackground");
            hideBackgroundField(hotSeatsObj, "mMiuiXBlurBackground");

            Object listContent = XposedHelpers.getObjectField(hotSeatsObj, "mListContent");
            if (listContent != null) {
                hideBackgroundField(listContent, "mContentBackground");
                if (listContent instanceof View) {
                    View listContentView = (View) listContent;
                    listContentView.setBackground(null);
                    if (listContentView instanceof ViewGroup) {
                        ViewGroup lcvg = (ViewGroup) listContentView;
                        lcvg.setClipChildren(false);
                        lcvg.setClipToPadding(false);
                    }

                    ViewGroup.LayoutParams listLp = listContentView.getLayoutParams();
                    if (listLp instanceof FrameLayout.LayoutParams) {
                        FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) listLp;
                        flp.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
                        flp.height = taskbarHeightPx;
                        flp.leftMargin = 0;
                        flp.rightMargin = 0;
                        listContentView.setLayoutParams(flp);
                    }
                }
            }

            // 3. Align hotSeatsView to START (after nav buttons)
            ViewGroup.LayoutParams lp = hotSeatsView.getLayoutParams();
            if (lp instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) lp;
                flp.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
                flp.height = taskbarHeightPx;
                flp.leftMargin = navOffset;
                flp.rightMargin = 0;
                flp.bottomMargin = 0;
                hotSeatsView.setLayoutParams(flp);
            }
            hotSeatsView.setTranslationX(0f);
            hotSeatsView.setBackground(null);
            if (hotSeatsView instanceof ViewGroup) {
                ViewGroup hsvg = (ViewGroup) hotSeatsView;
                hsvg.setClipChildren(false);
                hsvg.setClipToPadding(false);
            }

            // 4. Set DeX background and tray on parent dock container
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

            if (!isLaptopOverlay) {
                View existingTray = dockContainer.findViewById(R.id.dex_system_tray);
                if (existingTray != null) {
                    dockContainer.removeView(existingTray);
                }
                View existingNav = dockContainer.findViewById(R.id.dex_nav_buttons);
                if (existingNav != null) {
                    dockContainer.removeView(existingNav);
                }
                return;
            }

            int barHeightDp = ConfigManager.getTaskbarHeight(context);
            int barHeightPx = dp(context, barHeightDp);
            int opacity = ConfigManager.getTaskbarOpacity(context);
            int alpha = (int) (opacity * 255 / 100f);
            int bgColor = Color.argb(alpha, 0x14, 0x15, 0x18);

            // Dock Container layout params height & WindowManager update
            ViewGroup.LayoutParams containerLp = dockContainer.getLayoutParams();
            if (containerLp != null) {
                containerLp.height = barHeightPx;
                if (containerLp instanceof android.view.WindowManager.LayoutParams) {
                    android.view.WindowManager.LayoutParams wlp = (android.view.WindowManager.LayoutParams) containerLp;
                    wlp.height = barHeightPx;
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

            // Solid edge-to-edge acrylic taskbar background with crisp 1px top border
            GradientDrawable topBorder = new GradientDrawable();
            topBorder.setColor(Color.parseColor("#33FFFFFF"));

            GradientDrawable dexFill = new GradientDrawable();
            dexFill.setColor(bgColor);
            dexFill.setCornerRadius(0);

            LayerDrawable layerDrawable = new LayerDrawable(new GradientDrawable[]{topBorder, dexFill});
            layerDrawable.setLayerInset(1, 0, 1, 0, 0);

            dockContainer.setBackground(layerDrawable);
            dockContainer.setClipChildren(false);
            dockContainer.setClipToPadding(false);

            // 1. Far-Left Navigation Buttons Zone [ |||  ◯  <  Q  ::: ]
            View existingNav = dockContainer.findViewById(R.id.dex_nav_buttons);
            if (existingNav == null) {
                DeXNavButtonsView navView = new DeXNavButtonsView(context);
                navView.setId(R.id.dex_nav_buttons);
                FrameLayout.LayoutParams lpNav = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        barHeightPx
                );
                lpNav.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
                lpNav.leftMargin = dp(context, 4);
                dockContainer.addView(navView, lpNav);
            } else {
                existingNav.setVisibility(View.VISIBLE);
                if (existingNav instanceof DeXNavButtonsView) {
                    ((DeXNavButtonsView) existingNav).updateVisibilityConfig(context);
                }
                ViewGroup.LayoutParams lp = existingNav.getLayoutParams();
                if (lp instanceof FrameLayout.LayoutParams) {
                    FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) lp;
                    flp.height = barHeightPx;
                    flp.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
                    existingNav.setLayoutParams(flp);
                }
            }

            // 2. Far-Right System Tray
            View existingTray = dockContainer.findViewById(R.id.dex_system_tray);
            if (existingTray == null) {
                DeXSystemTrayView tray = new DeXSystemTrayView(context);
                tray.setId(R.id.dex_system_tray);
                FrameLayout.LayoutParams lpTray = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        barHeightPx
                );
                lpTray.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
                lpTray.bottomMargin = 0;
                lpTray.rightMargin = dp(context, 4);
                dockContainer.addView(tray, lpTray);
            } else {
                existingTray.setVisibility(View.VISIBLE);
                ViewGroup.LayoutParams trayLp = existingTray.getLayoutParams();
                if (trayLp instanceof FrameLayout.LayoutParams) {
                    FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) trayLp;
                    flp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
                    flp.height = barHeightPx;
                    flp.bottomMargin = 0;
                    flp.rightMargin = dp(context, 4);
                    existingTray.setLayoutParams(flp);
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error in applyDockContainerDeXStyle: " + t);
        }
    }

    private static void restoreWorkstationStyle(Object hotSeatsObj) {
        try {
            if (!(hotSeatsObj instanceof View)) return;
            View hotSeatsView = (View) hotSeatsObj;

            ViewParent parent = hotSeatsView.getParent();
            if (parent instanceof ViewGroup) {
                ViewGroup dockContainer = (ViewGroup) parent;
                dockContainer.setBackground(null);

                View existingTray = dockContainer.findViewById(R.id.dex_system_tray);
                if (existingTray != null) {
                    existingTray.setVisibility(View.GONE);
                }

                View existingNav = dockContainer.findViewById(R.id.dex_nav_buttons);
                if (existingNav != null) {
                    existingNav.setVisibility(View.GONE);
                }
            }

            ViewGroup.LayoutParams lp = hotSeatsView.getLayoutParams();
            if (lp instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) lp;
                flp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
                flp.leftMargin = 0;
                flp.rightMargin = 0;
                hotSeatsView.setLayoutParams(flp);
            }
            hotSeatsView.setTranslationX(0f);

            showBackgroundField(hotSeatsObj, "mBlurBackground");
            showBackgroundField(hotSeatsObj, "mBlurBackground2");
            showBackgroundField(hotSeatsObj, "mNormalBackground");
            showBackgroundField(hotSeatsObj, "mMiuiXBlurBackground");

            Object listContent = XposedHelpers.getObjectField(hotSeatsObj, "mListContent");
            if (listContent != null) {
                showBackgroundField(listContent, "mContentBackground");
                if (listContent instanceof View) {
                    View listContentView = (View) listContent;
                    ViewGroup.LayoutParams listLp = listContentView.getLayoutParams();
                    if (listLp instanceof FrameLayout.LayoutParams) {
                        FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) listLp;
                        flp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
                        flp.leftMargin = 0;
                        listContentView.setLayoutParams(flp);
                    }
                }
            }

            hotSeatsView.setBackground(null);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error in restoreWorkstationStyle: " + t.getMessage());
        }
    }

    private static void hideBackgroundField(Object target, String fieldName) {
        try {
            Object val = XposedHelpers.getObjectField(target, fieldName);
            if (val instanceof View) {
                View v = (View) val;
                v.setVisibility(View.GONE);
                v.setAlpha(0f);
                v.setBackground(null);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void showBackgroundField(Object target, String fieldName) {
        try {
            Object val = XposedHelpers.getObjectField(target, fieldName);
            if (val instanceof View) {
                View v = (View) val;
                v.setVisibility(View.VISIBLE);
                v.setAlpha(1.0f);
            }
        } catch (Throwable ignored) {
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

    private static int dp(Context context, int val) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, val, context.getResources().getDisplayMetrics());
    }
}
