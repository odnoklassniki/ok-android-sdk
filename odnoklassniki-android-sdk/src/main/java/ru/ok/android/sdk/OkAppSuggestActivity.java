package ru.ok.android.sdk;

public class OkAppSuggestActivity extends OkAppInviteActivity {

    public static final int OK_SUGGESTING_REQUEST_CODE = 22893;

    protected int getActivityView() {
        return R.layout.ok_app_suggest_activity;
    }

    @Override
    protected String getWidgetId() {
        return "WidgetSuggest";
    }

    @Override
    protected int getCancelledMessageId() {
        return R.string.suggest_canceled;
    }

}
