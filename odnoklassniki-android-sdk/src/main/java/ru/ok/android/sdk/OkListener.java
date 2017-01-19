package ru.ok.android.sdk;

import java.util.EnumSet;
import java.util.Map;

import org.json.JSONObject;

/**
 * Listener methods are guaranteed to be called on the main (UI) thread.
 *
 * @see OkAuthListener OkAuthListener if you are requesting authorization
 */
public interface OkListener {
    String KEY_EXCEPTION = "exception";
    String KEY_RESULT = "result";

    /**
     * Request was successful
     *
     * @see Odnoklassniki#request(String, Map, EnumSet, OkListener) request() for description how non-JSON-object
     * results are treated
     */
    void onSuccess(final JSONObject json);

    /**
     * Request was unsuccessful due any reason.
     */
    void onError(String error);
}