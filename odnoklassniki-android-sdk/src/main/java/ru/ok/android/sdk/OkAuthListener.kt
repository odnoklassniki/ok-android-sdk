package ru.ok.android.sdk

interface OkAuthListener : OkListener {
    /**
     * Authentication was cancelled by user
     */
    fun onCancel(error: String?)
}