package ru.ok.android.sdk;

import java.io.IOException;
import java.util.Collection;
import java.util.Currency;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.http.client.HttpClient;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import ru.ok.android.sdk.util.OkAuthType;
import ru.ok.android.sdk.util.OkEncryptUtil;
import ru.ok.android.sdk.util.OkNetUtil;
import ru.ok.android.sdk.util.OkPayment;
import ru.ok.android.sdk.util.OkScope;
import ru.ok.android.sdk.util.OkThreadUtil;

public class Odnoklassniki {
    private static Odnoklassniki sOdnoklassniki;

    /**
     * This method is required to be called before {@link Odnoklassniki#getInstance()}<br>
     * Note that instance is only created once. Multiple calls to this method wont' create multiple instances of the object
     */
    public static Odnoklassniki createInstance(final Context context, final String appId, final String appKey) {
        if ((appId == null) || (appKey == null)) {
            throw new IllegalArgumentException(context.getString(R.string.no_application_data));
        }
        if (sOdnoklassniki == null) {
            sOdnoklassniki = new Odnoklassniki(context.getApplicationContext(), appId, appKey);
        }
        return sOdnoklassniki;
    }

    /**
     * Get previously created instance.<br>
     * You must always call {@link Odnoklassniki#createInstance(Context, String, String)} before calling this method, or {@link IllegalStateException} will be thrown
     */
    public static Odnoklassniki getInstance() {
        if (sOdnoklassniki == null) {
            throw new IllegalStateException("No instance available. Odnoklassniki.createInstance() needs to be called before Odnoklassniki.getInstance()");
        }
        return sOdnoklassniki;
    }

    public static boolean hasInstance() {
        return (sOdnoklassniki != null);
    }

    private Odnoklassniki(final Context context, final String appId, final String appKey) {
        this.mContext = context;

        // APP INFO
        this.mAppId = appId;
        this.mAppKey = appKey;

        // HTTPCLIENT
        final HttpParams params = new BasicHttpParams();
        final SchemeRegistry registry = new SchemeRegistry();
        registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
        final ClientConnectionManager cm = new ThreadSafeClientConnManager(params, registry);
        mHttpClient = new DefaultHttpClient(cm, params);
        okPayment = new OkPayment(context);

        // RESTORE
        mAccessToken = TokenStore.getStoredAccessToken(context);
        mSessionSecretKey = TokenStore.getStoredSessionSecretKey(context);
        sdkToken = TokenStore.getSdkToken(context);
    }

    private Context mContext;

    // Application info
    protected final String mAppId;
    protected final String mAppKey;

    // Current tokens
    protected String mAccessToken;
    protected String mSessionSecretKey;
    protected String sdkToken;

    // Stuff
    protected final HttpClient mHttpClient;
    protected final OkPayment okPayment;

    /**
     * Set true if wish to support logging in via OK debug application installed instead of release one
     */
    protected boolean allowDebugOkSso;

    /**
     * Starts user authorization
     *
     * @param redirectUri the URI to which the access_token will be redirected
     * @param authType    selected auth type
     * @param scopes      {@link OkScope} - application request permissions as per {@link OkScope}.
     * @see OkAuthType
     */
    public final void requestAuthorization(Activity activity, @Nullable String redirectUri,
                                           OkAuthType authType, final String... scopes) {
        final Intent intent = new Intent(activity, OkAuthActivity.class);
        intent.putExtra(Shared.PARAM_CLIENT_ID, mAppId);
        intent.putExtra(Shared.PARAM_APP_KEY, mAppKey);
        intent.putExtra(Shared.PARAM_REDIRECT_URI, redirectUri);
        intent.putExtra(Shared.PARAM_AUTH_TYPE, authType);
        intent.putExtra(Shared.PARAM_SCOPES, scopes);
        intent.putExtra(Shared.PARAM_ALLOW_DEBUG_OK_SSO, allowDebugOkSso);
        activity.startActivityForResult(intent, Shared.OK_AUTH_REQUEST_CODE);
    }

    public boolean isActivityRequestOAuth(int requestCode) {
        return requestCode == Shared.OK_AUTH_REQUEST_CODE;
    }

