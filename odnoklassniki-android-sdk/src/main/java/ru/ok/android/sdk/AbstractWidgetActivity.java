package ru.ok.android.sdk;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import android.app.Activity;
import android.content.Context;
import android.net.http.SslError;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.KeyEvent;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;
import ru.ok.android.sdk.util.OkEncryptUtil;
import ru.ok.android.sdk.util.OkRequestUtil;

public abstract class AbstractWidgetActivity extends Activity {

    protected static final HashMap<String, String> DEFAULT_OPTIONS;

    static {
        DEFAULT_OPTIONS = new HashMap<>();
        DEFAULT_OPTIONS.put("st.popup", "on");
        DEFAULT_OPTIONS.put("st.silent", "on");
    }

    protected String mAppId;
    protected String mAccessToken;
    protected String mSessionSecretKey;
    protected final HashMap<String, String> args = new HashMap<>();
    protected boolean retryAllowed = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        args.clear();
        Bundle bundle = getIntent().getExtras();
        if (bundle != null) {
            mAppId = bundle.getString(Shared.PARAM_APP_ID);
            mAccessToken = bundle.getString(Shared.PARAM_ACCESS_TOKEN);
            mSessionSecretKey = bundle.getString(Shared.PARAM_SESSION_SECRET_KEY);
            if (bundle.containsKey(Shared.PARAM_WIDGET_ARGS)) {
                HashMap<String, String> map = (HashMap<String, String>) bundle.getSerializable(Shared.PARAM_WIDGET_ARGS);
                if (map != null) {
                    args.putAll(map);
                }
            }
            if (bundle.containsKey(Shared.PARAM_WIDGET_RETRY_ALLOWED)) {
                retryAllowed = bundle.getBoolean(Shared.PARAM_WIDGET_RETRY_ALLOWED, true);
            }
        }
    }

    protected abstract String getWidgetId();

    protected abstract void processResult(String result);

    protected abstract void processError(String error);

    protected final String getBaseUrl() {
        return Odnoklassniki.getInstance().getConnectBaseUrl() +
                "dk?st.cmd=" + getWidgetId() +
                "&st.access_token=" + mAccessToken +
                "&st.app=" + mAppId +
                "&st.return=" + getReturnUrl();
    }

    protected final String getReturnUrl() {
        return "okwidget://" + getWidgetId().toLowerCase();
    }

    /**
     * Prepares widget URL based on widget parameters
     *
     * @param options widget options (if null, default options are being sent)
     * @return widget url
     * @see #args
     * @see #DEFAULT_OPTIONS
     */
    protected String prepareUrl(@Nullable HashMap<String, String> options) {
        TreeMap<String, String> params = new TreeMap<>();
        for (Map.Entry<String, String> e : args.entrySet()) {
            params.put(e.getKey(), e.getValue());
        }
        params.put("st.return", getReturnUrl());

        StringBuilder sigSource = new StringBuilder(200);
        StringBuilder url = new StringBuilder(getBaseUrl());
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (Shared.WIDGET_SIGNED_ARGS.contains(e.getKey())) {
                sigSource.append(e.getKey()).append('=').append(e.getValue());
            }
            if (!e.getKey().equals("st.return")) {
                url.append('&').append(e.getKey()).append('=').append(OkRequestUtil.encode(e.getValue()));
            }
        }
        String signature = OkEncryptUtil.toMD5(sigSource + mSessionSecretKey);

        if (options == null) {
            options = DEFAULT_OPTIONS;
        }
        for (Map.Entry<String, String> e : options.entrySet()) {
            url.append('&').append(e.getKey()).append('=').append(e.getValue());
        }
        url.append("&st.signature=").append(signature);

        return url.toString();
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
                Bundle parameters = OkRequestUtil.getUrlParameters(url);
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
