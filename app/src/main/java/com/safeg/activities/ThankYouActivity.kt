package com.safeg.activities

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
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
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.io.InputStream
import java.security.KeyStore
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory


class ThankYouActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_thank_you)

        if(!StaticData.base64_mykad.isEmpty()){
            StaticData.request.icBased64.add("data:image/png;base64,"+StaticData.base64_mykad);
        }

        if(!StaticData.base64_face.isEmpty()){
            StaticData.request.icBased64.add("data:image/png;base64,"+StaticData.base64_face);
            StaticData.request.base64_face = ("data:image/png;base64,"+StaticData.base64_face);
        }

        if(!StaticData.base64_mysejahtera.isEmpty()){
            StaticData.request.icBased64.add("data:image/png;base64,"+StaticData.base64_mysejahtera);
        }

        if(!StaticData.base64_vaccinationCertificate.isEmpty()){
            StaticData.request.icBased64.add("data:image/png;base64,"+StaticData.base64_vaccinationCertificate);
        }

        StaticData.request.plksExpiry = "";

        val pDialog = AwesomeProgressDialog(this)
        pDialog.setCancelable(false)
        pDialog.setTitle("Please wait")
        pDialog.setMessage("")
        pDialog.setColoredCircle(R.color.pherosi)
        pDialog.show()
        val ow = ObjectMapper().writer().withDefaultPrettyPrinter()
        val json = ow.writeValueAsString(StaticData.request)
//        print("helllooo")
//        print(StaticData.request)
//        Log.d("DBG_", "Error when bind preview")
//        Log.d("DBG_", StaticData.request.toString())
//        return
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
                val okHttpClient = builder
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .build()
                AndroidNetworking.initialize(applicationContext, okHttpClient)
            } catch (thorowable : Throwable){
                Common.showToast(
                    applicationContext,
                    thorowable.message
                )
            }
            runOnUiThread(Runnable {
                AndroidNetworking.post(Constants.visitorPassRegistrationLite)
                    .setTag(Constants.visitorPassRegistrationLite)
                    .setPriority(Priority.HIGH)
                    .addJSONObjectBody(JSONObject(json))
                    .build()
                    .getAsJSONObject(object : JSONObjectRequestListener{
                        override fun onResponse(response: JSONObject?) {
                            pDialog.hide()
                            if(response!!.getString("status").compareTo("OK") == 0){
                                showInfoDialog(this@ThankYouActivity, "Process completed successfully.", "Information");
                                println("tttttttttttttttt")
                            } else {
                                showErrorDialog(this@ThankYouActivity, response!!.getString("message"), response!!.getString("status"));
                            }
                        }

                        override fun onError(anError: ANError?) {
                            pDialog.hide()
                            showErrorDialog(this@ThankYouActivity, anError!!.errorBody, "API Response");
                            Common.showToast(
                                applicationContext,
                                "Error Code : " + anError.errorCode + ", Details : " + anError.errorDetail
                            )
                        }
                    })
            })
        }).start()
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
            finish()
//            startActivity(Intent(this@ThankYouActivity, WelcomeActivity::class.java));
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
            finish()
            startActivity(Intent(this@ThankYouActivity, WelcomeActivity::class.java));
        }

        dialog.show()
    }


}
