package ru.ok.android.sdk;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;

import java.net.URLEncoder;

public class OkAuthActivity extends Activity {
    private static final int SSO_ACTIVITY_REQUEST_CODE = 31337;
    private static final String DEFAULT_SECRET_KEY = "6C6B6397C2BCE5EDB7290039";
    private static final String DEFAULT_REDIRECT_URI = "okauth://auth";

    private String mAppId;
    private String mAppKey;
    private String mRedirectUri;
    private String[] mScopes;
    private boolean mOauthOnly;

    private WebView mWebView;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ok_auth_activity);
        prepareWebView();

        Bundle bundle = savedInstanceState != null ? savedInstanceState : getIntent().getExtras();
        mAppId = bundle.getString(Shared.PARAM_CLIENT_ID);
        mAppKey = bundle.getString(Shared.PARAM_APP_KEY);
        mRedirectUri = bundle.getString(Shared.PARAM_REDIRECT_URI);
        if (mRedirectUri == null) {
            mRedirectUri = DEFAULT_REDIRECT_URI;
        }
        mScopes = bundle.getStringArray(Shared.PARAM_SCOPES);
        mOauthOnly = bundle.getBoolean(Shared.PARAM_OAUTH_ONLY);
        auth(mOauthOnly);
    }

    private void prepareWebView() {
        mWebView = (WebView) findViewById(R.id.web_view);
        mWebView.setWebViewClient(new OAuthWebViewClient(this));
        mWebView.getSettings().setJavaScriptEnabled(true);
    }

    @Override
    protected void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(Shared.PARAM_CLIENT_ID, mAppId);
        outState.putString(Shared.PARAM_APP_KEY, mAppKey);
        outState.putString(Shared.PARAM_REDIRECT_URI, mRedirectUri);
        outState.putStringArray(Shared.PARAM_SCOPES, mScopes);
        outState.putBoolean(Shared.PARAM_OAUTH_ONLY, mOauthOnly);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (KeyEvent.KEYCODE_BACK == keyCode) {
            showAlert(getString(R.string.authorization_canceled));
            return true;
        }
        return false;
    }

    private void auth(boolean oauthOnly) {
        if (hasAppInfo()) {
            if (oauthOnly || !trySsoAuthorization()) {
                mWebView.loadUrl(buildOAuthUrl());
            }
        } else {
            // If missing any extras required for auth
            onFail(getString(R.string.no_application_data));
        }
    }

    @SuppressWarnings("deprecation")
    private String buildOAuthUrl() {
        String url = String.format(Shared.OAUTH_GET_TOKEN_URL, mAppId, mRedirectUri);
        if ((mScopes != null) && (mScopes.length > 0)) {
            final String scopesString = URLEncoder.encode(TextUtils.join(";", mScopes));
            url = url + "&scope=" + scopesString;
        }
        return url;
    }

    /* SSO AUTHORIZATION */
    private boolean trySsoAuthorization() {
        boolean ssoAvailable = false;
        final Intent intent = new Intent();
        intent.setClassName("ru.ok.android", "ru.ok.android.external.LoginExternal");
        final ResolveInfo resolveInfo = getPackageManager().resolveActivity(intent, 0);
        if (resolveInfo != null) {
            try {
                final PackageInfo packageInfo = getPackageManager().getPackageInfo(resolveInfo.activityInfo.packageName, PackageManager.GET_SIGNATURES);
                for (final Signature signature : packageInfo.signatures) {
                    if (signature.toCharsString().equals(ODKL_APP_SIGNATURE)) {
                        ssoAvailable = true;
                    }
                }
            } catch (NameNotFoundException exc) {
            }
            if (ssoAvailable) {
                intent.putExtra(Shared.PARAM_CLIENT_ID, mAppId);
                intent.putExtra(Shared.PARAM_CLIENT_SECRET, DEFAULT_SECRET_KEY);
                intent.putExtra(Shared.PARAM_REDIRECT_URI, mRedirectUri);
                if ((mScopes != null) && (mScopes.length > 0)) {
                    intent.putExtra(Shared.PARAM_SCOPES, mScopes);
                }
                try {
                    startActivityForResult(intent, SSO_ACTIVITY_REQUEST_CODE);
                } catch (ActivityNotFoundException exc) {
                    ssoAvailable = false;
                }
            }
        }
        return ssoAvailable;
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        if (requestCode == SSO_ACTIVITY_REQUEST_CODE) {
            boolean error = true;
            final String errorStr = data != null ? data.getStringExtra(Shared.PARAM_ERROR) : "";
            if (resultCode == RESULT_OK) {
                final String accessToken = data.getStringExtra(Shared.PARAM_ACCESS_TOKEN);
                final String sessionSecretKey = data.getStringExtra(Shared.PARAM_SESSION_SECRET_KEY);
                final String refreshToken = data.getStringExtra(Shared.PARAM_REFRESH_TOKEN);

                if (accessToken != null) {
                    error = false;
                    onSuccess(accessToken, sessionSecretKey != null ? sessionSecretKey : refreshToken);
                }
            }
            if (error) {
                onFail(errorStr);
            }
            finish();
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    protected final void onFail(final String error) {
        Bundle bundle = new Bundle();
        bundle.putString(Shared.PARAM_ERROR, error);
        sendBundle(bundle);
        finish();
    }

    protected final void onSuccess(final String accessToken, final String sessionSecretKey) {
        TokenStore.store(this, accessToken, sessionSecretKey);
        final Bundle bundle = new Bundle();
        bundle.putString(Shared.PARAM_ACCESS_TOKEN, accessToken);
        bundle.putString(Shared.PARAM_SESSION_SECRET_KEY, sessionSecretKey);
        sendBundle(bundle);
        finish();
    }

    private void sendBundle(final Bundle bundle) {
        if (Odnoklassniki.hasInstance()) {
            Odnoklassniki.getInstance().onTokenResponseReceived(bundle);
        }
    }

    private boolean hasAppInfo() {
        return (mAppId != null) && (mAppKey != null);
    }

    private void showAlert(final String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(OkAuthActivity.this);
        builder.setMessage(message);
        builder.setPositiveButton(getString(R.string.retry), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                auth(mOauthOnly);
            }
        });
        builder.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                onFail(message);
            }
        });
        builder.show();
    }

    private static final String ODKL_APP_SIGNATURE = "3082025b308201c4a00302010202044f6760f9300d06092a864886f70d01010505003071310c300a06035504061303727573310c300a06035504081303737062310c300a0603550407130373706231163014060355040a130d4f646e6f6b6c6173736e696b6931143012060355040b130b6d6f62696c65207465616d311730150603550403130e416e647265792041736c616d6f763020170d3132303331393136333831375a180f32303636313232313136333831375a3071310c300a06035504061303727573310c300a06035504081303737062310c300a0603550407130373706231163014060355040a130d4f646e6f6b6c6173736e696b6931143012060355040b130b6d6f62696c65207465616d311730150603550403130e416e647265792041736c616d6f7630819f300d06092a864886f70d010101050003818d003081890281810080bea15bf578b898805dfd26346b2fbb662889cd6aba3f8e53b5b27c43a984eeec9a5d21f6f11667d987b77653f4a9651e20b94ff10594f76a93a6a36e6a42f4d851847cf1da8d61825ce020b7020cd1bc2eb435b0d416908be9393516ca1976ff736733c1d48ff17cd57f21ad49e05fc99384273efc5546e4e53c5e9f391c430203010001300d06092a864886f70d0101050500038181007d884df69a9748eabbdcfe55f07360433b23606d3b9d4bca03109c3ffb80fccb7809dfcbfd5a466347f1daf036fbbf1521754c2d1d999f9cbc66b884561e8201459aa414677e411e66360c3840ca4727da77f6f042f2c011464e99f34ba7df8b4bceb4fa8231f1d346f4063f7ba0e887918775879e619786728a8078c76647ed";

    private class OAuthWebViewClient extends OkWebViewClient {
        public OAuthWebViewClient(Context context) {
            super(context);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            if (url.startsWith(mRedirectUri)) {
                Uri uri = Uri.parse(url);
                String fragment = uri.getFragment();
                String accessToken = null, sessionSecretKey = null, error = null;
                for (String property : fragment.split("&")) {
                    String[] splitted = property.split("=");
                    if (splitted.length == 2) {
                        String key = splitted[0], value = splitted[1];
                        switch (key) {
                            case Shared.PARAM_ACCESS_TOKEN:
                                accessToken = value;
                                break;
                            case Shared.PARAM_SESSION_SECRET_KEY:
                            case Shared.PARAM_REFRESH_TOKEN:
                                sessionSecretKey = value;
                                break;
                            case Shared.PARAM_ERROR:
                                error = value;
                                break;
                        }
                    }
                }
                if (accessToken != null) {
                    onSuccess(accessToken, sessionSecretKey);
                } else {
                    onFail(error);
                }
                return true;
            }
            return super.shouldOverrideUrlLoading(view, url);
        }

        @Override
        public void onReceivedError(WebView view, int errorCode, final String description, String failingUrl) {
            super.onReceivedError(view, errorCode, description, failingUrl);
            showAlert(getErrorMessage(errorCode));
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            super.onReceivedSslError(view, handler, error);
            showAlert(getErrorMessage(error));
        }
    }
}