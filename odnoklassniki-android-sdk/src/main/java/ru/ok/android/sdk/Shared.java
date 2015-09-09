package ru.ok.android.sdk;

final class Shared {
    static final String REMOTE_PORTAL = "https://ok.ru/";
    static final String REMOTE_API = "http://api.ok.ru/";
    static final String REMOTE_WIDGETS = "https://connect.ok.ru/";

    static final String OAUTH_GET_TOKEN_URL = REMOTE_PORTAL + "oauth/authorize?client_id=%s&response_type=token&redirect_uri=%s&layout=m";

    // Messages
    static final int MESSAGE_AUTH_RESULT = 1337;

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
    static final String PARAM_MESSENGER = "msngr";
    static final String PARAM_METHOD = "method";
    static final String PARAM_OAUTH_ONLY = "oauth_only";
    static final String PARAM_REDIRECT_URI = "redirect_uri";
    static final String PARAM_REFRESH_TOKEN = "refresh_token";
    static final String PARAM_SCOPES = "scopes";
    static final String PARAM_SESSION_SECRET_KEY = "session_secret_key";
    static final String PARAM_SIGN = "sig";
    static final String PARAM_TYPE = "type";
    static final String PARAM_CODE = "code";
    static final String PARAM_USER_TEXT_ENABLE = "utext";

    // Api
    static final String API_URL = REMOTE_API + "fb.do";

    static final String PREFERENCES_FILE = "oksdkprefs";
}
