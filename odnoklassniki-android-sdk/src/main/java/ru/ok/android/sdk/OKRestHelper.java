package ru.ok.android.sdk;

import java.io.IOException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import static ru.ok.android.sdk.Shared.LOG_TAG;

public class OKRestHelper {

    private final Odnoklassniki ok;

    public OKRestHelper(Odnoklassniki ok) {
        this.ok = ok;
    }

    /**
     * sdk.init Initializes SDK sessions required for some SDK methods
     *
     * @param context context
     * @return true if session initialization succeeded or a token already exists
     */
    public boolean sdkInit(Context context) throws IOException {
        if (!TextUtils.isEmpty(ok.sdkToken)) {
            return true;
        }

        JSONObject sessionData = new JSONObject();
        try {
            sessionData.put("version", 2);
            sessionData.put("device_id", getDeviceId(context));
            sessionData.put("client_version", "android_sdk_1");
            sessionData.put("client_type", "SDK_ANDROID");
        } catch (JSONException e) {
            Log.e(LOG_TAG, "Creating sdk.init request: " + e.getMessage(), e);
            return false;
        }

        Map<String, String> params = new HashMap<>();
        params.put("session_data", sessionData.toString());

        String resp = ok.request("sdk.init", params, EnumSet.of(OkRequestMode.UNSIGNED));
        try {
            JSONObject json = new JSONObject(resp);
            if (json.has("session_key")) {
                ok.sdkToken = json.getString("session_key");
                if (!TextUtils.isEmpty(ok.sdkToken)) {
                    TokenStore.storeSdkToken(context, ok.sdkToken);
                    return true;
                }
            }
        } catch (JSONException e) {
            Log.e(LOG_TAG, "Parsing sdk.init response: " + e.getMessage(), e);
        }

        return false;
    }

    /**
     * sdk.sendNote
     *
     * @param note     note to send
     * @param listener callback
     * @throws IOException
     * @see #sdkInit(Context)
     */
    public boolean sdkSendNote(JSONObject note, OkListener listener) throws IOException {
        Map<String, String> params = new HashMap<>();
        params.put("note", note.toString());

        String response = ok.request("sdk.sendNote", params, EnumSet.of(OkRequestMode.SIGNED, OkRequestMode.SDK_SESSION));
        return notifyListener(listener, response);
    }

    protected boolean notifyListener(final OkListener listener, final String response) {
        try {
            final JSONObject json = new JSONObject(response);
            if (json.has(Shared.PARAM_ERROR_MSG)) {
                final String error = json.optString(Shared.PARAM_ERROR_MSG, json.toString());
                ok.notifyFailed(listener, error);
                return false;
            }
            ok.notifySuccess(listener, json);
        } catch (JSONException e) {
            final JSONObject json = new JSONObject();
            try {
                json.put("reponse", response);
            } catch (JSONException ignore) {
            }
            // some methods may return non-json results, just push them through
            ok.notifySuccess(listener, json);
        }
        return true;
    }

    @NonNull
    protected String getDeviceId(Context context) {
        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        String id1 = null;
        try {
            id1 = telephonyManager == null ? null : telephonyManager.getDeviceId();
        } catch (SecurityException e) {
            Log.d(LOG_TAG, e.getMessage(), e);
        }
        String id2 = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        return id1 + "_" + id2;
    }

}