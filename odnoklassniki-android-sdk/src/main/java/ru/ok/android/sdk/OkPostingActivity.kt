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
        setContentView(R.layout.ok_posting_activity)
        prepareWebView()

        val bundle = intent.extras
        if (bundle != null) {
            args["st.attachment"] = bundle.getString(Shared.PARAM_ATTACHMENT) ?: "{}"
            args["st.utext"] = if (bundle.getBoolean(Shared.PARAM_USER_TEXT_ENABLE, false)) "on" else "off"
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
            val type = json.getString(Shared.PARAM_TYPE)
            if ("error" == type) {
                resultIntent.putExtra(Shared.PARAM_ERROR, json.getString(Shared.PARAM_MESSAGE))
            } else {
                resultIntent.putExtra(Shared.PARAM_RESULT, json.toString())
            }
        } catch (e: JSONException) {
            resultIntent.putExtra(Shared.PARAM_ERROR, result)
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }
}
