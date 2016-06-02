package ru.ok.android.sdk;

public class OkAppSuggestActivity extends OkAppInviteActivity {

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
