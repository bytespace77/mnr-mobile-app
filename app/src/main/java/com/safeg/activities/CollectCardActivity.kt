package com.safeg.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.androidnetworking.AndroidNetworking
import com.androidnetworking.common.Priority
import com.androidnetworking.error.ANError
import com.androidnetworking.interfaces.JSONObjectRequestListener
import com.androidnetworking.interfaces.ParsedRequestListener
import com.awesomedialog.blennersilva.awesomedialoglibrary.AwesomeProgressDialog
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.safeg.Constants
import com.safeg.R
import com.safeg.StaticData
import com.safeg.adapters.StaffAdapter
import com.safeg.models.GetStaffPassByStaffNoOrNameResponseItem
import com.safeg.utils.Common
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.io.InputStream
import java.security.KeyStore
import java.security.SecureRandom
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import com.safeg.dispenser.K720Manager
import com.safeg.utils.Common.showToast

class CollectCardActivity : AppCompatActivity() {

    private lateinit var barcodeView: DecoratedBarcodeView
    private lateinit var k720Manager: K720Manager

    private var isProcessing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_scan_custom)

        barcodeView = findViewById(R.id.barcode_scanner)
        barcodeView.decodeContinuous(callback)
        barcodeView.getStatusView().setVisibility(View.GONE);
        findViewById<TextView>(R.id.tvTitle).setText("Scan Visitor Card QR Code")

        findViewById<ImageView>(R.id.ivBack).setOnClickListener {
                setResult(RESULT_CANCELED)
                finish()
            }
        try {
            System.loadLibrary("ttceserial_port")
            Log.d("K720", "✅ Native lib loaded")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("K720", "❌ Missing lib: ${e.message}")
        }

        k720Manager = K720Manager(this)

