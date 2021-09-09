@file:Suppress("DEPRECATION", "OverridingDeprecatedMember")

package ru.ok.android.sdk

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.content.pm.ResolveInfo
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.SslErrorHandler
import android.webkit.WebView
import ru.ok.android.sdk.util.OkAuthType
import java.net.URLEncoder

const val OAUTH_TYPE = "oauth_type"
const val OAUTH_TYPE_SERVER = "code"
const val OAUTH_TYPE_CLIENT = "token"

const val RESULT_FAILED = 2
const val RESULT_CANCELLED = 3

private const val SSO_ACTIVITY_REQUEST_CODE = 31337
private const val DEFAULT_SECRET_KEY = "6C6B6397C2BCE5EDB7290039"
private const val REDIRECT_URI = "okauth://auth"
private const val SSO_STARTED = "SSO_STARTED"
private const val MIN_OK_VER_WITH_SERVER_SSO = 638

private const val ODKL_APP_PUBLIC_SIGNATURE =
    "3082025b308201c4a00302010202044f6760f9300d06092a864886f70d01010505003071310c300a06035504061303727573310c300a06035504081303737062310c300a0603550407130373706231163014060355040a130d4f646e6f6b6c6173736e696b6931143012060355040b130b6d6f62696c65207465616d311730150603550403130e416e647265792041736c616d6f763020170d3132303331393136333831375a180f32303636313232313136333831375a3071310c300a06035504061303727573310c300a06035504081303737062310c300a0603550407130373706231163014060355040a130d4f646e6f6b6c6173736e696b6931143012060355040b130b6d6f62696c65207465616d311730150603550403130e416e647265792041736c616d6f7630819f300d06092a864886f70d010101050003818d003081890281810080bea15bf578b898805dfd26346b2fbb662889cd6aba3f8e53b5b27c43a984eeec9a5d21f6f11667d987b77653f4a9651e20b94ff10594f76a93a6a36e6a42f4d851847cf1da8d61825ce020b7020cd1bc2eb435b0d416908be9393516ca1976ff736733c1d48ff17cd57f21ad49e05fc99384273efc5546e4e53c5e9f391c430203010001300d06092a864886f70d0101050500038181007d884df69a9748eabbdcfe55f07360433b23606d3b9d4bca03109c3ffb80fccb7809dfcbfd5a466347f1daf036fbbf1521754c2d1d999f9cbc66b884561e8201459aa414677e411e66360c3840ca4727da77f6f042f2c011464e99f34ba7df8b4bceb4fa8231f1d346f4063f7ba0e887918775879e619786728a8078c76647ed"

class OkAuthActivity : Activity() {
    private var mAppId: String? = null
    private var mAppKey: String? = null
    private lateinit var mScopes: Array<String>
    private var authType: OkAuthType? = null
    private var ssoAuthorizationStarted = false
    private var withServerOauth = false

