package ru.ok.android.sdk.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Currency;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;
import ru.ok.android.sdk.Odnoklassniki;
import ru.ok.android.sdk.OkRequestMode;
import ru.ok.android.sdk.Shared;

public class OkPayment {

    private static final String PREF_QUEUE_PACKAGE = "ok.payment";
    private static final String PREF_QUEUE_KEY = "queue";
    private static final String TRX_ID = "id";
    private static final String AMOUNT = "amount";
    private static final String CURRENCY = "currency";
    private static final String TRIES = "tries";
    private static final int MAX_RETRY_COUNT = 20;

    private class Transaction {
        private String id;
        private String amount;
        private String currency;
        private int tries;

        public Transaction() {
        }

        public Transaction(String id, String amount, String currency) {
            this.id = id;
            this.amount = amount;
            this.currency = currency;
        }
    }

    private final Queue<Transaction> queue = new ConcurrentLinkedQueue<>();
    private final SharedPreferences prefs;

    public OkPayment(Context context) {
        this.prefs = context.getSharedPreferences(PREF_QUEUE_PACKAGE, Context.MODE_PRIVATE);
    }

    public void init() {
        queue.clear();
        queue.addAll(fromJson(prefs.getString(PREF_QUEUE_KEY, null)));
        transfer();
    }

    public void report(String trxId, String amount, Currency currency) {
        queue.offer(new Transaction(trxId, amount, currency.getCurrencyCode()));
        persist();
        transfer();
    }

    private void persist() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PREF_QUEUE_KEY, toJson());
        editor.apply();
    }

    private void transfer() {
        if (!queue.isEmpty()) {
            new TransferTask().execute();
        }
    }

    private String toJson() {
        JSONArray json = new JSONArray();
        try {
            for (Transaction trx : queue) {
                JSONObject obj = new JSONObject();

                obj.put(TRX_ID, trx.id);
                obj.put(AMOUNT, trx.amount);
                obj.put(CURRENCY, trx.currency);
                if (trx.tries > 0) {
                    obj.put(TRIES, trx.tries);
                }

                json.put(obj);
            }
        } catch (JSONException e) {
            Log.e(Shared.LOG_TAG, "Writing transactions queue: " + e.getMessage(), e);
        }
        return json.toString();
    }

    private List<Transaction> fromJson(String source) {
        List<Transaction> transactions = new ArrayList<>();

        if (source != null && !source.isEmpty()) {
            try {
                JSONArray json = new JSONArray(source);
                for (int i = 0; i < json.length(); i++) {
                    JSONObject obj = json.getJSONObject(i);
                    Transaction trx = new Transaction();

                    trx.id = obj.getString(TRX_ID);
                    trx.amount = obj.getString(AMOUNT);
                    trx.currency = obj.getString(CURRENCY);
                    trx.tries = obj.optInt(TRIES);

                    transactions.add(trx);
                }
            } catch (JSONException e) {
                Log.e(Shared.LOG_TAG, "Reading TRX queue from " + source + ": " + e.getMessage(), e);
            }
        }
        return transactions;
    }

    private class TransferTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            Map<String, String> map = new HashMap<>();
            Transaction trx;

            while ((trx = queue.peek()) != null) {
                map.clear();
                map.put("trx_id", trx.id);
                map.put("amount", trx.amount);
                map.put("currency", trx.currency);

                try {
                    String response = Odnoklassniki.getInstance().request("sdk.reportPayment",
                            map, EnumSet.of(OkRequestMode.SIGNED));
                    JSONObject json = new JSONObject(response);
                    if (json.optBoolean("result")) {
                        queue.remove();
                        persist();
                        continue;
                    }

                } catch (IOException | JSONException e) {
                    Log.d(Shared.LOG_TAG, "Failed to report TRX " + map + ", retry queued: " + e.getMessage(), e);
                }

                trx.tries++;
                if (trx.tries > MAX_RETRY_COUNT) {
                    Log.w(Shared.LOG_TAG, "Reporting TRX " + map + " failed " + trx.tries + " times, cancelling");
                    queue.remove();
                    persist();
                    continue;
                }
                persist();

                break;
            }
            return null;
        }
    }

}
