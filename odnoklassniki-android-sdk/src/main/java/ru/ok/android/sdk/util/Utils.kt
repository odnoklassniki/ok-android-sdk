package ru.ok.android.sdk.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import ru.ok.android.sdk.LOG_TAG
import java.lang.IllegalStateException
import java.lang.reflect.InvocationTargetException
import java.security.MessageDigest

object Utils {
    private var mainThreadHandler: Handler? = null

    fun toMD5(toEncrypt: String): String {
        return try {
            val digest = MessageDigest.getInstance("md5")
            digest.update(toEncrypt.toByteArray())
            val bytes = digest.digest()
            val sb = StringBuilder()
            for (i in bytes.indices) {
                sb.append(String.format("%02X", bytes[i]))
            }
            sb.toString().toLowerCase()
        } catch (exc: Exception) {
            throw IllegalStateException(exc)
        }
    }

    /**
     * Retrieves unique device advertising id (if applicable), or device id (as a fallback)<br></br>
     * Required for some methods like sdk.getInstallSource<br></br>
     * Could not be called from UI thread, needs wrapping via AsyncTask in this case due to gms restrictions.<br></br>
     * WARNING: This requires dependency on 'com.google.android.gms:play-services-ads:8.4.0' to work properly<br></br>
     * Consider caching the returning value if the result is needed frequently
     */
    fun getAdvertisingId(context: Context, fallbackToAndroidId: Boolean = false): String {
        var advId: String? = null

        try {
            // here we do AdvertisingIdClient.getAdvertisingIdInfo(context).getId() via reflection in order to
            // avoid using extra SDK dependency (gms)
            val googlePlayServicesUtil = Class.forName("com.google.android.gms.ads.identifier.AdvertisingIdClient")
            val getAdvertisingIdInfo = googlePlayServicesUtil.getMethod("getAdvertisingIdInfo", Context::class.java)
            val advertisingIdInfo = getAdvertisingIdInfo.invoke(null, context)
            if (advertisingIdInfo != null) {
                val getId = advertisingIdInfo.javaClass.getMethod("getId")
                advId = getId.invoke(advertisingIdInfo) as String
            } else {
                Log.d(LOG_TAG, "Requesting advertising info from gms, got null")
            }
        } catch (e: ClassNotFoundException) {
            if (fallbackToAndroidId) {
                Log.d(LOG_TAG, "A dependency on com.google.android.gms:play-services-ads is required in order to use Utils.getAdvertisingId, falling back to ANDROID_ID instead")
            } else {
                throw IllegalStateException("A dependency on com.google.android.gms:play-services-ads is required in order to use Utils.getAdvertisingId")
            }
        } catch (ignore: NoSuchMethodException) {
        } catch (ignore: InvocationTargetException) {
        } catch (ignore: IllegalAccessException) {
        }

        if (advId == null) {
            advId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }
        return advId ?: ""
    }

    fun getMainThreadHandler(): Handler {
        if (mainThreadHandler == null) mainThreadHandler = Handler(Looper.getMainLooper())
        return mainThreadHandler!!
    }

    fun isMainThread(): Boolean = Looper.getMainLooper() == Looper.myLooper()

    fun executeOnMain(runnable: Runnable) {
        if (isMainThread()) runnable.run()
        else queueOnMain(runnable)
    }

    fun queueOnMain(runnable: Runnable, delayMillis: Long = 10) {
        getMainThreadHandler().postDelayed(runnable, delayMillis)
    }

}
