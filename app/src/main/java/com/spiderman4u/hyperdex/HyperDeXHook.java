package com.spiderman4u.hyperdex;

import com.spiderman4u.hyperdex.config.Constants;
import com.spiderman4u.hyperdex.hooks.LauncherDockHook;
import com.spiderman4u.hyperdex.hooks.SystemUIHook;
import com.spiderman4u.hyperdex.hooks.WindowCaptionHook;

import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_InitPackageResources;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HyperDeXHook implements IXposedHookLoadPackage, IXposedHookInitPackageResources {

    private static final String TAG = "[HyperDeX] ";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (lpparam == null || lpparam.packageName == null) return;

        if (Constants.PKG_LAUNCHER.equals(lpparam.packageName)) {
            XposedBridge.log(TAG + "Hooking HyperOS Launcher: " + lpparam.packageName);
            try {
                LauncherDockHook.init(lpparam);
            } catch (Throwable t) {
                XposedBridge.log(TAG + "Error initializing LauncherDockHook: " + t);
            }
        } else if (Constants.PKG_SYSTEMUI.equals(lpparam.packageName)) {
            XposedBridge.log(TAG + "Hooking SystemUI: " + lpparam.packageName);
            try {
                WindowCaptionHook.init(lpparam);
            } catch (Throwable t) {
                XposedBridge.log(TAG + "Error initializing WindowCaptionHook: " + t);
            }
            try {
                SystemUIHook.init(lpparam);
            } catch (Throwable t) {
                XposedBridge.log(TAG + "Error initializing SystemUIHook: " + t);
            }
        }
    }

    @Override
    public void handleInitPackageResources(XC_InitPackageResources.InitPackageResourcesParam resparam) throws Throwable {
        // Resource hooks if needed
    }
}
