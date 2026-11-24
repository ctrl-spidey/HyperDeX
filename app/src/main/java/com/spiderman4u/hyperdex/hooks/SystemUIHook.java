package com.spiderman4u.hyperdex.hooks;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.input.InputManager;
import android.os.SystemClock;
import android.view.InputEvent;
import android.view.KeyEvent;

import com.spiderman4u.hyperdex.config.Constants;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class SystemUIHook {

    private static final String TAG = "[HyperDeX-SystemUI] ";
    private static Context sContext = null;
    private static ClassLoader sClassLoader = null;

    public static final String ACTION_NAV_TRIGGER = "com.spiderman4u.hyperdex.ACTION_NAV_TRIGGER";
    public static final String EXTRA_NAV_ACTION = "nav_action";

    public static void init(XC_LoadPackage.LoadPackageParam lpparam) {
        sClassLoader = lpparam.classLoader;

        try {
            Class<?> appCls = XposedHelpers.findClassIfExists("com.android.systemui.SystemUIApplication", lpparam.classLoader);
            if (appCls == null) {
                appCls = XposedHelpers.findClassIfExists("android.app.Application", lpparam.classLoader);
            }

            if (appCls != null) {
                XposedBridge.hookAllMethods(appCls, "onCreate", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.thisObject instanceof Context) {
                            sContext = (Context) param.thisObject;
                            registerNavReceiver(sContext);
                        }
                    }
                });
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed hooking SystemUIApplication: " + t);
        }
    }

    private static void registerNavReceiver(Context context) {
        if (context == null) return;
        try {
            IntentFilter filter = new IntentFilter(ACTION_NAV_TRIGGER);
            context.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    if (intent == null) return;
                    String action = intent.getStringExtra(EXTRA_NAV_ACTION);
                    if (action == null) return;

                    handleNavAction(action);
                }
            }, filter, Context.RECEIVER_EXPORTED);
            XposedBridge.log(TAG + "SystemUI Nav Receiver registered successfully");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error registering nav receiver: " + t);
        }
    }

    public static void handleNavAction(String action) {
        switch (action) {
            case "recents":
                toggleRecents();
                break;
            case "back":
                injectKey(KeyEvent.KEYCODE_BACK);
                break;
            case "home":
                triggerHome();
                break;
            case "search":
                injectKey(KeyEvent.KEYCODE_SEARCH);
                break;
            case "notifications":
                toggleNotifications();
                break;
            case "quick_settings":
                toggleQuickSettings();
                break;
            case "screenshot":
                injectKey(KeyEvent.KEYCODE_SYSRQ);
                break;
            case "power":
                showPowerDialog();
                break;
        }
    }

    private static void toggleRecents() {
        try {
            Class<?> depCls = XposedHelpers.findClassIfExists("com.android.systemui.Dependency", sClassLoader);
            if (depCls != null) {
                Class<?> cmdQueueCls = XposedHelpers.findClassIfExists("com.android.systemui.statusbar.CommandQueue", sClassLoader);
                if (cmdQueueCls != null) {
                    Object cmdQueue = XposedHelpers.callStaticMethod(depCls, "get", cmdQueueCls);
                    if (cmdQueue != null) {
                        XposedHelpers.callMethod(cmdQueue, "toggleRecentApps");
                        return;
                    }
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "toggleRecents via CommandQueue failed: " + t);
        }
        injectKey(KeyEvent.KEYCODE_APP_SWITCH);
    }

    private static void triggerHome() {
        try {
            if (sContext != null) {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_HOME);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                sContext.startActivity(intent);
                return;
            }
        } catch (Throwable ignored) {}
        injectKey(KeyEvent.KEYCODE_HOME);
    }

    private static void toggleNotifications() {
        try {
            Class<?> depCls = XposedHelpers.findClassIfExists("com.android.systemui.Dependency", sClassLoader);
            if (depCls != null) {
                Class<?> cmdQueueCls = XposedHelpers.findClassIfExists("com.android.systemui.statusbar.CommandQueue", sClassLoader);
                if (cmdQueueCls != null) {
                    Object cmdQueue = XposedHelpers.callStaticMethod(depCls, "get", cmdQueueCls);
                    if (cmdQueue != null) {
                        XposedHelpers.callMethod(cmdQueue, "animateExpandNotificationsPanel");
                        return;
                    }
                }
            }
        } catch (Throwable ignored) {}
        injectKey(KeyEvent.KEYCODE_NOTIFICATION);
    }

    private static void toggleQuickSettings() {
        try {
            Class<?> depCls = XposedHelpers.findClassIfExists("com.android.systemui.Dependency", sClassLoader);
            if (depCls != null) {
                Class<?> cmdQueueCls = XposedHelpers.findClassIfExists("com.android.systemui.statusbar.CommandQueue", sClassLoader);
                if (cmdQueueCls != null) {
                    Object cmdQueue = XposedHelpers.callStaticMethod(depCls, "get", cmdQueueCls);
                    if (cmdQueue != null) {
                        XposedHelpers.callMethod(cmdQueue, "animateExpandSettingsPanel", (String) null);
                        return;
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void showPowerDialog() {
        try {
            Class<?> depCls = XposedHelpers.findClassIfExists("com.android.systemui.Dependency", sClassLoader);
            if (depCls != null) {
                Class<?> gadCls = XposedHelpers.findClassIfExists("com.android.systemui.globalactions.GlobalActionsDialogLite", sClassLoader);
                if (gadCls != null) {
                    Object gad = XposedHelpers.callStaticMethod(depCls, "get", gadCls);
                    if (gad != null) {
                        XposedHelpers.callMethod(gad, "showOrHideDialog", false, true, null);
                        return;
                    }
                }
            }
        } catch (Throwable ignored) {}
        injectKey(KeyEvent.KEYCODE_POWER);
    }

    private static void injectKey(int keyCode) {
        try {
            long now = SystemClock.uptimeMillis();
            KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0);
            KeyEvent up = new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0);

            // 1. Try InputManagerGlobal (Android 14/15/16)
            try {
                Class<?> imgCls = Class.forName("android.hardware.input.InputManagerGlobal");
                Method getInstance = imgCls.getMethod("getInstance");
                Object img = getInstance.invoke(null);
                if (img != null) {
                    Method inject = imgCls.getMethod("injectInputEvent", InputEvent.class, int.class);
                    inject.invoke(img, down, 0);
                    inject.invoke(img, up, 0);
                    return;
                }
            } catch (Throwable ignored) {}

            // 2. Try InputManager.getInstance() or Context.getSystemService
            try {
                Class<?> imCls = Class.forName("android.hardware.input.InputManager");
                Object im = null;
                try {
                    Method getInstance = imCls.getMethod("getInstance");
                    im = getInstance.invoke(null);
                } catch (Throwable ignored) {
                    if (sContext != null) {
                        im = sContext.getSystemService(Context.INPUT_SERVICE);
                    }
                }
                if (im != null) {
                    Method inject = im.getClass().getMethod("injectInputEvent", InputEvent.class, int.class);
                    inject.invoke(im, down, 0);
                    inject.invoke(im, up, 0);
                    return;
                }
            } catch (Throwable ignored) {}

            // 3. Fallback to Instrumentation
            new Thread(() -> {
                try {
                    android.app.Instrumentation inst = new android.app.Instrumentation();
                    inst.sendKeyDownUpSync(keyCode);
                } catch (Throwable ignored) {}
            }).start();
        } catch (Throwable t) {
            XposedBridge.log(TAG + "injectKey " + keyCode + " failed: " + t);
        }
    }
}
