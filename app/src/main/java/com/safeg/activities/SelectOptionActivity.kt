package com.safeg.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import androidx.appcompat.app.AppCompatActivity
import com.androidnetworking.AndroidNetworking
import com.androidnetworking.common.Priority
import com.androidnetworking.error.ANError
import com.androidnetworking.interfaces.ParsedRequestListener
import com.awesomedialog.blennersilva.awesomedialoglibrary.AwesomeInfoDialog
import com.awesomedialog.blennersilva.awesomedialoglibrary.AwesomeProgressDialog
import com.safeg.Constants
import com.safeg.R
import com.safeg.StaticData
import com.safeg.databinding.ActivitySelectOptionBinding
import com.safeg.databinding.ActivityWelcomeBinding
import com.safeg.models.DoVisitorPassReqMobile
import com.safeg.models.GetActiveCountryResponseItem
import com.safeg.models.GetConfigResponseItem
import com.safeg.utils.Common
import okhttp3.OkHttpClient
import java.io.InputStream
import java.security.KeyStore
import java.security.SecureRandom
import java.util.ArrayList
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory


class SelectOptionActivity : AppCompatActivity(), View.OnClickListener {

    private val PERMISSIONS_REQUEST = 1001

    private lateinit var binding : ActivitySelectOptionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySelectOptionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setListeners()

        requestPermissions(false)
//        Constants.refreshUrls()
    }

    private fun invokeConfigApi() {
        val pDialog = AwesomeProgressDialog(this)
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
                AndroidNetworking.get(Constants.getModuleConfig)
                    .setTag(Constants.getModuleConfig)
                    .setPriority(Priority.HIGH)
                    .build()
                    .getAsObject(
                        GetConfigResponseItem::class.java,
                        object : ParsedRequestListener<GetConfigResponseItem> {
                            override fun onResponse(response: GetConfigResponseItem) {
                                pDialog.hide()
                                StaticData.moduleConfig = response;
                            }

                            override fun onError(anError: ANError) {
                                pDialog.hide()
                                Common.showToast(
                                    applicationContext,
                                    "Error Code : " + Constants.getModuleConfig  + " " + anError.errorCode + ", Details : " + anError.errorDetail
                                )
                            }
                        })
            })
        }).start()
    }

    private fun setListeners() {
        binding.rlForeigner.setOnClickListener(this)
        binding.rlCollect.setOnClickListener(this)
        binding.rlMalaysianPr.setOnClickListener(this)
        binding.ipSet.setOnClickListener(this)
    }

    private fun showIpSetDialog() {
        val editText = android.widget.EditText(this)
        editText.hint = "e.g. https://192.168.1.10:8443/"
        editText.setText(Constants.base_url)

        val padding = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._16sdp)
        editText.setPadding(padding, padding, padding, padding)

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Set Server IP / Base URL")
            .setMessage("Enter server base URL")
            .setView(editText)
            .setCancelable(false)
            .setPositiveButton("Save") { dialog, _ ->
                val url = editText.text.toString().trim()

                if (url.isEmpty()) {
                    Common.showToast(this, "Base URL cannot be empty")
                } else {
                    Constants.base_url = url
                    Constants.refreshUrls()
                    Common.showToast(this, "Base URL saved")
                    invokeConfigApi() // reload config with new IP
                    dialog.dismiss()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }


    override fun onClick(view: View?) {
        when (view?.id) {
            R.id.rlMalaysianPr -> {
                StaticData.isForeigner = false
                StaticData.collectCard = false
                StaticData.invitation = true
                if(StaticData.moduleConfig.vpOCR){
//                    finish()
                    startActivity(Intent(this@SelectOptionActivity, PortraitCaptureActivity::class.java))
                } else {
//                    finish()
                    startActivity(Intent(this@SelectOptionActivity, WelcomeActivity::class.java))
                }
            }
            R.id.rlForeigner -> {
                StaticData.isForeigner = true
                StaticData.invitation = false
//                finish()
                 startActivity(Intent(this@SelectOptionActivity, WelcomeActivity::class.java))
            }
            R.id.rlCollect -> {
                StaticData.collectCard = true
//                finish()
                startActivity(Intent(this@SelectOptionActivity, PortraitCaptureActivity::class.java))
            }
            R.id.ipSet -> {
// open dialog to set ip
                showIpSetDialog()
            }
        }
    }

    private fun requestPermissions(showToast: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                invokeConfigApi()
            } else {
                if (showToast) {
                    Common.showToast(this@SelectOptionActivity, "Please allow permissions to continue.")
                }
                val permissions = arrayOf<String>(
                    Manifest.permission.CAMERA
                )
                requestPermissions(permissions, PERMISSIONS_REQUEST)
            }
        } else {
            invokeConfigApi()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        requestPermissions(true)
    }

    override fun onBackPressed() {
        val dialog = AwesomeInfoDialog(this)
        dialog.setTitle("Confirmation")
        dialog.setMessage("Do you really want to close the " + getString(R.string.app_name) + "?")
        dialog.setColoredCircle(com.awesomedialog.blennersilva.awesomedialoglibrary.R.color.dialogInfoBackgroundColor)
        dialog.setDialogIconAndColor(
            com.awesomedialog.blennersilva.awesomedialoglibrary.R.drawable.ic_dialog_info,
            com.awesomedialog.blennersilva.awesomedialoglibrary.R.color.white
        )
        dialog.setCancelable(false)

        dialog.setPositiveButtonText("Yes")
        dialog.setPositiveButtonbackgroundColor(com.awesomedialog.blennersilva.awesomedialoglibrary.R.color.dialogInfoBackgroundColor)
        dialog.setPositiveButtonTextColor(com.awesomedialog.blennersilva.awesomedialoglibrary.R.color.white)
        dialog.setPositiveButtonClick {
            dialog.hide()
            finish()
        }

        dialog.setNegativeButtonText("No")
        dialog.setNegativeButtonbackgroundColor(com.awesomedialog.blennersilva.awesomedialoglibrary.R.color.dialogInfoBackgroundColor)
        dialog.setNegativeButtonTextColor(com.awesomedialog.blennersilva.awesomedialoglibrary.R.color.white)
        dialog.setNegativeButtonClick {
            dialog.hide()
        }
        dialog.show()
    }

    override fun onStart() {
        super.onStart()
        StaticData.request = DoVisitorPassReqMobile();
    }


}