package ru.ok.android.sdk;

public interface OkAuthListener extends OkListener {
    /**
     * Authentication was cancelled by user
     */
    void onCancel(String error);
}