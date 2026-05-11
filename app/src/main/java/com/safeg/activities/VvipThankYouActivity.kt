package com.safeg.activities

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.awesomedialog.blennersilva.awesomedialoglibrary.AwesomeInfoDialog
import com.safeg.R
import com.safeg.StaticData
import com.safeg.models.DoVisitorPassReqMobile

class VvipThankYouActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vvip_thankyou)

        findViewById<TextView>(R.id.tvVvipName).text = StaticData.vvipName.ifBlank { "" }

        showSuccessDialog()
    }

    private fun showSuccessDialog() {
        val dialog = AwesomeInfoDialog(this)
        dialog.setTitle("VVIP Card Issued!")
        dialog.setMessage("VVIP card has been successfully issued to ${StaticData.vvipName.ifBlank { "visitor" }}. Welcome!")
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
            goHome()
        }
        dialog.show()
    }

    private fun goHome() {
        StaticData.isVvip = false
        StaticData.vvipName = ""
        StaticData.vvipIc = ""
        StaticData.base64_face = ""
        StaticData.request = DoVisitorPassReqMobile()

        val intent = Intent(this, SelectOptionActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }
}