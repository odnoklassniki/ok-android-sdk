package ru.ok.android.sdk;

import androidx.annotation.Nullable;

public interface OkAuthListener extends OkListener {
    /**
     * Authentication was cancelled by user
     */
    void onCancel(@Nullable String error);
}