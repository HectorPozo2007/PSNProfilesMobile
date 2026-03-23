package com.example.psnprofilesmobile;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceError;
import android.widget.ProgressBar;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONArray;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public class MainActivity extends AppCompatActivity {

    private WebView myWebView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ProgressBar progressBar;
    private String userAgent;

    // Cache the CSS string in memory to avoid repeated disk reads
    private String cachedCss = null;

    // Executor service to offload I/O operations from the main thread
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    
    // Class-level Handler to prevent anonymous inner class memory leaks
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable domPollingRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Find views by ID
        myWebView = findViewById(R.id.webView);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        progressBar = findViewById(R.id.progressBar);

        // Load the CSS in a background thread to prevent StrictMode violations
        executorService.execute(() -> {
            cachedCss = getCSSFromAssets();
        });

        setupWebView();
        setupSwipeRefresh();
        setupBackNavigation();

        // Load the initial URL
        myWebView.loadUrl("https://psnprofiles.com/guides/popular");
    }

    private void setupWebView() {
        WebSettings webSettings = myWebView.getSettings();

        // Basic settings
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        
        // Hardware Acceleration
        myWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        
        // Delay image loading to improve performance
        webSettings.setLoadsImagesAutomatically(false);
        
        // Setup userAgent and cookies
        userAgent = webSettings.getUserAgentString();
        android.webkit.CookieManager cookieManager = android.webkit.CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(myWebView, true);

        // WebChromeClient to track loading progress for the ProgressBar
        myWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                if (newProgress < 100) {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(newProgress);
                } else {
                    progressBar.setVisibility(View.GONE);
                }
            }
        });

        // Set WebViewClient
        myWebView.setWebViewClient(new WebViewClient() {
            
            // Gracefully handle network offline or server crash scenarios
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                // Ensure we only hide loaders for the main frame to avoid hiding when a small tracking script fails
                if (request.isForMainFrame()) {
                    progressBar.setVisibility(View.GONE);
                    if (swipeRefreshLayout.isRefreshing()) {
                        swipeRefreshLayout.setRefreshing(false);
                    }
                }
            }
            
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                view.getSettings().setLoadsImagesAutomatically(true);
                // Stop the swipe refresh layout loading wheel
                if (swipeRefreshLayout.isRefreshing()) {
                    swipeRefreshLayout.setRefreshing(false);
                }

                // 1. First, we inject our CSS and Viewport natively via Javascript
                if (cachedCss != null && !cachedCss.isEmpty()) {
                    // We must escape our CSS to inject it cleanly using JS
                    String escapedCss = cachedCss
                            .replace("\\", "\\\\")
                            .replace("'", "\\'")
                            .replace("\"", "\\\"")
                            .replace("\n", "\\n")
                            .replace("\r", "");

                    String injectCSS = "javascript:(function() {" +
                            "var parent = document.getElementsByTagName('head').item(0);" +
                            "var style = document.createElement('style');" +
                            "style.type = 'text/css';" +
                            "style.innerHTML = '" + escapedCss + "';" +
                            "if (parent != null) parent.appendChild(style);" +
                            "var meta = document.createElement('meta');" +
                            "meta.name = 'viewport';" +
                            "meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no';"
                            +
                            "if (parent != null) parent.appendChild(meta);" +
                            "})()";
                    view.evaluateJavascript(injectCSS, null);
                }

                // 2. Active DOM Polling loop to bypass Cloudflare reliably without static 2000ms waits
                // Cancel any previous loop that might be running
                if (domPollingRunnable != null) {
                    mainHandler.removeCallbacks(domPollingRunnable);
                }
                
                domPollingRunnable = new Runnable() {
                    int attempts = 0;
                    final int MAX_ATTEMPTS = 20; // 10 seconds total given 500ms delay

                    @Override
                    public void run() {
                        if (myWebView == null) return;
                        
                        attempts++;
                        
                        // Check if a primary container or guide element has loaded, confirming Cloudflare has passed
                        myWebView.evaluateJavascript(
                            "(function() { return document.querySelector('#content') != null || document.querySelector('.guide') != null || document.querySelector('.box') != null; })();",
                            new ValueCallback<String>() {
                                @Override
                                public void onReceiveValue(String isDOMReady) {
                                    // evaluateJavascript returns the boolean as a string "true" or "false"
                                    if ("true".equals(isDOMReady)) {
                                        // DOM has fully rendered the target website elements! Extract the HTML.
                                        myWebView.evaluateJavascript(
                                                "(function() { return document.documentElement.outerHTML; })();",
                                                new ValueCallback<String>() {
                                                    @Override
                                                    public void onReceiveValue(String htmlString) {
                                                        if (htmlString != null && !htmlString.equals("null")) {
                                                            try {
                                                                JSONArray jsonArray = new JSONArray("[" + htmlString + "]");
                                                                String decodedHtml = jsonArray.getString(0);
                                                                
                                                                // Process with JSoup natively on a background thread!
                                                                executorService.execute(() -> {
                                                                    Document document = Jsoup.parse(decodedHtml);
                                                                    // TODO: Parse the data out of the connected document here
                                                                });
                                                            } catch (Exception e) {
                                                                e.printStackTrace();
                                                            }
                                                        }
                                                    }
                                                }
                                        );
                                    } else {
                                        // Target not found securely yet (e.g. still looping on Cloudflare 'Checking Browser')
                                        // Retry in 500ms, until max attempts hit
                                        if (attempts < MAX_ATTEMPTS) {
                                            mainHandler.postDelayed(domPollingRunnable, 500);
                                        }
                                    }
                                }
                            }
                        );
                    }
                };
                
                // Start the polling cycle 
                mainHandler.post(domPollingRunnable);
            }
        });
    }

    private void setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener(() -> {
            if (myWebView != null) {
                myWebView.reload();
            }
        });
    }

    private void setupBackNavigation() {
        // Modern replacement for deprecated onBackPressed()
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (myWebView != null && myWebView.canGoBack()) {
                    // Navigate back in WebView history
                    myWebView.goBack();
                } else {
                    // Otherwise exit the app normally
                    setEnabled(false); // disable custom callback to let default behavior run
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (myWebView != null) {
            myWebView.onPause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (myWebView != null) {
            myWebView.onResume();
        }
    }

    @Override
    protected void onDestroy() {
        // Stop background handlers to prevent memory leaks if activity is closed
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }
        
        if (myWebView != null) {
            // Safely detach WebView before destroying to prevent memory leaks
            ViewGroup parent = (ViewGroup) myWebView.getParent();
            if (parent != null) {
                parent.removeView(myWebView);
            }
            myWebView.removeAllViews();
            myWebView.destroy();
        }
        
        // Shutdown executor cleanly
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
        super.onDestroy();
    }

    // Safely parse stream instead of using un-reliable inputStream.available()
    private String getCSSFromAssets() {
        StringBuilder cssBuilder = new StringBuilder();
        try (InputStream inputStream = getAssets().open("style_psn.css");
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                cssBuilder.append(line).append("\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return cssBuilder.toString();
    }
}
