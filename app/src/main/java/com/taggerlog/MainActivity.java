package com.taggerlog;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Debug;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.IdpResponse;
import com.firebase.ui.auth.data.model.User;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;
import com.google.gson.Gson;

/*
import com.google.auth.oauth2.GoogleCredentials;

 */

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

public class MainActivity extends AppCompatActivity {

    private Gson gson;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseUser user;
    private WebView webView;
    private String TAG = "MainActivity";
    private static final int RC_SIGN_IN = 123;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        WebView.setWebContentsDebuggingEnabled(true);
        webView = (WebView) findViewById(R.id.webview);

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webView.addJavascriptInterface(new TaggerLogInterface(this), "AndroidInterface");
        webSettings.setDomStorageEnabled(true);
        webView.loadUrl("file:///android_asset/web/index.html");
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    /**
     * Javascript interface for use by the WebView
    */
    public class TaggerLogInterface {
        Context mContext;

        /** Instantiate the interface and set the context */
        TaggerLogInterface(Context c) {
            mContext = c;
        }

        @JavascriptInterface
        public void init() {
            webView.post(new Runnable() {
                @Override
                public void run() {
                    webView.evaluateJavascript("taggerlog.init();", null);
                    webView.evaluateJavascript("taggerlog.updateLoggedInUI();", null);
                }
            });
        }

        @JavascriptInterface
        public void deleteEntry(String id) {

        }

        @JavascriptInterface
        public void addEntry(Object o) {

        }

        @JavascriptInterface
        public void logIn() {
            // Choose authentication providers
            List<AuthUI.IdpConfig> providers = Arrays.asList(
                    new AuthUI.IdpConfig.GoogleBuilder().build());

            // Create and launch sign-in intent
            startActivityForResult(
                    AuthUI.getInstance()
                            .createSignInIntentBuilder()
                            .setAvailableProviders(providers)
                            .build(),
                    RC_SIGN_IN);
        }
        
        @JavascriptInterface
        public void getEntries() {
            Query q = db.collection("diary-entry").orderBy("date", Query.Direction.DESCENDING)
                    .whereEqualTo("uid", user.getUid())
                    .limit(10);

            q.get(Source.SERVER).addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                @Override
                public void onComplete(@NonNull Task<QuerySnapshot> task) {
                    if(task.isSuccessful()) {
                        for(QueryDocumentSnapshot doc : task.getResult()) {
                            Map<String, Object> data = doc.getData();
                            Timestamp dateTS = (Timestamp)data.get("date");
                            data.put("date", dateToISO(dateTS.toDate()));
                            Timestamp dateModifiedTS = (Timestamp)data.get("date-modified");
                            data.put("date-modified", dateToISO(dateModifiedTS.toDate()));
                            data.put("entry", data.get("entry").toString().replaceAll("\\n", "\\\\n"));
                            data.put("id", doc.getId());

                            String json = gson.toJson(data);
                            webView.evaluateJavascript(String.format("taggerlog.insertEntry('%s', true)", json), null);
                        }
                        webView.evaluateJavascript(String.format("taggerlog.updateQueryRelatedTags();"), null);
                        webView.evaluateJavascript(String.format("taggerlog.refreshUI();"), null);
                    }
                    else {
                        Log.w(TAG, "Error getting documents.", task.getException());
                    }
                }
            });
        }

        /**
         * Convert date to ISO 8601 format.
         *
         * @param d A Date object
         * @return A date string
         */
        public String dateToISO(Date d) {
            SimpleDateFormat sdf;
            sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            String text = sdf.format(d);
            return text;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            IdpResponse response = IdpResponse.fromResultIntent(data);

            if (resultCode == RESULT_OK) {
                // Successfully signed in
                user = FirebaseAuth.getInstance().getCurrentUser();

                gson = new Gson();
                Map<String, String> userData = new HashMap<String, String>();
                userData.put("uid", user.getUid());
                userData.put("displayName", user.getDisplayName());
                userData.put("email", user.getEmail());
                userData.put("photoURL", user.getPhotoUrl().toString());
                String json = gson.toJson(userData);
                webView.evaluateJavascript(String.format("taggerlog.setUser(JSON.parse('%s'));", json), null);
                // ...
            } else {
                // Sign in failed. If response is null the user canceled the
                // sign-in flow using the back button. Otherwise check
                // response.getError().getErrorCode() and handle the error.
                // ...
            }
        }
    }
}