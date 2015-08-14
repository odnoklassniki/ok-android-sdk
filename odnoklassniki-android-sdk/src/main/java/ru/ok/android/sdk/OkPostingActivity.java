package ru.ok.android.sdk;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.http.SslError;
import android.os.Bundle;
import android.view.KeyEvent;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URLEncoder;

import ru.ok.android.sdk.util.OkEncryptUtil;
import ru.ok.android.sdk.util.OkNetUtil;

/**
 * Created by Valery Ozhiganov
 */
public class OkPostingActivity extends Activity {
    private static final String RETURN_URL = "okposting://widget";
    private static final String WIDGET_URL_TEMPLATE =
            "https://connect.ok.ru/dk?st.cmd=WidgetMediatopicPost&st.access_token=%s&st.app=%s&st.attachment=%s&st.signature=%s&st.return=%s&st.popup=on&st.silent=on&st.utext=%s";

    private String mAppId;
    private String mAttachment;
    private String mAccessToken;
    private String mSessionSecretKey;
    private boolean mUserTextEnable;

    private static String calcSignature(String attachment, String secretKey) {
        String sigSource = String.format("st.attachment=%sst.return=%s%s", attachment, RETURN_URL, secretKey);
        return OkEncryptUtil.toMD5(sigSource);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ok_posting_activity);
        prepareWebView();

        Bundle bundle = savedInstanceState != null ? savedInstanceState : getIntent().getExtras();
        if (bundle != null) {
            mAppId = bundle.getString(Shared.PARAM_APP_ID);
            mAttachment = bundle.getString(Shared.PARAM_ATTACHMENT);
            mAccessToken = bundle.getString(Shared.PARAM_ACCESS_TOKEN);
            mSessionSecretKey = bundle.getString(Shared.PARAM_SESSION_SECRET_KEY);
            mUserTextEnable = bundle.getBoolean(Shared.PARAM_USER_TEXT_ENABLE, false);
        }

        loadPage();
    }

    private void prepareWebView() {
        WebView webView = (WebView) findViewById(R.id.web_view);
        webView.setWebViewClient(new OkPostingViewClient(this));
        webView.getSettings().setJavaScriptEnabled(true);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(Shared.PARAM_APP_ID, mAppId);
        outState.putString(Shared.PARAM_ATTACHMENT, mAttachment);
        outState.putString(Shared.PARAM_ACCESS_TOKEN, mAccessToken);
        outState.putString(Shared.PARAM_SESSION_SECRET_KEY, mSessionSecretKey);
        outState.putBoolean(Shared.PARAM_USER_TEXT_ENABLE, mUserTextEnable);
        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (KeyEvent.KEYCODE_BACK == keyCode) {
            showAlert(getString(R.string.posting_canceled));
            return true;
        }
        return false;
    }

    private void loadPage() {
        String signature = calcSignature(mAttachment, mSessionSecretKey);
        String url = String.format(WIDGET_URL_TEMPLATE, mAccessToken, mAppId,
                URLEncoder.encode(mAttachment), signature, RETURN_URL, mUserTextEnable ? "on" : "off");
        ((WebView) findViewById(R.id.web_view)).loadUrl(url);
    }

    private void showAlert(final String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(message);
        builder.setPositiveButton(getString(R.string.retry), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                loadPage();
            }
        });
        builder.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                processResult(message);
            }
        });
        builder.show();
    }

    private void processResult(String result) {
        Odnoklassniki odnoklassniki = Odnoklassniki.getInstance();
        if (odnoklassniki != null) {
            try {
                JSONObject json = new JSONObject(result);
                String type = json.getString(Shared.PARAM_TYPE);
                if ("error".equals(type)) {
                    odnoklassniki.notifyFailed(json.getString(Shared.PARAM_MESSAGE));
                } else {
                    odnoklassniki.notifySuccess(json);
                }
            } catch (JSONException e) {
                odnoklassniki.notifyFailed(result);
            }
        }
        finish();
    }

    private class OkPostingViewClient extends OkWebViewClient {
        public OkPostingViewClient(Context context) {
            super(context);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            if (url.startsWith(RETURN_URL)) {
                Bundle parameters = OkNetUtil.getUrlParameters(url);
                String result = parameters.getString("result");
                processResult(result);

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
