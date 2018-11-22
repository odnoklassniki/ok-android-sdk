package ru.ok.android.sdk

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.AsyncTask
import android.os.Build
import android.text.TextUtils
import android.webkit.CookieManager
import android.webkit.CookieSyncManager
import androidx.annotation.Nullable
import org.json.JSONException
import org.json.JSONObject
import ru.ok.android.sdk.util.*
import java.io.IOException
import java.util.*

open class Odnoklassniki protected constructor(private val context: Context, protected val appId: String, protected val appKey: String) {
    var mAccessToken: String? = TokenStore.getStoredAccessToken(context)
    var mSessionSecretKey: String? = TokenStore.getStoredSessionSecretKey(context)
    var sdkToken: String? = TokenStore.getSdkToken(context)
    var apiBaseUrl = Shared.REMOTE_API
    var connectBaseUrl = Shared.REMOTE_WIDGETS
    protected val okPayment: OkPayment = OkPayment(context)

    /** Set true if wish to support logging in via OK debug application installed instead of release one */
    protected var allowDebugOkSso = false
    /** Widgets ask user to retry the action on error (set false for instant error callback) */
    protected var allowWidgetRetry = true

    /**
     * Starts user authorization
     *
     * @param redirectUri the URI to which the access_token will be redirected
     * @param authType    selected auth type
     * @param scopes      [OkScope] - application request permissions as per [OkScope].
     * @see OkAuthType
     */
    fun requestAuthorization(activity: Activity, @Nullable redirectUri: String,
                             authType: OkAuthType, vararg scopes: String) {
        val intent = Intent(activity, OkAuthActivity::class.java)
        intent.putExtra(Shared.PARAM_CLIENT_ID, appId)
        intent.putExtra(Shared.PARAM_APP_KEY, appKey)
        intent.putExtra(Shared.PARAM_REDIRECT_URI, redirectUri)
        intent.putExtra(Shared.PARAM_AUTH_TYPE, authType)
        intent.putExtra(Shared.PARAM_SCOPES, scopes)
        intent.putExtra(Shared.PARAM_ALLOW_DEBUG_OK_SSO, allowDebugOkSso)
        activity.startActivityForResult(intent, Shared.OK_AUTH_REQUEST_CODE)
    }

    fun isActivityRequestOAuth(requestCode: Int): Boolean {
        return requestCode == Shared.OK_AUTH_REQUEST_CODE
    }

    fun onAuthActivityResult(request: Int, result: Int, @Nullable intent: Intent?, listener: OkListener): Boolean {
        if (isActivityRequestOAuth(request)) {
            if (intent == null) {
                val json = JSONObject()
                try {
                    json.put(Shared.PARAM_ACTIVITY_RESULT, result)
                } catch (ignore: JSONException) {
                }

                listener.onError(json.toString())
            } else {
                val accessToken = intent.getStringExtra(Shared.PARAM_ACCESS_TOKEN)
                if (accessToken == null) {
                    val error = intent.getStringExtra(Shared.PARAM_ERROR)
                    if (result == RESULT_CANCELLED && listener is OkAuthListener) {
                        listener.onCancel(error)
                    } else {
                        listener.onError(error)
                    }
                } else {
                    val sessionSecretKey = intent.getStringExtra(Shared.PARAM_SESSION_SECRET_KEY)
                    val refreshToken = intent.getStringExtra(Shared.PARAM_REFRESH_TOKEN)
                    val expiresIn = intent.getLongExtra(Shared.PARAM_EXPIRES_IN, 0)
                    mAccessToken = accessToken
                    mSessionSecretKey = sessionSecretKey ?: refreshToken
                    val json = JSONObject()
                    try {
                        json.put(Shared.PARAM_ACCESS_TOKEN, mAccessToken)
                        json.put(Shared.PARAM_SESSION_SECRET_KEY, mSessionSecretKey)
                        if (expiresIn > 0) {
                            json.put(Shared.PARAM_EXPIRES_IN, expiresIn)
                        }
                    } catch (ignore: JSONException) {
                    }

                    onValidSessionAppeared()
                    listener.onSuccess(json)
                }
            }

            return true
        }
        return false
    }

