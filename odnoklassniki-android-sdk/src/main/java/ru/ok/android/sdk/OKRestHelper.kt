package ru.ok.android.sdk

import android.annotation.SuppressLint
import java.io.IOException
import java.util.EnumSet

import org.json.JSONException
import org.json.JSONObject

import android.content.Context
import android.provider.Settings
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import android.telephony.TelephonyManager
import android.util.Log
import ru.ok.android.sdk.util.StatsBuilder

class OKRestHelper(private val ok: Odnoklassniki) {

    /**
     * sdk.init Initializes SDK sessions required for some SDK methods
     *
     * @param context context
     * @return true if session initialization succeeded or a token already exists
     */
    @Throws(IOException::class)
    fun sdkInit(context: Context): Boolean {
        if (!ok.sdkToken.isNullOrEmpty()) return true

        val sessionData = JSONObject()
        try {
            sessionData.put("version", 2)
            sessionData.put("device_id", getDeviceId(context))
            sessionData.put("client_version", "android_sdk_1")
            sessionData.put("client_type", "SDK_ANDROID")
        } catch (e: JSONException) {
            Log.e(LOG_TAG, "Creating sdk.init request: " + e.message, e)
            return false
        }

        val params = mapOf("session_data" to sessionData.toString())
        val resp = ok.request("sdk.init", params, EnumSet.of(OkRequestMode.UNSIGNED))
        try {
            val json = JSONObject(resp)
            if (json.has("session_key")) {
                ok.sdkToken = json.getString("session_key")
                if (!ok.sdkToken.isNullOrEmpty()) {
                    TokenStore.storeSdkToken(context, ok.sdkToken!!)
                    return true
                }
            }
        } catch (e: JSONException) {
            Log.e(LOG_TAG, "Parsing sdk.init response: " + e.message, e)
        }

        return false
    }

    protected fun notifyListener(listener: OkListener, response: String?): Boolean {
        try {
            val json = JSONObject(response)
            if (json.has(PARAM_ERROR_MSG)) {
                val error = json.optString(PARAM_ERROR_MSG, json.toString())
                ok.notifyFailed(listener, error)
                return false
            }
            ok.notifySuccess(listener, json)
        } catch (e: JSONException) {
            val json = JSONObject()
            try {
                json.put("reponse", response)
            } catch (ignore: JSONException) {
            }

            // some methods may return non-json results, just push them through
            ok.notifySuccess(listener, json)
        }
        return true
    }

    @SuppressLint("MissingPermission", "HardwareIds")
    @NonNull
    protected fun getDeviceId(context: Context): String {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager?
        var id1: String? = null
        try {
            @Suppress("DEPRECATION")
            id1 = telephonyManager?.deviceId
        } catch (e: SecurityException) {
            Log.d(LOG_TAG, e.message, e)
        }

        val id2 = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        return "${id1}_$id2"
    }

    /**
     * sdk.reportStats reporting custom statistics to OK platform
     *
     * @param builder  statistics builder
     * @param listener callback
     */
    @Throws(JSONException::class, IOException::class)
    fun sdkReportStats(builder: StatsBuilder, listener: OkListener) {
        val params = mapOf("stats" to builder.build().toString())
        val response = ok.request("sdk.reportStats", params, OkRequestMode.DEFAULT)
        notifyListener(listener, response)
    }

    /**
     * Retrieves endpoints for OK platform (async).
     * On success, sets them via [Odnoklassniki.setBasePlatformUrls]
     *
     * @param listener callback
     */
    fun sdkGetEndpoints(@Nullable listener: OkListener) {
        ok.requestAsync("sdk.getEndpoints", null, EnumSet.of(OkRequestMode.SIGNED), object : OkListener {
            override fun onSuccess(json: JSONObject) {
                val endpoints = json.optJSONObject("endpoints")
                if (endpoints != null) {
                    val widgetEndpoint = endpoints.optString("widgets", REMOTE_WIDGETS)
                    val apiEndpoint = endpoints.optString("api", REMOTE_API)
                    ok.setBasePlatformUrls(apiEndpoint, widgetEndpoint)
                }
                ok.notifySuccess(listener, json)
            }

            override fun onError(error: String?) {
                ok.notifyFailed(listener, error)
            }
        })
    }
}