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
import android.webkit.SslErrorHandler
import android.webkit.WebView
import ru.ok.android.sdk.util.OkAuthType
import java.net.URLEncoder

const val RESULT_FAILED = 2
const val RESULT_CANCELLED = 3

private const val SSO_ACTIVITY_REQUEST_CODE = 31337
private const val DEFAULT_SECRET_KEY = "6C6B6397C2BCE5EDB7290039"
private const val DEFAULT_REDIRECT_URI = "okauth://auth"
private const val SSO_STARTED = "SSO_STARTED"

private const val ODKL_APP_SIGNATURE = "3082025b308201c4a00302010202044f6760f9300d06092a864886f70d01010505003071310c300a06035504061303727573310c300a06035504081303737062310c300a0603550407130373706231163014060355040a130d4f646e6f6b6c6173736e696b6931143012060355040b130b6d6f62696c65207465616d311730150603550403130e416e647265792041736c616d6f763020170d3132303331393136333831375a180f32303636313232313136333831375a3071310c300a06035504061303727573310c300a06035504081303737062310c300a0603550407130373706231163014060355040a130d4f646e6f6b6c6173736e696b6931143012060355040b130b6d6f62696c65207465616d311730150603550403130e416e647265792041736c616d6f7630819f300d06092a864886f70d010101050003818d003081890281810080bea15bf578b898805dfd26346b2fbb662889cd6aba3f8e53b5b27c43a984eeec9a5d21f6f11667d987b77653f4a9651e20b94ff10594f76a93a6a36e6a42f4d851847cf1da8d61825ce020b7020cd1bc2eb435b0d416908be9393516ca1976ff736733c1d48ff17cd57f21ad49e05fc99384273efc5546e4e53c5e9f391c430203010001300d06092a864886f70d0101050500038181007d884df69a9748eabbdcfe55f07360433b23606d3b9d4bca03109c3ffb80fccb7809dfcbfd5a466347f1daf036fbbf1521754c2d1d999f9cbc66b884561e8201459aa414677e411e66360c3840ca4727da77f6f042f2c011464e99f34ba7df8b4bceb4fa8231f1d346f4063f7ba0e887918775879e619786728a8078c76647ed"

class OkAuthActivity : Activity() {
    private var mAppId: String? = null
    private var mAppKey: String? = null
    private var mRedirectUri: String? = null
    private lateinit var mScopes: Array<String>
    private var authType: OkAuthType? = null
    private var allowDebugOkSso = false
    private var ssoAuthorizationStarted = false

