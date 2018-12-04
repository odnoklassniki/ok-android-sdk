package ru.ok.android.sdk

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import org.json.JSONException
import org.json.JSONObject

internal class OkPostingActivity : AbstractWidgetActivity() {

    override val cancelledMessageId: Int
        get() = R.string.posting_canceled

    override val widgetId: String
        get() = "WidgetMediatopicPost"

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prepareWebView()

        val bundle = intent.extras
        if (bundle != null) {
            args["st.attachment"] = bundle.getString(PARAM_ATTACHMENT) ?: "{}"
            args["st.utext"] = if (bundle.getBoolean(PARAM_USER_TEXT_ENABLE, false)) "on" else "off"
        }

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
            val type = json.getString(PARAM_TYPE)
            if ("error" == type) {
                resultIntent.putExtra(PARAM_ERROR, json.getString(PARAM_MESSAGE))
            } else {
                resultIntent.putExtra(PARAM_RESULT, json.toString())
            }
        } catch (e: JSONException) {
            resultIntent.putExtra(PARAM_ERROR, result)
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }
}
