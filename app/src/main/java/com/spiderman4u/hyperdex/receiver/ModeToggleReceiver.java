package com.spiderman4u.hyperdex.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.spiderman4u.hyperdex.config.ConfigManager;
import com.spiderman4u.hyperdex.config.Constants;

public class ModeToggleReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        if (Constants.ACTION_TOGGLE_MODE.equals(intent.getAction())) {
            boolean current = ConfigManager.isDeXModeEnabled(context);
            ConfigManager.setDeXModeEnabled(context, !current);
        } else if (Constants.ACTION_SET_MODE.equals(intent.getAction())) {
            boolean target = intent.getBooleanExtra(Constants.EXTRA_DEX_MODE, true);
            ConfigManager.setDeXModeEnabled(context, target);
        }
    }
}