    public boolean onAuthActivityResult(int request, int result, @Nullable Intent intent, OkListener listener) {
        if (isActivityRequestOAuth(request)) {

            if (intent == null) {
                JSONObject json = new JSONObject();
                try {
                    json.put(Shared.PARAM_ACTIVITY_RESULT, result);
                } catch (JSONException ignore) {
                }
                listener.onError(json.toString());
            } else {
                final String accessToken = intent.getStringExtra(Shared.PARAM_ACCESS_TOKEN);
                if (accessToken == null) {
                    String error = intent.getStringExtra(Shared.PARAM_ERROR);
                    listener.onError(error);
                } else {
                    final String sessionSecretKey = intent.getStringExtra(Shared.PARAM_SESSION_SECRET_KEY);
                    final String refreshToken = intent.getStringExtra(Shared.PARAM_REFRESH_TOKEN);
                    long expiresIn = intent.getLongExtra(Shared.PARAM_EXPIRES_IN, 0);
                    mAccessToken = accessToken;
                    mSessionSecretKey = sessionSecretKey != null ? sessionSecretKey : refreshToken;
                    JSONObject json = new JSONObject();
                    try {
                        json.put(Shared.PARAM_ACCESS_TOKEN, mAccessToken);
                        json.put(Shared.PARAM_SESSION_SECRET_KEY, mSessionSecretKey);
                        if (expiresIn > 0) {
                            json.put(Shared.PARAM_EXPIRES_IN, expiresIn);
                        }
                    } catch (JSONException ignore) {
                    }
                    onValidSessionAppeared();
                    listener.onSuccess(json);
                }
            }

            return true;
        }
        return false;
    }

    public boolean isActivityRequestPost(int requestCode) {
        return requestCode == Shared.OK_POSTING_REQUEST_CODE;
    }

    public boolean isActivityRequestInvite(int requestCode) {
        return requestCode == Shared.OK_INVITING_REQUEST_CODE;
    }

    public boolean isActivityRequestSuggest(int requestCode) {
        return requestCode == Shared.OK_SUGGESTING_REQUEST_CODE;
    }

    public boolean isActivityRequestViral(int request) {
        return isActivityRequestPost(request) || isActivityRequestInvite(request) || isActivityRequestSuggest(request);
    }

    public boolean onActivityResultResult(int request, int result, @Nullable Intent intent, OkListener listener) {
        if (isActivityRequestViral(request)) {

            if (intent == null) {
                JSONObject json = new JSONObject();
                try {
                    json.put(Shared.PARAM_ACTIVITY_RESULT, result);
                } catch (JSONException ignore) {
                }
                listener.onError(json.toString());
            } else {
                if (intent.hasExtra(Shared.PARAM_ERROR)) {
                    listener.onError(intent.getStringExtra(Shared.PARAM_ERROR));
                } else {
                    try {
                        listener.onSuccess(new JSONObject(intent.getStringExtra(Shared.PARAM_RESULT)));
                    } catch (JSONException e) {
                        listener.onError(intent.getStringExtra(Shared.PARAM_RESULT));
                    }
                }
            }

            return true;
        }
        return false;
    }

    protected final void notifyFailed(final OkListener listener, final String error) {
        if (listener != null) {
            OkThreadUtil.executeOnMain(new Runnable() {
                public void run() {
                    listener.onError(error);
                }
            });
        }
    }

    protected final void notifySuccess(final OkListener listener, final JSONObject json) {
        if (listener != null) {
            OkThreadUtil.executeOnMain(new Runnable() {
                public void run() {
                    listener.onSuccess(json);
                }
            });
        }
    }

    /**
     * Call an API method and get the result as a String.
     * <p/>
     * <b>Note that those calls MUST be performed in a non-UI thread.</b>
     *
     * @param apiMethod  - odnoklassniki api method.
     * @param params     - map of key-value params
     * @param httpMethod - only "get" and "post" are supported.
     * @return query result
     * @throws IOException
     * @see #request(String, Map, EnumSet)
     */
    @Deprecated
    public final String request(final String apiMethod, final Map<String, String> params, final String httpMethod)
            throws IOException {
        if (TextUtils.isEmpty(apiMethod)) {
            throw new IllegalArgumentException(mContext.getString(R.string.api_method_cant_be_empty));
        }
        Map<String, String> requestParams = new TreeMap<>();
        if ((params != null) && !params.isEmpty()) {
            requestParams.putAll(params);
        }
        requestParams.put(Shared.PARAM_APP_KEY, mAppKey);
        requestParams.put(Shared.PARAM_METHOD, apiMethod);
        requestParams.put(Shared.PARAM_PLATFORM, Shared.APP_PLATFORM);
        signParameters(requestParams);
        requestParams.put(Shared.PARAM_ACCESS_TOKEN, mAccessToken);
        final String requestUrl = Shared.API_URL;
        String response;
        if ("post".equalsIgnoreCase(httpMethod)) {
            response = OkNetUtil.performPostRequest(mHttpClient, requestUrl, requestParams);
        } else {
            response = OkNetUtil.performGetRequest(mHttpClient, requestUrl, requestParams);
        }
        return response;
    }

