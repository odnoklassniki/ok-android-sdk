package ru.ok.android.sdk.util

/**
 * A list of currently available scopes
 *
 *
 * Methods **users.getLoggedInUser** и **users.getCurrentUser** do not require any scopes
 */
@Suppress("unused")
object OkScope {
    /**
     * Grants access to API methods.
     */
    const val VALUABLE_ACCESS = "VALUABLE_ACCESS"

    /**
     * Grants permission to set user status.
     */
    const val SET_STATUS = "SET_STATUS"

    /**
     * Grants access to photo content.
     */
    const val PHOTO_CONTENT = "PHOTO_CONTENT"

    /**
     * Grants access to group content.
     */
    const val GROUP_CONTENT = "GROUP_CONTENT"

    /**
     * Grants access to video content.
     */
    const val VIDEO_CONTENT = "VIDEO_CONTENT"

    /**
     * Grants access to invite to app.
     */
    const val APP_INVITE = "APP_INVITE"

    /**
     * Grants access to receive long access tokens
     */
    const val LONG_ACCESS_TOKEN = "LONG_ACCESS_TOKEN"
}
