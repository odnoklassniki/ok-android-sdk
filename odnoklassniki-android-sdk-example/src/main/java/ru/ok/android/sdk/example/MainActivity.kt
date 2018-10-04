package ru.ok.android.sdk.example

import java.io.IOException
import java.util.Currency
import java.util.EnumSet

import org.json.JSONException
import org.json.JSONObject

import android.app.Activity
import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.View.*
import android.widget.Toast
import ru.ok.android.sdk.OKRestHelper
import ru.ok.android.sdk.Odnoklassniki
import ru.ok.android.sdk.OkAuthListener
import ru.ok.android.sdk.OkListener
import ru.ok.android.sdk.OkRequestMode
import ru.ok.android.sdk.Shared
import ru.ok.android.sdk.util.OkAuthType
import ru.ok.android.sdk.util.OkDevice
import ru.ok.android.sdk.util.OkScope
import ru.ok.android.sdk.util.StatsBuilder
import kotlinx.android.synthetic.main.activity_main.*

private const val APP_ID = "125497344"
private const val APP_KEY = "CBABPLHIABABABABA"
private const val REDIRECT_URL = "okauth://ok125497344"

class MainActivity : Activity() {

    private lateinit var ok: Odnoklassniki

    /**
     * Creates a listener that displays result as a toast message
     */
    private val toastListener: OkListener
        get() = object : OkListener {
            override fun onSuccess(json: JSONObject) {
                Toast.makeText(this@MainActivity, json.toString(), Toast.LENGTH_LONG).show()
            }

            override fun onError(error: String) {
                Toast.makeText(this@MainActivity, String.format("%s: %s", getString(R.string.error), error), Toast.LENGTH_LONG).show()
            }
        }

