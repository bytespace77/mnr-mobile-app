package com.safeg.activities

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.androidnetworking.AndroidNetworking
import com.androidnetworking.common.Priority
import com.androidnetworking.error.ANError
import com.androidnetworking.interfaces.ParsedRequestListener
import com.awesomedialog.blennersilva.awesomedialoglibrary.AwesomeInfoDialog
import com.awesomedialog.blennersilva.awesomedialoglibrary.AwesomeProgressDialog
import com.ml.frlib.facenet_android.data.ObjectBoxStore
import com.safeg.Constants
import com.safeg.R
import com.safeg.StaticData
import com.safeg.databinding.ActivitySelectOptionBinding
import com.safeg.models.DoVisitorPassReqMobile
import com.safeg.models.GetConfigResponseItem
import com.safeg.utils.Common
import com.safeg.utils.SslUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.util.Log

class SelectOptionActivity : AppCompatActivity(), View.OnClickListener {

    private val PERMISSIONS_REQUEST = 1001
    private lateinit var binding: ActivitySelectOptionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySelectOptionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setListeners()
        requestPermissions(false)

        // ✅ Pre-warm ObjectBox on app start — so FaceDetectionActivity loads faster
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                ObjectBoxStore.init(applicationContext)
                Log.i("PreWarm", "ObjectBox pre-warmed ✅")
            } catch (t: Throwable) {
                Log.w("PreWarm", "Pre-warm failed: ${t.message}")
            }
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.activeNetworkInfo?.isConnected == true
    }

    private fun invokeConfigApi() {
        if (!isNetworkAvailable()) {
            Common.showToast(this, "No network connection. Please check your connection.", Common.ToastType.WARNING)
            return
        }

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
                AndroidNetworking.get(Constants.getModuleConfig)
                    .setTag(Constants.getModuleConfig)
                    .setPriority(Priority.HIGH)
                    .build()
                    .getAsObject(
                        GetConfigResponseItem::class.java,
                        object : ParsedRequestListener<GetConfigResponseItem> {
                            override fun onResponse(response: GetConfigResponseItem) {
                                pDialog.hide()
                                StaticData.moduleConfig = response
                            }

                            override fun onError(anError: ANError) {
                                pDialog.hide()
                                Common.showToast(
                                    applicationContext,
                                    "Config Error: ${anError.errorCode} — ${anError.errorDetail}",
                                    Common.ToastType.ERROR
                                )
                            }
                        })
            }
        }.start()
    }

    private fun setListeners() {
        binding.rlForeigner.setOnClickListener(this)
        binding.rlCollect.setOnClickListener(this)
        binding.rlMalaysianPr.setOnClickListener(this)
        binding.ipSet.setOnClickListener(this)
    }

    private fun showIpSetDialog() {
        val editText = android.widget.EditText(this)
        editText.hint = "e.g. http://192.168.1.10:8080"
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
                    Common.showToast(this, "Base URL cannot be empty", Common.ToastType.WARNING)
                } else {
                    Constants.base_url = url
                    Constants.refreshUrls()
                    Common.showToast(this, "Base URL saved: $url", Common.ToastType.SUCCESS)
                    invokeConfigApi()
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
                // ✅ Malaysian/PR — invitation flow
                StaticData.isForeigner = false
                StaticData.collectCard = false
                StaticData.invitation = true
                StaticData.isVvip = false
                StaticData.base64_face = ""
                if (StaticData.moduleConfig.vpOCR) {
                    startActivity(Intent(this@SelectOptionActivity, PortraitCaptureActivity::class.java))
                } else {
                    startActivity(Intent(this@SelectOptionActivity, WelcomeActivity::class.java))
                }
            }
            R.id.rlForeigner -> {
                // ✅ Foreigner — walk-in flow
                StaticData.isForeigner = true
                StaticData.invitation = false
                StaticData.collectCard = false
                StaticData.isVvip = false
                StaticData.base64_face = ""
                startActivity(Intent(this@SelectOptionActivity, WelcomeActivity::class.java))
            }
            R.id.rlCollect -> {
                // ✅ Collect Card — reset all flags properly
                StaticData.collectCard = true
                StaticData.invitation = false
                StaticData.isForeigner = false
                StaticData.isVvip = false
                StaticData.base64_face = ""
                startActivity(Intent(this@SelectOptionActivity, VvipQrScanActivity::class.java))
            }
            R.id.ipSet -> {
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
                    Common.showToast(this@SelectOptionActivity, "Please allow permissions to continue.", Common.ToastType.WARNING)
                }
                requestPermissions(arrayOf(Manifest.permission.CAMERA), PERMISSIONS_REQUEST)
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
        StaticData.request = DoVisitorPassReqMobile()
    }
}