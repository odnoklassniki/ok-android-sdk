package ru.ok.android.sdk.util

enum class OkAuthType {

    /**
     * Only authorize via installed OK application's provided SSO (client)<br></br>
     * No login will be required if user is already logged in the OK application
     */
    NATIVE_SSO,

    /**
     * Only authorize via client OAUTH operated via webview (client)<br></br>
     * Login is required in most cases other than repeated launch of the same application
     */
    WEBVIEW_OAUTH,

    /**
     * Try SSO authorization, and if unavailable, switch to webview (client)
     */
    ANY,

}
