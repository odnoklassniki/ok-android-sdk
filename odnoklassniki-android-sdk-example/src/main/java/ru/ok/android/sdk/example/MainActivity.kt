package ru.ok.android.sdk.example

import java.util.Currency

import org.json.JSONException

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View.*
import android.widget.Toast
import ru.ok.android.sdk.Odnoklassniki
import ru.ok.android.sdk.util.OkAuthType
import ru.ok.android.sdk.util.OkScope
import kotlinx.android.synthetic.main.activity_main.*
import ru.ok.android.sdk.ContextOkListener

// -------------- YOUR APP DATA GOES HERE ------------
private const val APP_ID = "125497344"
private const val APP_KEY = "CBABPLHIABABABABA"
private const val REDIRECT_URL = "okauth://ok125497344"
// -------------- YOUR APP DATA ENDS -----------------

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
            ok.requestAsync("users.getCurrentUser", listener = ContextOkListener(this,
                    onSuccess = { _, json -> toast("Get current user result: $json") },
                    onError = { _, err -> toast("Get current user failed: $err") }
            ))
        }
        sdk_get_friends.setOnClickListener {
            ok.requestAsync("friends.get", listener = ContextOkListener(this,
                    onSuccess = { _, json -> toast("Get user friends result: $json") },
                    onError = { _, err -> toast("Failed to get friends: $err") }
            ))
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
        sdk_app_invite.setOnClickListener { ok.performAppInvite(this) }
        sdk_app_suggest.setOnClickListener { ok.performAppSuggest(this, null) }
        sdk_report_payment.setOnClickListener {
            ok.reportPayment(Math.random().toString() + "", "6.28", Currency.getInstance("EUR"))
        }

        ok = Odnoklassniki(this, APP_ID, APP_KEY)
        ok.checkValidTokens(ContextOkListener(this,
                onSuccess = { _, _ -> showAppData() },
                onError = { _, err -> toast(getString(R.string.error) + ": $err") }
        ))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        when {
            Odnoklassniki.of(this).isActivityRequestOAuth(requestCode) -> {
                // process OAUTH sign-in response
                Odnoklassniki.of(this).onAuthActivityResult(requestCode, resultCode, data, ContextOkListener(this,
                        onSuccess = { _, json ->
                            try {
                                toast(String.format("access_token: %s", json.getString("access_token")))
                                showAppData()
                            } catch (e: JSONException) {
                                toast("unable to parse login request ${e.message}")
                            }
                        },
                        onError = { _, err -> toast(getString(R.string.error) + ": $err") },
                        onCancel = { _, err -> toast(getString(R.string.auth_cancelled) + ": $err") }
                ))
            }
            Odnoklassniki.of(this).isActivityRequestViral(requestCode) -> {
                // process called viral widgets (suggest / invite / post)
                Odnoklassniki.of(this).onActivityResultResult(requestCode, resultCode, data, ContextOkListener(this,
                        onSuccess = { _, json -> toast(json.toString()) },
                        onError = { _, err -> toast(getString(R.string.error) + ": $err") }
                ))
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun showAppData(loggedIn: Boolean = true) {
        sdk_form.visibility = if (loggedIn) VISIBLE else GONE
        login_block.visibility = if (loggedIn) GONE else VISIBLE
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}
