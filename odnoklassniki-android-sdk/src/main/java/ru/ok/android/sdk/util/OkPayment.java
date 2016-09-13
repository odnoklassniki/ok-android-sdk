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

    private class Transaction {
        private String id;
        private String amount;
        private String currency;

        public Transaction() {
        }

        public Transaction(String id, String amount, String currency) {
            this.id = id;
            this.amount = amount;
            this.currency = currency;
        }
    }

    private final Queue<Transaction> queue = new ConcurrentLinkedQueue<>();

    public void init(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_QUEUE_PACKAGE, Context.MODE_PRIVATE);
        queue.addAll(fromJson(prefs.getString(PREF_QUEUE_KEY, null)));
        transfer(context);
    }

    public void report(Context context, String trxId, String amount, Currency currency) {
        queue.offer(new Transaction(trxId, amount, currency.getCurrencyCode()));
        persist(context);
        transfer(context);
    }

    private void persist(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_QUEUE_PACKAGE, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PREF_QUEUE_KEY, toJson());
        editor.apply();
    }

    private void transfer(Context context) {
        if (!queue.isEmpty()) {
            new TransferTask().execute(context);
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

                    transactions.add(trx);
                }
            } catch (JSONException e) {
                Log.e(Shared.LOG_TAG, "Reading TRX queue from " + source + ": " + e.getMessage(), e);
            }
        }
        return transactions;
    }

    private class TransferTask extends AsyncTask<Context, Void, Void> {
        @Override
        protected Void doInBackground(Context... params) {
            Context context = params[0];
            Map<String, String> map = new HashMap<>();
            Transaction trx;

            while ((trx = queue.peek()) != null) {
                map.clear();
                map.put("trx_id", trx.id);
                map.put("amount", trx.amount);
                map.put("currency", trx.currency);

                try {
                    String response = Odnoklassniki.getInstance().request("sdk.reportPayment",
                            map, EnumSet.of(OkRequestMode.GET, OkRequestMode.SIGNED));
                    JSONObject json = new JSONObject(response);
                    if (json.optBoolean("result")) {
                        queue.remove();
                        persist(context);
                        continue;
                    }

                } catch (IOException | JSONException e) {
                    Log.d(Shared.LOG_TAG, "Failed to report TRX " + map + ", retry queued: " + e.getMessage(), e);
                }
                break;
            }
            return null;
        }
    }

}
