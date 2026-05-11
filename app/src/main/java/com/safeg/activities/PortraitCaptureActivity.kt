package com.safeg.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.view.View
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
import com.safeg.dispenser.K720Manager
import org.json.JSONObject

class PortraitCaptureActivity : AppCompatActivity() {

    private lateinit var barcodeView: DecoratedBarcodeView
    private lateinit var qrInput: EditText
    private lateinit var k720Manager: K720Manager
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

        findViewById<TextView>(R.id.collectCardMsg).text = "Scan Invitation QR Code"
        findViewById<TextView>(R.id.tvSubtitle).text = "Place QR Code within the QR Scanner"
        findViewById<TextView>(R.id.tvThankYou).visibility = View.GONE
        findViewById<ImageView>(R.id.arrow).visibility = View.GONE
        findViewById<TextView>(R.id.tvTitle).visibility = View.GONE

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

    private fun lookupVisitor(data: String) {
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
                    .setTag(Constants.getVisitorPassByStaffNoOrName)
                    .setPriority(Priority.HIGH)
                    .addJSONObjectBody(JSONObject().put("username", data))
                    .build()
                    .getAsObjectList(
                        GetStaffPassByStaffNoOrNameResponseItem::class.java,
                        object : ParsedRequestListener<List<GetStaffPassByStaffNoOrNameResponseItem>> {
                            override fun onResponse(response: List<GetStaffPassByStaffNoOrNameResponseItem>) {
                                pDialog.hide()
                                if (response.isNotEmpty()) {
                                    val visitor = response[0]

                                    // ✅ Set all visitor data
                                    StaticData.request.ic = visitor.icNo ?: ""
                                    StaticData.request.passport = visitor.passportNo ?: ""
                                    StaticData.request.fullName = visitor.name ?: "" // ✅ set full name

                                    when {
                                        // ✅ VVIP — go to VvipConfirm
                                        StaticData.invitation &&
                                                visitor.visitorType?.equals("VVIP", ignoreCase = true) == true -> {
                                            StaticData.isVvip = true
                                            StaticData.vvipName = visitor.name ?: ""
                                            StaticData.vvipIc = visitor.icNo
                                                ?.takeIf { it.isNotBlank() }
                                                ?: visitor.passportNo ?: ""
                                            startActivity(Intent(this@PortraitCaptureActivity, VvipConfirmActivity::class.java))
                                        }

                                        // ✅ Collect Card — go to CollectCard directly
                                        StaticData.collectCard -> {
                                            Common.showToast(applicationContext, "Scan Card now.", Common.ToastType.INFO)
                                            if (!visitor.passportNo.isNullOrBlank() && visitor.icNo.isNullOrBlank()) {
                                                StaticData.request.ic = visitor.passportNo ?: ""
                                            }
                                            startActivity(Intent(this@PortraitCaptureActivity, CollectCardActivity::class.java))
                                        }

                                        // ✅ Invitation — fix foreigner flag, go to CardDetails
                                        StaticData.invitation -> {
                                            if (!visitor.passportNo.isNullOrBlank() && visitor.icNo.isNullOrBlank()) {
                                                StaticData.isForeigner = true
                                                StaticData.request.ic = visitor.passportNo ?: ""
                                            }
                                            startActivity(Intent(this@PortraitCaptureActivity, CardDetailsActivity::class.java))
                                        }

                                        // ✅ Walk-in local — go to NoticeActivity
                                        else -> {
                                            if (!visitor.passportNo.isNullOrBlank() && visitor.icNo.isNullOrBlank()) {
                                                StaticData.isForeigner = true
                                                StaticData.request.ic = visitor.passportNo ?: ""
                                                startActivity(Intent(this@PortraitCaptureActivity, CardDetailsActivity::class.java))
                                                return
                                            }
                                            startActivity(Intent(this@PortraitCaptureActivity, NoticeActivity::class.java))
                                        }
                                    }
                                } else {
                                    Common.showToast(applicationContext, "No Records Found", Common.ToastType.ERROR)
                                    startActivity(Intent(this@PortraitCaptureActivity, SelectOptionActivity::class.java))
                                }
                            }

                            override fun onError(anError: ANError) {
                                pDialog.hide()
                                Log.e("Portrait", anError.errorDetail)
                                Common.showToast(applicationContext, "No Records Found", Common.ToastType.ERROR)
                                finish()
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