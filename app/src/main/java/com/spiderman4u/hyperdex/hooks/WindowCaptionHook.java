package com.spiderman4u.hyperdex.hooks;

import android.content.Context;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.spiderman4u.hyperdex.config.ConfigManager;

import java.lang.reflect.Field;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class WindowCaptionHook {

    private static final String TAG = "[HyperDeX-Caption] ";

    public static void init(XC_LoadPackage.LoadPackageParam lpparam) {
        hookMiuiCaptionContainerView(lpparam);
        hookAppHeaderViewHolder(lpparam);
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

                    if (!ConfigManager.isDeXModeEnabled(context) || !ConfigManager.isDeXButtonsEnabled(context)) {
                        return;
                    }

                    // Ensure button container is visible and styled
                    LinearLayout container = (LinearLayout) XposedHelpers.getObjectField(param.thisObject, "mCaptionButtonContainer");
                    if (container != null) {
                        container.setVisibility(View.VISIBLE);
                        // Iterate through caption buttons to ensure all control actions are enabled
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

                    if (!ConfigManager.isDeXModeEnabled(context) || !ConfigManager.isDeXButtonsEnabled(context)) {
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
    private static <T> T getField(Object target, String fieldName, Class<T> type) {
        try {
            Field f = XposedHelpers.findFieldIfExists(target.getClass(), fieldName);
            if (f != null) {
                f.setAccessible(true);
                Object val = f.get(target);
                if (type.isInstance(val)) {
                    return (T) val;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