//        if (k720Manager.connect()) {
//            Common.showToast(this, "con")
//        } else {
//            Common.showToast(this, "nocon")
//        }
    }

    private val callback = BarcodeCallback { result: BarcodeResult? ->
        if (isProcessing) return@BarcodeCallback   // 🔒 block multiple triggers
        isProcessing = true
        result?.let {
            val data = it.text
            val intent = intent
            intent.putExtra("qr_data", data)
            setResult(RESULT_OK, intent)
//            Common.showToast(this, data)
//            finish()
//            startActivity(Intent(applicationContext, VisitUpdateDetailsActivity::class.java))

            val pDialog = AwesomeProgressDialog(this)
            pDialog.setCancelable(false)
            pDialog.setCancelable(false)
            pDialog.setCancelable(false)
            pDialog.setTitle("Please wait")
            pDialog.setMessage("")
            pDialog.setColoredCircle(R.color.pherosi)
            pDialog.show()
            Thread(Runnable {
                try {
                    val trustStore = KeyStore.getInstance("PKCS12")
                    val In: InputStream = getResources().openRawResource(
                        R.raw.server
                    )
                    trustStore.load(In, "safeg2023".toCharArray())
                    val tmf = TrustManagerFactory
                        .getInstance(TrustManagerFactory.getDefaultAlgorithm())
                    tmf.init(trustStore)
                    val sslCtx = SSLContext.getInstance("TLS")
                    sslCtx.init(
                        null, tmf.trustManagers,
                        SecureRandom()
                    )
                    HttpsURLConnection.setDefaultSSLSocketFactory(
                        sslCtx
                            .socketFactory
                    )
                    val builder = OkHttpClient.Builder()
                    builder.sslSocketFactory( sslCtx.socketFactory)
                    val okHttpClient = builder.build()
                    AndroidNetworking.initialize(applicationContext, okHttpClient)
                } catch (thorowable : Throwable){
                    Common.showToast(
                        applicationContext,
                        thorowable.message
                    )
                }
                runOnUiThread {
                    // 1️⃣ Call decryption API first
                    AndroidNetworking.post(Constants.decrypt)
                        .setTag(Constants.decrypt)
                        .setPriority(Priority.HIGH)
                        .addJSONObjectBody( JSONObject()
                            .put("ciphertext", data)
                            .put("secret", "M&RV\$it0@"))
                        .build()
                        .getAsJSONObject(object : JSONObjectRequestListener {
                            override fun onResponse(response: JSONObject) {
                                // Decrypted value from API
                                val decryptedValue = response.optString("value") // should be "QR Code Encrypt"
//                                Common.showToast(
//                                    applicationContext,
//                                    decryptedValue
//                                )
                                // Now call your existing insert API
                                AndroidNetworking.post(Constants.insertVendorPassCard)
                                    .setTag(Constants.insertVendorPassCard)
                                    .setPriority(Priority.HIGH)
                                    .addJSONObjectBody(
                                        JSONObject()
                                            .put("cardId", decryptedValue)
                                            .put("icNo", StaticData.request.ic)
                                    )
                                    .build()
                                    .getAsJSONObject(object : JSONObjectRequestListener {
                                        override fun onResponse(insertResponse: JSONObject) {
                                            pDialog.hide()
                                            if (insertResponse.getBoolean("status")) {
                                                finish()
                                                startActivity(
                                                    Intent(
                                                        this@CollectCardActivity,
                                                        SelectOptionActivity::class.java
                                                    )
                                                )
                                                Common.showToast(
                                                    applicationContext,
                                                    "Card assigned successfully"
                                                )
                                            } else {
                                                Common.showToast(
                                                    applicationContext,
                                                    insertResponse.getString("message")
                                                )
                                                startActivity(
                                                    Intent(
                                                        this@CollectCardActivity,
                                                        WelcomeActivity::class.java
                                                    )
                                                )
                                            }
                                        }

                                        override fun onError(anError: ANError) {
                                            pDialog.hide()
                                            Common.showToast(
                                                applicationContext,
                                                "Insert API Error: ${anError.errorDetail}"
                                            )
                                            startActivity(
                                                Intent(
                                                    this@CollectCardActivity,
                                                    WelcomeActivity::class.java
                                                )
                                            )
                                        }
                                    })
                            }

                            override fun onError(anError: ANError) {
                                pDialog.hide()
                                Log.e("K720", "Decrypt API Error: ${anError.errorBody} ${Constants.decrypt}")
                                Common.showToast(
                                    applicationContext,
                                    "Decrypt API Error:  ${Constants.decrypt}"
                                )
/////
//                                AndroidNetworking.post(Constants.insertVendorPassCard)
//                                    .setTag(Constants.insertVendorPassCard)
//                                    .setPriority(Priority.HIGH)
//                                    .addJSONObjectBody(
//                                        JSONObject()
//                                            .put("cardId", "1002")
//                                            .put("icNo", StaticData.request.ic)
//                                    )
//                                    .build()
//                                    .getAsJSONObject(object : JSONObjectRequestListener {
//                                        override fun onResponse(insertResponse: JSONObject) {
//                                            pDialog.hide()
//                                            if (insertResponse.getBoolean("status")) {
//                                                finish()
//                                                startActivity(
//                                                    Intent(
//                                                        this@CollectCardActivity,
//                                                        SelectOptionActivity::class.java
//                                                    )
//                                                )
//                                                Common.showToast(
//                                                    applicationContext,
//                                                    "Card assigned successfully"
//                                                )
//                                            } else {
//                                                Common.showToast(
//                                                    applicationContext,
//                                                    "No Records found."
//                                                )
//                                                startActivity(
//                                                    Intent(
//                                                        this@CollectCardActivity,
//                                                        WelcomeActivity::class.java
//                                                    )
//                                                )
//                                            }
//                                        }
//
//                                        override fun onError(anError: ANError) {
//                                            pDialog.hide()
//                                            Common.showToast(
//                                                applicationContext,
//                                                "Insert API Error: ${anError.errorDetail}"
//                                            )
//                                            startActivity(
//                                                Intent(
//                                                    this@CollectCardActivity,
//                                                    WelcomeActivity::class.java
//                                                )
//                                            )
//                                        }
//                                    })

//////

                            }
                        })
                }

        }).start()
        }
    }

    override fun onResume() {
        super.onResume()
        barcodeView.resume()
    }

    override fun onPause() {
        super.onPause()
        barcodeView.pause()
    }
}
