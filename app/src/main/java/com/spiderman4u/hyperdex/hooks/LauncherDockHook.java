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
import android.view.View;
import android.view.ViewGroup;

import com.spiderman4u.hyperdex.config.ConfigManager;
import com.spiderman4u.hyperdex.config.Constants;

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
                    if (sCurrentHotSeatsInstance instanceof View) {
                        View v = (View) sCurrentHotSeatsInstance;
                        applyDockLayout(sCurrentHotSeatsInstance, v.getContext());
                    }
                }
            });

            XposedBridge.log(TAG + "LaptopDockController hooks applied");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed hooking LaptopDockController: " + t);
        }
    }

    private static void applyDockLayout(Object hotSeatsObj, Context context) {
        try {
            if (hotSeatsObj == null || context == null) return;

            boolean isDeX = ConfigManager.isDeXModeEnabled(context);
            boolean edgeToEdge = ConfigManager.isEdgeToEdgeEnabled(context);

            if (isDeX && edgeToEdge) {
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

            // 2. Hide inner floating pill background views so they do not render on top of the taskbar
            hideBackgroundField(hotSeatsObj, "mBlurBackground");
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

            // 3. Set the solid full-width Samsung DeX acrylic taskbar background on HotSeats view
            if (hotSeatsObj instanceof View) {
                View hotSeatsView = (View) hotSeatsObj;

                GradientDrawable topBorder = new GradientDrawable();
                topBorder.setColor(Color.parseColor("#26FFFFFF")); // 15% white subtle top border

                GradientDrawable dexFill = new GradientDrawable();
                dexFill.setColor(Color.parseColor("#E6141518")); // Classic DeX 90% acrylic dark
                dexFill.setCornerRadius(0); // Flat edge-to-edge

                LayerDrawable layerDrawable = new LayerDrawable(new GradientDrawable[]{topBorder, dexFill});
                layerDrawable.setLayerInset(1, 0, 1, 0, 0); // 1px border

                hotSeatsView.setBackground(layerDrawable);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error in applyDeXStyle: " + t.getMessage());
        }
    }

    private static void restoreWorkstationStyle(Object hotSeatsObj) {
        try {
            // Restore inner pill background visibility
            showBackgroundField(hotSeatsObj, "mBlurBackground");
            showBackgroundField(hotSeatsObj, "mNormalBackground");
            showBackgroundField(hotSeatsObj, "mMiuiXBlurBackground");

            Object listContent = XposedHelpers.getObjectField(hotSeatsObj, "mListContent");
            if (listContent != null) {
                showBackgroundField(listContent, "mContentBackground");
            }

            // Clear the outer DeX background
            if (hotSeatsObj instanceof View) {
                ((View) hotSeatsObj).setBackground(null);
            }
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
            Uri uri = Settings.Global.getUriFor("hyperdex_mode");
            context.getContentResolver().registerContentObserver(uri, true, new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override
                public void onChange(boolean selfChange) {
                    XposedBridge.log(TAG + "Settings.Global hyperdex_mode changed");
                    if (sCurrentHotSeatsInstance instanceof View) {
                        View v = (View) sCurrentHotSeatsInstance;
                        applyDockLayout(sCurrentHotSeatsInstance, v.getContext());
                        v.requestLayout();
                        v.invalidate();
                    }
                }
            });

            // Also register broadcast receiver as fallback
            IntentFilter filter = new IntentFilter(Constants.ACTION_MODE_CHANGED);
            context.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    if (sCurrentHotSeatsInstance instanceof View) {
                        View v = (View) sCurrentHotSeatsInstance;
                        applyDockLayout(sCurrentHotSeatsInstance, v.getContext());
                        v.requestLayout();
                        v.invalidate();
                    }
                }
            }, filter, Context.RECEIVER_EXPORTED);

            sObserverRegistered = true;
            XposedBridge.log(TAG + "Observer and receiver registered successfully");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error registering observer: " + t);
        }
    }
}
