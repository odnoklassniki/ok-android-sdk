package ru.ok.android.sdk.util;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import ru.ok.android.sdk.Odnoklassniki;
import static ru.ok.android.sdk.SharedKt.LOG_TAG;
import static ru.ok.android.sdk.SharedKt.PARAM_APP_KEY;
import static ru.ok.android.sdk.SharedKt.PARAM_METHOD;

public class OkRequestUtil {
    private static final String ENCODING = "UTF-8";

    private static String inputStreamToString(final InputStream is, int contentLength) throws IOException {
        final StringBuilder sb = new StringBuilder(Math.max(contentLength, 128));
        final char[] buffer = new char[0x1000];
        final Reader in = new InputStreamReader(is, ENCODING);
        try {
            int read;
            do {
                read = in.read(buffer, 0, buffer.length);
                if (read > 0) {
                    sb.append(buffer, 0, read);
                }
            } while (read >= 0);
        } finally {
            closeSilently(in);
        }
        return sb.toString();
    }

    private static void closeSilently(final Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception exc) {
                // Do nothing
            }
        }
    }

    /**
     * @return bundle that contains key-value entries of the url query and fragment.
     */
    public static Bundle getUrlParameters(final String url) {
        final Bundle bundle = new Bundle();
        final String[] separated = url.split("\\?");
        if (separated.length > 1) {
            final String query = separated[1];
            final String[] params = query.split("[&#]");
            for (final String param : params) {
                final String[] keyvalue = param.split("=");
                final String key = URLDecoder.decode(keyvalue[0]);
                String value = null;
                if (keyvalue.length > 1) {
                    value = URLDecoder.decode(keyvalue[1]);
                }
                bundle.putString(key, value);
            }
        }
        return bundle;
    }

    public static String encode(String str) {
        try {
            return URLEncoder.encode(str, ENCODING);
        } catch (UnsupportedEncodingException e) {
            //should never be called
            Log.e(LOG_TAG, e.getLocalizedMessage());
        }
        return null;
    }

    public static String executeRequest(Map<String, String> params) throws IOException {
        if (params == null ||
                !params.containsKey(PARAM_METHOD) ||
                !params.containsKey(PARAM_APP_KEY)) {
            return null;
        }

        return new Request(params).execute();
    }

    private static class Request {
        private int timeout = 3000;

        private final List<Pair<String, String>> params = new ArrayList<>();

        Request(Map<String, String> params) {
            for (Entry<String, String> pair : params.entrySet()) {
                this.params.add(new Pair<>(pair.getKey(), pair.getValue()));
            }
        }

        String execute() throws IOException {
            URL url = new URL(Odnoklassniki.Companion.getInstance().getApiBaseUrl() + "fb.do");
            final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setReadTimeout(timeout);
            connection.setConnectTimeout(timeout + 5000);
            connection.setRequestMethod("POST");
            connection.setUseCaches(false);
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setRequestProperty("Connection", "Keep-Alive");

            final ArrayList<String> queryParameters = new ArrayList<>(params.size());
            for (Pair<String, String> pair : params) {
                if (pair.first == null || pair.second == null) {
                    continue;
                }
                queryParameters.add(String.format("%s=%s", URLEncoder.encode(pair.first, ENCODING), URLEncoder.encode(pair.second, ENCODING)));
            }

            final String query = TextUtils.join("&", queryParameters);
            if (query.length() > 0) {
                final OutputStream os = connection.getOutputStream();
                final BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, ENCODING));

                writer.write(query);
                writer.flush();

                writer.close();
                os.close();
            }

            return inputStreamToString(connection.getInputStream(), connection.getContentLength());
        }
    }
}