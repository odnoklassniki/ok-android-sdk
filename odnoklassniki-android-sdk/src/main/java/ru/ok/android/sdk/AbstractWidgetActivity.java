package ru.ok.android.sdk;

import android.app.Activity;
import android.content.Context;
import android.net.http.SslError;
import android.os.Bundle;
import android.view.KeyEvent;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;
import ru.ok.android.sdk.util.OkNetUtil;

public abstract class AbstractWidgetActivity extends Activity {

    protected String mAppId;
    protected String mAccessToken;
    protected String mSessionSecretKey;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle bundle = getIntent().getExtras();
        if (bundle != null) {
            mAppId = bundle.getString(Shared.PARAM_APP_ID);
            mAccessToken = bundle.getString(Shared.PARAM_ACCESS_TOKEN);
            mSessionSecretKey = bundle.getString(Shared.PARAM_SESSION_SECRET_KEY);
        }
    }

    protected abstract String getWidgetId();

    protected abstract void processResult(String result);

    protected abstract void processError(String error);

    protected final String getBaseUrl() {
        return Shared.REMOTE_WIDGETS + "dk?st.cmd=" + getWidgetId() +
                "&st.access_token=" + mAccessToken +
                "&st.app=" + mAppId +
                "&st.return=" + getReturnUrl();
    }

    protected final String getReturnUrl() {
        return "okwidget://" + getWidgetId().toLowerCase();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (KeyEvent.KEYCODE_BACK == keyCode) {
            processError(getString(getCancelledMessageId()));
            return true;
        }
        return false;
    }

    protected abstract int getCancelledMessageId();

    protected final class OkWidgetViewClient extends OkWebViewClient {
        public OkWidgetViewClient(Context context) {
            super(context);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            if (url.startsWith(getReturnUrl())) {
                    Bundle parameters = OkNetUtil.getUrlParameters(url);
                    String result = parameters.getString("result");
                    processResult(result);
                return true;
            }
            return super.shouldOverrideUrlLoading(view, url);
        }

        @Override
        public void onReceivedError(WebView view, int errorCode, final String description,
                                    String failingUrl) {
            super.onReceivedError(view, errorCode, description, failingUrl);
            processError(getErrorMessage(errorCode));
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            super.onReceivedSslError(view, handler, error);
            processError(getErrorMessage(error));
        }
    }
}
