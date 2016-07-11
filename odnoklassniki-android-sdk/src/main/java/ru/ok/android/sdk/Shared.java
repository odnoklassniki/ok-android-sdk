package ru.ok.android.sdk;

import java.util.Arrays;
import java.util.List;

public interface Shared {
    String REMOTE_PORTAL = "https://ok.ru/";
    String REMOTE_API = "http://api.ok.ru/";
    String REMOTE_WIDGETS = "https://connect.ok.ru/";

    String OAUTH_GET_TOKEN_URL = REMOTE_PORTAL + "oauth/authorize?client_id=%s&response_type=token&redirect_uri=%s&layout=m";

    // Params
    String PARAM_ACCESS_TOKEN = "access_token";
    String PARAM_APP_ID = "appId";
    String PARAM_APP_KEY = "application_key";
    String PARAM_ATTACHMENT = "attachment";
    String PARAM_CLIENT_ID = "client_id";
    String PARAM_CLIENT_SECRET = "client_secret";
    String PARAM_ERROR = "error";
    String PARAM_ERROR_MSG = "error_msg";
    String PARAM_MESSAGE = "message";
    String PARAM_METHOD = "method";
    String PARAM_AUTH_TYPE = "auth_type";
    String PARAM_REDIRECT_URI = "redirect_uri";
    String PARAM_REFRESH_TOKEN = "refresh_token";
    String PARAM_EXPIRES_IN = "expires_in";
    String PARAM_SCOPES = "scopes";
    String PARAM_SESSION_SECRET_KEY = "session_secret_key";
    String PARAM_SIGN = "sig";
    String PARAM_TYPE = "type";
    String PARAM_CODE = "code";
    String PARAM_USER_TEXT_ENABLE = "utext";
    String PARAM_WIDGET_ARGS = "widget_args";
    String PARAM_PLATFORM = "platform";
    String PARAM_LOGGED_IN_USER = "logged_in_user";
    String PARAM_RESULT = "result";
    String PARAM_SDK_TOKEN = "sdkToken";

    // android-specific params
    String PARAM_ACTIVITY_RESULT = "activity_result";

    // Api
    String API_URL = REMOTE_API + "fb.do";

    String APP_PLATFORM = "ANDROID";
    String PREFERENCES_FILE = "oksdkprefs";

    /**
     * viral widget arguments that need to be signed
     */
    List<String> WIDGET_SIGNED_ARGS =
            Arrays.asList("st.attachment", "st.return", "st.redirect_uri", "st.state");

    // android activity request codes

    int OK_AUTH_REQUEST_CODE = 22890;
    int OK_POSTING_REQUEST_CODE = 22891;
    int OK_INVITING_REQUEST_CODE = 22892;
    int OK_SUGGESTING_REQUEST_CODE = 22893;

    String LOG_TAG = "ok_android_sdk";
}
