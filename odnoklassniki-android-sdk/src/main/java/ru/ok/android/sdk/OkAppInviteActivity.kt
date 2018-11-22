package ru.ok.android.sdk

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import org.json.JSONException
import org.json.JSONObject

internal open class OkAppInviteActivity : AbstractWidgetActivity() {

    protected open val activityView: Int
        get() = R.layout.ok_app_invite_activity

    override val cancelledMessageId: Int
        get() = R.string.invite_canceled

    override val widgetId: String
        get() = "WidgetInvite"

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(activityView)
        prepareWebView()
        val url = prepareUrl(null)
        findViewById<WebView>(R.id.web_view).loadUrl(url)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun prepareWebView() {
        val webView = findViewById<WebView>(R.id.web_view)
        webView.webViewClient = OkWidgetViewClient(this)
        webView.settings.javaScriptEnabled = true
    }

    override fun processResult(result: String?) {
        val resultIntent = Intent()
        try {
            val json = JSONObject(result)
            val code = json.optString(Shared.PARAM_CODE)
            if ("ok".equals(code, ignoreCase = true)) {
                resultIntent.putExtra(Shared.PARAM_RESULT, json.toString())
            } else {
                resultIntent.putExtra(Shared.PARAM_ERROR, json.getString(Shared.PARAM_MESSAGE))
            }
        } catch (e: JSONException) {
            resultIntent.putExtra(Shared.PARAM_ERROR, result)
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }
}
