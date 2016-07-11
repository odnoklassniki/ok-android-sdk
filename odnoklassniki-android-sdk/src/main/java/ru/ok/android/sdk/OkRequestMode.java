package ru.ok.android.sdk;

import java.util.EnumSet;

public enum OkRequestMode {
    /**
     * Request should be processed via HTTP GET
     */
    GET,
    /**
     * Request should be processed via HTTP POST
     */
    POST,
    /**
     * Request should be signed using OAUTH access token
     */
    SIGNED,
    /**
     * Request should not be signed
     */
    UNSIGNED,
    /**
     * SDK session required
     */
    SDK_SESSION,
    //
    ;

    /**
     * Default request modes
     */
    public static final EnumSet<OkRequestMode> DEFAULT = EnumSet.of(OkRequestMode.GET, OkRequestMode.SIGNED);
}
