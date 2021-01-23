package com.taggerlog;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import android.os.Bundle;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class MainActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private WebView webView;
    private WebView windowWebView;
    private ConstraintLayout container;
    private String TAG = "taggerlog";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mAuth = FirebaseAuth.getInstance();
        WebView.setWebContentsDebuggingEnabled(true);
        webView = (WebView) findViewById(R.id.webview);
        windowWebView = (WebView) findViewById(R.id.newWindowWebView);
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setSupportMultipleWindows(true);
        webSettings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        webSettings.setUserAgentString("Mozilla/5.0 (Linux; Android 10; moto g(8) power) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.141 Mobile Safari/537.36");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture,
                                Message resultMsg) {
                handleCreateWebWindowRequest(resultMsg);
                return true;
            }
        });

        webView.loadUrl("https://diarystore.firebaseapp.com/");
    }

    void handleCreateWebWindowRequest(Message resultMsg) {
        if(resultMsg == null) {
            return;
        }
        if(resultMsg.obj != null && resultMsg.obj instanceof WebView.WebViewTransport) {
            WebView.WebViewTransport transport = (WebView.WebViewTransport)resultMsg.obj;

            WebSettings windowWebViewSettings = windowWebView.getSettings();
            windowWebViewSettings.setJavaScriptEnabled(true);
            windowWebViewSettings.setJavaScriptCanOpenWindowsAutomatically(true);
            windowWebViewSettings.setSupportMultipleWindows(true);
            windowWebViewSettings.setDomStorageEnabled(true);
            windowWebViewSettings.setUserAgentString("Mozilla/5.0 (Linux; Android 10; moto g(8) power) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.141 Mobile Safari/537.36");
            windowWebView.setWebViewClient(new WebViewClient());
            windowWebView.setWebChromeClient(new WebChromeClient() {
                @Override
                public void onCloseWindow(WebView window) {
                    super.onCloseWindow(window);
                    handleCloseWebWindowRequest();
                }
            });

            webView.setVisibility(View.GONE);
            windowWebView.setVisibility(View.VISIBLE);
            transport.setWebView(windowWebView);
            resultMsg.sendToTarget();
        }
    }

    void handleCloseWebWindowRequest() {
        if (!isWebWindowOpened()) return;
        windowWebView.setVisibility(View.INVISIBLE);
        webView.setVisibility(View.VISIBLE);
    }

    boolean isWebWindowOpened() {
        return true;
    }

    @Override
    public void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        updateUI(currentUser);
    }

    public void updateUI(FirebaseUser currentUser) {

    }
}