package ru.ok.android.sdk;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.webkit.WebView;

import org.json.JSONException;
import org.json.JSONObject;

public class OkAppInviteActivity extends AbstractWidgetActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        String url = prepareUrl(null);
        ((WebView) findViewById(R.id.web_view)).loadUrl(url);
    }

    @Override
    protected String getWidgetId() {
        return "WidgetInvite";
    }

    @Override
    protected void processResult(String result) {
        Odnoklassniki odnoklassniki = Odnoklassniki.getInstance();
        Intent resultIntent = new Intent();
        if (odnoklassniki != null) {
            try {
                JSONObject json = new JSONObject(result);
                String code = json.optString(Shared.PARAM_CODE);
                if ("ok".equalsIgnoreCase(code)) {
                    resultIntent.putExtra(Shared.PARAM_RESULT, json.toString());
                } else {
                    resultIntent.putExtra(Shared.PARAM_ERROR, json.getString(Shared.PARAM_MESSAGE));
                }
            } catch (JSONException e) {
                resultIntent.putExtra(Shared.PARAM_ERROR, result);
            }
        }
        setResult(Activity.RESULT_OK, resultIntent);
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