    /**
     * Performs a REST API request and gets result as a string<br/>
     * <br/>
     * Note that a method is synchronous so should not be called from UI thread<br/>
     *
     * @param method REST method
     * @param params request params
     * @param mode   request mode
     * @return query result
     * @throws IOException
     * @see OkRequestMode#DEFAULT OkRequestMode.DEFAULT default request mode
     */
    public final String request(String method,
                                @Nullable Map<String, String> params,
                                @Nullable EnumSet<OkRequestMode> mode)
            throws IOException {

        if (TextUtils.isEmpty(method)) {
            throw new IllegalArgumentException(mContext.getString(R.string.api_method_cant_be_empty));
        }
        if (mode == null) {
            mode = OkRequestMode.DEFAULT;
        }
        Map<String, String> requestParams = new TreeMap<>();
        if ((params != null) && !params.isEmpty()) {
            requestParams.putAll(params);
        }
        requestParams.put(Shared.PARAM_APP_KEY, mAppKey);
        requestParams.put(Shared.PARAM_METHOD, method);
        requestParams.put(Shared.PARAM_PLATFORM, Shared.APP_PLATFORM);
        if (mode.contains(OkRequestMode.SDK_SESSION)) {
            if (TextUtils.isEmpty(sdkToken)) {
                throw new IllegalArgumentException("SDK token is required for method call, have not forget to call sdkInit?");
            }
            requestParams.put(Shared.PARAM_SDK_TOKEN, sdkToken);
        }
        if (mode.contains(OkRequestMode.SIGNED)) {
            signParameters(requestParams);
            requestParams.put(Shared.PARAM_ACCESS_TOKEN, mAccessToken);
        }
        final String requestUrl = Shared.API_URL;
        String response;
        if (mode.contains(OkRequestMode.POST)) {
            response = OkNetUtil.performPostRequest(mHttpClient, requestUrl, requestParams);
        } else {
            response = OkNetUtil.performGetRequest(mHttpClient, requestUrl, requestParams);
        }
        return response;
    }

    /**
     * Call an API method and get the result as a String.
     * <p/>
     * Note, that those calls <b>MUST be performed in a non-UI thread</b>.<br/>
     * Note, that if an api method does not return JSONObject but might return array or just a value,
     * this method should not be used. Thus it is preferable to use #request(String, Map, EnumSet) instead
     *
     * @param apiMethod  - odnoklassniki api method.
     * @param params     - map of key-value params
     * @param httpMethod - only "get" and "post" are supported.
     * @param listener   - listener which will be called after method call
     * @throws IOException
     * @see #request(String, Map, EnumSet)
     */
    public final void request(final String apiMethod, final Map<String, String> params,
                              final String httpMethod, OkListener listener) throws IOException {
        String response = request(apiMethod, params, httpMethod);
        try {
            JSONObject json = new JSONObject(response);
            if (json.has(Shared.PARAM_ERROR_MSG)) {
                notifyFailed(listener, json.getString(Shared.PARAM_ERROR_MSG));
            } else {
                notifySuccess(listener, json);
            }
        } catch (JSONException e) {
            notifyFailed(listener, response);
        }
    }

    /**
     * Convenience method to send invitation to the application to friends.
     * <p/>
     * <b>Important: User must confirm the list of recipients. It must be obvious for user, that his action will result sending the pack of invitations to other users. Violating this rule will cause the application to be blocked by administration. In
     * case of any questions or doubts please contact API support team.</b>
     * <p/>
     * <b>Note: Use method friends.getByDevices to get user's friends having devices you are interested in.</b>
     *
     * @param friendUids     - list of recipient friend ids (required).
     * @param invitationText - invitation text (can be null).
     * @param deviceGroups   - list of device groups on which the invitation will be shown. Check {@link ru.ok.android.sdk.util.OkDevice} enum for the list of supported device groups (cannot be null).
     * @return
     * @throws IOException
     */
    public final String inviteFriends(final Collection<String> friendUids, final String invitationText, final String... deviceGroups)
            throws IOException {
        if ((friendUids == null) || friendUids.isEmpty()) {
            throw new IllegalArgumentException(mContext.getString(R.string.friend_uids_cant_be_empty));
        }
        final String friendsParamValue = TextUtils.join(",", friendUids);
        final Map<String, String> params = new HashMap<>();
        params.put("uids", friendsParamValue);
        if (!TextUtils.isEmpty(invitationText)) {
            params.put("text", invitationText);
        }
        if ((deviceGroups != null) && (deviceGroups.length > 0)) {
            final String deviceParamValue = TextUtils.join(",", deviceGroups);
            params.put("devices", deviceParamValue);
        }
        return request("friends.appInvite", params, "get");
    }

