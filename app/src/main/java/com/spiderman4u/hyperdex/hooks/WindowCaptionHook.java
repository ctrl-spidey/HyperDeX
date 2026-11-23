package com.spiderman4u.hyperdex.hooks;

import android.content.Context;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.spiderman4u.hyperdex.R;
import com.spiderman4u.hyperdex.config.ConfigManager;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class WindowCaptionHook {

    private static final String TAG = "[HyperDeX-Caption] ";
    private static final List<WeakReference<View>> sStatusBarViews = new ArrayList<>();
    private static boolean sStatusBarObserverRegistered = false;

    public static void init(XC_LoadPackage.LoadPackageParam lpparam) {
        hookMiuiCaptionContainerView(lpparam);
        hookAppHeaderViewHolder(lpparam);
        hookStatusBar(lpparam);
    }

    private static void hookMiuiCaptionContainerView(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> captionViewCls = XposedHelpers.findClassIfExists(
                    "com.android.wm.shell.multitasking.miuimultiwinswitch.miuiwindowdecor.handlemenu.MiuiCaptionContainerView",
                    lpparam.classLoader
            );

            if (captionViewCls == null) {
                XposedBridge.log(TAG + "Class MiuiCaptionContainerView not found");
                return;
            }

            XposedBridge.hookAllMethods(captionViewCls, "initButtonAndBg", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (!(param.thisObject instanceof View)) return;
                    View captionView = (View) param.thisObject;
                    Context context = captionView.getContext();

                    if (!ConfigManager.shouldApplyDeXTaskbar(context)) {
                        return;
                    }

                    // 1. Dark sleek PC titlebar background
                    GradientDrawable bg = new GradientDrawable();
                    bg.setColor(Color.parseColor("#DE18191C"));
                    bg.setCornerRadii(new float[]{14, 14, 14, 14, 0, 0, 0, 0});
                    captionView.setBackground(bg);

                    // 2. Add App Title text on the left if not present
                    if (captionView instanceof ViewGroup) {
                        ViewGroup group = (ViewGroup) captionView;
                        View existingTitle = group.findViewById(R.id.dex_window_title);
                        if (existingTitle == null) {
                            try {
                                CharSequence appName = (CharSequence) XposedHelpers.getObjectField(param.thisObject, "mAppName");
                                if (appName != null && appName.length() > 0) {
                                    TextView tvTitle = new TextView(context);
                                    tvTitle.setId(R.id.dex_window_title);
                                    tvTitle.setText(appName);
                                    tvTitle.setTextColor(Color.WHITE);
                                    tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                                    tvTitle.setTypeface(null, Typeface.BOLD);
                                    tvTitle.setPadding(dp(context, 12), 0, 0, 0);

                                    LinearLayout.LayoutParams lpTitle = new LinearLayout.LayoutParams(
                                            ViewGroup.LayoutParams.WRAP_CONTENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                    );
                                    lpTitle.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
                                    group.addView(tvTitle, 0, lpTitle);
                                }
                            } catch (Throwable ignored) {}
                        }
                    }

                    // 3. Ensure button container is visible and styled
                    LinearLayout container = (LinearLayout) XposedHelpers.getObjectField(param.thisObject, "mCaptionButtonContainer");
                    if (container != null) {
                        container.setVisibility(View.VISIBLE);
                        for (int i = 0; i < container.getChildCount(); i++) {
                            View child = container.getChildAt(i);
                            if (child != null) {
                                child.setVisibility(View.VISIBLE);
                                child.setAlpha(1.0f);
                            }
                        }
                    }
                }
            });

            XposedBridge.log(TAG + "MiuiCaptionContainerView hooked successfully");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed hooking MiuiCaptionContainerView: " + t);
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

                // 1. Hook onAttachedToWindow
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

                // 2. Hook onMeasure - suppress dimensions when DeX active
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
                            }
                        }
                    }
                });

                // 3. Hook draw and onDraw - block canvas rendering
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

                XposedBridge.log(TAG + "StatusBar class hooked: " + className);
            } catch (Throwable t) {
                XposedBridge.log(TAG + "Failed hooking statusBar class " + className + ": " + t);
            }
        }

        // Hook StatusBarWindowControllerImpl.apply to keep status bar window hidden
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
                XposedBridge.log(TAG + "StatusBarWindowControllerImpl.apply hooked");
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed hooking StatusBarWindowControllerImpl: " + t);
        }
    }

    private static synchronized void registerStatusBarView(View v) {
        if (v == null) return;
        boolean exists = false;
        for (WeakReference<View> ref : sStatusBarViews) {
            if (ref.get() == v) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            sStatusBarViews.add(new WeakReference<>(v));
        }
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

                    if (!ConfigManager.shouldApplyDeXTaskbar(context)) {
                        return;
                    }

                    ImageButton minBtn = getField(holder, "minimizeWindowButton", ImageButton.class);
                    if (minBtn != null) {
                        minBtn.setVisibility(View.VISIBLE);
                        minBtn.setAlpha(1.0f);
                    }

                    ImageButton maxBtn = getField(holder, "maximizeWindowButton", ImageButton.class);
                    if (maxBtn != null) {
                        maxBtn.setVisibility(View.VISIBLE);
                        maxBtn.setAlpha(1.0f);
                    }

                    ImageButton closeBtn = getField(holder, "closeWindowButton", ImageButton.class);
                    if (closeBtn != null) {
                        closeBtn.setVisibility(View.VISIBLE);
                        closeBtn.setAlpha(1.0f);
                    }
                }
            });

            XposedBridge.log(TAG + "AppHeaderViewHolder hooked successfully");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed hooking AppHeaderViewHolder: " + t);
        }
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

    private static int dp(Context context, int val) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, val, context.getResources().getDisplayMetrics());
    }
}