    private lateinit var mWebView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.ok_auth_activity)
        prepareWebView()

        val bundle = savedInstanceState ?: intent.extras
        mAppId = bundle!!.getString(Shared.PARAM_CLIENT_ID)
        mAppKey = bundle.getString(Shared.PARAM_APP_KEY)
        mRedirectUri = bundle.getString(Shared.PARAM_REDIRECT_URI) ?: DEFAULT_REDIRECT_URI
        mScopes = bundle.getStringArray(Shared.PARAM_SCOPES) ?: arrayOf()
        authType = bundle.getSerializable(Shared.PARAM_AUTH_TYPE) as OkAuthType
        allowDebugOkSso = bundle.getBoolean(Shared.PARAM_ALLOW_DEBUG_OK_SSO)
        ssoAuthorizationStarted = bundle.getBoolean(SSO_STARTED, false)

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
        outState.putString(Shared.PARAM_CLIENT_ID, mAppId)
        outState.putString(Shared.PARAM_APP_KEY, mAppKey)
        outState.putString(Shared.PARAM_REDIRECT_URI, mRedirectUri)
        outState.putStringArray(Shared.PARAM_SCOPES, mScopes)
        outState.putSerializable(Shared.PARAM_AUTH_TYPE, authType)
        outState.putBoolean(SSO_STARTED, ssoAuthorizationStarted)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (KeyEvent.KEYCODE_BACK == keyCode) {
            showAlert(getString(R.string.authorization_canceled))
            return true
        }
        return false
    }

    private fun auth() {
        if (!hasAppInfo()) {
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
        var url = "${Odnoklassniki.instance.connectBaseUrl}oauth/authorize?client_id=$mAppId&response_type=token&redirect_uri=$mRedirectUri&layout=m&platform=${Shared.APP_PLATFORM}"
        if (!mScopes.isNullOrEmpty()) {
            val scopesString = URLEncoder.encode(mScopes.joinToString(separator = ";"))
            url = "$url&scope=$scopesString"
        }
        return url
    }

    private fun resolveOkAppLogin(intent: Intent, aPackage: String): ResolveInfo {
        intent.setClassName(aPackage, "ru.ok.android.external.LoginExternal")
        return packageManager.resolveActivity(intent, 0)
    }

    /* SSO AUTHORIZATION */
    @Suppress("DEPRECATION")
    private fun startSsoAuthorization(): Boolean {
        var ssoAvailable = false
        val intent = Intent()
        var resolveInfo: ResolveInfo? = resolveOkAppLogin(intent, "ru.ok.android")
        if (allowDebugOkSso && resolveInfo == null) {
            resolveInfo = resolveOkAppLogin(intent, "ru.ok.android.debug")
        }
        if (resolveInfo != null) {
            try {
                val packageInfo = packageManager.getPackageInfo(resolveInfo.activityInfo.packageName, PackageManager.GET_SIGNATURES)
                if (packageInfo == null || packageInfo.versionCode < 120) {
                    return false
                }
                ssoAvailable = packageInfo.signatures.any { it.toCharsString() == ODKL_APP_SIGNATURE }
            } catch (ignore: NameNotFoundException) {
            }

            if (ssoAvailable) {
                intent.putExtra(Shared.PARAM_CLIENT_ID, mAppId)
                intent.putExtra(Shared.PARAM_CLIENT_SECRET, DEFAULT_SECRET_KEY)
                intent.putExtra(Shared.PARAM_REDIRECT_URI, mRedirectUri)
                if (mScopes.isNotEmpty()) intent.putExtra(Shared.PARAM_SCOPES, mScopes)
                try {
                    startActivityForResult(intent, SSO_ACTIVITY_REQUEST_CODE)
                } catch (exc: ActivityNotFoundException) {
                    ssoAvailable = false
                }
            }
        }
        return ssoAvailable
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == SSO_ACTIVITY_REQUEST_CODE) {
            ssoAuthorizationStarted = false
            var error = true
            val errorStr = data?.getStringExtra(Shared.PARAM_ERROR) ?: ""
            if (resultCode == Activity.RESULT_OK) {
                val accessToken = data?.getStringExtra(Shared.PARAM_ACCESS_TOKEN)
                val sessionSecretKey = data?.getStringExtra(Shared.PARAM_SESSION_SECRET_KEY)
                val refreshToken = data?.getStringExtra(Shared.PARAM_REFRESH_TOKEN)
                val expiresIn = data?.getLongExtra(Shared.PARAM_EXPIRES_IN, 0) ?: 0

                if (accessToken != null) {
                    error = false
                    onSuccess(accessToken, sessionSecretKey ?: refreshToken, expiresIn)
                }
            }
            if (error) onFail(errorStr)
            finish()
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    protected fun onCancel(error: String) {
        val result = Intent()
        result.putExtra(Shared.PARAM_ERROR, error)
        setResult(RESULT_CANCELLED, result)
        finish()
    }

    protected fun onFail(error: String?) {
        val result = Intent()
        result.putExtra(Shared.PARAM_ERROR, error)
        setResult(RESULT_FAILED, result)
        finish()
    }

    protected fun onSuccess(accessToken: String, sessionSecretKey: String?, expiresIn: Long) {
        TokenStore.store(this, accessToken, sessionSecretKey!!)
        val result = Intent()
        result.putExtra(Shared.PARAM_ACCESS_TOKEN, accessToken)
        result.putExtra(Shared.PARAM_SESSION_SECRET_KEY, sessionSecretKey)
        if (expiresIn > 0) result.putExtra(Shared.PARAM_EXPIRES_IN, expiresIn)
        setResult(Activity.RESULT_OK, result)
        finish()
    }

    private fun hasAppInfo(): Boolean = mAppId != null && mAppKey != null

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
            if (url.startsWith(mRedirectUri!!)) {
                val uri = Uri.parse(url)
                val fragment = uri.fragment
                var accessToken: String? = null
                var sessionSecretKey: String? = null
                var error: String? = null
                var expiresIn: Long = 0
                if (fragment != null) {
                    for (property in fragment.split("&".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
                        val splitted = property.split("=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                        if (splitted.size == 2) {
                            val key = splitted[0]
                            val value = splitted[1]
                            when (key) {
                                Shared.PARAM_ACCESS_TOKEN -> accessToken = value
                                Shared.PARAM_SESSION_SECRET_KEY, Shared.PARAM_REFRESH_TOKEN -> sessionSecretKey = value
                                Shared.PARAM_ERROR -> error = value
                                Shared.PARAM_EXPIRES_IN -> expiresIn = if (value.isEmpty()) 0 else java.lang.Long.parseLong(value)
                            }
                        }
                    }
                }
                if (accessToken != null) {
                    onSuccess(accessToken, sessionSecretKey, expiresIn)
                } else {
                    onFail(error)
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