    /**
     * Check if access token is available (can be used to check if previously used access token and refresh token was successfully loaded from the storage).
     * Also check is it valid with method call
     */
    public final void checkValidTokens(final OkListener listener) {
        if (mAccessToken == null || mSessionSecretKey == null) {
            notifyFailed(listener, mContext.getString(R.string.no_valid_token));
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String response = request("users.getLoggedInUser", null, "get");

                    if (response != null && response.length() > 2 && TextUtils.isDigitsOnly(response.substring(1, response.length() - 1))) {
                        JSONObject json = new JSONObject();
                        try {
                            json.put(Shared.PARAM_ACCESS_TOKEN, mAccessToken);
                            json.put(Shared.PARAM_SESSION_SECRET_KEY, mSessionSecretKey);
                            json.put(Shared.PARAM_LOGGED_IN_USER, response);
                        } catch (JSONException ignore) {
                        }
                        onValidSessionAppeared();
                        notifySuccess(listener, json);
                    } else {
                        try {
                            JSONObject json = new JSONObject(response);
                            if (json.has(Shared.PARAM_ERROR_MSG)) {
                                notifyFailed(listener, json.getString(Shared.PARAM_ERROR_MSG));
                                return;
                            }
                        } catch (JSONException ignore) {
                        }
                        notifyFailed(listener, response);
                    }
                } catch (IOException e) {
                    notifyFailed(listener, e.getMessage());
                }
            }
        }).start();
    }

    /**
     * Call an API posting widget
     *
     * @param attachment      - json with publishing attachment
     * @param userTextEnabled - ability to enable user comment
     * @param args            widget arguments as specified in documentation
     */
    public void performPosting(Activity activity, String attachment, boolean userTextEnabled,
                               @Nullable HashMap<String, String> args) {
        Intent intent = new Intent(activity, OkPostingActivity.class);
        intent.putExtra(Shared.PARAM_APP_ID, mAppId);
        intent.putExtra(Shared.PARAM_ATTACHMENT, attachment);
        intent.putExtra(Shared.PARAM_ACCESS_TOKEN, mAccessToken);
        intent.putExtra(Shared.PARAM_WIDGET_ARGS, args);
        intent.putExtra(Shared.PARAM_SESSION_SECRET_KEY, mSessionSecretKey);
        intent.putExtra(Shared.PARAM_USER_TEXT_ENABLE, userTextEnabled);
        activity.startActivityForResult(intent, Shared.OK_POSTING_REQUEST_CODE);
    }

    /**
     * Calls application invite widget
     *
     * @param args widget arguments as specified in documentation
     */
    public void performAppInvite(Activity activity, HashMap<String, String> args) {
        performAppSuggestInvite(activity, OkAppInviteActivity.class, args, Shared.OK_INVITING_REQUEST_CODE);
    }

    /**
     * Calls application suggest widget
     *
     * @param args widget arguments as specified in documentation
     */
    public void performAppSuggest(Activity activity, HashMap<String, String> args) {
        performAppSuggestInvite(activity, OkAppSuggestActivity.class, args, Shared.OK_SUGGESTING_REQUEST_CODE);
    }

    private void performAppSuggestInvite(Activity activity, Class<? extends AbstractWidgetActivity> clazz,
                                         HashMap<String, String> args, int requestCode) {
        Intent intent = new Intent(activity, clazz);
        intent.putExtra(Shared.PARAM_APP_ID, mAppId);
        intent.putExtra(Shared.PARAM_ACCESS_TOKEN, mAccessToken);
        intent.putExtra(Shared.PARAM_SESSION_SECRET_KEY, mSessionSecretKey);
        intent.putExtra(Shared.PARAM_WIDGET_ARGS, args);
        activity.startActivityForResult(intent, requestCode);
    }

    private void signParameters(final Map<String, String> params) {
        final StringBuilder sb = new StringBuilder(100);
        for (final Entry<String, String> entry : params.entrySet()) {
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        final String paramsString = sb.toString();
        final String sig = OkEncryptUtil.toMD5(paramsString + mSessionSecretKey);
        params.put(Shared.PARAM_SIGN, sig);
    }

    /**
     * Clears all token information from sdk and webView cookies
     */
    public final void clearTokens() {
        mAccessToken = null;
        mSessionSecretKey = null;
        sdkToken = null;
        TokenStore.removeStoredTokens(mContext);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            clearCookies();
        } else {
            clearCookiesOld();
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void clearCookies() {
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.removeAllCookies(null);
    }

    private void clearCookiesOld() {
        CookieSyncManager.createInstance(mContext);
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.removeAllCookie();
    }

    /**
     * Reports a payment and sends (async) via sdk.reportPayment method<br/>
     */
    public void reportPayment(String trxId, String amount, Currency currency) {
        okPayment.report(trxId, amount, currency);
    }

    private void onValidSessionAppeared() {
        okPayment.init();
    }
}