    fun isActivityRequestPost(requestCode: Int): Boolean = requestCode == Shared.OK_POSTING_REQUEST_CODE

    fun isActivityRequestInvite(requestCode: Int): Boolean = requestCode == Shared.OK_INVITING_REQUEST_CODE

    fun isActivityRequestSuggest(requestCode: Int): Boolean = requestCode == Shared.OK_SUGGESTING_REQUEST_CODE

    fun isActivityRequestViral(request: Int): Boolean =
            isActivityRequestPost(request) || isActivityRequestInvite(request) || isActivityRequestSuggest(request)

    fun onActivityResultResult(request: Int, result: Int, @Nullable intent: Intent?, listener: OkListener): Boolean {
        if (isActivityRequestViral(request)) {
            if (intent == null) {
                val json = JSONObject()
                try {
                    json.put(Shared.PARAM_ACTIVITY_RESULT, result)
                } catch (ignore: JSONException) {
                }

                listener.onError(json.toString())
            } else {
                if (intent.hasExtra(Shared.PARAM_ERROR)) {
                    listener.onError(intent.getStringExtra(Shared.PARAM_ERROR))
                } else {
                    try {
                        listener.onSuccess(JSONObject(intent.getStringExtra(Shared.PARAM_RESULT)))
                    } catch (e: JSONException) {
                        listener.onError(intent.getStringExtra(Shared.PARAM_RESULT))
                    }
                }
            }
            return true
        }
        return false
    }

    fun notifyFailed(listener: OkListener?, error: String?) {
        if (listener != null) {
            OkThreadUtil.executeOnMain { listener.onError(error) }
        }
    }

    fun notifySuccess(listener: OkListener?, json: JSONObject) {
        if (listener != null) {
            OkThreadUtil.executeOnMain { listener.onSuccess(json) }
        }
    }

    /**
     * Performs a REST API request and gets result as a string<br></br>
     * <br></br>
     * Note that a method is synchronous so should not be called from UI thread<br></br>
     *
     * @param method REST method
     * @param params request params
     * @param mode   request mode
     * @return query result
     * @see OkRequestMode.DEFAULT OkRequestMode.DEFAULT default request mode
     */
    @Throws(IOException::class)
    fun request(method: String, params: Map<String, String>? = null, mode: EnumSet<OkRequestMode> = OkRequestMode.DEFAULT): String? {
        if (TextUtils.isEmpty(method)) throw IllegalArgumentException(context.getString(R.string.api_method_cant_be_empty))
        val requestParams = TreeMap<String, String>()
        if (!params.isNullOrEmpty()) requestParams.putAll(params)
        requestParams[Shared.PARAM_APP_KEY] = appKey
        requestParams[Shared.PARAM_METHOD] = method
        if (!mode.contains(OkRequestMode.NO_PLATFORM_REPORTING)) requestParams[Shared.PARAM_PLATFORM] = Shared.APP_PLATFORM
        if (mode.contains(OkRequestMode.SDK_SESSION)) {
            if (sdkToken.isNullOrEmpty()) throw IllegalArgumentException("SDK token is required for method call, have not forget to call sdkInit?")
            requestParams[Shared.PARAM_SDK_TOKEN] = sdkToken!!
        }
        if (mode.contains(OkRequestMode.SIGNED) && !mAccessToken.isNullOrEmpty()) {
            signParameters(requestParams)
            requestParams[Shared.PARAM_ACCESS_TOKEN] = mAccessToken!!
        }
        return OkRequestUtil.executeRequest(requestParams)
    }

    /**
     * Performs a REST API request and gets result via a listener callback
     * <br></br>
     * Note that a method is synchronous so should not be called from UI thread<br></br>
     * <br></br>
     * Note that some methods do not return JSON objects (there are few that returns either arrays [] or primitives
     * so cannot be parsed directly. In such case, a JSON is created {"result": responseString} and success result
     * notified
     *
     * @param method   REST method
     * @param params   request params
     * @param mode     request mode
     * @param listener listener
     * @return true if method succeeded
     * @see OkRequestMode.DEFAULT OkRequestMode.DEFAULT default request mode
     */
    fun request(method: String, params: Map<String, String>? = null, mode: EnumSet<OkRequestMode> = OkRequestMode.DEFAULT, listener: OkListener): Boolean {
        val response: String?
        try {
            response = request(method, params, mode)
        } catch (e: IOException) {
            notifyFailed(listener, toJson(KEY_EXCEPTION, e.message).toString())
            return false
        }

        val json: JSONObject
        try {
            json = JSONObject(response)
        } catch (e: JSONException) {
            // assume the result is correct and wrap with simple JSON
            notifySuccess(listener, toJson(KEY_RESULT, response))
            return true
        }

        return if (json.has(Shared.PARAM_ERROR_MSG)) {
            notifyFailed(listener, json.optString(Shared.PARAM_ERROR_MSG))
            false
        } else {
            notifySuccess(listener, json)
            true
        }
    }

