package com.safeg.activities

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
import com.safeg.databinding.ActivityVaccinationDetailsBinding
import com.safeg.models.*
import com.safeg.utils.Common
import com.safeg.utils.Utils
import java.io.FileDescriptor
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*


class VaccinationDetailsActivity : AppCompatActivity() {

    lateinit var mUtils: Utils

    private lateinit var binding: ActivityVaccinationDetailsBinding

    lateinit var fullVaccinatedStringList: ArrayList<String>
    var selectedFullyVaccinated: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityVaccinationDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mUtils = Utils()

        initPlksExpiryDropdown()

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
                if (StaticData.base64_mysejahtera.isNullOrEmpty()) {
                    Common.showToast(
                        this@VaccinationDetailsActivity,
                        "Please upload or capture valid Mysejahtera Malaysia."
                    )
                } else if (binding.etCovid19RiskStatus.text.toString().isEmpty()) {
                    Common.showToast(this@VaccinationDetailsActivity, "Please enter Covid-19 risk status.")
                } else if (selectedFullyVaccinated.isEmpty()) {
                    Common.showToast(this@VaccinationDetailsActivity, "Please select yes or no from fully vaccinated dropdown.")
                } else {
                    StaticData.request.vaccinationStatus = binding.etCovid19RiskStatus.text.toString()
                    StaticData.request.fullyVaccineFlag = selectedFullyVaccinated
                    finish()
                    startActivity(Intent(applicationContext, VaccinationCertificateActivity::class.java))
                }
            }
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        try {
            if (requestCode == 10002) {
                val bitmap: Bitmap = (data?.extras?.get("data") as Bitmap?)!!
                StaticData.base64_mysejahtera = Utils().bitmapToBase64(bitmap)
                binding.ivCard.setImageBitmap(bitmap)
            }
            if (requestCode == 10001) {
                var path: Uri? = null
                path = data?.data
                val parcelFileDescriptor = contentResolver.openFileDescriptor(path!!, "r")
                val fileDescriptor: FileDescriptor = parcelFileDescriptor!!.fileDescriptor
                val bitmap = BitmapFactory.decodeFileDescriptor(fileDescriptor)
                parcelFileDescriptor.close()

                StaticData.base64_mysejahtera = Utils().bitmapToBase64(bitmap)
                binding.ivCard.setImageBitmap(bitmap)
            }
        } catch (throwablae: Throwable) {

        }
    }

    private fun initPlksExpiryDropdown() {
        fullVaccinatedStringList = ArrayList()
        fullVaccinatedStringList.add("Yes")
        fullVaccinatedStringList.add("No")

        binding.spFullyVaccinated.setItem(fullVaccinatedStringList)

        binding.spFullyVaccinated.setOnItemSelectedListener(object :
            AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                adapterView: AdapterView<*>?,
                view: View,
                position: Int,
                id: Long
            ) {
                selectedFullyVaccinated = fullVaccinatedStringList.get(position)
            }

            override fun onNothingSelected(adapterView: AdapterView<*>?) {}
        })
    }
}