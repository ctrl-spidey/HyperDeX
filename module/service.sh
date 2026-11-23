#!/system/bin/sh
MODDIR=${0%/*}

# Wait until boot completes
while [ "$(getprop sys.boot_completed)" != "1" ]; do
    sleep 2
done

# Ensure LSPosed module is enabled and scopes attached
if [ -x "/data/adb/lspd/cli" ]; then
    /data/adb/lspd/cli module enable com.spiderman4u.hyperdex >/dev/null 2>&1
    /data/adb/lspd/cli scope add com.spiderman4u.hyperdex com.miui.home >/dev/null 2>&1
    /data/adb/lspd/cli scope add com.spiderman4u.hyperdex com.android.systemui >/dev/null 2>&1
fi
