package com.safeg.activities;

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.androidnetworking.AndroidNetworking
import com.androidnetworking.common.Priority
import com.androidnetworking.error.ANError
import com.androidnetworking.interfaces.JSONObjectRequestListener
import com.awesomedialog.blennersilva.awesomedialoglibrary.AwesomeProgressDialog
import com.fasterxml.jackson.databind.ObjectMapper
import com.safeg.Constants
import com.safeg.R
import com.safeg.StaticData
import com.safeg.databinding.ActivityNoticeBinding
import com.safeg.databinding.ActivityWelcomeBinding
import com.safeg.utils.Common
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.io.InputStream
import java.security.KeyStore
import java.security.SecureRandom
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

public class NoticeActivity : AppCompatActivity(), View.OnClickListener {

    private lateinit var binding : ActivityNoticeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNoticeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setListeners()
    }

    private fun setListeners() {
        binding.rlOkay.setOnClickListener(this)
        binding.ivBack.setOnClickListener(this)
    }

    override fun onClick(view: View?) {
        when (view?.id) {
            R.id.rlOkay -> {
                finish()
                startActivity(Intent(this@NoticeActivity, CardDetailsActivity::class.java))
            }

            R.id.ivBack -> {
                finish()
//                startActivity(Intent(this@NoticeActivity, CardDetailsActivity::class.java))
            }
        }
    }
}
