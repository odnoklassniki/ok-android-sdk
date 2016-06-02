package ru.ok.android.sdk.example;

import java.io.IOException;
import java.util.EnumSet;
import java.util.HashMap;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;
import ru.ok.android.sdk.Odnoklassniki;
import ru.ok.android.sdk.OkListener;
import ru.ok.android.sdk.OkRequestMode;
import ru.ok.android.sdk.util.OkAuthType;
import ru.ok.android.sdk.util.OkDevice;
import ru.ok.android.sdk.util.OkScope;

public class MainActivity extends Activity {
    protected static final String APP_ID = "125497344";
    protected static final String APP_KEY = "CBABPLHIABABABABA";
    protected static final String REDIRECT_URL = "okauth://ok125497344";

    protected Odnoklassniki odnoklassniki;

    private View loginView;
    private View formView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        loginView = findViewById(R.id.login_block);

        Button mLoginBtn = (Button) findViewById(R.id.sdk_login_any);
        Button mLoginBtnSso = (Button) findViewById(R.id.sdk_login_sso);
        Button mLoginBtnOAuth = (Button) findViewById(R.id.sdk_login_oauth);
        mLoginBtn.setOnClickListener(new LoginClickListener(OkAuthType.ANY));
        mLoginBtnSso.setOnClickListener(new LoginClickListener(OkAuthType.NATIVE_SSO));
        mLoginBtnOAuth.setOnClickListener(new LoginClickListener(OkAuthType.WEBVIEW_OAUTH));

        formView = findViewById(R.id.sdk_form);
        findViewById(R.id.sdk_get_currentuser).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View view) {
                new GetCurrentUserTask().execute();
            }
        });
        findViewById(R.id.sdk_get_friends).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View view) {
                new GetFriendsTask().execute();
            }
        });
        findViewById(R.id.sdk_logout).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View view) {
                odnoklassniki.clearTokens();
                hideForm();
            }
        });

        findViewById(R.id.sdk_post).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                odnoklassniki.performPosting(MainActivity.this,
                        "{\"media\":[{\"type\":\"text\",\"text\":\"hello world!\"}]}",
                        false, null);
            }
        });
        findViewById(R.id.sdk_app_invite).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                odnoklassniki.performAppInvite(MainActivity.this, null);
            }
        });
        findViewById(R.id.sdk_app_suggest).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                odnoklassniki.performAppSuggest(MainActivity.this, null);
            }
        });

        odnoklassniki = Odnoklassniki.createInstance(this, APP_ID, APP_KEY);
        odnoklassniki.checkValidTokens(new OkListener() {
            @Override
            public void onSuccess(JSONObject json) {
                showForm();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(MainActivity.this, String.format("%s: %s", getString(R.string.error), error), Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (Odnoklassniki.getInstance().onAuthActivityResult(requestCode, resultCode, data, getAuthListener())) {
            // authorization finished
        } else if (Odnoklassniki.getInstance().onActivityResultResult(requestCode, resultCode, data, getToastListener())) {
            //
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    /**
     * Creates a listener that displays result as a toast message
     */
    @NonNull
    private OkListener getToastListener() {
        return new OkListener() {
            @Override
            public void onSuccess(final JSONObject json) {
                Toast.makeText(MainActivity.this, json.toString(), Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(MainActivity.this, String.format("%s: %s", getString(R.string.error), error), Toast.LENGTH_LONG).show();
            }
        };
    }

    /**
     * Creates a listener that is run on OAUTH authorization completion
     */
    @NonNull
    private OkListener getAuthListener() {
        return new OkListener() {
            @Override
            public void onSuccess(final JSONObject json) {
                try {
                    Toast.makeText(MainActivity.this,
                            String.format("access_token: %s", json.getString("access_token")),
                            Toast.LENGTH_SHORT).show();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                showForm();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(MainActivity.this,
                        String.format("%s: %s", getString(R.string.error), error),
                        Toast.LENGTH_SHORT).show();
            }
        };
    }

    protected final void showForm() {
        formView.setVisibility(View.VISIBLE);
        loginView.setVisibility(View.GONE);

        testIfInstallationSourceIsOK();
    }

    protected final void hideForm() {
        formView.setVisibility(View.GONE);
        loginView.setVisibility(View.VISIBLE);
    }

    // Using AsyncTask is arbitrary choice
    // Developers should do a better error handling job ;)

    protected final class GetCurrentUserTask extends AsyncTask<Void, Void, String> {
        @Override
        protected String doInBackground(final Void... params) {
            try {
                return odnoklassniki.request("users.getCurrentUser", null, "get");
            } catch (Exception exc) {
                Log.e("Odnoklassniki", "Failed to get current user info", exc);
            }
            return null;
        }

        @Override
        protected void onPostExecute(final String result) {
            if (result != null) {
                Toast.makeText(MainActivity.this, "Get current user result: " + result, Toast.LENGTH_SHORT).show();
            }
        }
    }

    protected final class GetFriendsTask extends AsyncTask<Void, Void, String> {
        @Override
        protected String doInBackground(final Void... params) {
            try {
                return odnoklassniki.request("friends.get", null, "get");
            } catch (Exception exc) {
                Log.e("Odnoklassniki", "Failed to get friends", exc);
            }
            return null;
        }

        @Override
        protected void onPostExecute(final String result) {
            if (result != null) {
                Toast.makeText(MainActivity.this, "Get user friends result: " + result, Toast.LENGTH_SHORT).show();
            }
        }
    }

    protected class LoginClickListener implements OnClickListener {
        private OkAuthType authType;

        public LoginClickListener(OkAuthType authType) {
            this.authType = authType;
        }

        @Override
        public void onClick(final View view) {
            odnoklassniki.requestAuthorization(MainActivity.this, REDIRECT_URL, authType, OkScope.VALUABLE_ACCESS);
        }
    }

    private void testIfInstallationSourceIsOK() {
        new AsyncTask<Void, Void, Integer>() {
            @Override
            protected Integer doInBackground(Void... params) {
                HashMap<String, String> args = new HashMap<>();
                args.put("adv_id", OkDevice.getAdvertisingId(MainActivity.this));
                int result;
                try {
                    result = Integer.parseInt(odnoklassniki.request("sdk.getInstallSource", args, EnumSet.of(OkRequestMode.GET, OkRequestMode.UNSIGNED)));
                } catch (IOException | NumberFormatException e) {
                    result = -1;
                }
                return result;
            }

            @Override
            protected void onPostExecute(Integer result) {
                Toast.makeText(MainActivity.this,
                        (result > 0 ?
                                "application installation caused by OK app (" + result + ")" :
                                "application is not caused by OK app (" + result + ")"),
                        Toast.LENGTH_SHORT).show();
            }
        }.execute();
    }
}
