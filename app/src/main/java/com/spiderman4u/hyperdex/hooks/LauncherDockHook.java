package com.spiderman4u.hyperdex.hooks;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.Color;
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

import com.spiderman4u.hyperdex.R;
import com.spiderman4u.hyperdex.config.ConfigManager;
import com.spiderman4u.hyperdex.config.Constants;
import com.spiderman4u.hyperdex.views.DeXSystemTrayView;

import java.lang.reflect.Field;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class LauncherDockHook {

    private static final String TAG = "[HyperDeX-Dock] ";
    private static Object sCurrentHotSeatsInstance = null;
    private static boolean sObserverRegistered = false;

    public static void init(XC_LoadPackage.LoadPackageParam lpparam) {
        hookHotSeats(lpparam);
        hookLaptopDockController(lpparam);
        hookLaptopModeController(lpparam);
        hookBackgroundPillClasses(lpparam);
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

            // Hook onMeasure
            XposedBridge.hookAllMethods(hotSeatsCls, "onMeasure", new XC_MethodHook() {
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

            XposedBridge.log(TAG + "HotSeats hooks applied successfully");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed hooking HotSeats: " + t);
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

    private static void hookBackgroundPillClasses(XC_LoadPackage.LoadPackageParam lpparam) {
        String[] bgClasses = new String[] {
            "com.miui.home.launcher.hotseats.HotSeatsListContentMiuiXBlurBackground",
            "com.miui.home.launcher.hotseats.HotSeatsListContentNormalBackground",
            "com.miui.home.launcher.hotseats.HotSeatsListContentBlurBackground",
            "com.miui.home.launcher.hotseats.HotSeatsListContentBlurBackground2"
        };

        for (String className : bgClasses) {
            try {
                Class<?> cls = XposedHelpers.findClassIfExists(className, lpparam.classLoader);
                if (cls == null) continue;

                // 1. Hook onDraw and draw - completely suppress canvas rendering of the white rounded pill
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

                // 2. Hook setVisibility and setAlpha - keep GONE and alpha 0
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

                // 3. Hook updateRoundRect and updateBackgroundSize
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

                // 4. Hook onMeasure
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

    private static void applyDeXStyle(Object hotSeatsObj) {
        try {
            if (!(hotSeatsObj instanceof View)) return;
            View hotSeatsView = (View) hotSeatsObj;
            Context context = hotSeatsView.getContext();

            // 1. Force outer container layout params to full width
            Field bgParamField = XposedHelpers.findFieldIfExists(hotSeatsObj.getClass(), "mBackgroundLayoutParam");
            if (bgParamField != null) {
                Object paramObj = bgParamField.get(hotSeatsObj);
                if (paramObj instanceof ViewGroup.MarginLayoutParams) {
                    ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) paramObj;
                    lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
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

            // Also check list content background
            Object listContent = XposedHelpers.getObjectField(hotSeatsObj, "mListContent");
            if (listContent != null) {
                hideBackgroundField(listContent, "mContentBackground");
                if (listContent instanceof View) {
                    ((View) listContent).setBackground(null);
                }
            }

            // 3. Set the solid full-width Samsung DeX acrylic taskbar background on inline_dock_container
            ViewParent parent = hotSeatsView.getParent();
            if (parent instanceof ViewGroup) {
                ViewGroup dockContainer = (ViewGroup) parent;

                GradientDrawable topBorder = new GradientDrawable();
                topBorder.setColor(Color.parseColor("#26FFFFFF")); // 15% white subtle top border

                GradientDrawable dexFill = new GradientDrawable();
                dexFill.setColor(Color.parseColor("#E6141518")); // Classic DeX 90% acrylic dark
                dexFill.setCornerRadius(0); // Flat edge-to-edge

                LayerDrawable layerDrawable = new LayerDrawable(new GradientDrawable[]{topBorder, dexFill});
                layerDrawable.setLayerInset(1, 0, 1, 0, 0); // 1px border

                dockContainer.setBackground(layerDrawable);

                // 4. Add Samsung DeX System Tray View to bottom-right
                View existingTray = dockContainer.findViewById(R.id.dex_system_tray);
                if (existingTray == null) {
                    DeXSystemTrayView tray = new DeXSystemTrayView(context);
                    tray.setId(R.id.dex_system_tray);
                    FrameLayout.LayoutParams lpTray = new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                    );
                    lpTray.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
                    lpTray.rightMargin = dp(context, 8);
                    dockContainer.addView(tray, lpTray);
                } else {
                    existingTray.setVisibility(View.VISIBLE);
                }
            }

            // Clear any background on hotSeatsView itself so dockContainer background shines through
            hotSeatsView.setBackground(null);

        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error in applyDeXStyle: " + t.getMessage());
        }
    }

    private static void restoreWorkstationStyle(Object hotSeatsObj) {
        try {
            if (!(hotSeatsObj instanceof View)) return;
            View hotSeatsView = (View) hotSeatsObj;

            // Remove DeX background from inline_dock_container
            ViewParent parent = hotSeatsView.getParent();
            if (parent instanceof ViewGroup) {
                ViewGroup dockContainer = (ViewGroup) parent;
                dockContainer.setBackground(null);

                // Hide System Tray
                View existingTray = dockContainer.findViewById(R.id.dex_system_tray);
                if (existingTray != null) {
                    existingTray.setVisibility(View.GONE);
                }
            }

            // Restore inner pill background visibility
            showBackgroundField(hotSeatsObj, "mBlurBackground");
            showBackgroundField(hotSeatsObj, "mBlurBackground2");
            showBackgroundField(hotSeatsObj, "mNormalBackground");
            showBackgroundField(hotSeatsObj, "mMiuiXBlurBackground");

            Object listContent = XposedHelpers.getObjectField(hotSeatsObj, "mListContent");
            if (listContent != null) {
                showBackgroundField(listContent, "mContentBackground");
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
            // Observer for Settings.Global hyperdex_mode
            Uri uriGlobal = Settings.Global.getUriFor("hyperdex_mode");
            context.getContentResolver().registerContentObserver(uriGlobal, true, new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override
                public void onChange(boolean selfChange) {
                    XposedBridge.log(TAG + "Settings.Global hyperdex_mode changed");
                    refreshDock();
                }
            });

            // Observer for Settings.Global hyperdex_apply_normal
            Uri uriNormal = Settings.Global.getUriFor("hyperdex_apply_normal");
            context.getContentResolver().registerContentObserver(uriNormal, true, new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override
                public void onChange(boolean selfChange) {
                    XposedBridge.log(TAG + "Settings.Global hyperdex_apply_normal changed");
                    refreshDock();
                }
            });

            // Observer for Settings.System miui_dkt_mode (HyperOS Workstation Mode)
            Uri uriDkt = Settings.System.getUriFor("miui_dkt_mode");
            context.getContentResolver().registerContentObserver(uriDkt, true, new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override
                public void onChange(boolean selfChange) {
                    XposedBridge.log(TAG + "Settings.System miui_dkt_mode changed");
                    refreshDock();
                }
            });

            // Also register broadcast receiver as fallback
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
    }

    private static int dp(Context context, int val) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, val, context.getResources().getDisplayMetrics());
    }
}
