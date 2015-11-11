package ru.ok.android.sdk;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.webkit.CookieManager;

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

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import android.webkit.CookieSyncManager;
import ru.ok.android.sdk.util.OkEncryptUtil;
import ru.ok.android.sdk.util.OkNetUtil;
import ru.ok.android.sdk.util.OkScope;
import ru.ok.android.sdk.util.OkThreadUtil;

public class Odnoklassniki {
    private static Odnoklassniki sOdnoklassniki;
    private final ConcurrentLinkedQueue<DeferredData> deferredDataList = new ConcurrentLinkedQueue<>();

    private Context mContext;

    // Application info
    protected final String mAppId;
    protected final String mAppKey;

    // Current tokens
    protected String mAccessToken;
    protected String mSessionSecretKey;

    // Listeners
    protected volatile OkListener mOkListener;

    // Stuff
    protected final HttpClient mHttpClient;


    /**
     * @deprecated use {@link #createInstance(android.content.Context, String, String)} instead.
     */
    @Deprecated
    public static final Odnoklassniki createInstance(final Context context, final String appId, final String appSecret, final String appKey) {
        return createInstance(context, appId, appKey);
    }


    /**
     * This method is required to be called before {@link Odnoklassniki#getInstance()}<br>
     * Note that instance is only created once. Multiple calls to this method wont' create multiple instances of the object
     * (thread-safe singleton)
     *
     */
    public static final Odnoklassniki createInstance(final Context context, final String appId, final String appKey) {
        if ((appId == null) || (appKey == null)) {
            throw new IllegalArgumentException(context.getString(R.string.no_application_data));
        }

        if (sOdnoklassniki == null) {
            synchronized (Odnoklassniki.class) {
                if (sOdnoklassniki == null) {
                    sOdnoklassniki = new Odnoklassniki(context.getApplicationContext(), appId, appKey);
                }
            }
        }
        return sOdnoklassniki;
    }

    /**
     * Get previously created instance.<br>
     * You must always call {@link Odnoklassniki#createInstance(Context, String, String)} before calling this method, or {@link IllegalStateException} will be thrown
     */
    public static final Odnoklassniki getInstance(Context context) {
        return getInstance();
    }

    public static final Odnoklassniki getInstance() {
        if (sOdnoklassniki == null) {
            throw new IllegalStateException("No instance available. Odnoklassniki.createInstance() needs to be called before Odnoklassniki.getInstance()");
        }
        return sOdnoklassniki;
    }

