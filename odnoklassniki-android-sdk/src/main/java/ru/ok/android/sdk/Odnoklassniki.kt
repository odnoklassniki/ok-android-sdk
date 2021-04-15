package ru.ok.android.sdk

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.AsyncTask
import android.os.Build
import android.text.TextUtils
import android.webkit.CookieManager
import android.webkit.CookieSyncManager
import androidx.fragment.app.Fragment
import org.json.JSONException
import org.json.JSONObject
import ru.ok.android.sdk.util.*
import java.io.IOException
import java.util.*

open class Odnoklassniki(
        private val context: Context,
        id: String? = null,
        key: String? = null
) {
    var mAccessToken: String?
    var mSessionSecretKey: String?
    var sdkToken: String?
    protected val okPayment: OkPayment

    /** Widgets ask user to retry the action on error (set false for instant error callback) */
    var allowWidgetRetry = true

    protected val appId: String
    protected val appKey: String

    init {
        if (id == null || key == null) {
            val (storedId, storedKey) = TokenStore.getAppInfo(context)
            if (storedId == null || storedKey == null) {
                throw IllegalStateException("No instance available. Odnoklassniki.createInstance() needs to be called")
            }
            appId = storedId
            appKey = storedKey
        } else {
            appId = id
            appKey = key
            TokenStore.setAppInfo(context, id, key)
        }

        mAccessToken = TokenStore.getStoredAccessToken(context)
        mSessionSecretKey = TokenStore.getStoredSessionSecretKey(context)
        sdkToken = TokenStore.getSdkToken(context)
        okPayment = OkPayment(context)

        sOdnoklassniki = this
    }

    /**
     * Starts user authorization
     * <br/>
     * Note: If using server OAUTH (as described in https://apiok.ru/en/ext/oauth/server), you are responsible to retrieving
     *     access_token and setting it in #setTokens
     *
     * @param authType    selected auth type
     * @param scopes      [OkScope] - application request permissions as per [OkScope].
     * @see OkAuthType
     */
    fun requestAuthorization(activity: Activity, authType: OkAuthType,
                             vararg scopes: String, withServerOauth: Boolean = false) {
        startAuth(authType, scopes, activity = activity, withServerOauth = withServerOauth)
    }

    /**
     * Starts user authorization
     * <br/>
     * Note: If using server OAUTH (as described in https://apiok.ru/en/ext/oauth/server), you are responsible to retrieving
     *     access_token and setting it view #setTokens
     *
     * @param authType    selected auth type
     * @param scopes      [OkScope] - application request permissions as per [OkScope].
     * @see OkAuthType
     */
    fun requestAuthorization(fragment: Fragment, authType: OkAuthType,
                             vararg scopes: String, withServerOauth: Boolean = false) {
        startAuth(authType, scopes, fragment = fragment, withServerOauth = withServerOauth)
    }

    /**
     * prepares Intent to be executed with startActivityForResult
     */
    private inline fun startRequest(activity: Activity?, fragment: Fragment?, code: Int, target: Class<*>, crossinline filler: (Intent) -> Unit) {
        val intent = when {
            activity != null -> Intent(activity, target)
            fragment != null -> Intent(fragment.context, target)
            else -> return
        }
        filler.invoke(intent)
        activity?.startActivityForResult(intent, code)
        fragment?.startActivityForResult(intent, code)
    }

    private fun startAuth(authType: OkAuthType, scopes: Array<out String>, activity: Activity? = null, fragment: Fragment? = null,
                          withServerOauth: Boolean) =
            startRequest(activity, fragment, OK_AUTH_REQUEST_CODE, OkAuthActivity::class.java) { intent ->
                intent.putExtra(PARAM_CLIENT_ID, appId)
                intent.putExtra(PARAM_APP_KEY, appKey)
                intent.putExtra(PARAM_AUTH_TYPE, authType)
                intent.putExtra(PARAM_SCOPES, scopes)
                if (withServerOauth) intent.putExtra(OAUTH_TYPE, OAUTH_TYPE_SERVER)
            }

    fun isActivityRequestOAuth(requestCode: Int): Boolean {
        return requestCode == OK_AUTH_REQUEST_CODE
    }

    fun onAuthActivityResult(request: Int, result: Int, intent: Intent?, listener: OkListener): Boolean {
        if (isActivityRequestOAuth(request)) {
            if (intent == null) {
                val json = JSONObject()
                try {
                    json.put(PARAM_ACTIVITY_RESULT, result)
                } catch (ignore: JSONException) {
                }

                listener.onError(json.toString())
                return true
            }

            val serverCode = intent.getStringExtra(PARAM_CODE)
            val accessToken = intent.getStringExtra(PARAM_ACCESS_TOKEN)
            when {
                !serverCode.isNullOrBlank() -> listener.onSuccess(JSONObject().also {
                    it.put(PARAM_CODE, serverCode)
                })
                accessToken == null -> {
                    val error = intent.getStringExtra(PARAM_ERROR)
                    when {
                        result == RESULT_CANCELLED && listener is OkAuthListener -> listener.onCancel(error)
                        else -> listener.onError(error)
                    }
                }
                else -> {
                    val sessionSecretKey = intent.getStringExtra(PARAM_SESSION_SECRET_KEY)
                    val refreshToken = intent.getStringExtra(PARAM_REFRESH_TOKEN)
                    val expiresIn = intent.getLongExtra(PARAM_EXPIRES_IN, 0)
                    setTokens(context, accessToken, sessionSecretKey ?: refreshToken)
                    val json = JSONObject()
                    try {
                        json.put(PARAM_ACCESS_TOKEN, mAccessToken)
                        json.put(PARAM_SESSION_SECRET_KEY, mSessionSecretKey)
                        if (expiresIn > 0) {
                            json.put(PARAM_EXPIRES_IN, expiresIn)
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

    fun isActivityRequestPost(requestCode: Int): Boolean = requestCode == OK_POSTING_REQUEST_CODE

    fun isActivityRequestInvite(requestCode: Int): Boolean = requestCode == OK_INVITING_REQUEST_CODE

    fun isActivityRequestSuggest(requestCode: Int): Boolean = requestCode == OK_SUGGESTING_REQUEST_CODE

    fun isActivityRequestViral(request: Int): Boolean =
            isActivityRequestPost(request) || isActivityRequestInvite(request) || isActivityRequestSuggest(request)

    fun onActivityResultResult(request: Int, result: Int, intent: Intent?, listener: OkListener): Boolean {
        if (isActivityRequestViral(request)) {
            if (intent == null) {
                val json = JSONObject()
                try {
                    json.put(PARAM_ACTIVITY_RESULT, result)
                } catch (ignore: JSONException) {
                }

                listener.onError(json.toString())
            } else {
                if (intent.hasExtra(PARAM_ERROR)) {
                    listener.onError(intent.getStringExtra(PARAM_ERROR))
                } else {
                    try {
                        listener.onSuccess(JSONObject(intent.getStringExtra(PARAM_RESULT)))
                    } catch (e: JSONException) {
                        listener.onError(intent.getStringExtra(PARAM_RESULT))
                    }
                }
            }
            return true
        }
        return false
    }

    fun notifyFailed(listener: OkListener?, error: String?) {
        if (listener != null) {
            Utils.executeOnMain(Runnable { listener.onError(error) })
        }
    }

    fun notifySuccess(listener: OkListener?, json: JSONObject) {
        if (listener != null) {
            Utils.executeOnMain(Runnable { listener.onSuccess(json) })
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
    fun request(method: String, params: Map<String, String>? = null, mode: Set<OkRequestMode> = OkRequestMode.DEFAULT): String? {
        if (TextUtils.isEmpty(method)) throw IllegalArgumentException(context.getString(R.string.api_method_cant_be_empty))
        val requestParams = TreeMap<String, String>()
        if (!params.isNullOrEmpty()) requestParams.putAll(params)
        requestParams[PARAM_APP_KEY] = appKey
        requestParams[PARAM_METHOD] = method
        if (!mode.contains(OkRequestMode.NO_PLATFORM_REPORTING)) requestParams[PARAM_PLATFORM] = APP_PLATFORM
        if (mode.contains(OkRequestMode.SDK_SESSION)) {
            requestParams["sdkToken"] = sdkToken
                    ?: throw IllegalArgumentException("SDK token is required for method call, have not forget to call sdkInit?")
        }
        if (mode.contains(OkRequestMode.SIGNED) && !mAccessToken.isNullOrEmpty()) {
            signParameters(requestParams)
            requestParams[PARAM_ACCESS_TOKEN] = mAccessToken!!
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
    fun request(method: String, params: Map<String, String>? = null, mode: Set<OkRequestMode> = OkRequestMode.DEFAULT, listener: OkListener): Boolean {
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

        return if (json.has(PARAM_ERROR_MSG)) {
            notifyFailed(listener, json.optString(PARAM_ERROR_MSG))
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
    fun requestAsync(method: String, params: Map<String, String>? = null, mode: Set<OkRequestMode> = OkRequestMode.DEFAULT, listener: OkListener): AsyncTask<Void, Void, Void> {
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
                        json.put(PARAM_ACCESS_TOKEN, mAccessToken)
                        json.put(PARAM_SESSION_SECRET_KEY, mSessionSecretKey)
                        json.put(PARAM_LOGGED_IN_USER, response)
                    } catch (ignore: JSONException) {
                    }

                    onValidSessionAppeared()
                    notifySuccess(listener, json)
                } else {
                    try {
                        val json = JSONObject(response)
                        if (json.has(PARAM_ERROR_MSG)) {
                            notifyFailed(listener, json.getString(PARAM_ERROR_MSG))
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
    fun performPosting(activity: Activity, attachment: String, userTextEnabled: Boolean, args: HashMap<String, String>? = null) =
            performPosting(attachment, userTextEnabled, args, activity = activity)

    /**
     * @see performPosting
     */
    fun performPosting(fragment: Fragment, attachment: String, userTextEnabled: Boolean, args: HashMap<String, String>? = null) =
            performPosting(attachment, userTextEnabled, args, fragment = fragment)

    private fun performPosting(attachment: String, userTextEnabled: Boolean, args: HashMap<String, String>? = null,
                               activity: Activity? = null, fragment: Fragment? = null) {
        startRequest(activity, fragment, OK_POSTING_REQUEST_CODE, OkPostingActivity::class.java) { intent ->
            intent.putExtra(PARAM_APP_ID, appId)
            intent.putExtra(PARAM_ATTACHMENT, attachment)
            intent.putExtra(PARAM_ACCESS_TOKEN, mAccessToken)
            intent.putExtra(PARAM_WIDGET_ARGS, args)
            intent.putExtra(PARAM_WIDGET_RETRY_ALLOWED, allowWidgetRetry)
            intent.putExtra(PARAM_SESSION_SECRET_KEY, mSessionSecretKey)
            intent.putExtra(PARAM_USER_TEXT_ENABLE, userTextEnabled)
        }
    }

    /**
     * Calls application invite widget
     *
     * @param args widget arguments as specified in documentation
     */
    fun performAppInvite(activity: Activity, args: HashMap<String, String>? = null) =
            performAppSuggestInvite(OkAppInviteActivity::class.java, args, OK_INVITING_REQUEST_CODE, activity = activity)

    /**
     * Calls application invite widget
     *
     * @param args widget arguments as specified in documentation
     */
    fun performAppInvite(fragment: Fragment, args: HashMap<String, String>? = null) =
            performAppSuggestInvite(OkAppInviteActivity::class.java, args, OK_INVITING_REQUEST_CODE, fragment = fragment)

    /**
     * Calls application suggest widget
     *
     * @param args widget arguments as specified in documentation
     */
    fun performAppSuggest(activity: Activity, args: HashMap<String, String>? = null) {
        performAppSuggestInvite(OkAppSuggestActivity::class.java, args, OK_SUGGESTING_REQUEST_CODE, activity = activity)
    }

    /**
     * Calls application suggest widget
     *
     * @param args widget arguments as specified in documentation
     */
    fun performAppSuggest(fragment: Fragment, args: HashMap<String, String>? = null) {
        performAppSuggestInvite(OkAppSuggestActivity::class.java, args, OK_SUGGESTING_REQUEST_CODE, fragment = fragment)
    }

    private fun performAppSuggestInvite(clazz: Class<out AbstractWidgetActivity>,
                                        args: HashMap<String, String>?,
                                        code: Int,
                                        activity: Activity? = null,
                                        fragment: Fragment? = null) {
        startRequest(activity, fragment, code, clazz) { intent ->
            intent.putExtra(PARAM_APP_ID, appId)
            intent.putExtra(PARAM_ACCESS_TOKEN, mAccessToken)
            intent.putExtra(PARAM_WIDGET_RETRY_ALLOWED, allowWidgetRetry)
            intent.putExtra(PARAM_SESSION_SECRET_KEY, mSessionSecretKey)
            intent.putExtra(PARAM_WIDGET_ARGS, args)
        }
    }

    private fun signParameters(params: MutableMap<String, String>) {
        val sb = StringBuilder(100)
        params.forEach { sb.append(it.key).append('=').append(it.value) }
        val paramsString = sb.toString()
        val sig = Utils.toMD5(paramsString + mSessionSecretKey)
        params[PARAM_SIGN] = sig
    }

    /**
     * Clears all token information from sdk and webView cookies
     */
    @Suppress("DEPRECATION")
    fun clearTokens() {
        mAccessToken = null
        mSessionSecretKey = null
        sdkToken = null
        TokenStore.removeStoredTokens(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().removeAllCookies(null)
        } else {
            CookieSyncManager.createInstance(context)
            CookieManager.getInstance().removeAllCookie()
        }
    }

    fun setTokens(ctx: Context, accessToken: String, sessionSecretKey: String, withPersistance: Boolean = false) {
        mAccessToken = accessToken
        mSessionSecretKey = sessionSecretKey
        if (withPersistance) TokenStore.store(ctx, accessToken, sessionSecretKey)
    }

    /**
     * Reports a payment and sends (async) via sdk.reportPayment method<br></br>
     */
    fun reportPayment(trxId: String, amount: String, currency: Currency) = okPayment.report(trxId, amount, currency)

    private fun onValidSessionAppeared() = okPayment.init()

    companion object {
        @SuppressLint("StaticFieldLeak")
        @JvmStatic
        @Volatile
        protected var sOdnoklassniki: Odnoklassniki? = null

        /**
         * This method is required to be called before [Odnoklassniki.instance]<br></br>
         */
        @JvmStatic
        fun createInstance(context: Context, appId: String, appKey: String): Odnoklassniki {
            if (appId.isBlank() || appKey.isBlank()) throw IllegalArgumentException(context.getString(R.string.no_application_data))
            return Odnoklassniki(context.applicationContext, appId, appKey)
        }

        /**
         * Get previously created instance.<br></br>
         * You must always call [Odnoklassniki.createInstance] before calling this method, or [IllegalStateException] will be thrown
         */
        @Deprecated("Use of(context) for safe access")
        @JvmStatic
        val instance: Odnoklassniki
            get() {
                if (sOdnoklassniki == null) {
                    throw IllegalStateException("No instance available. Odnoklassniki.createInstance() needs to be called before Odnoklassniki.of()")
                }
                return sOdnoklassniki!!
            }

        @JvmStatic
        fun hasInstance(): Boolean {
            return sOdnoklassniki != null
        }

        fun of(context: Context): Odnoklassniki {
            return sOdnoklassniki ?: Odnoklassniki(context)
        }
    }

    private fun toJson(key: String, value: String?): JSONObject =
            try {
                JSONObject().put(key, value)
            } catch (e: JSONException) {
                JSONObject()
            }
}