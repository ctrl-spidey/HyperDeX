package com.spiderman4u.hyperdex.hooks;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.spiderman4u.hyperdex.config.ConfigManager;
import com.spiderman4u.hyperdex.config.Constants;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class LauncherDockHook {

    private static final String TAG = "[HyperDeX-Dock] ";
    private static Object sCurrentHotSeatsInstance = null;

    public static void init(XC_LoadPackage.LoadPackageParam lpparam) {
        hookHotSeats(lpparam);
        hookLaptopDockController(lpparam);
    }

    private static void hookHotSeats(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> hotSeatsCls = XposedHelpers.findClassIfExists("com.miui.home.launcher.hotseats.HotSeats", lpparam.classLoader);
            if (hotSeatsCls == null) {
                XposedBridge.log(TAG + "Class com.miui.home.launcher.hotseats.HotSeats not found");
                return;
            }

            // Hook onAttachedToWindow to register mode change receiver
            XposedBridge.hookAllMethods(hotSeatsCls, "onAttachedToWindow", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    sCurrentHotSeatsInstance = param.thisObject;
                    if (param.thisObject instanceof View) {
                        View hotSeatsView = (View) param.thisObject;
                        Context context = hotSeatsView.getContext();
                        registerModeReceiver(context);
                        applyDeXTaskbarStyle(param.thisObject);
                    }
                }
            });

            // Hook onMeasure to enforce edge-to-edge full width in DeX mode
            XposedBridge.hookAllMethods(hotSeatsCls, "onMeasure", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (isDeXActive()) {
                        applyDeXTaskbarStyle(param.thisObject);
                    }
                }
            });

            // Hook initBackground
            XposedBridge.hookAllMethods(hotSeatsCls, "initBackground", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (isDeXActive()) {
                        applyDeXTaskbarStyle(param.thisObject);
                    }
                }
            });

            // Hook updatePadding
            XposedBridge.hookAllMethods(hotSeatsCls, "updatePadding", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (isDeXActive() && param.thisObject instanceof View) {
                        View v = (View) param.thisObject;
                        // Reduce bottom floating padding so taskbar is anchored flat to screen bottom
                        v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), 0);
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
            if (dockCtrlCls == null) {
                XposedBridge.log(TAG + "Class LaptopDockController not found");
                return;
            }

            XposedBridge.hookAllMethods(dockCtrlCls, "showDockbar", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (isDeXActive() && sCurrentHotSeatsInstance != null) {
                        applyDeXTaskbarStyle(sCurrentHotSeatsInstance);
                    }
                }
            });

            XposedBridge.log(TAG + "LaptopDockController hooks applied");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed hooking LaptopDockController: " + t);
        }
    }

    private static void applyDeXTaskbarStyle(Object hotSeatsObj) {
        try {
            if (hotSeatsObj == null) return;

            // 1. Check if DeX mode & edge-to-edge option are active
            if (!isDeXActive() || !ConfigManager.isEdgeToEdgeEnabled(null)) {
                return;
            }

            // 2. Adjust mBackgroundLayoutParam to MATCH_PARENT and 0 margins
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

            // 3. Apply Samsung DeX dark acrylic styled background
            if (hotSeatsObj instanceof View) {
                View hotSeatsView = (View) hotSeatsObj;
                GradientDrawable topBorder = new GradientDrawable();
                topBorder.setColor(Color.parseColor("#26FFFFFF")); // 15% white subtle divider

                GradientDrawable dexFill = new GradientDrawable();
                dexFill.setColor(Color.parseColor("#E6141518")); // DeX signature 90% acrylic dark
                dexFill.setCornerRadius(0); // Flat edge-to-edge bottom

                LayerDrawable layerDrawable = new LayerDrawable(new GradientDrawable[]{topBorder, dexFill});
                layerDrawable.setLayerInset(1, 0, 1, 0, 0); // 1px top border line

                hotSeatsView.setBackground(layerDrawable);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error applying DeX Taskbar style: " + t.getMessage());
        }
    }

    private static void registerModeReceiver(Context context) {
        try {
            IntentFilter filter = new IntentFilter(Constants.ACTION_MODE_CHANGED);
            context.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    XposedBridge.log(TAG + "Received ACTION_MODE_CHANGED");
                    if (sCurrentHotSeatsInstance instanceof View) {
                        View v = (View) sCurrentHotSeatsInstance;
                        v.post(() -> {
                            if (isDeXActive()) {
                                applyDeXTaskbarStyle(sCurrentHotSeatsInstance);
                            } else {
                                // Reset to stock workstation dock styling
                                v.setBackground(null);
                            }
                            v.requestLayout();
                            v.invalidate();
                        });
                    }
                }
            }, filter, Context.RECEIVER_EXPORTED);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error registering mode receiver: " + t);
        }
    }

    private static boolean isDeXActive() {
        return ConfigManager.isDeXModeEnabled(null);
    }
}