    /**
     * Check if singleton is instantiated
     *
     * @return true if instance exist, false if not
     */
    public static final boolean hasInstance() {
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


	/* *** AUTHORIZATION *** */
    public final void requestAuthorization() {
        requestAuthorization(false, (String) null);
    }

    public final void requestAuthorization(final Context context, final boolean oauthOnly) {
        requestAuthorization(null, oauthOnly, (String) null);
    }

    public final void requestAuthorization(final boolean oauthOnly, final String... scopes) {
        requestAuthorization(null, oauthOnly, scopes);
    }

    /**
     * If user has Odnoklassniki application installed, SDK will try to authorize user through it, otherwise, for safety reasons, authorization through browser will be requested.<br>
     * With oauthOnly flag set to true, the authorization will be requested only through browser.
     *
     * Listener should be explicitly set by calling {@link #setOkListener(OkListener)}. If listener will be a context you <b>must</b>
     * also explicitly remove your listener by calling {@link #removeOkListener()} when context may become invalid or paused
     * `onPause` method is recommended
     *
     * @param redirectUri the URI to which the access_token will be redirected
     * @param oauthOnly   true - use only web authorization, false - use web authorization or authorization via android app if installed
     * @param scopes      {@link OkScope} - application request permissions as per {@link OkScope}.
     */
    public final void requestAuthorization(String redirectUri, final boolean oauthOnly, final String... scopes) {
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
                } catch (JSONException e) {
                }
                notifySuccess(json);
            }
        }
    }

    protected final void notifyFailed(final String error) {
        //Cheap check
        if (mOkListener != null) {
            notifyFailedInUiThread(mOkListener, error);
        } else {
            //Expensive with lock check
            synchronized (deferredDataList) {
                if (mOkListener != null) {
                    notifyFailedInUiThread(mOkListener, error);
                } else {
                    deferredDataList.offer(new DeferredData(error));
                }
            }
        }
    }

    protected final void notifyFailedInUiThread(final OkListener listener, final String error) {
        OkThreadUtil.executeOnMain(new Runnable() {
            public void run() {
                listener.onError(error);
            }
        });
    }

    protected final void notifySuccess(final JSONObject json) {
        //Cheap check
        if (mOkListener != null) {
            notifySuccessInUiThread(mOkListener, json);
        } else {
            //Expensive with lock check
            synchronized (deferredDataList) {
                if (mOkListener != null) {
                    notifySuccessInUiThread(mOkListener, json);
                } else {
                    deferredDataList.offer(new DeferredData(json));
                }
            }
        }
    }

    protected final void notifySuccessInUiThread(final OkListener listener, final JSONObject json) {
        OkThreadUtil.executeOnMain(new Runnable() {
            public void run() {
                listener.onSuccess(json);
            }
        });
    }

    /**
     * Removes deferred data if client do not need to receive something that was already requested but not received
     */
    public void clearDefferedData() {
        deferredDataList.clear();
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
    public final String requestSync(final String apiMethod, final String httpMethod) throws IOException {
        return requestSync(apiMethod, null, httpMethod);
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
    public final String requestSync(final String apiMethod, final Map<String, String> params, final String httpMethod)
            throws IOException {
        if (TextUtils.isEmpty(apiMethod)) {
            throw new IllegalArgumentException(mContext.getString(R.string.api_method_cant_be_empty));
        }
        Map<String, String> requestparams = new TreeMap<String, String>();
        if ((params != null) && !params.isEmpty()) {
            requestparams.putAll(params);
        }
        requestparams.put(Shared.PARAM_APP_KEY, mAppKey);
        requestparams.put(Shared.PARAM_METHOD, apiMethod);
        signParameters(requestparams);
        requestparams.put(Shared.PARAM_ACCESS_TOKEN, mAccessToken);
        final String requestUrl = Shared.API_URL;
        String response = null;
        if ("post".equalsIgnoreCase(httpMethod)) {
            response = OkNetUtil.performPostRequest(mHttpClient, requestUrl, requestparams);
        } else {
            response = OkNetUtil.performGetRequest(mHttpClient, requestUrl, requestparams);
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
     * @throws IOException
     */
    public final void requestAsync(final String apiMethod, final Map<String, String> params,
                                   final String httpMethod) throws IOException {
        String response = requestSync(apiMethod, params, httpMethod);
        try {
            JSONObject json = new JSONObject(response);
            if (json.has(Shared.PARAM_ERROR_MSG)) {
                notifyFailedInUiThread(mOkListener, json.getString(Shared.PARAM_ERROR_MSG));
            } else {
                notifySuccessInUiThread(mOkListener, json);
            }
        } catch (JSONException e) {
            notifyFailedInUiThread(mOkListener, response);
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
        final Map<String, String> params = new HashMap<String, String>();
        params.put("uids", friendsParamValue);
        if (!TextUtils.isEmpty(invitationText)) {
            params.put("text", invitationText);
        }
        if ((deviceGroups != null) && (deviceGroups.length > 0)) {
            final String deviceParamValue = TextUtils.join(",", deviceGroups);
            params.put("devices", deviceParamValue);
        }
        return requestSync("friends.appInvite", params, "get");
    }

    /**
     * Check if access token is available (can be used to check if previously used access token and refresh token was successfully loaded from the storage).
     * Also check is it valid with method call
     */
    public final void checkValidTokens() {
        if (mAccessToken == null || mSessionSecretKey == null) {
            notifyFailedInUiThread(mOkListener, mContext.getString(R.string.no_valid_token));
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String response = requestSync("users.getLoggedInUser", "get");

                    if (response != null && response.length() > 2 && TextUtils.isDigitsOnly(response.substring(1, response.length() - 1))) {
                        JSONObject json = new JSONObject();
                        try {
                            json.put(Shared.PARAM_ACCESS_TOKEN, mAccessToken);
                            json.put(Shared.PARAM_SESSION_SECRET_KEY, mSessionSecretKey);
                        } catch (JSONException e) {
                        }
                        notifySuccessInUiThread(mOkListener, json);
                    } else {
                        try {
                            JSONObject json = new JSONObject(response);
                            if (json.has(Shared.PARAM_ERROR_MSG)) {
                                notifyFailedInUiThread(mOkListener, json.getString(Shared.PARAM_ERROR_MSG));
                                return;
                            }
                        } catch (JSONException e) {
                        }
                        notifyFailedInUiThread(mOkListener, response);
                    }
                } catch (IOException e) {
                    notifyFailedInUiThread(mOkListener, e.getMessage());
                }
            }
        }).start();
    }

    /**
     * Call an API posting widget
     *
     * @param attachment      - json with publishing attachment
     * @param userTextEnabled - ability to enable user comment
     */
    public void performPosting(String attachment, boolean userTextEnabled) {
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
     */
    public void performAppInvite() {
        performAppSuggestInvite(OkAppInviteActivity.class);
    }

    /**
     * Calls application suggest widget
     *
     */
    public void performAppSuggest() {
        performAppSuggestInvite(OkAppSuggestActivity.class);
    }

    private void performAppSuggestInvite(Class<? extends AbstractWidgetActivity> clazz) {
        Intent intent = new Intent(mContext, clazz);
        intent.putExtra(Shared.PARAM_APP_ID, mAppId);
        intent.putExtra(Shared.PARAM_ACCESS_TOKEN, mAccessToken);
        intent.putExtra(Shared.PARAM_SESSION_SECRET_KEY, mSessionSecretKey);
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

    /**
     * Context (activity or fragment) from which you are setting listener by this method may go to background, or even be invalidated by Android OS during
     * processing request. So there are at least 2 problems in such case:
     * 1). If you will not remove listener in `onPause` or `onStop` method - when listener will be informed about result,
     *     activity/fragment which is listening will be no longer be valid. If you will try to change fragment in back stack or go to another activity it will cause `IllegalStateException`.
     *     (http://stackoverflow.com/questions/8040280/how-to-handle-handler-messages-when-activity-fragment-is-paused)
     *     Also strong reference to MainActivity will still exist in SDK preventing JVM from freeing memory
     *
     * 2). If you will unsubscribe itself, result may come <b>before</b> same (or new activity/fragment) will subscribe itself. So data will be lost.
     *     It can be easily reproduced by switching on option `Do not keep activities` (http://habrahabr.ru/post/221679/) on device. Every time when you leave you activity/fragment which
     *     is interested in result it will be "killed" and every time you will come back to re-created activity/fragment result will be lost
     *
     * So best practice is explicitly set and remove listeners in `onResume` and `onPause`, and be ready to receive deferred data immediately after setting callback
     *
     * @param listener
     */
    public final void setOkListener(final OkListener listener) {
        sendDeferredData(listener);

        this.mOkListener = listener;
    }

    /**
     *  Sends deferred data to listeners
     *
     * @param listener
     */
    private void sendDeferredData(final OkListener listener) {
        if (deferredDataList.size() > 0) {
            synchronized (deferredDataList) {
                for (final DeferredData deferredData : deferredDataList) {
                    if (deferredData.isDeferredDataError()) {
                        OkThreadUtil.executeOnMain(new Runnable() {
                            public void run() {
                                listener.onError(deferredData.getErrorCode());
                            }
                        });
                    } else {
                        OkThreadUtil.executeOnMain(new Runnable() {
                            public void run() {
                                listener.onSuccess(deferredData.getResponse());
                            }
                        });
                    }
                }

                deferredDataList.clear();
            }
        }
    }

    /**
     * @see #setOkListener(OkListener)
     */
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


    /**
     * Inner class to store
     */
    private static class DeferredData {
        private String errorCode;
        private JSONObject response;


        /**
         * Set deferredData as error code
         *
         * @param errorCode
         */
        public DeferredData(String errorCode) {
            this.errorCode = errorCode;
            this.response = null;
        }

        /**
         * @param response
         */
        public DeferredData(JSONObject response) {
            this.errorCode = null;
            this.response = response;
        }

        public boolean isDeferredDataError() {
            return errorCode != null;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public JSONObject getResponse() {
            return response;
        }
    }
}