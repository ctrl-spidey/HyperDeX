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
    private static volatile Object sNotificationListener = null;

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
        try {
            Class<?> nispCls = XposedHelpers.findClassIfExists(
                    "com.android.systemui.statusbar.notification.interruption.NotificationInterruptStateProviderImpl",
                    lpparam.classLoader
            );
            if (nispCls != null) {
                for (String method : new String[]{"checkHeadsUp", "canAlertHeadsUpCommon", "shouldHeadsUp"}) {
                    try {
                        XposedBridge.hookAllMethods(nispCls, method, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                if (sContext != null && ConfigManager.shouldApplyDeXTaskbar(sContext) && ConfigManager.isFloatingPopupsEnabled(sContext)) {
                                    param.setResult(false);
                                }
                            }
                        });
                    } catch (Throwable ignored) {}
                }
                XposedBridge.log(TAG + "HeadsUp suppression hooked on NotificationInterruptStateProviderImpl");
            }
        } catch (Throwable ignored) {}

        try {
            Class<?> coordCls = XposedHelpers.findClassIfExists(
                    "com.android.systemui.statusbar.notification.collection.coordinator.HeadsUpCoordinator",
                    lpparam.classLoader
            );
            if (coordCls != null) {
                XposedBridge.hookAllMethods(coordCls, "bindForAsyncHeadsUp", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (sContext != null && ConfigManager.shouldApplyDeXTaskbar(sContext) && ConfigManager.isFloatingPopupsEnabled(sContext)) {
                            param.setResult(null);
                        }
                    }
                });
            }
        } catch (Throwable ignored) {}

        String[] headsUpClasses = new String[] {
            "com.android.systemui.statusbar.notification.headsup.HeadsUpManagerImpl",
            "com.android.systemui.statusbar.policy.HeadsUpManager",
            "com.android.systemui.statusbar.phone.HeadsUpManagerPhone"
        };
        for (String clsName : headsUpClasses) {
            try {
                Class<?> cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader);
                if (cls != null) {
                    XposedBridge.hookAllMethods(cls, "shouldHeadsUpBecomePinned", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if (sContext != null && ConfigManager.shouldApplyDeXTaskbar(sContext) && ConfigManager.isFloatingPopupsEnabled(sContext)) {
                                param.setResult(false);
                            }
                        }
                    });
                    XposedBridge.hookAllMethods(cls, "showNotification", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if (sContext != null && ConfigManager.shouldApplyDeXTaskbar(sContext) && ConfigManager.isFloatingPopupsEnabled(sContext)) {
                                param.setResult(null);
                            }
                        }
                    });
                }
            } catch (Throwable ignored) {}
        }
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

            IntentFilter dismissFilter = new IntentFilter(Constants.ACTION_DISMISS_NOTIFICATION);
            context.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    if (intent == null) return;
                    String key = intent.getStringExtra("key");
                    dismissSingleNotification(key);
                }
            }, dismissFilter, Context.RECEIVER_EXPORTED);

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
            case "minimize_all":
                WindowCaptionHook.performMinimizeAll(sContext);
                break;
            case "close_task":
                WindowCaptionHook.performClose(sContext, taskId, null);
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
        String[] listenerClasses = new String[] {
            "com.android.systemui.statusbar.notification.MiuiNotificationListener",
            "com.android.systemui.statusbar.NotificationListener"
        };
        for (String clsName : listenerClasses) {
            try {
                Class<?> notifListenerCls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader);
                if (notifListenerCls != null) {
                    XposedBridge.hookAllMethods(notifListenerCls, "onListenerConnected", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            sNotificationListener = param.thisObject;
                            syncAllActiveNotifications(param.thisObject);
                        }
                    });
                    XposedBridge.hookAllMethods(notifListenerCls, "onNotificationPosted", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            sNotificationListener = param.thisObject;
                            if (param.args.length > 0 && param.args[0] instanceof StatusBarNotification) {
                                broadcastNewNotification((StatusBarNotification) param.args[0], false);
                            }
                        }
                    });
                    XposedBridge.hookAllMethods(notifListenerCls, "onNotificationRemoved", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            sNotificationListener = param.thisObject;
                            if (param.args.length > 0 && param.args[0] instanceof StatusBarNotification) {
                                broadcastRemovedNotification((StatusBarNotification) param.args[0]);
                            }
                        }
                    });
                    XposedBridge.log(TAG + "Hooked notification listener: " + clsName);
                }
            } catch (Throwable ignored) {}
        }

        // Fallback: NotificationEntryManager
        try {
            Class<?> nemCls = XposedHelpers.findClassIfExists("com.android.systemui.statusbar.notification.NotificationEntryManager", lpparam.classLoader);
            if (nemCls != null) {
                XposedBridge.hookAllMethods(nemCls, "addNotification", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.args.length > 0 && param.args[0] instanceof StatusBarNotification) {
                            broadcastNewNotification((StatusBarNotification) param.args[0], false);
                        }
                    }
                });
            }
        } catch (Throwable ignored) {}
    }

    private static void syncAllActiveNotifications(Object listener) {
        if (listener == null || sContext == null) return;
        try {
            StatusBarNotification[] sbns = (StatusBarNotification[]) XposedHelpers.callMethod(listener, "getActiveNotifications");
            if (sbns != null) {
                for (StatusBarNotification sbn : sbns) {
                    broadcastNewNotification(sbn, true);
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void broadcastNewNotification(StatusBarNotification sbn, boolean syncOnly) {
        if (sbn == null || sContext == null) return;
        try {
            Notification n = sbn.getNotification();
            if (n == null) return;
            // Ignore ongoing background service notifications and group summaries
            if ((n.flags & Notification.FLAG_ONGOING_EVENT) != 0) return;
            if ((n.flags & Notification.FLAG_FOREGROUND_SERVICE) != 0) return;
            if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
            if ("android".equals(sbn.getPackageName()) || "com.android.systemui".equals(sbn.getPackageName())) return;

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
            intent.putExtra("sync_only", syncOnly);
            sContext.sendBroadcast(intent);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "broadcastNewNotification error: " + t);
        }
    }

    private static void broadcastRemovedNotification(StatusBarNotification sbn) {
        if (sbn == null || sContext == null) return;
        try {
            Intent intent = new Intent(Constants.ACTION_REMOVE_NOTIFICATION);
            intent.setPackage("com.miui.home");
            intent.putExtra("key", sbn.getKey());
            intent.putExtra("pkg", sbn.getPackageName());
            sContext.sendBroadcast(intent);
        } catch (Throwable ignored) {}
    }

    private static void dismissSingleNotification(String key) {
        if (key == null || key.isEmpty()) return;
        try {
            if (sNotificationListener != null) {
                XposedHelpers.callMethod(sNotificationListener, "cancelNotification", key);
            }
        } catch (Throwable ignored) {}
    }

    private static void clearAllNotifications() {
        try {
            if (sNotificationListener != null) {
                XposedHelpers.callMethod(sNotificationListener, "cancelAllNotifications");
            }
        } catch (Throwable ignored) {}

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
        // 1. Android 14/15 native ScreenshotHelper (ScreenshotRequest.Builder)
        if (sContext != null) {
            try {
                Class<?> shCls = Class.forName("com.android.internal.util.ScreenshotHelper");
                Object helper = shCls.getConstructor(Context.class).newInstance(sContext);
                try {
                    Class<?> reqBuilderCls = Class.forName("com.android.internal.util.ScreenshotRequest$Builder");
                    Object builder = reqBuilderCls.getConstructor(int.class, int.class).newInstance(1, 0);
                    Object request = reqBuilderCls.getMethod("build").invoke(builder);
                    Class<?> reqCls = Class.forName("com.android.internal.util.ScreenshotRequest");
                    Method takeReq = shCls.getMethod("takeScreenshot", reqCls, Handler.class, Consumer.class);
                    takeReq.invoke(helper, request, new Handler(Looper.getMainLooper()), null);
                    return;
                } catch (Throwable ignored) {
                    Method takeScreenshot = shCls.getMethod("takeScreenshot", int.class, Handler.class, Consumer.class);
                    takeScreenshot.invoke(helper, 1, new Handler(Looper.getMainLooper()), null);
                    return;
                }
            } catch (Throwable ignored) {}

            // 2. Native MIUI / HyperOS ScreenShotService (it is a Service, not a BroadcastReceiver!)
            try {
                Intent scIntent = new Intent("com.miui.screenshot.action.SCREENSHOT");
                scIntent.setClassName("com.miui.screenshot", "com.miui.gallery.editor.photo.screen.home.ScreenShotService");
                if (sContext.startService(scIntent) != null) {
                    return;
                }
            } catch (Throwable ignored) {}
        }

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