    /**
     * Creates a listener that is run on OAUTH authorization completion
     */
    private val authListener: OkAuthListener
        get() = object : OkAuthListener {
            override fun onSuccess(json: JSONObject) {
                try {
                    Toast.makeText(this@MainActivity,
                            String.format("access_token: %s", json.getString("access_token")),
                            Toast.LENGTH_SHORT).show()
                } catch (e: JSONException) {
                    e.printStackTrace()
                }

                showLoginForm()
            }

            override fun onError(error: String) {
                Toast.makeText(this@MainActivity,
                        String.format("%s: %s", getString(R.string.error), error),
                        Toast.LENGTH_SHORT).show()
            }

            override fun onCancel(error: String) {
                Toast.makeText(this@MainActivity,
                        String.format("%s: %s", getString(R.string.auth_cancelled), error),
                        Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sdk_login_any.setOnClickListener(LoginClickListener(OkAuthType.ANY))
        sdk_login_sso.setOnClickListener(LoginClickListener(OkAuthType.NATIVE_SSO))
        sdk_login_oauth.setOnClickListener(LoginClickListener(OkAuthType.WEBVIEW_OAUTH))

        sdk_get_currentuser.setOnClickListener {
            ok.requestAsync("users.getCurrentUser", null, null, object : OkListener {
                override fun onSuccess(json: JSONObject) {
                    Toast.makeText(this@MainActivity, "Get current user result: " + json.toString(), Toast.LENGTH_SHORT).show()
                }

                override fun onError(error: String) {
                    Toast.makeText(this@MainActivity, "Get current user failed: $error", Toast.LENGTH_SHORT).show()
                }
            })
        }
        sdk_get_friends.setOnClickListener {
            ok.requestAsync("friends.get", null, null, object : OkListener {
                override fun onSuccess(json: JSONObject?) {
                    Toast.makeText(this@MainActivity, "Get user friends result: $json", Toast.LENGTH_SHORT).show()
                }

                override fun onError(error: String?) {
                    Log.e(Shared.LOG_TAG, "Failed to get friends: $error")
                }
            })
        }
        sdk_logout.setOnClickListener {
            ok.clearTokens()
            showLoginForm(false)
        }

        sdk_post.setOnClickListener {
            val json = ("{'media':[" +
                    "{'type':'text', 'text':'Hello world!'}" + ','.toString() +
                    "{'type':'link', 'url':'https://apiok.ru/'}" + ','.toString() +
                    "{'type':'app', 'text':'Welcome from sample', 'images':[{'url':'https://apiok.ru/res/img/main/app_create.png'}], 'actions':[{'text': 'Play me!', 'mark': 'play_me_from_app_block'}]}" +
                    "]}").replace("'".toRegex(), "\"")
            ok.performPosting(this@MainActivity, json, false, null)
        }
        sdk_app_invite.setOnClickListener { ok.performAppInvite(this@MainActivity, null) }
        sdk_app_suggest.setOnClickListener { ok.performAppSuggest(this@MainActivity, null) }
        sdk_send_note.setOnClickListener {
            SdkSendNote().execute()
        }
        sdk_report_payment.setOnClickListener {
            ok.reportPayment(Math.random().toString() + "", "6.28", Currency.getInstance("EUR"))
        }
        sdk_report_stats.setOnClickListener {
            SdkReportStats().execute()
        }

        ok = Odnoklassniki.createInstance(this, APP_ID, APP_KEY)
        ok.checkValidTokens(object : OkListener {
            override fun onSuccess(json: JSONObject) {
                showLoginForm()
            }

            override fun onError(error: String) {
                Toast.makeText(this@MainActivity, String.format("%s: %s", getString(R.string.error), error), Toast.LENGTH_LONG).show()
            }
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        when {
            Odnoklassniki.getInstance().isActivityRequestOAuth(requestCode) -> Odnoklassniki.getInstance().onAuthActivityResult(requestCode, resultCode, data, authListener)
            Odnoklassniki.getInstance().isActivityRequestViral(requestCode) -> Odnoklassniki.getInstance().onActivityResultResult(requestCode, resultCode, data, toastListener)
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    fun showLoginForm(show: Boolean = true) {
        sdk_form.visibility = if (show) VISIBLE else GONE
        login_block.visibility = if (show) GONE else VISIBLE
        if (show) {
            testIfInstallationSourceIsOK()
        }
    }

    private inner class LoginClickListener(private val authType: OkAuthType) : OnClickListener {
        override fun onClick(view: View) {
            ok.requestAuthorization(this@MainActivity, REDIRECT_URL, authType, OkScope.VALUABLE_ACCESS)
        }
    }

    private fun testIfInstallationSourceIsOK() {
        val args = mapOf("adv_id" to OkDevice.getAdvertisingId(this@MainActivity))
        ok.requestAsync("sdk.getInstallSource", args, EnumSet.of(OkRequestMode.UNSIGNED), object : OkListener {
            override fun onSuccess(json: JSONObject) {
                try {
                    val result = Integer.parseInt(json.optString("result"))
                    Toast.makeText(this@MainActivity,
                            if (result > 0)
                                "application installation caused by OK app ($result)"
                            else
                                "application is not caused by OK app ($result)",
                            Toast.LENGTH_SHORT).show()
                } catch (e: NumberFormatException) {
                    Toast.makeText(this@MainActivity, "invalid value while getting install source $json", Toast.LENGTH_SHORT).show()
                }

            }

            override fun onError(error: String) {
                Toast.makeText(this@MainActivity, "error while getting install source $error", Toast.LENGTH_SHORT).show()
            }
        })
    }

    //NOTE: Developers should use better task mechanism than AsyncTask
    private inner class SdkSendNote : AsyncTask<Void, Void, String>() {
        override fun doInBackground(vararg params: Void): String? {
            try {
                val helper = OKRestHelper(ok)
                if (!helper.sdkInit(this@MainActivity)) {
                    Toast.makeText(this@MainActivity, "sdk.sendNote: no sdk token available", Toast.LENGTH_SHORT).show()
                    return null
                }
                val note = JSONObject()
                note.put("uid", "12345678")
                note.put("image", "http://domain.tld/img.png")
                note.put("message", "Hello World from Android SDK!")
                note.put("payload", "SGVsbG8gd29ybGQhISE=")
                helper.sdkSendNote(note, object : OkListener {
                    override fun onSuccess(json: JSONObject?) {
                        val text = json?.toString()
                        Toast.makeText(this@MainActivity, "sdk.sendNote: OK " + text!!, Toast.LENGTH_SHORT).show()
                    }

                    override fun onError(error: String) {
                        Toast.makeText(this@MainActivity, "sdk.sendNote: ERROR $error", Toast.LENGTH_SHORT).show()
                    }
                })
            } catch (e: Exception) {
                Log.e(Shared.LOG_TAG, "sdk.sendNote " + e.message, e)
            }

            return null
        }
    }

    //NOTE: Developers should use better task mechanism than AsyncTask
    inner class SdkReportStats : AsyncTask<Void, Void, Void>() {
        override fun doInBackground(vararg voids: Void): Void? {
            val helper = OKRestHelper(ok)
            try {
                val builder = StatsBuilder()
                        .addCounter(StatsBuilder.Type.COUNTER, "bananas", System.currentTimeMillis(), "1")
                helper.sdkReportStats(builder, object : OkListener {
                    override fun onSuccess(json: JSONObject) {
                        Log.d(Shared.LOG_TAG, "statistics reported OK " + json.toString())
                    }

                    override fun onError(error: String) {
                        Log.d(Shared.LOG_TAG, "statistics reported ERR $error")
                    }
                })
            } catch (e: JSONException) {
                Log.d(Shared.LOG_TAG, "error with statistics reporting: ${e.message}", e)
            } catch (e: IOException) {
                Log.d(Shared.LOG_TAG, "error with statistics reporting: ${e.message}", e)
            }

            return null
        }
    }
}
