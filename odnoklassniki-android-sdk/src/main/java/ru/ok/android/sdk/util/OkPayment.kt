package ru.ok.android.sdk.util

import android.annotation.SuppressLint
import java.io.IOException
import java.util.ArrayList
import java.util.Currency
import java.util.EnumSet
import java.util.HashMap
import java.util.concurrent.ConcurrentLinkedQueue

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

import android.content.Context
import android.content.SharedPreferences
import android.os.AsyncTask
import android.util.Log
import ru.ok.android.sdk.API_ERR_PERMISSION_DENIED
import ru.ok.android.sdk.LOG_TAG
import ru.ok.android.sdk.Odnoklassniki
import ru.ok.android.sdk.OkRequestMode

private const val PREF_QUEUE_PACKAGE = "ok.payment"
private const val PREF_QUEUE_KEY = "queue"
private const val TRX_ID = "id"
private const val AMOUNT = "amount"
private const val CURRENCY = "currency"
private const val TRIES = "tries"
private const val MAX_RETRY_COUNT = 20
private const val METHOD = "sdk.reportPayment"

class OkPayment(context: Context) {
    private val queue = ConcurrentLinkedQueue<Transaction>()
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_QUEUE_PACKAGE, Context.MODE_PRIVATE)

    internal inner class Transaction {
        var id: String = ""
        var amount: String = ""
        var currency: String = ""
        var tries: Int = 0

        constructor()

        constructor(id: String, amount: String, currency: String) {
            this.id = id
            this.amount = amount
            this.currency = currency
        }
    }

    fun init() {
        queue.clear()
        queue.addAll(fromJson(prefs.getString(PREF_QUEUE_KEY, null)))
        transfer()
    }

    fun report(trxId: String, amount: String, currency: Currency) {
        queue.offer(Transaction(trxId, amount, currency.currencyCode))
        persist()
        transfer()
    }

    private fun persist() {
        val editor = prefs.edit()
        editor.putString(PREF_QUEUE_KEY, toJson())
        editor.apply()
    }

    private fun transfer() {
        if (!queue.isEmpty()) TransferTask().execute()
    }

    private fun toJson(): String {
        val json = JSONArray()
        try {
            for (trx in queue) {
                val obj = JSONObject()
                obj.put(TRX_ID, trx.id)
                obj.put(AMOUNT, trx.amount)
                obj.put(CURRENCY, trx.currency)
                if (trx.tries > 0) obj.put(TRIES, trx.tries)
                json.put(obj)
            }
        } catch (e: JSONException) {
            Log.e(LOG_TAG, "Writing transactions queue: " + e.message, e)
        }
        return json.toString()
    }

    private fun fromJson(source: String?): List<Transaction> {
        val transactions = ArrayList<Transaction>()
        if (!source.isNullOrEmpty()) {
            try {
                val json = JSONArray(source)
                for (i in 0 until json.length()) {
                    val obj = json.getJSONObject(i)
                    val trx = Transaction()
                    trx.id = obj.getString(TRX_ID)
                    trx.amount = obj.getString(AMOUNT)
                    trx.currency = obj.getString(CURRENCY)
                    trx.tries = obj.optInt(TRIES)
                    transactions.add(trx)
                }
            } catch (e: JSONException) {
                Log.e(LOG_TAG, "Reading TRX queue from " + source + ": " + e.message, e)
            }
        }
        return transactions
    }

    @SuppressLint("StaticFieldLeak")
    private inner class TransferTask : AsyncTask<Void, Void, Void>() {
        override fun doInBackground(vararg params: Void): Void? {
            val map = HashMap<String, String>()
            do {
                val trx = queue.peek() ?: break
                map.clear()
                map["trx_id"] = trx.id
                map["amount"] = trx.amount
                map["currency"] = trx.currency

                try {
                    val response = Odnoklassniki.instance.request(METHOD, map, EnumSet.of(OkRequestMode.SIGNED))
                    val json = JSONObject(response)
                    if (json.optBoolean("result")) {
                        queue.remove()
                        persist()
                        continue
                    } else {
                        Log.d(LOG_TAG, METHOD + " resulted with error: " + json.toString())
                        if (json.optInt("error_code", 0) == API_ERR_PERMISSION_DENIED) {
                            Log.e(LOG_TAG, "Did not you forgot to ask moderators for permission to access $METHOD?")
                        }
                    }
                } catch (e: IOException) {
                    Log.d(LOG_TAG, "Failed to report TRX " + map + ", retry queued: " + e.message, e)
                } catch (e: JSONException) {
                    Log.d(LOG_TAG, "Failed to report TRX " + map + ", retry queued: " + e.message, e)
                }

                trx.tries++
                if (trx.tries > MAX_RETRY_COUNT) {
                    Log.w(LOG_TAG, "Reporting TRX " + map + " failed " + trx.tries + " times, cancelling")
                    queue.remove()
                    persist()
                    continue
                }
                persist()
                break
            } while (true)
            return null
        }
    }
}
