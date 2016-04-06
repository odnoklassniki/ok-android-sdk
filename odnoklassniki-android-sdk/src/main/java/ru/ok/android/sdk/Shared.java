package ru.ok.android.sdk;

import java.util.Arrays;
import java.util.List;

final class Shared {
    static final String REMOTE_PORTAL = "https://ok.ru/";
    static final String REMOTE_API = "http://api.ok.ru/";
    static final String REMOTE_WIDGETS = "https://connect.ok.ru/";

    static final String OAUTH_GET_TOKEN_URL = REMOTE_PORTAL + "oauth/authorize?client_id=%s&response_type=token&redirect_uri=%s&layout=m";

    // Params
    static final String PARAM_ACCESS_TOKEN = "access_token";
    static final String PARAM_APP_ID = "appId";
    static final String PARAM_APP_KEY = "application_key";
    static final String PARAM_ATTACHMENT = "attachment";
    static final String PARAM_CLIENT_ID = "client_id";
    static final String PARAM_CLIENT_SECRET = "client_secret";
    static final String PARAM_ERROR = "error";
    static final String PARAM_ERROR_MSG = "error_msg";
    static final String PARAM_MESSAGE = "message";
    static final String PARAM_METHOD = "method";
    static final String PARAM_AUTH_TYPE = "auth_type";
    static final String PARAM_REDIRECT_URI = "redirect_uri";
    static final String PARAM_REFRESH_TOKEN = "refresh_token";
    static final String PARAM_EXPIRES_IN = "expires_in";
    static final String PARAM_SCOPES = "scopes";
    static final String PARAM_SESSION_SECRET_KEY = "session_secret_key";
    static final String PARAM_SIGN = "sig";
    static final String PARAM_TYPE = "type";
    static final String PARAM_CODE = "code";
    static final String PARAM_USER_TEXT_ENABLE = "utext";
    static final String PARAM_WIDGET_ARGS = "widget_args";

    // Api
    static final String API_URL = REMOTE_API + "fb.do";

    static final String PREFERENCES_FILE = "oksdkprefs";

    /**
     * viral widget arguments that need to be signed
     */
    static final List<String> WIDGET_SIGNED_ARGS =
            Arrays.asList("st.attachment", "st.return", "st.redirect_uri", "st.state");
}
