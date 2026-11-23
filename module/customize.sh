SKIPUNZIP=1

ui_print "****************************************"
ui_print "       HyperDeX - Tablet Edition        "
ui_print " Samsung DeX & Dual Workstation Engine  "
ui_print "      By Spider & Antigravity           "
ui_print "****************************************"

# Check root environment
if [ "$KSU" = "true" ]; then
    ui_print "- Environment: KernelSU (ksu)"
elif [ "$APATCH" = "true" ]; then
    ui_print "- Environment: APatch"
elif [ "$MAGISK_VER" ]; then
    ui_print "- Environment: Magisk ($MAGISK_VER)"
else
    ui_print "- Environment: Standard Root"
fi

# Extract module files
ui_print "- Extracting module files..."
unzip -o "$ZIPFILE" -x 'META-INF/*' -d "$MODPATH" >/dev/null 2>&1

# Set permissions
set_perm_recursive "$MODPATH" 0 0 0755 0644
set_perm "$MODPATH/service.sh" 0 0 0755
set_perm "$MODPATH/post-fs-data.sh" 0 0 0755

# Auto-install APK if bundled
APK_SRC="$MODPATH/app/HyperDeX.apk"
if [ -f "$APK_SRC" ]; then
    ui_print "- Installing HyperDeX companion app..."
    pm install -r "$APK_SRC" >/dev/null 2>&1
fi

# Enable in LSPosed if CLI is available
if [ -x "/data/adb/lspd/cli" ]; then
    ui_print "- Configuring LSPosed scope..."
    /data/adb/lspd/cli module enable com.spiderman4u.hyperdex >/dev/null 2>&1
    /data/adb/lspd/cli scope add com.spiderman4u.hyperdex com.miui.home >/dev/null 2>&1
    /data/adb/lspd/cli scope add com.spiderman4u.hyperdex com.android.systemui >/dev/null 2>&1
fi

ui_print " "
ui_print "✓ HyperDeX installed successfully!"
ui_print "✓ Enable the module in LSPosed and reboot."
ui_print " "
