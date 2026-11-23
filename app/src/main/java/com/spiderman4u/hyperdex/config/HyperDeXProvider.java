package com.spiderman4u.hyperdex.config;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class HyperDeXProvider extends ContentProvider {

    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    @Override
    public Bundle call(@NonNull String method, @Nullable String arg, @Nullable Bundle extras) {
        Context context = getContext();
        if (context == null) return null;

        SharedPreferences prefs = context.getSharedPreferences(Constants.PREF_FILE, Context.MODE_PRIVATE);

        if (Constants.METHOD_GET_PREFS.equals(method)) {
            Bundle result = new Bundle();
            result.putBoolean(Constants.PREF_DEX_MODE_ENABLED, prefs.getBoolean(Constants.PREF_DEX_MODE_ENABLED, true));
            result.putBoolean(Constants.PREF_EDGE_TO_EDGE, prefs.getBoolean(Constants.PREF_EDGE_TO_EDGE, true));
            result.putBoolean(Constants.PREF_START_MENU, prefs.getBoolean(Constants.PREF_START_MENU, true));
            result.putBoolean(Constants.PREF_DEX_BUTTONS, prefs.getBoolean(Constants.PREF_DEX_BUTTONS, true));
            return result;
        } else if (Constants.METHOD_SET_PREF.equals(method) && arg != null && extras != null) {
            SharedPreferences.Editor editor = prefs.edit();
            if (extras.containsKey("boolean_val")) {
                editor.putBoolean(arg, extras.getBoolean("boolean_val"));
            }
            editor.apply();
            Bundle res = new Bundle();
            res.putBoolean("success", true);
            return res;
        }

        return super.call(method, arg, extras);
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }
}
