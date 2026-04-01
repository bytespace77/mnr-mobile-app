package com.safeg.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.androidnetworking.AndroidNetworking
import com.androidnetworking.common.Priority
import com.androidnetworking.error.ANError
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

class PortraitCaptureActivity : AppCompatActivity() {

    private lateinit var barcodeView: DecoratedBarcodeView
    private lateinit var k720Manager: K720Manager

    private var isProcessing = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_scan_custom)

        barcodeView = findViewById(R.id.barcode_scanner)
        barcodeView.decodeContinuous(callback)
        barcodeView.getStatusView().setVisibility(View.GONE);

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
            barcodeView.pause()
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
            runOnUiThread(Runnable {
                AndroidNetworking.post(Constants.getVisitorPassByStaffNoOrName)
                    .setTag(Constants.getVisitorPassByStaffNoOrName)
                    .setPriority(Priority.HIGH)
//                    .addQueryParameter("keyword", search)
                    .addJSONObjectBody(JSONObject().put("username", data))
                    .build()
                    .getAsObjectList(
                        GetStaffPassByStaffNoOrNameResponseItem::class.java,
                        object :
                            ParsedRequestListener<List<GetStaffPassByStaffNoOrNameResponseItem>> {
                            override fun onResponse(response: List<GetStaffPassByStaffNoOrNameResponseItem>) {
                                pDialog.hide()
                                if (response.size > 0) {
                                    StaticData.request.ic = response.get(0).icNo
                                    StaticData.request.passport = response.get(0).passportNo
                                    if (StaticData.collectCard) {
                                        Common.showToast(
                                            applicationContext,
                                            "Scan Card now."
                                        )
                                        if (response[0].icNo != null && !response[0].icNo.trim().isEmpty()) {}
                                        else if (response[0].passportNo != null && !response[0].passportNo.trim().isEmpty()) {
//                                            StaticData.isForeigner = true
                                            StaticData.request.ic = response[0].passportNo
                                        }
                                        startActivity(
                                            Intent(
                                                this@PortraitCaptureActivity,
                                                CollectCardActivity::class.java
                                            )
                                        )

                                    } else {
                                        if (response[0].icNo != null && !response[0].icNo.trim().isEmpty()) {}
                                        else if (response[0].passportNo != null && !response[0].passportNo.trim().isEmpty())
                                        {StaticData.isForeigner = true
                                            StaticData.request.ic = response[0].passportNo
                                            startActivity(
                                                Intent(
                                                    this@PortraitCaptureActivity,
                                                    CardDetailsActivity::class.java
                                                )
                                            )
                                        return}
                                        startActivity(
                                            Intent(
                                                this@PortraitCaptureActivity,
                                                NoticeActivity::class.java
                                            )
                                        )

                                    }
                                } else {
                                    Common.showToast(
                                        applicationContext,
                                        "No Records found for $data."
                                    )
                                    startActivity(Intent(this@PortraitCaptureActivity, SelectOptionActivity::class.java))
                                }
                            }

                            override fun onError(anError: ANError) {
                                print(anError.errorDetail)
                                pDialog.hide()
                                Common.showToast(
                                    applicationContext,
                                    "No Records found." + anError.errorDetail
                                )
                                finish()
                            }
                        })
            })

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
