package ru.ok.android.sdk.util;

/**
 * A list of currently available scopes
 * <p/>
 * Methods <b>users.getLoggedInUser</b> и <b>users.getCurrentUser</b> do not require any scopes
 */
public class OkScope {
    /**
     * Grants access to API methods.
     */
    public static final String VALUABLE_ACCESS = "VALUABLE_ACCESS";

    /**
     * Grants permission to set user status.
     */
    public static final String SET_STATUS = "SET_STATUS";

    /**
     * Grants access to photo content.
     */
    public static final String PHOTO_CONTENT = "PHOTO_CONTENT";

    /**
     * Grants access to group content.
     */
    public static final String GROUP_CONTENT = "GROUP_CONTENT";

    /**
     * Grants access to video content.
     */
    public static final String VIDEO_CONTENT = "VIDEO_CONTENT";

    /**
     * Grants access to invite to app.
     */
    public static final String APP_INVITE = "APP_INVITE";

    /**
     * Grants access to receive long access tokens
     */
    public static final String LONG_ACCESS_TOKEN = "LONG_ACCESS_TOKEN";
}
