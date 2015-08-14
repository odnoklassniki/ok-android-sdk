package ru.ok.android.sdk;

import org.json.JSONObject;

/**
 * Listener methods are guaranteed to be called on the main (UI) thread.
 */
public interface OkListener {
    /**
     * Request was successful
     */
    public void onSuccess(final JSONObject json);

    /**
     * Request was unsuccessful due any reason.
     */
    public void onError(String error);
}