package com.safeg.activities

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.safeg.R
import com.safeg.StaticData

class VvipConfirmActivity : AppCompatActivity() {

    // ✅ Store locally so back press shows correct data even if StaticData cleared
    private var localVvipName: String = ""
    private var localVvipIc: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vvip_confirm)

        // ✅ Capture before any downstream activity clears StaticData
        localVvipName = StaticData.vvipName.ifBlank { "—" }
        localVvipIc = StaticData.vvipIc.ifBlank { "—" }

        findViewById<TextView>(R.id.tvVvipName).text = localVvipName
        findViewById<TextView>(R.id.tvVvipIc).text = localVvipIc

        findViewById<ImageView>(R.id.ivBack).setOnClickListener { finish() }

        findViewById<RelativeLayout>(R.id.rlProceed).setOnClickListener {
            startActivity(Intent(this, FaceDetectionActivity::class.java))
        }
    }

    // ✅ Restore StaticData + refresh UI on back press
    override fun onResume() {
        super.onResume()
        if (localVvipName.isNotBlank() && localVvipName != "—") {
            StaticData.vvipName = localVvipName
        }
        if (localVvipIc.isNotBlank() && localVvipIc != "—") {
            StaticData.vvipIc = localVvipIc
        }
        findViewById<TextView>(R.id.tvVvipName).text = localVvipName
        findViewById<TextView>(R.id.tvVvipIc).text = localVvipIc
    }
}