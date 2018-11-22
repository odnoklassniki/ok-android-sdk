package ru.ok.android.sdk

import android.content.Context
import android.net.http.SslError
import android.os.Build
import android.view.View
import android.webkit.SslErrorHandler
import android.webkit.WebView
import android.webkit.WebViewClient

@Suppress("DEPRECATION", "OverridingDeprecatedMember")
internal open class OkWebViewClient(private val mContext: Context) : WebViewClient() {
    protected var showPage = true

    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
        showPage = true
        return super.shouldOverrideUrlLoading(view, url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        view.visibility = if (showPage) View.VISIBLE else View.INVISIBLE
    }

    override fun onReceivedError(view: WebView, errorCode: Int, description: String, failingUrl: String) {
        showPage = false
        super.onReceivedError(view, errorCode, description, failingUrl)
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        showPage = false
        super.onReceivedSslError(view, handler, error)
    }

    fun getErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            WebViewClient.ERROR_CONNECT -> getString(R.string.error_connect)
            WebViewClient.ERROR_FAILED_SSL_HANDSHAKE -> getString(R.string.error_failed_ssl_handshake)
            WebViewClient.ERROR_HOST_LOOKUP -> getString(R.string.error_host_lookup)
            WebViewClient.ERROR_TIMEOUT -> getString(R.string.error_timeout)
            else -> getString(R.string.error_unknown)
        }
    }

    fun getErrorMessage(error: SslError): String {
        val errorCode = error.primaryError
        return when (errorCode) {
            SslError.SSL_EXPIRED -> getString(R.string.error_ssl_expired)
            SslError.SSL_IDMISMATCH -> getString(R.string.error_ssl_id_mismatch)
            SslError.SSL_NOTYETVALID -> getString(R.string.error_ssl_not_yet_valid)
            SslError.SSL_UNTRUSTED -> getString(R.string.error_ssl_untrusted)
            else -> {
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH && errorCode == SslError.SSL_DATE_INVALID) {
                    getString(R.string.error_ssl_date_invalid)
                } else getString(R.string.error_unknown)
            }
        }
    }

    private fun getString(resId: Int): String = mContext.getString(resId)
}
