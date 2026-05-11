package com.safeg.activities

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import com.androidnetworking.AndroidNetworking
import com.androidnetworking.common.Priority
import com.androidnetworking.error.ANError
import com.androidnetworking.interfaces.ParsedRequestListener
import com.awesomedialog.blennersilva.awesomedialoglibrary.AwesomeProgressDialog
import com.safeg.Constants
import com.safeg.R
import com.safeg.StaticData
import com.safeg.databinding.ActivityVaccinationInformationBinding
import com.safeg.utils.Common
import com.safeg.utils.SslUtils
import com.safeg.models.GetActiveVaccineResponseItem

class VaccinationInformationActivity : AppCompatActivity() {

    lateinit var vaccineTypeModelList: ArrayList<GetActiveVaccineResponseItem>
    lateinit var vaccineTypeStringList: ArrayList<String>
    var selectedVaccineType: Int = -1

    private lateinit var binding: ActivityVaccinationInformationBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityVaccinationInformationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        invokeGetActiveVaccineTypesApi()

        binding.tvPartialyVaccinatedNotVaccinated.setOnClickListener {
            if (StaticData.moduleConfig.vpSaliva) {
                finish()
                startActivity(Intent(this@VaccinationInformationActivity, SalivaTestRequiredActivity::class.java))
            } else {
                finish()
                startActivity(Intent(this@VaccinationInformationActivity, ThankYouActivity::class.java))
            }
        }

        binding.tvFullyVaccinated.setOnClickListener {
            if (selectedVaccineType == -1) {
                Common.showToast(this@VaccinationInformationActivity, "Please select vaccination type.")
            } else {
                if (StaticData.moduleConfig.vpVACOCR) {
                    StaticData.request.vaccineType = selectedVaccineType.toString()
                    finish()
                    startActivity(Intent(this@VaccinationInformationActivity, VaccinationRecognitionActivity::class.java))
                } else {
                    finish()
                    startActivity(Intent(this@VaccinationInformationActivity, VaccinationCertificateActivity::class.java))
                }
            }
        }
    }

    private fun invokeGetActiveVaccineTypesApi() {
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
                AndroidNetworking.get(Constants.getActiveVaccineList)
                    .setTag(Constants.getActiveVaccineList)
                    .setPriority(Priority.HIGH)
                    .build()
                    .getAsObjectList(
                        GetActiveVaccineResponseItem::class.java,
                        object : ParsedRequestListener<ArrayList<GetActiveVaccineResponseItem>> {
                            override fun onResponse(response: ArrayList<GetActiveVaccineResponseItem>) {
                                pDialog.hide()
                                Log.d("response", "getActiveVaccine size : " + response.size)
                                vaccineTypeModelList = response
                                vaccineTypeStringList = ArrayList()
                                for (item in response) vaccineTypeStringList.add(item.name)
                                binding.spVaccineType.setItem(vaccineTypeStringList)
                                binding.spVaccineType.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
                                    override fun onItemSelected(adapterView: AdapterView<*>?, view: View, position: Int, id: Long) {
                                        selectedVaccineType = vaccineTypeModelList.get(position).id
                                    }
                                    override fun onNothingSelected(adapterView: AdapterView<*>?) {}
                                })
                            }

                            override fun onError(anError: ANError) {
                                pDialog.hide()
                                Common.showToast(applicationContext, "Error Code : ${anError.errorCode}, Details : ${anError.errorDetail}")
                            }
                        })
            }
        }.start()
    }
}