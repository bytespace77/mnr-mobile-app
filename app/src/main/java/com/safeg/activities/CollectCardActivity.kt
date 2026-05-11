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
import com.androidnetworking.interfaces.JSONObjectRequestListener
import com.awesomedialog.blennersilva.awesomedialoglibrary.AwesomeProgressDialog
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.safeg.Constants
import com.safeg.R
import com.safeg.StaticData
import com.safeg.utils.Common
import com.safeg.utils.SslUtils
import com.safeg.dispenser.K720Manager
import org.json.JSONObject

class CollectCardActivity : AppCompatActivity() {

    companion object {
        init {
            try {
                System.loadLibrary("ttceserial_port")
                Log.d("K720", "✅ Native lib loaded")
            } catch (e: UnsatisfiedLinkError) {
                Log.e("K720", "❌ Native lib: ${e.message}")
            }
        }
    }

    private lateinit var barcodeView: DecoratedBarcodeView
    private lateinit var qrInput: EditText
    private var k720Manager: K720Manager? = null
    private lateinit var collectCardMsg: TextView
    private lateinit var arrow: ImageView
    private var isProcessing = false
    private val handler = Handler(Looper.getMainLooper())
    private var scanRunnable: Runnable? = null
    private var dispenseThread: Thread? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
            setContentView(R.layout.activity_qr_scan_custom2)

            // ✅ Debug toast
            Common.showToast(applicationContext, "CollectCard started — isVvip:${StaticData.isVvip} inv:${StaticData.invitation}", Common.ToastType.INFO)

            barcodeView = findViewById(R.id.barcode_scanner)
            barcodeView.visibility = View.GONE

            qrInput = findViewById(R.id.qrInput)
            collectCardMsg = findViewById(R.id.collectCardMsg)
            arrow = findViewById(R.id.arrow)

            qrInput.showSoftInputOnFocus = false

            collectCardMsg.text = "Kindly Collect Card and Scan Card QR Code"
            collectCardMsg.visibility = View.VISIBLE

            findViewById<TextView>(R.id.tvSubtitle).text = "Place QR Code within the QR Scanner"
            findViewById<TextView>(R.id.tvThankYou).visibility = View.VISIBLE
            findViewById<ImageView>(R.id.ivScanIcon).visibility = View.GONE

            arrow.rotation = 90f
            arrow.scaleX = 2f
            arrow.visibility = View.VISIBLE

            findViewById<TextView>(R.id.tvTitle).visibility = View.GONE

            qrInput.requestFocus()
            hideKeyboard()

            findViewById<ImageView>(R.id.ivBack).setOnClickListener {
                setResult(RESULT_CANCELED)
                finish()
            }

            try {
                k720Manager = K720Manager(this)
            } catch (e: Exception) {
                Log.e("K720", "K720Manager init failed: ${e.message}")
                Common.showToast(applicationContext, "K720 init failed: ${e.message}", Common.ToastType.ERROR)
            }

