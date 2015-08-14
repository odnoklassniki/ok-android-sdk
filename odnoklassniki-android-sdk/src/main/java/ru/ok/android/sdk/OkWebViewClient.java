package ru.ok.android.sdk;

import android.content.Context;
import android.net.http.SslError;
import android.os.Build;
import android.view.View;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import ru.ok.android.sdk.R;

/**
 * Created by Valery Ozhiganov
 */
class OkWebViewClient extends WebViewClient {
    private final Context mContext;
    protected boolean showPage = true;

    public OkWebViewClient(Context context) {
        super();
        mContext = context;
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        showPage = true;
        return super.shouldOverrideUrlLoading(view, url);
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        if (showPage) {
            view.setVisibility(View.VISIBLE);
        } else {
            view.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void onReceivedError(WebView view, int errorCode, final String description, String failingUrl) {
        showPage = false;
        super.onReceivedError(view, errorCode, description, failingUrl);
    }

    @Override
    public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
        showPage = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
            super.onReceivedSslError(view, handler, error);
        }
    }

    public String getErrorMessage(int errorCode) {
        String message = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR) {
            switch (errorCode) {
                case ERROR_CONNECT:
                    message = getString(R.string.error_connect);
                    break;
                case ERROR_FAILED_SSL_HANDSHAKE:
                    message = getString(R.string.error_failed_ssl_handshake);
                    break;
                case ERROR_HOST_LOOKUP:
                    message = getString(R.string.error_host_lookup);
                    break;
                case ERROR_TIMEOUT:
                    message = getString(R.string.error_timeout);
                    break;
            }
        }
        return message != null ? message : getString(R.string.error_unknown);
    }

    public String getErrorMessage(SslError error) {
        String message = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
            int errorCode = error.getPrimaryError();
            switch (errorCode) {
                case SslError.SSL_EXPIRED:
                    message = getString(R.string.error_ssl_expired);
                    break;
                case SslError.SSL_IDMISMATCH:
                    message = getString(R.string.error_ssl_id_mismatch);
                    break;
                case SslError.SSL_NOTYETVALID:
                    message = getString(R.string.error_ssl_not_yet_valid);
                    break;
                case SslError.SSL_UNTRUSTED:
                    message = getString(R.string.error_ssl_untrusted);
                    break;
            }

            if (message == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH
                    && errorCode == SslError.SSL_DATE_INVALID) {
                message = getString(R.string.error_ssl_date_invalid);
            }
        }

        return message != null ? message : getString(R.string.error_unknown);
    }

    private String getString(int resId) {
        return mContext.getString(resId);
    }
}
