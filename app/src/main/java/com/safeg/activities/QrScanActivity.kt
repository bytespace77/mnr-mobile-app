package com.safeg.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions

class QrScanActivity : AppCompatActivity() {

    private val qrLauncher = registerForActivityResult(ScanContract()) { result: ScanIntentResult ->
        if (result.contents != null) {
            // Return the scanned data to the calling activity
            val intent = Intent()
            intent.putExtra("qr_data", result.contents)
            setResult(Activity.RESULT_OK, intent)
            finish()
        } else {
            Toast.makeText(this, "No QR code detected", Toast.LENGTH_SHORT).show()
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startQrScan()
    }

    private fun startQrScan() {
        val options = ScanOptions()
        options.setPrompt("Scan Staff QR Code")
        options.setBeepEnabled(true)
        options.setOrientationLocked(false)
        options.setCaptureActivity(CaptureActivity::class.java)
        qrLauncher.launch(options)
    }
}