    /**
     * Performs a REST API request and gets result via a listener callback
     * <br></br>
     * This is an async wrapper for [.request]
     *
     * @param method   REST method
     * @param params   request params
     * @param mode     request mode
     * @param listener listener
     * @return AsyncTask background task for the request
     */
    fun requestAsync(method: String, params: Map<String, String>? = null, mode: EnumSet<OkRequestMode> = OkRequestMode.DEFAULT, listener: OkListener): AsyncTask<Void, Void, Void> {
        @SuppressLint("StaticFieldLeak")
        val task = object : AsyncTask<Void, Void, Void>() {
            override fun doInBackground(vararg parameters: Void): Void? {
                request(method, params, mode, listener)
                return null
            }
        }
        return task.execute()
    }

    /**
     * Check if access token is available (can be used to check if previously used access token and refresh token was successfully loaded from the storage).
     * Also check is it valid with method call
     */
    fun checkValidTokens(listener: OkListener) {
        if (mAccessToken == null || mSessionSecretKey == null) {
            notifyFailed(listener, context.getString(R.string.no_valid_token))
            return
        }

        Thread(Runnable {
            try {
                val response = request("users.getLoggedInUser")

                if (response != null && response.length > 2 && TextUtils.isDigitsOnly(response.substring(1, response.length - 1))) {
                    val json = JSONObject()
                    try {
                        json.put(Shared.PARAM_ACCESS_TOKEN, mAccessToken)
                        json.put(Shared.PARAM_SESSION_SECRET_KEY, mSessionSecretKey)
                        json.put(Shared.PARAM_LOGGED_IN_USER, response)
                    } catch (ignore: JSONException) {
                    }

                    onValidSessionAppeared()
                    notifySuccess(listener, json)
                } else {
                    try {
                        val json = JSONObject(response)
                        if (json.has(Shared.PARAM_ERROR_MSG)) {
                            notifyFailed(listener, json.getString(Shared.PARAM_ERROR_MSG))
                            return@Runnable
                        }
                    } catch (ignore: JSONException) {
                    }

                    notifyFailed(listener, response)
                }
            } catch (e: IOException) {
                notifyFailed(listener, e.message)
            }
        }).start()
    }

    /**
     * Call an API posting widget
     *
     * @param attachment      - json with publishing attachment
     * @param userTextEnabled - ability to enable user comment
     * @param args            widget arguments as specified in documentation
     */
    fun performPosting(activity: Activity, attachment: String, userTextEnabled: Boolean, args: HashMap<String, String>? = null) {
        val intent = Intent(activity, OkPostingActivity::class.java)
        intent.putExtra(Shared.PARAM_APP_ID, appId)
        intent.putExtra(Shared.PARAM_ATTACHMENT, attachment)
        intent.putExtra(Shared.PARAM_ACCESS_TOKEN, mAccessToken)
        intent.putExtra(Shared.PARAM_WIDGET_ARGS, args)
        intent.putExtra(Shared.PARAM_WIDGET_RETRY_ALLOWED, allowWidgetRetry)
        intent.putExtra(Shared.PARAM_SESSION_SECRET_KEY, mSessionSecretKey)
        intent.putExtra(Shared.PARAM_USER_TEXT_ENABLE, userTextEnabled)
        activity.startActivityForResult(intent, Shared.OK_POSTING_REQUEST_CODE)
    }

