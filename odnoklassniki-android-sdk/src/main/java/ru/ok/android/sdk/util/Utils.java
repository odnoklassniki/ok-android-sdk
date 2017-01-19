package ru.ok.android.sdk.util;

import org.json.JSONException;
import org.json.JSONObject;

public class Utils {

    public static JSONObject toJson(String key, String value) {
        try {
            return new JSONObject().put(key, value);
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

}
