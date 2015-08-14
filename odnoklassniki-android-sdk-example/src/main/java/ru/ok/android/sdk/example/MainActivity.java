package ru.ok.android.sdk.example;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import ru.ok.android.sdk.Odnoklassniki;
import ru.ok.android.sdk.OkListener;
import ru.ok.android.sdk.util.OkScope;

public class MainActivity extends Activity {
    protected static final String APP_ID = "125497344";
    protected static final String APP_KEY = "CBABPLHIABABABABA";
    protected static final String REDIRECT_URL = "okauth://ok125497344";

    protected Odnoklassniki mOdnoklassniki;

    private View mLoginBtn;
    private View mFormView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mLoginBtn = findViewById(R.id.sdk_login);
        mLoginBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View view) {
                mOdnoklassniki.requestAuthorization(new OkListener() {
                    @Override
                    public void onSuccess(final JSONObject json) {
                        try {
                            Toast.makeText(MainActivity.this, String.format("access_token: %s", json.getString("access_token")), Toast.LENGTH_SHORT).show();
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        showForm();
                    }

                    @Override
                    public void onError(String error) {
                        Toast.makeText(MainActivity.this, String.format("%s: %s", getString(R.string.error), error), Toast.LENGTH_SHORT).show();
                    }
                }, REDIRECT_URL, false, OkScope.VALUABLE_ACCESS);
            }
        });

        mFormView = findViewById(R.id.sdk_form);
        findViewById(R.id.sdk_get_currentuser).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View view) {
                new GetCurrentUserTask().execute(new Void[0]);
            }
        });
        findViewById(R.id.sdk_get_friends).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View view) {
                new GetFriendsTask().execute(new Void[0]);
            }
        });
        findViewById(R.id.sdk_logout).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View view) {
                mOdnoklassniki.clearTokens();
                hideForm();
            }
        });
        findViewById(R.id.sdk_post).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mOdnoklassniki.performPosting("{\"media\":[{\"type\":\"text\",\"text\":\"hello world!\"}]}", false, new OkListener() {
                    @Override
                    public void onSuccess(final JSONObject json) {
                        Toast.makeText(MainActivity.this, json.toString(), Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onError(String error) {
                        Toast.makeText(MainActivity.this, String.format("%s: %s", getString(R.string.error), error), Toast.LENGTH_LONG).show();
                    }
                });
            }
        });

        mOdnoklassniki = Odnoklassniki.createInstance(this, APP_ID, APP_KEY);
        mOdnoklassniki.checkValidTokens(new OkListener() {
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

    protected final void showForm() {
        mFormView.setVisibility(View.VISIBLE);
        mLoginBtn.setVisibility(View.GONE);
    }

    protected final void hideForm() {
        mFormView.setVisibility(View.GONE);
        mLoginBtn.setVisibility(View.VISIBLE);
    }

    // Using AsyncTask is arbitrary choice
    // Developers should do a better error handling job ;)

    protected final class GetCurrentUserTask extends AsyncTask<Void, Void, String> {
        @Override
        protected String doInBackground(final Void... params) {
            try {
                return mOdnoklassniki.request("users.getCurrentUser", null, "get");
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
                return mOdnoklassniki.request("friends.get", null, "get");
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
}
