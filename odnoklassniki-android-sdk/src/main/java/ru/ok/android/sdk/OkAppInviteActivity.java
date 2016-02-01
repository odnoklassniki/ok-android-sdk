package ru.ok.android.sdk;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.webkit.WebView;
import ru.ok.android.sdk.util.OkEncryptUtil;

public class OkAppInviteActivity extends AbstractWidgetActivity {

    protected HashMap<String, String> args;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle bundle = getIntent().getExtras();
        if (bundle != null) {
            args = (HashMap<String, String>) bundle.getSerializable(Shared.PARAM_WIDGET_ARGS);
        }

        setContentView(getActivityView());
        prepareWebView();
        loadPage();
    }

    protected int getActivityView() {
        return R.layout.ok_app_invite_activity;
    }

    private void prepareWebView() {
        WebView webView = (WebView) findViewById(R.id.web_view);
        webView.setWebViewClient(new OkWidgetViewClient(this));
        webView.getSettings().setJavaScriptEnabled(true);
    }

    protected int getCancelledMessageId() {
        return R.string.invite_canceled;
    }

    private void loadPage() {
        TreeMap<String, String> params = new TreeMap<>();
        if (args != null) {
            for (Map.Entry<String, String> e : args.entrySet()) {
                params.put(e.getKey(), e.getValue());
            }
        }
        params.put("st.return", getReturnUrl());

        StringBuilder sigSource = new StringBuilder();
        StringBuilder url = new StringBuilder(getBaseUrl());
        for (Map.Entry<String, String> e : params.entrySet()) {
            sigSource.append(e.getKey()).append('=').append(e.getValue());
            if (!e.getKey().equals("st.return")) {
                url.append('&').append(e.getKey()).append('=').append(e.getValue());
            }
        }
        String signature = OkEncryptUtil.toMD5(sigSource + mSessionSecretKey);
        url.append("&st.popup=on&st.silent=on").append("&st.signature=").append(signature);
        ((WebView) findViewById(R.id.web_view)).loadUrl(url.toString());
    }

    @Override
    protected String getWidgetId() {
        return "WidgetInvite";
    }

    @Override
    protected void processResult(String result) {
        Odnoklassniki odnoklassniki = Odnoklassniki.getInstance();
        if (odnoklassniki != null) {
            try {
                JSONObject json = new JSONObject(result);
                String code = json.optString(Shared.PARAM_CODE);
                if ("ok".equalsIgnoreCase(code)) {
                    odnoklassniki.notifySuccess(json);
                } else {
                    odnoklassniki.notifyFailed(json.getString(Shared.PARAM_MESSAGE));
                }
            } catch (JSONException e) {
                odnoklassniki.notifyFailed(result);
            }
        }
        finish();
    }

    @Override
    protected void processError(final String error) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(error);
        builder.setPositiveButton(getString(R.string.retry), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                loadPage();
            }
        });
        builder.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                processResult(error);
            }
        });
        builder.show();
    }
}