    private lateinit var mWebView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.oksdk_webview_activity)
        findViewById<View>(R.id.web_view).visibility = View.INVISIBLE
        prepareWebView()

        val bundle = savedInstanceState ?: intent.extras ?: Bundle()
        mAppId = bundle.getString(PARAM_CLIENT_ID)
        mAppKey = bundle.getString(PARAM_APP_KEY)
        mScopes = bundle.getStringArray(PARAM_SCOPES) ?: arrayOf()
        authType = if (bundle.getSerializable(PARAM_AUTH_TYPE) is OkAuthType)
            bundle.getSerializable(PARAM_AUTH_TYPE) as OkAuthType else OkAuthType.ANY
        ssoAuthorizationStarted = bundle.getBoolean(SSO_STARTED, false)
        withServerOauth = bundle.getString(OAUTH_TYPE) == OAUTH_TYPE_SERVER

        if (!ssoAuthorizationStarted) auth()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun prepareWebView() {
        mWebView = findViewById(R.id.web_view)
        mWebView.webViewClient = OAuthWebViewClient(this)
        mWebView.settings.javaScriptEnabled = true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(PARAM_CLIENT_ID, mAppId)
        outState.putString(PARAM_APP_KEY, mAppKey)
        outState.putStringArray(PARAM_SCOPES, mScopes)
        outState.putSerializable(PARAM_AUTH_TYPE, authType)
        outState.putBoolean(SSO_STARTED, ssoAuthorizationStarted)
        outState.putString(OAUTH_TYPE, if (withServerOauth) OAUTH_TYPE_SERVER else null)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (KeyEvent.KEYCODE_BACK == keyCode) {
            showAlert(getString(R.string.authorization_canceled))
            return true
        }
        return false
    }

    private fun auth() {
        if (mAppId.isNullOrBlank() || mAppKey.isNullOrBlank()) {
            onFail(getString(R.string.no_application_data))
            return
        }

        if (authType == OkAuthType.NATIVE_SSO || authType == OkAuthType.ANY) {
            if (startSsoAuthorization()) {
                ssoAuthorizationStarted = true
                return
            } else if (authType == OkAuthType.NATIVE_SSO) {
                onFail(getString(R.string.no_ok_application_installed))
                return
            }
        }

        if (authType == OkAuthType.WEBVIEW_OAUTH || authType == OkAuthType.ANY) {
            mWebView.loadUrl(buildOAuthUrl())
        }
    }

    private fun buildOAuthUrl(): String {
        val responseType = if (withServerOauth) OAUTH_TYPE_SERVER else OAUTH_TYPE_CLIENT
        var url = "${REMOTE_WIDGETS}oauth/authorize?client_id=$mAppId&response_type=$responseType&redirect_uri=$REDIRECT_URI&layout=m&platform=$APP_PLATFORM"
        if (!mScopes.isNullOrEmpty()) {
            val scopesString = URLEncoder.encode(mScopes.joinToString(separator = ";"))
            url = "$url&scope=$scopesString"
        }
        return url
    }

    private fun resolveOkAppLogin(intent: Intent): ResolveInfo? {
        intent.setClassName("ru.ok.android", "ru.ok.android.external.LoginExternal")
        return packageManager.resolveActivity(intent, 0)
    }

    /* SSO AUTHORIZATION */
    @Suppress("DEPRECATION")
    private fun startSsoAuthorization(): Boolean {
        val intent = Intent()
        val resolveInfo = resolveOkAppLogin(intent) ?: return false
        try {
            val packageInfo = packageManager.getPackageInfo(resolveInfo.activityInfo.packageName, PackageManager.GET_SIGNATURES)
                ?.takeIf { it.versionCode >= 120 } ?: return false
            if (withServerOauth && packageInfo.versionCode < MIN_OK_VER_WITH_SERVER_SSO) return false
            if (packageInfo.signatures.any { it.toCharsString() == ODKL_APP_PUBLIC_SIGNATURE }) {
                intent.putExtra(PARAM_CLIENT_ID, mAppId)
                intent.putExtra(PARAM_CLIENT_SECRET, DEFAULT_SECRET_KEY)
                if (withServerOauth) intent.putExtra(OAUTH_TYPE, OAUTH_TYPE_SERVER)
                if (mScopes.isNotEmpty()) intent.putExtra(PARAM_SCOPES, mScopes)
                try {
                    startActivityForResult(intent, SSO_ACTIVITY_REQUEST_CODE)
                    return true
                } catch (ignore: ActivityNotFoundException) {
                }
            }
        } catch (ignore: NameNotFoundException) {
        }
        return false
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == SSO_ACTIVITY_REQUEST_CODE) {
            ssoAuthorizationStarted = false
            val errorStr = data?.getStringExtra(PARAM_ERROR) ?: ""
            when (resultCode) {
                RESULT_OK -> {
                    val code = data?.getStringExtra(PARAM_CODE)
                    val accessToken = data?.getStringExtra(PARAM_ACCESS_TOKEN)
                    val sessionSecretKey = data?.getStringExtra(PARAM_SESSION_SECRET_KEY)
                    val refreshToken = data?.getStringExtra(PARAM_REFRESH_TOKEN)
                    val expiresIn = data?.getLongExtra(PARAM_EXPIRES_IN, 0) ?: 0

                    if (accessToken != null || code != null) {
                        onSuccess(accessToken, sessionSecretKey ?: refreshToken, expiresIn, code)
                    } else {
                        onFail(errorStr)
                    }
                }
                RESULT_CANCELED -> {
                    onCancel(errorStr)
                }
                RESULT_FAILED -> {
                    onFail(errorStr)
                }
            }
            finish()
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun onCancel(error: String) {
        val result = Intent()
        result.putExtra(PARAM_ERROR, error)
        setResult(RESULT_CANCELLED, result)
        finish()
    }

    private fun onFail(error: String?) {
        val result = Intent()
        result.putExtra(PARAM_ERROR, error)
        setResult(RESULT_FAILED, result)
        finish()
    }

    private fun onSuccess(accessToken: String? = null, sessionSecretKey: String? = null, expiresIn: Long = 0, code: String? = null) {
        val result = Intent()
        if (accessToken != null) {
            TokenStore.store(this, accessToken, sessionSecretKey!!)

            result.putExtra(PARAM_ACCESS_TOKEN, accessToken)
            result.putExtra(PARAM_SESSION_SECRET_KEY, sessionSecretKey)
            if (expiresIn > 0) result.putExtra(PARAM_EXPIRES_IN, expiresIn)
        } else {
            result.putExtra(PARAM_CODE, code)
        }
        setResult(RESULT_OK, result)
        finish()
    }

    private fun showAlert(message: String) {
        if (isFinishing) return

        try {
            AlertDialog.Builder(this@OkAuthActivity)
                .setMessage(message)
                .setPositiveButton(getString(R.string.retry)) { _, _ -> auth() }
                .setNegativeButton(getString(R.string.cancel)) { _, _ -> onCancel(message) }
                .show()
        } catch (ignore: RuntimeException) {
            // this usually happens during fast back. avoid crash in such a case
            onCancel(message)
        }

    }

    private inner class OAuthWebViewClient(context: Context) : OkWebViewClient(context) {
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            if (url.startsWith(REDIRECT_URI)) {
                val uri = Uri.parse(url)
                var accessToken: String? = null
                var sessionSecretKey: String? = null
                var error: String? = null
                var expiresIn: Long = 0

                uri.fragment?.let { fragment ->
                    val args = fragment.split("&")
                        .filter { it.contains('=') }
                        .map { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
                        .filter { it.first.isNotEmpty() && it.second.isNotEmpty() }
                        .toMap()
                    accessToken = args[PARAM_ACCESS_TOKEN]
                    sessionSecretKey = args[PARAM_SESSION_SECRET_KEY] ?: args[PARAM_REFRESH_TOKEN]
                    error = args[PARAM_ERROR]
                    expiresIn = args[PARAM_EXPIRES_IN]?.toLongOrNull() ?: 0
                }

                val code = when {
                    // success server oauth response arrives via query, errors via fragment
                    withServerOauth -> uri.getQueryParameter(PARAM_CODE)?.trim()?.takeIf { it.isNotEmpty() }
                    else -> null
                }

                when {
                    accessToken != null -> onSuccess(accessToken = accessToken, sessionSecretKey = sessionSecretKey, expiresIn = expiresIn)
                    code != null -> onSuccess(code = code)
                    else -> onFail(error)
                }
                return true
            } else if (url.contains("st.cmd=userMain")) {
                // If user presses "forget password" and goes via the account return procedure, the context of
                // OAUTH authorization is lost. We catch successful user navigation to his main page to retry OAUTH
                // with the new currently valid user session
                mWebView.loadUrl(buildOAuthUrl())
                return true
            }
            return super.shouldOverrideUrlLoading(view, url)
        }

        override fun onReceivedError(view: WebView, errorCode: Int, description: String, failingUrl: String) {
            super.onReceivedError(view, errorCode, description, failingUrl)
            showAlert(getErrorMessage(errorCode))
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            super.onReceivedSslError(view, handler, error)
            showAlert(getErrorMessage(error))
        }
    }
}