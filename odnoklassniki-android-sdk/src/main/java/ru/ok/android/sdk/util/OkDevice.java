package ru.ok.android.sdk.util;

import java.io.IOException;

import com.google.android.gms.ads.identifier.AdvertisingIdClient;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;

import android.content.Context;
import android.provider.Settings;

public class OkDevice {

    /**
     * Retrieves unique device advertising id (if applicable), or device id (as a fallback)<br/>
     * Required for some methods like sdk.getInstallSource<br/>
     * Could not be called from UI thread, needs wrapping via AsyncTask in this case due to gms restrictions.
     *
     * @param context context
     * @return unique id
     */
    public static String getAdvertisingId(Context context) {
        String advId = null;
        try {
            AdvertisingIdClient.Info info = AdvertisingIdClient.getAdvertisingIdInfo(context);
            advId = info.getId();
        } catch (GooglePlayServicesNotAvailableException | GooglePlayServicesRepairableException | IOException ignore) {
        }

        if (advId == null) {
            advId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        }
        return advId;
    }

}
