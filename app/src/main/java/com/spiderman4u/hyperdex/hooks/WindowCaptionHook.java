package com.spiderman4u.hyperdex.hooks;

import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
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
        hookAppHeaderViewHolder(lpparam);
        hookDesktopModeDecoration(lpparam);
    }

    private static void hookAppHeaderViewHolder(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> holderCls = XposedHelpers.findClassIfExists(
                    "com.android.wm.shell.windowdecor.viewholder.AppHeaderViewHolder",
                    lpparam.classLoader
            );

            if (holderCls == null) {
                XposedBridge.log(TAG + "Class AppHeaderViewHolder not found in SystemUI");
                return;
            }

            // Hook bindData to enforce classic DeX Minimize, Maximize, and Close buttons
            XposedBridge.hookAllMethods(holderCls, "bindData", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (!ConfigManager.isDeXModeEnabled(null) || !ConfigManager.isDeXButtonsEnabled(null)) {
                        return; // Retain vanilla HyperOS workstation window decor
                    }

                    Object holder = param.thisObject;

                    // 1. Force Minimize Button Visible
                    ImageButton minBtn = getField(holder, "minimizeWindowButton", ImageButton.class);
                    if (minBtn != null) {
                        minBtn.setVisibility(View.VISIBLE);
                        minBtn.setAlpha(1.0f);
                    }

                    // 2. Force Maximize Button Visible
                    ImageButton maxBtn = getField(holder, "maximizeWindowButton", ImageButton.class);
                    if (maxBtn != null) {
                        maxBtn.setVisibility(View.VISIBLE);
                        maxBtn.setAlpha(1.0f);
                    }

                    // 3. Force Close Button Visible
                    ImageButton closeBtn = getField(holder, "closeWindowButton", ImageButton.class);
                    if (closeBtn != null) {
                        closeBtn.setVisibility(View.VISIBLE);
                        closeBtn.setAlpha(1.0f);
                    }

                    // 4. Optionally hide the 3-dot openMenuButton in favor of the clean DeX titlebar buttons
                    View menuBtn = getField(holder, "openMenuButton", View.class);
                    if (menuBtn != null) {
                        menuBtn.setVisibility(View.GONE);
                    }

                    // 5. App Name & Icon on the top left
                    TextView appName = getField(holder, "appNameTextView", TextView.class);
                    if (appName != null) {
                        appName.setVisibility(View.VISIBLE);
                    }

                    ImageView appIcon = getField(holder, "appIconImageView", ImageView.class);
                    if (appIcon != null) {
                        appIcon.setVisibility(View.VISIBLE);
                    }
                }
            });

            XposedBridge.log(TAG + "AppHeaderViewHolder hooked successfully");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed hooking AppHeaderViewHolder: " + t);
        }
    }

    private static void hookDesktopModeDecoration(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> decorCls = XposedHelpers.findClassIfExists(
                    "com.android.wm.shell.windowdecor.DesktopModeWindowDecoration",
                    lpparam.classLoader
            );

            if (decorCls == null) return;

            XposedBridge.hookAllMethods(decorCls, "isCaptionVisible", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (ConfigManager.isDeXModeEnabled(null)) {
                        param.setResult(true);
                    }
                }
            });

            XposedBridge.log(TAG + "DesktopModeWindowDecoration hooked successfully");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed hooking DesktopModeWindowDecoration: " + t);
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
