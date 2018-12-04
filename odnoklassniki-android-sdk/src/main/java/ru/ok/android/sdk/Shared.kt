package ru.ok.android.sdk

const val REMOTE_API = "https://api.ok.ru/"
const val REMOTE_WIDGETS = "https://connect.ok.ru/"

// Params
const val PARAM_ACCESS_TOKEN = "access_token"
const val PARAM_APP_ID = "appId"
const val PARAM_APP_KEY = "application_key"
const val PARAM_ATTACHMENT = "attachment"
const val PARAM_CLIENT_ID = "client_id"
const val PARAM_CLIENT_SECRET = "client_secret"
const val PARAM_ERROR = "error"
const val PARAM_ERROR_MSG = "error_msg"
const val PARAM_MESSAGE = "message"
const val PARAM_METHOD = "method"
const val PARAM_AUTH_TYPE = "auth_type"
const val PARAM_REDIRECT_URI = "redirect_uri"
const val PARAM_REFRESH_TOKEN = "refresh_token"
const val PARAM_EXPIRES_IN = "expires_in"
const val PARAM_SCOPES = "scopes"
const val PARAM_SESSION_SECRET_KEY = "session_secret_key"
const val PARAM_SIGN = "sig"
const val PARAM_TYPE = "type"
const val PARAM_CODE = "code"
const val PARAM_USER_TEXT_ENABLE = "utext"
const val PARAM_WIDGET_ARGS = "widget_args"
const val PARAM_WIDGET_RETRY_ALLOWED = "widget_retry_allowed"
const val PARAM_PLATFORM = "platform"
const val PARAM_LOGGED_IN_USER = "logged_in_user"
const val PARAM_RESULT = "result"

// android-specific params
const val PARAM_ACTIVITY_RESULT = "activity_result"

const val APP_PLATFORM = "ANDROID"
const val PREFERENCES_FILE = "oksdkprefs"

/**
 * viral widget arguments that need to be signed
 */
val WIDGET_SIGNED_ARGS = arrayOf("st.attachment", "st.return", "st.redirect_uri", "st.state")

// android activity request codes

const val OK_AUTH_REQUEST_CODE = 22890
const val OK_POSTING_REQUEST_CODE = 22891
const val OK_INVITING_REQUEST_CODE = 22892
const val OK_SUGGESTING_REQUEST_CODE = 22893

const val LOG_TAG = "ok_android_sdk"

const val API_ERR_PERMISSION_DENIED = 10
