package com.safeg.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.androidnetworking.AndroidNetworking
import com.androidnetworking.common.Priority
import com.androidnetworking.error.ANError
import com.androidnetworking.interfaces.JSONObjectRequestListener
import com.awesomedialog.blennersilva.awesomedialoglibrary.AwesomeInfoDialog
import com.awesomedialog.blennersilva.awesomedialoglibrary.AwesomeProgressDialog
import com.fasterxml.jackson.databind.ObjectMapper
import com.safeg.Constants
import com.safeg.R
import com.safeg.StaticData
import com.safeg.utils.Common
import com.safeg.utils.SslUtils
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ThankYouActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FLOW = "extra_flow"
        const val FLOW_WALKIN = "walkin"
        const val FLOW_INVITATION = "invitation"
        const val FLOW_COLLECT_CARD = "collect_card"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_thank_you)

        // ✅ Get flow type from intent
        val flow = intent.getStringExtra(EXTRA_FLOW) ?: FLOW_WALKIN

        // ✅ Find message TextView in layout
        val tvMessage = findViewById<TextView>(R.id.tvThankYouMessage)

        when (flow) {
            FLOW_INVITATION, FLOW_COLLECT_CARD -> {
                // ✅ Invitation or Collect Card — card issued message + auto redirect
                tvMessage?.text = "Thank You, Have a Great Day!\nYour visitor card has been issued successfully."
                autoRedirectToMain(3000)
            }
            else -> {
                // ✅ Walk-in — registration API call
                tvMessage?.text = "An email/SMS has been sent\nto your phone as digital\nconfirmation for your visit"
                invokeRegistrationApi()
            }
        }
    }

    // ✅ Auto redirect to main page after delay
    private fun autoRedirectToMain(delayMs: Long) {
        Handler(Looper.getMainLooper()).postDelayed({
            // ✅ Reset StaticData for next visitor
            resetStaticData()
            finish()
            val intent = Intent(this@ThankYouActivity, SelectOptionActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }, delayMs)
    }

    // ✅ Reset all static data for next visitor
    private fun resetStaticData() {
        StaticData.request = com.safeg.models.DoVisitorPassReqMobile()
        StaticData.base64_mykad = ""
        StaticData.base64_face = ""
        StaticData.base64_mysejahtera = ""
        StaticData.base64_vaccinationCertificate = ""
        StaticData.isForeigner = false
        StaticData.collectCard = false
        StaticData.invitation = false
        StaticData.isVvip = false
        StaticData.vvipName = ""
        StaticData.vvipIc = ""
    }

    private fun invokeRegistrationApi() {
        if (!StaticData.base64_mykad.isEmpty()) {
            StaticData.request.icBased64.add("data:image/png;base64," + StaticData.base64_mykad)
        }
        if (!StaticData.base64_face.isEmpty()) {
            StaticData.request.icBased64.add("data:image/png;base64," + StaticData.base64_face)
            StaticData.request.base64_face = ("data:image/png;base64," + StaticData.base64_face)
        }
        if (!StaticData.base64_mysejahtera.isEmpty()) {
            StaticData.request.icBased64.add("data:image/png;base64," + StaticData.base64_mysejahtera)
        }
        if (!StaticData.base64_vaccinationCertificate.isEmpty()) {
            StaticData.request.icBased64.add("data:image/png;base64," + StaticData.base64_vaccinationCertificate)
        }

        StaticData.request.plksExpiry = ""

        val pDialog = AwesomeProgressDialog(this).apply {
            setCancelable(false)
            setTitle("Please wait")
            setMessage("")
            setColoredCircle(R.color.pherosi)
            show()
        }

        val ow = ObjectMapper().writer().withDefaultPrettyPrinter()
        val json = ow.writeValueAsString(StaticData.request)

        Log.d("PAYLOAD", json)
        Log.d("PAYLOAD_URL", Constants.visitorPassRegistrationLite)

        Thread {
            val client = SslUtils.trustAllClient().newBuilder()
                .connectTimeout(180, TimeUnit.SECONDS)
                .readTimeout(180, TimeUnit.SECONDS)
                .writeTimeout(180, TimeUnit.SECONDS)
                .build()
            AndroidNetworking.initialize(applicationContext, client)

            runOnUiThread {
                AndroidNetworking.post(Constants.visitorPassRegistrationLite)
                    .setTag(Constants.visitorPassRegistrationLite)
                    .setPriority(Priority.HIGH)
                    .addJSONObjectBody(JSONObject(json))
                    .build()
                    .getAsJSONObject(object : JSONObjectRequestListener {
                        override fun onResponse(response: JSONObject?) {
                            pDialog.hide()
                            Log.d("PAYLOAD_RESPONSE", response.toString())
                            if (response!!.getString("status").compareTo("OK") == 0) {
                                showInfoDialog(this@ThankYouActivity, "Process completed successfully.", "Information")
                            } else {
                                showErrorDialog(this@ThankYouActivity, response.getString("message"), response.getString("status"))
                            }
                        }

                        override fun onError(anError: ANError?) {
                            pDialog.hide()
                            Log.e("PAYLOAD_ERROR", "errorBody: ${anError?.errorBody}, errorDetail: ${anError?.errorDetail}")
                            showErrorDialog(this@ThankYouActivity, anError!!.errorBody, "API Response")
                            Common.showToast(applicationContext, "Error Code : " + anError.errorCode + ", Details : " + anError.errorDetail, Common.ToastType.ERROR)
                        }
                    })
            }
        }.start()
    }

    fun showInfoDialog(context: Activity?, msg: String?, title: String?) {
        val dialog = AwesomeInfoDialog(context)
        dialog.setTitle(title)
        dialog.setMessage(msg)
        dialog.setColoredCircle(com.awesomedialog.blennersilva.awesomedialoglibrary.R.color.dialogInfoBackgroundColor)
        dialog.setDialogIconAndColor(
            com.awesomedialog.blennersilva.awesomedialoglibrary.R.drawable.ic_dialog_info,
            com.awesomedialog.blennersilva.awesomedialoglibrary.R.color.white
        )
        dialog.setCancelable(false)
        dialog.setPositiveButtonText("OK")
        dialog.setPositiveButtonbackgroundColor(com.awesomedialog.blennersilva.awesomedialoglibrary.R.color.dialogInfoBackgroundColor)
        dialog.setPositiveButtonTextColor(com.awesomedialog.blennersilva.awesomedialoglibrary.R.color.white)
        dialog.setPositiveButtonClick {
            dialog.hide()
            resetStaticData()
            finish()
            val intent = Intent(this@ThankYouActivity, SelectOptionActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
        dialog.show()
    }

    fun showErrorDialog(context: Activity?, msg: String?, title: String?) {
        val dialog = AwesomeInfoDialog(context)
        dialog.setTitle(title)
        dialog.setMessage(msg)
        dialog.setColoredCircle(com.awesomedialog.blennersilva.awesomedialoglibrary.R.color.dialogErrorBackgroundColor)
        dialog.setDialogIconAndColor(
            com.awesomedialog.blennersilva.awesomedialoglibrary.R.drawable.ic_dialog_error,
            com.awesomedialog.blennersilva.awesomedialoglibrary.R.color.white
        )
        dialog.setCancelable(false)
        dialog.setPositiveButtonText("OK")
        dialog.setPositiveButtonbackgroundColor(com.awesomedialog.blennersilva.awesomedialoglibrary.R.color.dialogErrorBackgroundColor)
        dialog.setPositiveButtonTextColor(com.awesomedialog.blennersilva.awesomedialoglibrary.R.color.white)
        dialog.setPositiveButtonClick {
            dialog.hide()
            resetStaticData()
            finish()
            startActivity(Intent(this@ThankYouActivity, WelcomeActivity::class.java))
        }
        dialog.show()
    }
}