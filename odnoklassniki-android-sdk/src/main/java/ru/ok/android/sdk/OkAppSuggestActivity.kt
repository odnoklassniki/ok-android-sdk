package ru.ok.android.sdk

internal class OkAppSuggestActivity : OkAppInviteActivity() {

    override val widgetId: String
        get() = "WidgetSuggest"

    override val cancelledMessageId: Int
        get() = R.string.suggest_canceled

}
