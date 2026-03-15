package com.mascan.attendance;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.PermissionRequest;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.JavascriptInterface;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.graphics.drawable.GradientDrawable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.ResultPoint;
import com.journeyapps.barcodescanner.BarcodeView;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar progressBar;
    private FrameLayout scannerOverlayHost;
    private FrameLayout embeddedScannerContainer;
    private BarcodeView embeddedScannerView;
    private SharedPreferences prefs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean embeddedScannerActive = false;
    private boolean scannerCooldown = false;
    private int embeddedScannerDocLeftPx = 0;
    private int embeddedScannerDocTopPx = 0;
    private int embeddedScannerWidthPx = 0;
    private int embeddedScannerHeightPx = 0;

    // Change this default IP to your laptop's local IP address (e.g. 192.168.1.100)
    private static final String DEFAULT_SERVER_IP = "192.168.1.100";
    private static final int DEFAULT_PORT = 5000;
    private static final String PREFS_NAME = "MaScanPrefs";
    private static final String KEY_SERVER_IP = "server_ip";
    private static final String KEY_SERVER_PORT = "server_port";
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 1001;
    private static final int QR_SCANNER_REQUEST_CODE = 2001;
    public static final String EXTRA_QR_RESULT = "qr_result";
    private static final int MENU_SERVER_SETTINGS = 1;
    private static final int MENU_RELOAD = 2;
    private static final int MENU_NATIVE_SCAN = 3;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        scannerOverlayHost = findViewById(R.id.scannerOverlayHost);

        ensureCameraPermission();

        // Configure WebView settings
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setAllowFileAccess(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMediaPlaybackRequiresUserGesture(false);
        webView.addJavascriptInterface(new WebAppBridge(), "MaScanAndroid");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            webView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                if (embeddedScannerActive) {
                    applyEmbeddedScannerBounds();
                }
            });
        }

        // Handle page loading progress
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 100) {
                    progressBar.setVisibility(android.view.View.VISIBLE);
                    progressBar.setProgress(newProgress);
                } else {
                    progressBar.setVisibility(android.view.View.GONE);
                }
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA)
                            == PackageManager.PERMISSION_GRANTED) {
                        request.grant(request.getResources());
                    } else {
                        request.deny();
                        ensureCameraPermission();
                        Toast.makeText(MainActivity.this,
                                "Camera permission is required for QR scanning",
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                syncEmbeddedScannerBoundsWithPage();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        WebResourceError error) {
                if (request.isForMainFrame()) {
                    stopEmbeddedScanner();
                    showConnectionError();
                }
            }
        });

        loadServerUrl();
    }

    private void ensureCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_REQUEST_CODE);
        }
    }

    private String getServerUrl() {
        String ip = prefs.getString(KEY_SERVER_IP, DEFAULT_SERVER_IP);
        int port = prefs.getInt(KEY_SERVER_PORT, DEFAULT_PORT);
        return "http://" + ip + ":" + port;
    }

    private void loadServerUrl() {
        if (!isNetworkAvailable()) {
            showNoNetworkError();
            return;
        }
        webView.loadUrl(getServerUrl());
    }

    private void showConnectionError() {
        new AlertDialog.Builder(this)
                .setTitle("Connection Failed")
                .setMessage("Could not connect to the server at:\n" + getServerUrl()
                        + "\n\nMake sure:\n"
                        + "• Your laptop Flask server is running\n"
                        + "• Your phone and laptop are on the same WiFi\n"
                        + "• The IP address is correct (tap Settings to change it)")
                .setPositiveButton("Retry", (d, w) -> loadServerUrl())
                .setNegativeButton("Change Server IP", (d, w) -> showServerSettingsDialog())
                .show();
    }

    private void showNoNetworkError() {
        new AlertDialog.Builder(this)
                .setTitle("No Network")
                .setMessage("Please connect to WiFi to use MaScan.")
                .setPositiveButton("Retry", (d, w) -> loadServerUrl())
                .show();
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    private void showServerSettingsDialog() {
        String currentIp = prefs.getString(KEY_SERVER_IP, DEFAULT_SERVER_IP);
        int currentPort = prefs.getInt(KEY_SERVER_PORT, DEFAULT_PORT);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Server Settings");

        android.view.View view = getLayoutInflater().inflate(R.layout.dialog_server_settings, null);
        EditText etIp = view.findViewById(R.id.et_server_ip);
        EditText etPort = view.findViewById(R.id.et_server_port);
        etIp.setText(currentIp);
        etPort.setText(String.valueOf(currentPort));
        builder.setView(view);

        builder.setPositiveButton("Connect", (dialog, which) -> {
            String newIp = etIp.getText().toString().trim();
            String portStr = etPort.getText().toString().trim();
            if (newIp.isEmpty()) {
                Toast.makeText(this, "IP address cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }
            int newPort = DEFAULT_PORT;
            try {
                newPort = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid port, using 5000", Toast.LENGTH_SHORT).show();
            }
            prefs.edit()
                    .putString(KEY_SERVER_IP, newIp)
                    .putInt(KEY_SERVER_PORT, newPort)
                    .apply();
            webView.clearCache(true);
            loadServerUrl();
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_SERVER_SETTINGS, 0, "Server Settings")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, MENU_RELOAD, 1, "Reload")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, MENU_NATIVE_SCAN, 2, "Native QR Scan")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MENU_SERVER_SETTINGS) {
            showServerSettingsDialog();
            return true;
        } else if (item.getItemId() == MENU_RELOAD) {
            loadServerUrl();
            return true;
        } else if (item.getItemId() == MENU_NATIVE_SCAN) {
            startNativeQrScan();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void startNativeQrScan() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ensureCameraPermission();
            Toast.makeText(this, "Grant camera permission and try again", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent scannerIntent = new Intent(this, ScannerActivity.class);
        startActivityForResult(scannerIntent, QR_SCANNER_REQUEST_CODE);
    }

    private class WebAppBridge {
        @JavascriptInterface
        public void startNativeQrScan() {
            runOnUiThread(() -> MainActivity.this.startNativeQrScan());
        }

        @JavascriptInterface
        public boolean canUseEmbeddedQrScan() {
            return true;
        }

        @JavascriptInterface
        public void startEmbeddedQrScan(int leftPx, int topPx, int widthPx, int heightPx) {
            runOnUiThread(() -> MainActivity.this.startEmbeddedScanner(leftPx, topPx, widthPx, heightPx));
        }

        @JavascriptInterface
        public void updateEmbeddedQrScanBounds(int leftPx, int topPx, int widthPx, int heightPx) {
            runOnUiThread(() -> MainActivity.this.updateEmbeddedScannerBounds(leftPx, topPx, widthPx, heightPx));
        }

        @JavascriptInterface
        public void stopEmbeddedQrScan() {
            runOnUiThread(MainActivity.this::stopEmbeddedScanner);
        }
    }

    private void ensureEmbeddedScannerView() {
        if (embeddedScannerView != null) {
            return;
        }

        embeddedScannerContainer = new FrameLayout(this);
        GradientDrawable roundedMask = new GradientDrawable();
        roundedMask.setColor(0xFF111111);
        roundedMask.setCornerRadius(dpToPx(18));
        embeddedScannerContainer.setBackground(roundedMask);
        embeddedScannerContainer.setClipToOutline(true);
        embeddedScannerContainer.setClipChildren(true);
        embeddedScannerContainer.setVisibility(View.INVISIBLE);

        embeddedScannerView = new BarcodeView(this);
        embeddedScannerView.setDecoderFactory(
            new DefaultDecoderFactory(Collections.singletonList(BarcodeFormat.QR_CODE))
        );
        embeddedScannerView.setVisibility(View.INVISIBLE);
        embeddedScannerContainer.addView(embeddedScannerView, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ));
        scannerOverlayHost.addView(embeddedScannerContainer);
    }

    private void startEmbeddedScanner(int leftPx, int topPx, int widthPx, int heightPx) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ensureCameraPermission();
            Toast.makeText(this, "Grant camera permission and reload scanner page", Toast.LENGTH_SHORT).show();
            return;
        }

        ensureEmbeddedScannerView();
        updateEmbeddedScannerBounds(leftPx, topPx, widthPx, heightPx);

        if (!embeddedScannerActive) {
            embeddedScannerView.decodeContinuous(embeddedScanCallback);
            embeddedScannerView.resume();
            embeddedScannerActive = true;
        }

        scannerOverlayHost.setVisibility(View.VISIBLE);
        embeddedScannerContainer.setVisibility(View.VISIBLE);
        embeddedScannerView.setVisibility(View.VISIBLE);
        embeddedScannerContainer.bringToFront();
    }

    private void updateEmbeddedScannerBounds(int leftPx, int topPx, int widthPx, int heightPx) {
        if (scannerOverlayHost == null) {
            return;
        }

        ensureEmbeddedScannerView();

        embeddedScannerDocLeftPx = Math.max(leftPx, 0);
        embeddedScannerDocTopPx = Math.max(topPx, 0);
        embeddedScannerWidthPx = Math.max(widthPx, 120);
        embeddedScannerHeightPx = Math.max(heightPx, 120);

        applyEmbeddedScannerBounds();
    }

    private void applyEmbeddedScannerBounds() {
        if (embeddedScannerContainer == null || webView == null) {
            return;
        }

        int safeLeft = Math.max(embeddedScannerDocLeftPx - webView.getScrollX(), 0);
        int safeTop = Math.max(embeddedScannerDocTopPx - webView.getScrollY(), 0);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                embeddedScannerWidthPx,
                embeddedScannerHeightPx
        );
        params.leftMargin = safeLeft;
        params.topMargin = safeTop;
        embeddedScannerContainer.setLayoutParams(params);
    }

    private void stopEmbeddedScanner() {
        if (embeddedScannerView != null) {
            embeddedScannerView.pause();
            embeddedScannerView.setVisibility(View.INVISIBLE);
        }
        if (embeddedScannerContainer != null) {
            embeddedScannerContainer.setVisibility(View.INVISIBLE);
        }
        if (scannerOverlayHost != null) {
            scannerOverlayHost.setVisibility(View.GONE);
        }
        embeddedScannerActive = false;
        scannerCooldown = false;
    }

    private final BarcodeCallback embeddedScanCallback = new BarcodeCallback() {
        @Override
        public void barcodeResult(BarcodeResult result) {
            if (!embeddedScannerActive || scannerCooldown || result == null || TextUtils.isEmpty(result.getText())) {
                return;
            }

            scannerCooldown = true;
            submitScannedQrToWeb(result.getText());
            mainHandler.postDelayed(() -> scannerCooldown = false, 1400);
        }

        @Override
        public void possibleResultPoints(List<ResultPoint> resultPoints) {
            // Not used.
        }
    };

    private void syncEmbeddedScannerBoundsWithPage() {
        if (!embeddedScannerActive) {
            return;
        }

        String script = "(function(){"
                + "var el=document.getElementById('qr-reader');"
                + "if(!el){return null;}"
                + "var rect=el.getBoundingClientRect();"
                + "var dpr=window.devicePixelRatio||1;"
                + "return JSON.stringify({left:Math.round(rect.left*dpr),top:Math.round(rect.top*dpr),width:Math.round(rect.width*dpr),height:Math.round(rect.height*dpr)});"
                + "})();";

        webView.evaluateJavascript(script, value -> {
            if (value == null || value.equals("null") || value.equals("\"null\"")) {
                return;
            }

            String clean = value.replace("\\\"", "").replace("\"", "");
            try {
                int left = extractJsonInt(clean, "left");
                int top = extractJsonInt(clean, "top");
                int width = extractJsonInt(clean, "width");
                int height = extractJsonInt(clean, "height");
                updateEmbeddedScannerBounds(left, top, width, height);
            } catch (Exception ignored) {
                // Ignore malformed geometry updates.
            }
        });
    }

    private int extractJsonInt(String json, String key) {
        String token = "\"" + key + "\":";
        int start = json.indexOf(token);
        if (start < 0) {
            return 0;
        }
        start += token.length();
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        return Integer.parseInt(json.substring(start, end));
    }

    private float dpToPx(int dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private String escapeForJs(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private void submitScannedQrToWeb(String qrValue) {
        if (TextUtils.isEmpty(qrValue)) {
            return;
        }

        String escapedQr = escapeForJs(qrValue);
        String script = "(function(){"
                + "var input=document.getElementById('qr-input');"
                + "var button=document.getElementById('submit-btn');"
                + "if(input){input.value='" + escapedQr + "';}"
                + "if(button){button.click();return 'submitted';}"
                + "if(typeof submitQRCode==='function'){submitQRCode();return 'submitted';}"
                + "return 'missing-elements';"
                + "})();";

        webView.evaluateJavascript(script, value -> {
            if (value != null && value.contains("submitted")) {
                Toast.makeText(MainActivity.this, "QR submitted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(MainActivity.this,
                        "Open the scanner page in the web app, then scan again",
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Camera permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this,
                        "Camera permission denied. Scanner may not work in this app.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == QR_SCANNER_REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                String qrContent = data.getStringExtra(EXTRA_QR_RESULT);
                if (!TextUtils.isEmpty(qrContent)) {
                    submitScannedQrToWeb(qrContent);
                } else {
                    Toast.makeText(this, "No QR code detected", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Scan cancelled", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onPause() {
        stopEmbeddedScanner();
        super.onPause();
    }
}
