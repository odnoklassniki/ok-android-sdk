package ru.ok.android.sdk

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.net.http.SslError
import android.os.Bundle
import android.view.KeyEvent
import android.webkit.SslErrorHandler
import android.webkit.WebView
import ru.ok.android.sdk.util.Utils
import ru.ok.android.sdk.util.OkRequestUtil
import java.util.*

private val DEFAULT_OPTIONS = mapOf(
        "st.popup" to "on",
        "st.silent" to "on"
)

internal abstract class AbstractWidgetActivity : Activity() {
    protected var mAppId: String? = null
    protected var mAccessToken: String? = null
    protected var mSessionSecretKey: String? = null
    protected val args = HashMap<String, String>()
    protected var retryAllowed = true

    protected open val layoutId: Int
        get() = R.layout.oksdk_webview_activity

    protected abstract val widgetId: String

    protected val baseUrl: String
        get() = Odnoklassniki.instance.connectBaseUrl +
                "dk?st.cmd=" + widgetId +
                "&st.access_token=" + mAccessToken +
                "&st.app=" + mAppId +
                "&st.return=" + returnUrl

    protected val returnUrl: String
        get() = "okwidget://" + widgetId.toLowerCase()

    protected abstract val cancelledMessageId: Int

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(layoutId)

        args.clear()
        val bundle = intent.extras
        if (bundle != null) {
            mAppId = bundle.getString(PARAM_APP_ID)
            mAccessToken = bundle.getString(PARAM_ACCESS_TOKEN)
            mSessionSecretKey = bundle.getString(PARAM_SESSION_SECRET_KEY)
            if (bundle.containsKey(PARAM_WIDGET_ARGS)) {
                @Suppress("UNCHECKED_CAST")
                val map = bundle.getSerializable(PARAM_WIDGET_ARGS) as? HashMap<String, String>
                if (map != null) {
                    args.putAll(map)
                }
            }
            if (bundle.containsKey(PARAM_WIDGET_RETRY_ALLOWED)) {
                retryAllowed = bundle.getBoolean(PARAM_WIDGET_RETRY_ALLOWED, true)
            }
        }
    }

    protected abstract fun processResult(result: String?)

    protected fun processError(error: String) {
        if (!retryAllowed) {
            processResult(error)
            return
        }
        try {
            AlertDialog.Builder(this)
                    .setMessage(error)
                    .setPositiveButton(getString(R.string.retry)) { _, _ -> findViewById<WebView>(R.id.web_view).loadUrl(prepareUrl()) }
                    .setNegativeButton(getString(R.string.cancel)) { _, _ -> processResult(error) }
                    .show()
        } catch (ignore: RuntimeException) {
            // this usually happens during fast back. avoid crash in such a case
            processResult(error)
        }

    }

    /**
     * Prepares widget URL based on widget parameters
     *
     * @param options widget options (if null, default options are being sent)
     * @return widget url
     * @see .args
     *
     * @see .DEFAULT_OPTIONS
     */
    protected fun prepareUrl(options: Map<String, String>? = null): String {
        val params = TreeMap<String, String>()
        for ((key, value) in args) params[key] = value
        params["st.return"] = returnUrl

        val sigSource = StringBuilder(200)
        val url = StringBuilder(baseUrl)
        for ((key, value) in params) {
            if (WIDGET_SIGNED_ARGS.contains(key)) sigSource.append(key).append('=').append(value)
            if (key != "st.return") url.append('&').append(key).append('=').append(OkRequestUtil.encode(value))
        }
        val signature = Utils.toMD5(sigSource.toString() + mSessionSecretKey)

        for ((key, value) in options ?: DEFAULT_OPTIONS) url.append('&').append(key).append('=').append(value)
        url.append("&st.signature=").append(signature)

        return url.toString()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (KeyEvent.KEYCODE_BACK == keyCode) {
            processError(getString(cancelledMessageId))
            return true
        }
        return false
    }

    @Suppress("OverridingDeprecatedMember", "DEPRECATION")
    inner class OkWidgetViewClient(context: Context) : OkWebViewClient(context) {
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            if (url.startsWith(returnUrl)) {
                val parameters = OkRequestUtil.getUrlParameters(url)
                val result = parameters.getString("result")
                processResult(result)
                return true
            }
            return super.shouldOverrideUrlLoading(view, url)
        }

        override fun onReceivedError(view: WebView, errorCode: Int, description: String, failingUrl: String) {
            super.onReceivedError(view, errorCode, description, failingUrl)
            processError(getErrorMessage(errorCode))
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            super.onReceivedSslError(view, handler, error)
            processError(getErrorMessage(error))
        }
    }
}
