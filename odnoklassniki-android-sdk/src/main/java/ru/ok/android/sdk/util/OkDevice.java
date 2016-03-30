package ru.ok.android.sdk.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;

public class OkDevice {

    /**
     * Retrieves unique device advertising id (if applicable), or device id (as a fallback)<br/>
     * Required for some methods like sdk.getInstallSource<br/>
     * Could not be called from UI thread, needs wrapping via AsyncTask in this case due to gms restrictions.<br/>
     * WARNING: This requires dependency on 'com.google.android.gms:play-services-ads:8.4.0' to work properly<br/>
     * Consider caching the returning value if the result is needed frequently
     *
     * @param context context
     * @return unique id
     */
    public static String getAdvertisingId(Context context) {
        String advId = null;

        try {
            // here we do AdvertisingIdClient.getAdvertisingIdInfo(context).getId() via reflection in order to
            // avoid using extra SDK dependency (gms)
            Class<?> googlePlayServicesUtil = Class.forName("com.google.android.gms.ads.identifier.AdvertisingIdClient");
            Method getAdvertisingIdInfo = googlePlayServicesUtil.getMethod("getAdvertisingIdInfo", Context.class);
            Object advertisingIdInfo = getAdvertisingIdInfo.invoke(null, context);
            Method getId = advertisingIdInfo.getClass().getMethod("getId");
            advId = (String) getId.invoke(advertisingIdInfo);
        } catch (ClassNotFoundException e) {
            Log.d("Odnoklassniki", "A dependency on com.google.android.gms:play-services-ads is required in order to use OkDevice.getAdvertisingId, falling back to ANDROID_ID instead");
        } catch (NoSuchMethodException ignore) {
        } catch (InvocationTargetException ignore) {
        } catch (IllegalAccessException ignore) {
        }

        if (advId == null) {
            advId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        }
        return advId;
    }

}
