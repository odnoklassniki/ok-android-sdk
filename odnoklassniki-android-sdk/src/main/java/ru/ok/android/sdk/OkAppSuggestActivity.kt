package ru.ok.android.sdk

internal class OkAppSuggestActivity : OkAppInviteActivity() {

    override val activityView: Int
        get() = R.layout.ok_app_suggest_activity

    override val widgetId: String
        get() = "WidgetSuggest"

    override val cancelledMessageId: Int
        get() = R.string.suggest_canceled

}
