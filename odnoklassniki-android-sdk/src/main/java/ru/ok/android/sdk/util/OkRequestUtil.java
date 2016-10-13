package ru.ok.android.sdk.util;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
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
import ru.ok.android.sdk.Shared;

public class OkRequestUtil {
    private static final String DEFAULT_ENCODING = "UTF-8";
    private static URL API_URL = null;

    static {
        try {
            API_URL = new URL(Shared.API_URL);
        } catch (MalformedURLException e) {
            //should never be called
            Log.e(Shared.LOG_TAG, e.getLocalizedMessage());
        }

        assert API_URL != null;
    }

    private static String inputStreamToString(final InputStream is) throws IOException {
        final char[] buffer = new char[0x10000];
        final StringBuilder sb = new StringBuilder();
        final Reader in = new InputStreamReader(is, DEFAULT_ENCODING);
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
     * @param url
     * @return bundle that contains key-value entries of the url query and fragment.
     */
    public static final Bundle getUrlParameters(final String url) {
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

    public static final String executeRequest(Map<String, String> params) throws IOException {
        if (params == null ||
                !params.containsKey(Shared.PARAM_METHOD) ||
                !params.containsKey(Shared.PARAM_APP_KEY)) {
            return null;
        }

        return new Request(params).execute();
    }

    private static class Request {
        private int timeout = 3000;

        private List<Pair<String, String>> params = null;

        Request(Map<String, String> params) {
            this.params = new ArrayList<>();
            for (Entry<String, String> pair : params.entrySet()) {
                this.params.add(new Pair<>(pair.getKey(), pair.getValue()));
            }
        }

        String execute() throws IOException {
            final HttpURLConnection connection = (HttpURLConnection) API_URL.openConnection();
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
                queryParameters.add(String.format("%s=%s", URLEncoder.encode(pair.first, DEFAULT_ENCODING), URLEncoder.encode(pair.second, DEFAULT_ENCODING)));
            }

            final String query = TextUtils.join("&", queryParameters);
            if (query.length() > 0) {
                final OutputStream os = connection.getOutputStream();
                final BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, DEFAULT_ENCODING));

                writer.write(query);
                writer.flush();

                writer.close();
                os.close();
            }

            return inputStreamToString(connection.getInputStream());
        }
    }
}