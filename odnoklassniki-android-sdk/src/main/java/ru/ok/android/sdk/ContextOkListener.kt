package ru.ok.android.sdk

import android.content.Context
import org.json.JSONObject
import java.lang.ref.WeakReference

/**
 * Listener that validates that only holds a weak reference to a context,
 * so will not be called if context no longer available
 */
class ContextOkListener(
        context: Context,
        private val onSuccess: ((ctx: Context, json: JSONObject) -> Unit?)? = null,
        private val onCancel: ((ctx: Context, err: String?) -> Unit)? = null,
        private val onError: ((ctx: Context, err: String?) -> Unit)? = null)
    : OkAuthListener {

    private val contextRef = WeakReference(context)

    override fun onSuccess(json: JSONObject) {
        val ctx = contextRef.get()
        if (ctx != null) onSuccess?.invoke(ctx, json)
    }

    override fun onCancel(error: String?) {
        val ctx = contextRef.get()
        if (ctx != null) onCancel?.invoke(ctx, error)
    }

    override fun onError(error: String?) {
        val ctx = contextRef.get()
        if (ctx != null) onError?.invoke(ctx, error)
    }

}