package ru.ok.android.sdk

import java.util.EnumSet

enum class OkRequestMode {
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
    /**
     * Not reporting platform via REST
     */
    NO_PLATFORM_REPORTING;


    companion object {
        /**
         * Default request modes
         */
        @JvmStatic
        val DEFAULT = EnumSet.of(OkRequestMode.SIGNED)
    }
}
