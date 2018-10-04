package ru.ok.android.sdk.example

import java.util.Currency

import org.json.JSONException
import org.json.JSONObject

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View.*
import android.widget.Toast
import ru.ok.android.sdk.Odnoklassniki
import ru.ok.android.sdk.OkAuthListener
import ru.ok.android.sdk.OkListener
import ru.ok.android.sdk.util.OkAuthType
import ru.ok.android.sdk.util.OkScope
import kotlinx.android.synthetic.main.activity_main.*

private const val APP_ID = "125497344"
private const val APP_KEY = "CBABPLHIABABABABA"
private const val REDIRECT_URL = "okauth://ok125497344"

class MainActivity : Activity() {

    private lateinit var ok: Odnoklassniki

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // NOTE: application should use just one of the login methods, ANY is preferable
        sdk_login_any.setOnClickListener {
            ok.requestAuthorization(this, REDIRECT_URL, OkAuthType.ANY, OkScope.VALUABLE_ACCESS)
        }
        sdk_login_sso.setOnClickListener {
            ok.requestAuthorization(this, REDIRECT_URL, OkAuthType.NATIVE_SSO, OkScope.VALUABLE_ACCESS)
        }
        sdk_login_oauth.setOnClickListener {
            ok.requestAuthorization(this, REDIRECT_URL, OkAuthType.WEBVIEW_OAUTH, OkScope.VALUABLE_ACCESS)
        }

        sdk_get_currentuser.setOnClickListener {
            ok.requestAsync("users.getCurrentUser", null, null, object : OkListener {
                override fun onSuccess(json: JSONObject) {
                    toast("Get current user result: " + json.toString())
                }

                override fun onError(error: String) {
                    toast("Get current user failed: $error")
                }
            })
        }
        sdk_get_friends.setOnClickListener {
            ok.requestAsync("friends.get", null, null, object : OkListener {
                override fun onSuccess(json: JSONObject?) {
                    toast("Get user friends result: $json")
                }

                override fun onError(error: String?) {
                    toast("Failed to get friends: $error")
                }
            })
        }
        sdk_logout.setOnClickListener {
            ok.clearTokens()
            showAppData(false)
        }

        sdk_post.setOnClickListener {
            val json = """
                {"media": [
                    {"type":"text", "text":"Hello world!"},
                    {"type":"link", "url":"https://apiok.ru/"},
                    {"type":"app",
                        "text":"Welcome from sample",
                        "images":[{"url":"https://apiok.ru/res/img/main/app_create.png"}],
                        "actions":[{"text": "Play me!", "mark": "play_me_from_app_block"}]
                    }]
                }
            """.trimIndent()
            ok.performPosting(this, json, false, null)
        }
        sdk_app_invite.setOnClickListener { ok.performAppInvite(this, null) }
        sdk_app_suggest.setOnClickListener { ok.performAppSuggest(this, null) }
        sdk_report_payment.setOnClickListener {
            ok.reportPayment(Math.random().toString() + "", "6.28", Currency.getInstance("EUR"))
        }

        ok = Odnoklassniki.createInstance(this, APP_ID, APP_KEY)
        ok.checkValidTokens(object : OkListener {
            override fun onSuccess(json: JSONObject) {
                showAppData()
            }

            override fun onError(error: String) {
                toast(String.format("%s: %s", getString(R.string.error), error))
            }
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        when {
            Odnoklassniki.getInstance().isActivityRequestOAuth(requestCode) -> {
                // process OAUTH sign-in response
                Odnoklassniki.getInstance().onAuthActivityResult(requestCode, resultCode, data, object : OkAuthListener {
                    override fun onSuccess(json: JSONObject) {
                        try {
                            toast(String.format("access_token: %s", json.getString("access_token")))
                            showAppData()
                        } catch (e: JSONException) {
                            toast("unable to parse login request ${e.message}")
                        }
                    }

                    override fun onError(error: String) {
                        toast(String.format("%s: %s", getString(R.string.error), error))
                    }

                    override fun onCancel(error: String) {
                        toast(String.format("%s: %s", getString(R.string.auth_cancelled), error))
                    }
                })
            }
            Odnoklassniki.getInstance().isActivityRequestViral(requestCode) -> {
                // process called viral widgets (suggest / invite / post)
                Odnoklassniki.getInstance().onActivityResultResult(requestCode, resultCode, data, object : OkListener {
                    override fun onSuccess(json: JSONObject) {
                        toast(json.toString())
                    }

                    override fun onError(error: String) {
                        toast(String.format("%s: %s", getString(R.string.error), error))
                    }
                })
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    fun showAppData(loggedIn: Boolean = true) {
        sdk_form.visibility = if (loggedIn) VISIBLE else GONE
        login_block.visibility = if (loggedIn) GONE else VISIBLE
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}
