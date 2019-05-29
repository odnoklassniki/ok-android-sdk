package ru.ok.android.sdk

import android.content.Context
import android.content.SharedPreferences

private const val PREF_ACCESS_TOKEN = "acctkn"
private const val PREF_SESSION_SECRET_KEY = "ssk"
private const val PREF_SDK_TOKEN = "ok_sdk_tkn"
private const val PREF_APP_ID = "app_id"
private const val PREF_APP_KEY = "app_key"

internal object TokenStore {

    @JvmStatic
    fun store(context: Context, accessToken: String, sessionSecretKey: String) {
        val prefs = getPreferences(context)
        val editor = prefs.edit()
        editor.putString(PREF_ACCESS_TOKEN, accessToken)
        editor.putString(PREF_SESSION_SECRET_KEY, sessionSecretKey)
        editor.apply()
    }

    @JvmStatic
    fun removeStoredTokens(context: Context) {
        val editor = getPreferences(context).edit()
        editor.remove(PREF_ACCESS_TOKEN)
        editor.remove(PREF_SESSION_SECRET_KEY)
        editor.remove(PREF_SDK_TOKEN)
        editor.apply()
    }

    @JvmStatic
    fun getStoredAccessToken(context: Context): String? =
            getPreferences(context).getString(PREF_ACCESS_TOKEN, null)

    @JvmStatic
    fun getStoredSessionSecretKey(context: Context): String? =
            getPreferences(context).getString(PREF_SESSION_SECRET_KEY, null)

    private fun getPreferences(context: Context): SharedPreferences =
            context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)

    @JvmStatic
    fun storeSdkToken(context: Context, sdkToken: String) {
        val editor = getPreferences(context).edit()
        editor.putString(PREF_SDK_TOKEN, sdkToken)
        editor.apply()
    }

    @JvmStatic
    fun getSdkToken(context: Context): String? =
            getPreferences(context).getString(PREF_SDK_TOKEN, null)

    fun getAppInfo(context: Context): Pair<String?, String?> = getPreferences(context).let {
        Pair(it.getString(PREF_APP_ID, null), it.getString(PREF_APP_KEY, null))
    }

    fun setAppInfo(context: Context, id: String, key: String) {
        getPreferences(context).edit()
                .putString(PREF_APP_ID, id)
                .putString(PREF_APP_KEY, key)
                .apply()
    }

}
