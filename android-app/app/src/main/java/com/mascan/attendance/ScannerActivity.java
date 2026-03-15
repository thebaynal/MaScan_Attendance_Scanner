package com.mascan.attendance;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.ResultPoint;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;
import java.util.Collections;
import java.util.List;

public class ScannerActivity extends AppCompatActivity {

    private DecoratedBarcodeView barcodeView;
    private TextView statusText;
    private boolean scanned = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_scanner);

        barcodeView = findViewById(R.id.barcode_scanner);
        statusText = findViewById(R.id.scanner_status_text);

        barcodeView.getBarcodeView().setDecoderFactory(
                new DefaultDecoderFactory(Collections.singletonList(BarcodeFormat.QR_CODE))
        );

        // Make the scanning frame compact and centered, similar to wallet apps.
        barcodeView.getViewFinder().setLaserVisibility(true);

        findViewById(R.id.btn_cancel_scan).setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        scanned = false;
        if (statusText != null) {
            statusText.setText("Align QR code inside the frame");
        }
        barcodeView.decodeSingle(scanCallback);
        barcodeView.resume();
    }

    @Override
    protected void onPause() {
        barcodeView.pause();
        super.onPause();
    }

    private final BarcodeCallback scanCallback = new BarcodeCallback() {
        @Override
        public void barcodeResult(BarcodeResult result) {
            if (scanned || result == null || result.getText() == null) {
                return;
            }
            scanned = true;

            Intent data = new Intent();
            data.putExtra(MainActivity.EXTRA_QR_RESULT, result.getText());
            setResult(RESULT_OK, data);
            finish();
        }

        @Override
        public void possibleResultPoints(List<ResultPoint> resultPoints) {
            // Not used
        }
    };
}
