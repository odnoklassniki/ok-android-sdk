package ru.ok.android.sdk

import org.json.JSONObject

const val KEY_EXCEPTION = "exception"
const val KEY_RESULT = "result"

/**
 * Listener methods are guaranteed to be called on the main (UI) thread.
 *
 * @see OkAuthListener OkAuthListener if you are requesting authorization
 */
interface OkListener {
    /**
     * Request was successful
     *
     * @see Odnoklassniki.request
     */
    fun onSuccess(json: JSONObject)

    /**
     * Request was unsuccessful due any reason.
     */
    fun onError(error: String?)
}