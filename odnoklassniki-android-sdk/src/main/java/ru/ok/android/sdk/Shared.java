package ru.ok.android.sdk;

import java.util.Arrays;
import java.util.List;

public final class Shared {
    public static final String REMOTE_PORTAL = "https://ok.ru/";
    public static final String REMOTE_API = "http://api.ok.ru/";
    public static final String REMOTE_WIDGETS = "https://connect.ok.ru/";

    public static final String OAUTH_GET_TOKEN_URL = REMOTE_PORTAL + "oauth/authorize?client_id=%s&response_type=token&redirect_uri=%s&layout=m";

    // Params
    public static final String PARAM_ACCESS_TOKEN = "access_token";
    public static final String PARAM_APP_ID = "appId";
    public static final String PARAM_APP_KEY = "application_key";
    public static final String PARAM_ATTACHMENT = "attachment";
    public static final String PARAM_CLIENT_ID = "client_id";
    public static final String PARAM_CLIENT_SECRET = "client_secret";
    public static final String PARAM_ERROR = "error";
    public static final String PARAM_ERROR_MSG = "error_msg";
    public static final String PARAM_MESSAGE = "message";
    public static final String PARAM_METHOD = "method";
    public static final String PARAM_AUTH_TYPE = "auth_type";
    public static final String PARAM_REDIRECT_URI = "redirect_uri";
    public static final String PARAM_REFRESH_TOKEN = "refresh_token";
    public static final String PARAM_EXPIRES_IN = "expires_in";
    public static final String PARAM_SCOPES = "scopes";
    public static final String PARAM_SESSION_SECRET_KEY = "session_secret_key";
    public static final String PARAM_SIGN = "sig";
    public static final String PARAM_TYPE = "type";
    public static final String PARAM_CODE = "code";
    public static final String PARAM_USER_TEXT_ENABLE = "utext";
    public static final String PARAM_WIDGET_ARGS = "widget_args";
    public static final String PARAM_PLATFORM = "platform";
    public static final String PARAM_LOGGED_IN_USER = "logged_in_user";
    public static final String PARAM_RESULT = "result";

    // Api
    public static final String API_URL = REMOTE_API + "fb.do";

    public static final String APP_PLATFORM = "ANDROID";
    public static final String PREFERENCES_FILE = "oksdkprefs";

    /**
     * viral widget arguments that need to be signed
     */
    public static final List<String> WIDGET_SIGNED_ARGS =
            Arrays.asList("st.attachment", "st.return", "st.redirect_uri", "st.state");
}
