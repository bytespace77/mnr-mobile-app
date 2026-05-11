package com.safeg.activities

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.AdapterView
import androidx.appcompat.app.AppCompatActivity
import com.androidnetworking.AndroidNetworking
import com.androidnetworking.common.Priority
import com.androidnetworking.error.ANError
import com.androidnetworking.interfaces.ParsedRequestListener
import com.safeg.Constants
import com.safeg.StaticData
import com.safeg.databinding.ActivityCardDetailsBinding
import com.safeg.databinding.ActivityVaccinationCertificateDetailsBinding
import com.safeg.databinding.ActivityVaccinationDetailsBinding
import com.safeg.models.*
import com.safeg.utils.Common
import com.safeg.utils.Utils
import java.io.FileDescriptor
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*


class VaccinationCertificateDetailsActivity : AppCompatActivity() {

    lateinit var mUtils: Utils

    private lateinit var binding: ActivityVaccinationCertificateDetailsBinding

    lateinit var fullVaccinatedStringList: ArrayList<String>
    var selectedFullyVaccinated: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityVaccinationCertificateDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mUtils = Utils()

        binding.llLastDoseDate.setOnClickListener {
            dateOfBirthClicked()
        }

        binding.tvLastDoseDate.setOnClickListener {
            dateOfBirthClicked()
        }

        binding.ivLastDoseDate.setOnClickListener {
            dateOfBirthClicked()
        }

        binding.tvLastDoseDateTitle.setOnClickListener {
            dateOfBirthClicked()
        }

        binding.ivBack.setOnClickListener {
            finish()
        }

        binding.tvCaptrure.setOnClickListener {
            val camera_intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            startActivityForResult(camera_intent, 10002)
        }

        binding.tvUploadPhoto.setOnClickListener {
            val intent =
                Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            intent.type = "image/*"
            startActivityForResult(Intent.createChooser(intent, "Select Picture"), 10001)
        }


        binding.rlNext.setOnClickListener(object : View.OnClickListener {
            override fun onClick(p0: View?) {
                if (StaticData.base64_vaccinationCertificate.isNullOrEmpty()) {
                    Common.showToast(
                        this@VaccinationCertificateDetailsActivity,
                        "Please upload or capture valid Mysejahtera Malaysia."
                    )
                } else if (binding.tvLastDoseDate.text.toString().isEmpty()) {
                    Common.showToast(this@VaccinationCertificateDetailsActivity, "Please enter Covid-19 risk status.")
                } else {
                    StaticData.request.lastDoseDate = binding.tvLastDoseDate.text.toString();
                    finish()
                    startActivity(Intent(applicationContext, ThankYouActivity::class.java))
                }
            }
        })
    }

    private fun dateOfBirthClicked() {
        val c: Calendar = Calendar.getInstance()
        var month: Int = c.get(Calendar.MONTH)
        var day: Int = c.get(Calendar.DAY_OF_MONTH)
        var year: Int = c.get(Calendar.YEAR)

        if (!binding.tvLastDoseDate.text.toString().isEmpty()) {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.sssZ")
            try {
                var date = format.parse(binding.tvLastDoseDate.text.toString())
                c.timeInMillis = date.time
                month = c.get(Calendar.MONTH)
                day = c.get(Calendar.DAY_OF_MONTH)
                year = c.get(Calendar.YEAR)
            } catch (e: ParseException) {
                e.printStackTrace()
            }
        }


        val datePickerDialog = DatePickerDialog(
            this,
            { view, year, month, dayOfMonth ->

                c.set(year, month, dayOfMonth);

                val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.sssZ", Locale.US)
                val time: String = df.format(Date(c.timeInMillis))
                binding.tvLastDoseDate.setText(time)
            },
            year,
            month,
            day
        )
        datePickerDialog.show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        try {
            if (requestCode == 10002) {
                val bitmap: Bitmap = (data?.extras?.get("data") as Bitmap?)!!
                StaticData.base64_vaccinationCertificate = Utils().bitmapToBase64(bitmap)
                binding.ivCard.setImageBitmap(bitmap)
            }
            if (requestCode == 10001) {
                var path: Uri? = null
                path = data?.data
                val parcelFileDescriptor = contentResolver.openFileDescriptor(path!!, "r")
                val fileDescriptor: FileDescriptor = parcelFileDescriptor!!.fileDescriptor
                val bitmap = BitmapFactory.decodeFileDescriptor(fileDescriptor)
                parcelFileDescriptor.close()

                StaticData.base64_vaccinationCertificate = Utils().bitmapToBase64(bitmap)
                binding.ivCard.setImageBitmap(bitmap)
            }
        } catch (throwablae: Throwable) {

        }
    }


}