    /**
     * Calls application invite widget
     *
     * @param args widget arguments as specified in documentation
     */
    fun performAppInvite(activity: Activity, args: HashMap<String, String>? = null) =
            performAppSuggestInvite(activity, OkAppInviteActivity::class.java, args, Shared.OK_INVITING_REQUEST_CODE)

    /**
     * Calls application suggest widget
     *
     * @param args widget arguments as specified in documentation
     */
    fun performAppSuggest(activity: Activity, args: HashMap<String, String>? = null) =
            performAppSuggestInvite(activity, OkAppSuggestActivity::class.java, args, Shared.OK_SUGGESTING_REQUEST_CODE)

    private fun performAppSuggestInvite(activity: Activity, clazz: Class<out AbstractWidgetActivity>,
                                        args: HashMap<String, String>?, requestCode: Int) {
        val intent = Intent(activity, clazz)
        intent.putExtra(Shared.PARAM_APP_ID, appId)
        intent.putExtra(Shared.PARAM_ACCESS_TOKEN, mAccessToken)
        intent.putExtra(Shared.PARAM_WIDGET_RETRY_ALLOWED, allowWidgetRetry)
        intent.putExtra(Shared.PARAM_SESSION_SECRET_KEY, mSessionSecretKey)
        intent.putExtra(Shared.PARAM_WIDGET_ARGS, args)
        activity.startActivityForResult(intent, requestCode)
    }

    private fun signParameters(params: MutableMap<String, String>) {
        val sb = StringBuilder(100)
        params.forEach { sb.append(it.key).append('=').append(it.value) }
        val paramsString = sb.toString()
        val sig = Utils.toMD5(paramsString + mSessionSecretKey)
        params[Shared.PARAM_SIGN] = sig
    }

    /**
     * Clears all token information from sdk and webView cookies
     */
    fun clearTokens() {
        mAccessToken = null
        mSessionSecretKey = null
        sdkToken = null
        TokenStore.removeStoredTokens(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            clearCookies()
        } else {
            clearCookiesOld()
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun clearCookies() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies(null)
    }

    @Suppress("DEPRECATION")
    private fun clearCookiesOld() {
        CookieSyncManager.createInstance(context)
        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookie()
    }

    /**
     * Reports a payment and sends (async) via sdk.reportPayment method<br></br>
     */
    fun reportPayment(trxId: String, amount: String, currency: Currency) = okPayment.report(trxId, amount, currency)

    private fun onValidSessionAppeared() = okPayment.init()

    /**
     * Sets the base urls for communicating with OK platform
     *
     * @param apiBaseUrl     api server url
     * @param connectBaseUrl connect (widgets) server url
     * @see OKRestHelper.sdkGetEndpoints
     */
    fun setBasePlatformUrls(apiBaseUrl: String, connectBaseUrl: String) {
        this.apiBaseUrl = apiBaseUrl
        this.connectBaseUrl = connectBaseUrl
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        @JvmStatic
        protected var sOdnoklassniki: Odnoklassniki? = null

        /**
         * This method is required to be called before [Odnoklassniki.getInstance]<br></br>
         * Note that instance is only created once. Multiple calls to this method wont' create multiple instances of the object
         */
        @JvmStatic
        fun createInstance(context: Context, appId: String?, appKey: String?): Odnoklassniki {
            if (appId == null || appKey == null) {
                throw IllegalArgumentException(context.getString(R.string.no_application_data))
            }
            if (sOdnoklassniki == null) {
                sOdnoklassniki = Odnoklassniki(context.applicationContext, appId, appKey)
            }
            return sOdnoklassniki!!
        }

        /**
         * Get previously created instance.<br></br>
         * You must always call [Odnoklassniki.createInstance] before calling this method, or [IllegalStateException] will be thrown
         */
        @JvmStatic
        val instance: Odnoklassniki
            get() {
                if (sOdnoklassniki == null) {
                    throw IllegalStateException("No instance available. Odnoklassniki.createInstance() needs to be called before Odnoklassniki.getInstance()")
                }
                return sOdnoklassniki!!
            }

        @JvmStatic
        fun hasInstance(): Boolean {
            return sOdnoklassniki != null
        }

    }

    private fun toJson(key: String, value: String?): JSONObject =
            try {
                JSONObject().put(key, value)
            } catch (e: JSONException) {
                JSONObject()
            }
}