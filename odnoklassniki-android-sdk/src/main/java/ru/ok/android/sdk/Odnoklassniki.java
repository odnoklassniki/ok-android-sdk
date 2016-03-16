package ru.ok.android.sdk;

import java.io.IOException;
import java.util.Collection;
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
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import ru.ok.android.sdk.util.OkEncryptUtil;
import ru.ok.android.sdk.util.OkNetUtil;
import ru.ok.android.sdk.util.OkScope;
import ru.ok.android.sdk.util.OkThreadUtil;

public class Odnoklassniki {
    private static Odnoklassniki sOdnoklassniki;


    /**
     * @deprecated use {@link #createInstance(android.content.Context, String, String)} instead.
     */
    @Deprecated
    public static Odnoklassniki createInstance(final Context context, final String appId, final String appSecret, final String appKey) {
        return createInstance(context, appId, appKey);
    }

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
    public static Odnoklassniki getInstance(Context context) {
        return getInstance();
    }

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

        // RESTORE
        mAccessToken = TokenStore.getStoredAccessToken(context);
        mSessionSecretKey = TokenStore.getStoredSessionSecretKey(context);
    }

    private Context mContext;

    // Application info
    protected final String mAppId;
    protected final String mAppKey;

    // Current tokens
    protected String mAccessToken;
    protected String mSessionSecretKey;

    // Listeners
    protected OkListener mOkListener;

    // Stuff
    protected final HttpClient mHttpClient;

	/* *** AUTHORIZATION *** */

    public final void requestAuthorization(final Context context) {
        requestAuthorization(context, false, (String) null);
    }

    public final void requestAuthorization(final Context context, final OkListener listener, final boolean oauthOnly) {
        requestAuthorization(listener, null, oauthOnly, (String) null);
    }

    public final void requestAuthorization(final Context context, final boolean oauthOnly, final String... scopes) {
        requestAuthorization(mOkListener, null, oauthOnly, scopes);
    }

    /**
     * If user has Odnoklassniki application installed, SDK will try to authorize user through it, otherwise, for safety reasons, authorization through browser will be requested.<br>
     * With oauthOnly flag set to true, the authorization will be requested only through browser.
     *
     * @param listener    listener which will be called after authorization
     * @param redirectUri the URI to which the access_token will be redirected
     * @param oauthOnly   true - use only web authorization, false - use web authorization or authorization via android app if installed
     * @param scopes      {@link OkScope} - application request permissions as per {@link OkScope}.
     */
    public final void requestAuthorization(OkListener listener, String redirectUri, final boolean oauthOnly, final String... scopes) {
        this.mOkListener = listener;

        final Intent intent = new Intent(mContext, OkAuthActivity.class);
        intent.putExtra(Shared.PARAM_CLIENT_ID, mAppId);
        intent.putExtra(Shared.PARAM_APP_KEY, mAppKey);
        intent.putExtra(Shared.PARAM_REDIRECT_URI, redirectUri);
        intent.putExtra(Shared.PARAM_OAUTH_ONLY, oauthOnly);
        intent.putExtra(Shared.PARAM_SCOPES, scopes);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
    }

    void onTokenResponseReceived(final Bundle result) {
        if (result == null) {
            notifyFailed(null);
        } else {
            final String accessToken = result.getString(Shared.PARAM_ACCESS_TOKEN);
            if (accessToken == null) {
                String error = result.getString(Shared.PARAM_ERROR);
                notifyFailed(error);
            } else {
                final String sessionSecretKey = result.getString(Shared.PARAM_SESSION_SECRET_KEY);
                final String refreshToken = result.getString(Shared.PARAM_REFRESH_TOKEN);
                mAccessToken = accessToken;
                mSessionSecretKey = sessionSecretKey != null ? sessionSecretKey : refreshToken;
                JSONObject json = new JSONObject();
                try {
                    json.put(Shared.PARAM_ACCESS_TOKEN, mAccessToken);
                    json.put(Shared.PARAM_SESSION_SECRET_KEY, mSessionSecretKey);
                } catch (JSONException ignore) {
                }
                notifySuccess(json);
            }
        }
    }

    protected final void notifyFailed(final String error) {
        notifyFailed(mOkListener, error);
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

    protected final void notifySuccess(final JSONObject json) {
        notifySuccess(mOkListener, json);
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

	/* **** API REQUESTS *** */

    /**
     * Call an API method and get the result as a String.
     * <p/>
     * <b>Note that those calls MUST be performed in a non-UI thread.</b>
     *
     * @param apiMethod  - odnoklassniki api method.
     * @param httpMethod - only "get" and "post" are supported.
     * @return query result
     * @throws IOException in case of a problem or the connection was aborted.
     */
    public final String request(final String apiMethod, final String httpMethod) throws IOException {
        return request(apiMethod, null, httpMethod);
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
     */
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
     * Call an API method and get the result as a String.
     * <p/>
     * <b>Note that those calls MUST be performed in a non-UI thread.</b>
     *
     * @param apiMethod  - odnoklassniki api method.
     * @param params     - map of key-value params
     * @param httpMethod - only "get" and "post" are supported.
     * @param listener   - listener which will be called after method call
     * @throws IOException
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
                    String response = request("users.getLoggedInUser", "get");

                    if (response != null && response.length() > 2 && TextUtils.isDigitsOnly(response.substring(1, response.length() - 1))) {
                        JSONObject json = new JSONObject();
                        try {
                            json.put(Shared.PARAM_ACCESS_TOKEN, mAccessToken);
                            json.put(Shared.PARAM_SESSION_SECRET_KEY, mSessionSecretKey);
                        } catch (JSONException ignore) {
                        }
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
     * @param postingListener - listener which will be called after method call
     */
    public void performPosting(String attachment, boolean userTextEnabled, OkListener postingListener) {
        this.mOkListener = postingListener;

        Intent intent = new Intent(mContext, OkPostingActivity.class);
        intent.putExtra(Shared.PARAM_APP_ID, mAppId);
        intent.putExtra(Shared.PARAM_ATTACHMENT, attachment);
        intent.putExtra(Shared.PARAM_ACCESS_TOKEN, mAccessToken);
        intent.putExtra(Shared.PARAM_SESSION_SECRET_KEY, mSessionSecretKey);
        intent.putExtra(Shared.PARAM_USER_TEXT_ENABLE, userTextEnabled);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
    }

    /**
     * Calls application invite widget
     *
     * @param listener callback notification listener
     * @param args     widget arguments as specified in documentation
     */
    public void performAppInvite(OkListener listener, HashMap<String, String> args) {
        performAppSuggestInvite(listener, OkAppInviteActivity.class, args);
    }

    /**
     * Calls application suggest widget
     *
     * @param listener callback notification listener
     * @param args     widget arguments as specified in documentation
     */
    public void performAppSuggest(OkListener listener, HashMap<String, String> args) {
        performAppSuggestInvite(listener, OkAppSuggestActivity.class, args);
    }

    private void performAppSuggestInvite(OkListener listener, Class<? extends AbstractWidgetActivity> clazz,
                                         HashMap<String, String> args) {
        this.mOkListener = listener;
        Intent intent = new Intent(mContext, clazz);
        intent.putExtra(Shared.PARAM_APP_ID, mAppId);
        intent.putExtra(Shared.PARAM_ACCESS_TOKEN, mAccessToken);
        intent.putExtra(Shared.PARAM_SESSION_SECRET_KEY, mSessionSecretKey);
        intent.putExtra(Shared.PARAM_WIDGET_ARGS, args);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
    }

    private void signParameters(final Map<String, String> params) {
        final StringBuilder sb = new StringBuilder();
        for (final Entry<String, String> entry : params.entrySet()) {
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        final String paramsString = sb.toString();
        final String sig = OkEncryptUtil.toMD5(paramsString + mSessionSecretKey);
        params.put(Shared.PARAM_SIGN, sig);
    }

    public final void setOkListener(OkListener listener) {
        this.mOkListener = listener;
    }

    public final void removeOkListener() {
        this.mOkListener = null;
    }

	/* **** LOGOUT **** */

    /**
     * Clears all token information from sdk and webView cookies
     */
    public final void clearTokens() {
        mAccessToken = null;
        mSessionSecretKey = null;
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
}