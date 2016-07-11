package ru.ok.android.sdk;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

class TokenStore {
    private static final String PREF_ACCESS_TOKEN = "acctkn";
    private static final String PREF_SESSION_SECRET_KEY = "ssk";
    private static final String PREF_SDK_TOKEN = "ok_sdk_tkn";

    static void store(final Context context, final String accessToken, final String sessionSecretKey) {
        final SharedPreferences prefs = getPreferences(context);
        final Editor editor = prefs.edit();
        editor.putString(PREF_ACCESS_TOKEN, accessToken);
        editor.putString(PREF_SESSION_SECRET_KEY, sessionSecretKey);
        editor.apply();
    }

    static void removeStoredTokens(final Context context) {
        final SharedPreferences prefs = getPreferences(context);
        final Editor editor = prefs.edit();
        editor.remove(PREF_ACCESS_TOKEN);
        editor.remove(PREF_SESSION_SECRET_KEY);
        editor.remove(PREF_SDK_TOKEN);
        editor.apply();
    }

    static String getStoredAccessToken(final Context context) {
        final SharedPreferences prefs = getPreferences(context);
        return prefs.getString(PREF_ACCESS_TOKEN, null);
    }

    static String getStoredSessionSecretKey(final Context context) {
        final SharedPreferences prefs = getPreferences(context);
        return prefs.getString(PREF_SESSION_SECRET_KEY, null);
    }

    static SharedPreferences getPreferences(final Context context) {
        return context.getSharedPreferences(Shared.PREFERENCES_FILE, Context.MODE_PRIVATE);
    }

    static void storeSdkToken(Context context, String sdkToken) {
        SharedPreferences prefs = getPreferences(context);
        Editor editor = prefs.edit();
        editor.putString(PREF_SDK_TOKEN, sdkToken);
        editor.apply();
    }

    static String getSdkToken(Context context) {
        final SharedPreferences prefs = getPreferences(context);
        return prefs.getString(PREF_SDK_TOKEN, null);
    }
}