            dispenseThread = Thread {
                try {
                    val mgr = k720Manager ?: run {
                        runOnUiThread { Common.showToast(applicationContext, "K720 manager is null!", Common.ToastType.ERROR) }
                        return@Thread
                    }
                    val connected = mgr.connect()
                    // ✅ Debug toast
                    runOnUiThread { Common.showToast(applicationContext, "K720 connected: $connected", Common.ToastType.INFO) }
                    if (connected) {
                        runOnUiThread { Common.showToast(applicationContext, "Dispensing Card...", Common.ToastType.INFO) }
                        mgr.sendCard()
                    } else {
                        runOnUiThread { Common.showToast(applicationContext, "Card Dispenser Not Connected", Common.ToastType.WARNING) }
                    }
                } catch (e: Exception) {
                    Log.e("K720", "dispense error: ${e.message}")
                    runOnUiThread { Common.showToast(applicationContext, "Dispense error: ${e.message}", Common.ToastType.ERROR) }
                }
            }
            dispenseThread!!.start()

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
                            processCard(scanned)
                        }
                    }
                    handler.postDelayed(scanRunnable!!, 200)
                }
            })

        } catch (e: Exception) {
            Log.e("COLLECT", "onCreate crash: ${e.message}", e)
            Common.showToast(applicationContext, "CollectCard crash: ${e.message}", Common.ToastType.ERROR)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scanRunnable?.let { handler.removeCallbacks(it) }
        try {
            k720Manager?.disconnect()
            Log.d("K720", "K720 disconnected ✅")
        } catch (e: Exception) {
            Log.e("K720", "Disconnect error: ${e.message}")
        }
        k720Manager = null
    }

    private fun hideKeyboard() {
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(qrInput.windowToken, 0)
            currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
            window.decorView.postDelayed({
                if (!isFinishing && !isDestroyed) {
                    imm.hideSoftInputFromWindow(qrInput.windowToken, 0)
                }
            }, 300)
        } catch (e: Exception) {
            Log.e("COLLECT", "hideKeyboard error: ${e.message}")
        }
    }

    private fun processCard(data: String) {
        // ✅ Debug toast
        Common.showToast(applicationContext, "Scanning — isVvip:${StaticData.isVvip} inv:${StaticData.invitation}", Common.ToastType.INFO)

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
                AndroidNetworking.post(Constants.decrypt)
                    .setTag(Constants.decrypt)
                    .setPriority(Priority.HIGH)
                    .addJSONObjectBody(
                        JSONObject()
                            .put("ciphertext", data)
                            .put("secret", "M&RV\$it0@")
                    )
                    .build()
                    .getAsJSONObject(object : JSONObjectRequestListener {
                        override fun onResponse(response: JSONObject) {
                            val decryptedValue = response.optString("value", "")
                            // ✅ Debug toast
                            Common.showToast(applicationContext, "Decrypt ok — value:${decryptedValue.take(10)}", Common.ToastType.INFO)

                            val requestBody = if (StaticData.isVvip) {
                                JSONObject()
                                    .put("cardId", decryptedValue)
                                    .put("icNo", StaticData.vvipIc.ifBlank { StaticData.request.ic })
                                    .put("role", "VVIP")
                                    .put("visitorType", "VVIP")
                            } else {
                                JSONObject()
                                    .put("cardId", decryptedValue)
                                    .put("icNo",
                                        if (StaticData.isForeigner)
                                            StaticData.request.passport.ifBlank { StaticData.request.ic }
                                        else
                                            StaticData.request.ic
                                    )
                            }

                            AndroidNetworking.post(Constants.insertVendorPassCard)
                                .setTag(Constants.insertVendorPassCard)
                                .setPriority(Priority.HIGH)
                                .addJSONObjectBody(requestBody)
                                .build()
                                .getAsJSONObject(object : JSONObjectRequestListener {
                                    override fun onResponse(insertResponse: JSONObject) {
                                        pDialog.hide()
                                        // ✅ Debug toast
                                        Common.showToast(applicationContext, "Insert: ${insertResponse.optString("status")}", Common.ToastType.INFO)

                                        val isSuccess = try {
                                            insertResponse.getBoolean("status")
                                        } catch (e: Exception) {
                                            insertResponse.optString("status", "") == "true"
                                        }

                                        if (isSuccess) {
                                            if (StaticData.isVvip) {
                                                Common.showToast(applicationContext, "VVIP Card Assigned Successfully", Common.ToastType.SUCCESS)
                                                finish()
                                                startActivity(Intent(this@CollectCardActivity, VvipThankYouActivity::class.java))
                                            } else {
                                                Common.showToast(applicationContext, "Card Assigned Successfully", Common.ToastType.SUCCESS)
                                                finish()
                                                val intent = Intent(this@CollectCardActivity, ThankYouActivity::class.java)
                                                intent.putExtra(
                                                    ThankYouActivity.EXTRA_FLOW,
                                                    if (StaticData.invitation) ThankYouActivity.FLOW_INVITATION
                                                    else ThankYouActivity.FLOW_COLLECT_CARD
                                                )
                                                startActivity(intent)
                                            }
                                        } else {
                                            val msg = insertResponse.optString("message", "")
                                            val displayMsg = when {
                                                msg.contains("expired", ignoreCase = true) -> "Card Expired — Please Try Again"
                                                msg.contains("exist", ignoreCase = true) -> "Card Already Assigned"
                                                msg.contains("invalid", ignoreCase = true) -> "Invalid Card"
                                                msg.isNotBlank() -> msg
                                                else -> "Failed to Assign Card"
                                            }
                                            Common.showToast(applicationContext, displayMsg, Common.ToastType.ERROR)
                                            isProcessing = false
                                            qrInput.requestFocus()
                                            hideKeyboard()
                                        }
                                    }

                                    override fun onError(anError: ANError) {
                                        pDialog.hide()
                                        Common.showToast(applicationContext, "Insert error: ${anError.errorCode} ${anError.errorDetail}", Common.ToastType.ERROR)
                                        isProcessing = false
                                        qrInput.requestFocus()
                                        hideKeyboard()
                                    }
                                })
                        }

                        override fun onError(anError: ANError) {
                            pDialog.hide()
                            Common.showToast(applicationContext, "Decrypt error: ${anError.errorCode} ${anError.errorDetail}", Common.ToastType.ERROR)
                            isProcessing = false
                            qrInput.requestFocus()
                            hideKeyboard()
                        }
                    })
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        if (!isFinishing && !isDestroyed) {
            qrInput.requestFocus()
            hideKeyboard()
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        }
    }
}