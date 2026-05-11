package com.safeg.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.androidnetworking.AndroidNetworking
import com.androidnetworking.common.Priority
import com.androidnetworking.error.ANError
import com.androidnetworking.interfaces.ParsedRequestListener
import com.awesomedialog.blennersilva.awesomedialoglibrary.AwesomeProgressDialog
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.safeg.Constants
import com.safeg.R
import com.safeg.StaticData
import com.safeg.models.GetStaffPassByStaffNoOrNameResponseItem
import com.safeg.utils.Common
import com.safeg.utils.SslUtils
import org.json.JSONObject

class VvipQrScanActivity : AppCompatActivity() {

    private lateinit var barcodeView: DecoratedBarcodeView
    private lateinit var qrInput: EditText
    private var isProcessing = false
    private val handler = Handler(Looper.getMainLooper())
    private var scanRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        setContentView(R.layout.activity_qr_scan_custom2)

        barcodeView = findViewById(R.id.barcode_scanner)
        barcodeView.visibility = View.GONE

        qrInput = findViewById(R.id.qrInput)
        qrInput.showSoftInputOnFocus = false
        qrInput.requestFocus()
        hideKeyboard()

        findViewById<TextView>(R.id.collectCardMsg).text = "Scan QR Code"
        findViewById<TextView>(R.id.tvSubtitle).text = "Place QR Code within the QR Scanner"
        findViewById<TextView>(R.id.tvThankYou).visibility = View.GONE
        findViewById<ImageView>(R.id.arrow).visibility = View.GONE
        findViewById<TextView>(R.id.tvTitle).visibility = View.GONE

        findViewById<ImageView>(R.id.ivBack).setOnClickListener { finish() }

        qrInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                scanRunnable?.let { handler.removeCallbacks(it) }
                scanRunnable = Runnable {
                    val scanned = s?.toString()?.trim() ?: return@Runnable
                    if (scanned.isNotEmpty() && !isProcessing) {
                        isProcessing = true
                        qrInput.setText("")
                        lookupVisitor(scanned)
                    }
                }
                handler.postDelayed(scanRunnable!!, 200)
            }
        })
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(qrInput.windowToken, 0)
        currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        window.decorView.postDelayed({
            imm.hideSoftInputFromWindow(qrInput.windowToken, 0)
        }, 300)
    }

    private fun lookupVisitor(qrData: String) {
        val pDialog = AwesomeProgressDialog(this).apply {
            setCancelable(false)
            setTitle("Please wait")
            setMessage("")
            setColoredCircle(R.color.pherosi)
            show()
        }

        Thread {
            AndroidNetworking.initialize(applicationContext, SslUtils.trustAllClient())
            runOnUiThread {
                AndroidNetworking.post(Constants.getVisitorPassByStaffNoOrName)
                    .setTag("vvip_lookup")
                    .setPriority(Priority.HIGH)
                    .addJSONObjectBody(JSONObject().put("username", qrData))
                    .build()
                    .getAsObjectList(
                        GetStaffPassByStaffNoOrNameResponseItem::class.java,
                        object : ParsedRequestListener<List<GetStaffPassByStaffNoOrNameResponseItem>> {
                            override fun onResponse(response: List<GetStaffPassByStaffNoOrNameResponseItem>) {
                                pDialog.hide()
                                if (response.isNotEmpty()) {
                                    val visitor = response[0]

                                    Log.d("VVIP_DEBUG", "visitorType=${visitor.visitorType} icNo=${visitor.icNo} passportNo=${visitor.passportNo} name=${visitor.name}")

                                    // ✅ Set all visitor data including fullName
                                    StaticData.request.ic = visitor.icNo ?: ""
                                    StaticData.request.passport = visitor.passportNo ?: ""
                                    StaticData.request.fullName = visitor.name ?: "" // ✅ fix

                                    when {
                                        // ✅ VVIP — always go to VvipConfirm → FaceDetection → CollectCard
                                        visitor.visitorType?.equals("VVIP", ignoreCase = true) == true -> {
                                            StaticData.isVvip = true
                                            StaticData.vvipName = visitor.name ?: ""
                                            StaticData.vvipIc = visitor.icNo
                                                ?.takeIf { it.isNotBlank() }
                                                ?: visitor.passportNo ?: ""
                                            Common.showToast(applicationContext, "VVIP: ${visitor.name} | IC: ${StaticData.vvipIc}", Common.ToastType.INFO)
                                            startActivity(Intent(this@VvipQrScanActivity, VvipConfirmActivity::class.java))
                                        }

                                        // ✅ Collect Card — normal user → straight to CollectCard
                                        StaticData.collectCard -> {
                                            if (!visitor.passportNo.isNullOrBlank() && visitor.icNo.isNullOrBlank()) {
                                                StaticData.isForeigner = true
                                                StaticData.request.passport = visitor.passportNo ?: ""
                                                StaticData.request.ic = visitor.passportNo ?: ""
                                            }
                                            startActivity(Intent(this@VvipQrScanActivity, CollectCardActivity::class.java))
                                        }

                                        // ✅ Foreigner — go to CardDetails
                                        !visitor.passportNo.isNullOrBlank() && visitor.icNo.isNullOrBlank() -> {
                                            StaticData.isForeigner = true
                                            StaticData.request.ic = visitor.passportNo ?: ""
                                            startActivity(Intent(this@VvipQrScanActivity, CardDetailsActivity::class.java))
                                        }

                                        // ✅ Local — go to NoticeActivity
                                        else -> {
                                            startActivity(Intent(this@VvipQrScanActivity, NoticeActivity::class.java))
                                        }
                                    }
                                } else {
                                    Common.showToast(applicationContext, "No Visitor Found for this QR", Common.ToastType.ERROR)
                                    isProcessing = false
                                    qrInput.requestFocus()
                                    hideKeyboard()
                                }
                            }

                            override fun onError(anError: ANError) {
                                pDialog.hide()
                                Log.e("Visitor", "Lookup error: ${anError.errorDetail}")
                                Common.showToast(applicationContext, "Network Error — Please Try Again", Common.ToastType.ERROR)
                                isProcessing = false
                                qrInput.requestFocus()
                                hideKeyboard()
                            }
                        }
                    )
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        qrInput.requestFocus()
        hideKeyboard()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
    }
}