package com.spiderman4u.hyperdex.hooks;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import android.view.InputEvent;
import android.view.KeyEvent;

import com.spiderman4u.hyperdex.config.ConfigManager;
import com.spiderman4u.hyperdex.config.Constants;

import java.lang.reflect.Method;
import java.util.function.Consumer;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class SystemUIHook {

    private static final String TAG = "[HyperDeX-SystemUI] ";
    private static Context sContext = null;
    private static ClassLoader sClassLoader = null;

    public static final String ACTION_NAV_TRIGGER = Constants.ACTION_NAV_TRIGGER;
    public static final String EXTRA_NAV_ACTION = Constants.EXTRA_NAV_ACTION;

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
                            registerReceivers(sContext);
                        }
                    }
                });
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed hooking SystemUIApplication: " + t);
        }

        hookNotifications(lpparam);
        hookHeadsUpSuppression(lpparam);
    }

    private static void hookHeadsUpSuppression(XC_LoadPackage.LoadPackageParam lpparam) {
        String[] headsUpClasses = new String[] {
            "com.android.systemui.statusbar.notification.headsup.HeadsUpManagerImpl",
            "com.android.systemui.statusbar.policy.HeadsUpManager",
            "com.android.systemui.statusbar.phone.HeadsUpManagerPhone"
        };
        for (String clsName : headsUpClasses) {
            try {
                Class<?> cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader);
                if (cls != null) {
                    XposedBridge.hookAllMethods(cls, "showNotification", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if (sContext != null && ConfigManager.shouldApplyDeXTaskbar(sContext)) {
                                param.setResult(null); // Suppress top heads-up banner in DeX mode!
                            }
                        }
                    });
                }
            } catch (Throwable ignored) {}
        }

        try {
            Class<?> nispCls = XposedHelpers.findClassIfExists(
                    "com.android.systemui.statusbar.notification.interruption.NotificationInterruptStateProviderImpl",
                    lpparam.classLoader
            );
            if (nispCls != null) {
                XposedBridge.hookAllMethods(nispCls, "shouldHeadsUp", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (sContext != null && ConfigManager.shouldApplyDeXTaskbar(sContext)) {
                            param.setResult(false);
                        }
                    }
                });
            }
        } catch (Throwable ignored) {}
    }

    private static void registerReceivers(Context context) {
        if (context == null) return;
        try {
            IntentFilter navFilter = new IntentFilter(ACTION_NAV_TRIGGER);
            context.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    if (intent == null) return;
                    String action = intent.getStringExtra(EXTRA_NAV_ACTION);
                    if (action == null) return;

                    int taskId = intent.getIntExtra("task_id", -1);
                    handleNavAction(action, taskId);
                }
            }, navFilter, Context.RECEIVER_EXPORTED);

            IntentFilter clearFilter = new IntentFilter(Constants.ACTION_CLEAR_NOTIFICATIONS);
            context.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    clearAllNotifications();
                }
            }, clearFilter, Context.RECEIVER_EXPORTED);

            XposedBridge.log(TAG + "SystemUI Nav & Clear Receivers registered successfully");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error registering receivers: " + t);
        }
    }

    public static void handleNavAction(String action) {
        handleNavAction(action, -1);
    }

    public static void handleNavAction(String action, int taskId) {
        switch (action) {
            case "minimize_task":
                WindowCaptionHook.performMinimize(sContext, taskId, null);
                break;
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
                triggerScreenshot();
                break;
            case "power":
                showPowerDialog();
                break;
        }
    }

    private static void hookNotifications(XC_LoadPackage.LoadPackageParam lpparam) {
        // 1. Hook NotificationListener in SystemUI
        try {
            Class<?> notifListenerCls = XposedHelpers.findClassIfExists("com.android.systemui.statusbar.NotificationListener", lpparam.classLoader);
            if (notifListenerCls != null) {
                XposedBridge.hookAllMethods(notifListenerCls, "onNotificationPosted", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.args.length > 0 && param.args[0] instanceof StatusBarNotification) {
                            broadcastNewNotification((StatusBarNotification) param.args[0]);
                        }
                    }
                });
            }
        } catch (Throwable ignored) {}

        // 2. Hook NotificationEntryManager in SystemUI
        try {
            Class<?> nemCls = XposedHelpers.findClassIfExists("com.android.systemui.statusbar.notification.NotificationEntryManager", lpparam.classLoader);
            if (nemCls != null) {
                XposedBridge.hookAllMethods(nemCls, "addNotification", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.args.length > 0 && param.args[0] instanceof StatusBarNotification) {
                            broadcastNewNotification((StatusBarNotification) param.args[0]);
                        }
                    }
                });
            }
        } catch (Throwable ignored) {}
    }

    private static void broadcastNewNotification(StatusBarNotification sbn) {
        if (sbn == null || sContext == null) return;
        try {
            Notification n = sbn.getNotification();
            if (n == null) return;
            // Ignore ongoing background service notifications
            if ((n.flags & Notification.FLAG_ONGOING_EVENT) != 0) return;

            Bundle extras = n.extras;
            CharSequence title = extras != null ? extras.getCharSequence(Notification.EXTRA_TITLE) : null;
            CharSequence text = extras != null ? extras.getCharSequence(Notification.EXTRA_TEXT) : null;
            if (title == null && text == null) return;

            Intent intent = new Intent(Constants.ACTION_NEW_NOTIFICATION);
            intent.setPackage("com.miui.home");
            intent.putExtra("pkg", sbn.getPackageName());
            intent.putExtra("title", title != null ? title.toString().trim() : "");
            intent.putExtra("text", text != null ? text.toString().trim() : "");
            intent.putExtra("post_time", sbn.getPostTime());
            intent.putExtra("key", sbn.getKey());
            sContext.sendBroadcast(intent);
            XposedBridge.log(TAG + "Broadcasted new notification for " + sbn.getPackageName());
        } catch (Throwable t) {
            XposedBridge.log(TAG + "broadcastNewNotification error: " + t);
        }
    }

    private static void clearAllNotifications() {
        try {
            if (sContext != null) {
                NotificationManager nm = (NotificationManager) sContext.getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) {
                    nm.cancelAll();
                }
            }
        } catch (Throwable ignored) {}

        try {
            Class<?> depCls = XposedHelpers.findClassIfExists("com.android.systemui.Dependency", sClassLoader);
            if (depCls != null) {
                Class<?> nemCls = XposedHelpers.findClassIfExists("com.android.systemui.statusbar.notification.NotificationEntryManager", sClassLoader);
                if (nemCls != null) {
                    Object nem = XposedHelpers.callStaticMethod(depCls, "get", nemCls);
                    if (nem != null) {
                        XposedHelpers.callMethod(nem, "removeAllNotificationRows");
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void triggerScreenshot() {
        // 1. Native MIUI / HyperOS screenshot broadcast
        try {
            if (sContext != null) {
                Intent intent = new Intent("com.miui.screenshot.action.SCREENSHOT");
                intent.setPackage("com.miui.screenshot");
                sContext.sendBroadcast(intent);
                return;
            }
        } catch (Throwable ignored) {}

        try {
            if (sContext != null) {
                Intent intent = new Intent("com.miui.screenshot.ACTION_SCREENSHOT");
                intent.setPackage("com.miui.screenshot");
                sContext.sendBroadcast(intent);
                return;
            }
        } catch (Throwable ignored) {}

        // 2. Android native ScreenshotHelper
        try {
            if (sContext != null) {
                Class<?> shCls = Class.forName("com.android.internal.util.ScreenshotHelper");
                Object helper = shCls.getConstructor(Context.class).newInstance(sContext);
                Method takeScreenshot = shCls.getMethod("takeScreenshot", int.class, Handler.class, Consumer.class);
                takeScreenshot.invoke(helper, 1, new Handler(Looper.getMainLooper()), null);
                return;
            }
        } catch (Throwable ignored) {}

        // 3. Hardware key fallback (KEYCODE_SYSRQ)
        injectKey(KeyEvent.KEYCODE_SYSRQ);
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
