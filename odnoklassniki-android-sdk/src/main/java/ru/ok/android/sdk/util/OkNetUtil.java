package ru.ok.android.sdk.util;

import android.os.Bundle;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;

public class OkNetUtil {
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

    public static final String performPostRequest(final HttpClient httpClient, final String url, final Map<String, String> params)
            throws IOException {
        final HttpPost request = new HttpPost(url);
        if ((params != null) && (!params.isEmpty())) {
            final ArrayList<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(params.size());
            for (final Entry<String, String> entry : params.entrySet()) {
                final NameValuePair pair = new BasicNameValuePair(entry.getKey(), entry.getValue());
                nameValuePairs.add(pair);
            }
            try {
                final UrlEncodedFormEntity encodedEntity = new UrlEncodedFormEntity(nameValuePairs, "utf-8");
                request.setEntity(encodedEntity);
            } catch (Exception exc) {
                // Impossibru!
            }
        }
        return executeHttpRequest(httpClient, request);
    }

    public static final String performGetRequest(final HttpClient httpClient, final String url, final Map<String, String> params)
            throws IOException {
        String paramString = null;
        if ((params != null) && (!params.isEmpty())) {
            final ArrayList<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(params.size());
            for (final Entry<String, String> entry : params.entrySet()) {
                final NameValuePair pair = new BasicNameValuePair(entry.getKey(), entry.getValue());
                nameValuePairs.add(pair);
            }
            paramString = URLEncodedUtils.format(nameValuePairs, "utf-8");
        }
        String requestUrl = url;
        if (paramString != null) {
            requestUrl += "?" + paramString;
        }
        final HttpGet request = new HttpGet(requestUrl);
        return executeHttpRequest(httpClient, request);
    }

    private static final String executeHttpRequest(final HttpClient httpClient, final HttpRequestBase httpRequest)
            throws IOException {
        final HttpResponse httpResponse = httpClient.execute(httpRequest);
        final HttpEntity httpEntity = httpResponse.getEntity();
        if (httpEntity != null) {
            try {
                return OkIOUtil.inputStreamToString(httpEntity.getContent());
            } catch (Exception exc) {
            }
        } else {
            throw new IOException();
        }
        return null;
    }
}
