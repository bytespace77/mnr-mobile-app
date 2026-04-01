package com.safeg.activities

import android.app.ProgressDialog
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import com.androidnetworking.AndroidNetworking
import com.androidnetworking.common.Priority
import com.androidnetworking.error.ANError
import com.androidnetworking.interfaces.ParsedRequestListener
import com.awesomedialog.blennersilva.awesomedialoglibrary.AwesomeProgressDialog
import com.chivorn.smartmaterialspinner.SmartMaterialSpinner
import com.safeg.Constants
import com.safeg.R
import com.safeg.StaticData
import com.safeg.databinding.ActivityVaccinationInformationBinding
import com.safeg.utils.Common
import com.safeg.models.GetActiveVaccineResponseItem
import okhttp3.OkHttpClient
import java.io.InputStream
import java.security.KeyStore
import java.security.SecureRandom
import java.util.ArrayList
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

class VaccinationInformationActivity : AppCompatActivity() {

    lateinit var vaccineTypeModelList: ArrayList<GetActiveVaccineResponseItem>;
    lateinit var vaccineTypeStringList: ArrayList<String>
    var selectedVaccineType:Int = -1

    private lateinit var binding : ActivityVaccinationInformationBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityVaccinationInformationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        invokeGetActiveVaccineTypesApi()

        binding.tvPartialyVaccinatedNotVaccinated.setOnClickListener(object : View.OnClickListener{
            override fun onClick(p0: View?) {
                if(StaticData.moduleConfig.vpSaliva){
                    finish()
                    startActivity(Intent(this@VaccinationInformationActivity, SalivaTestRequiredActivity::class.java))
                } else {
                    finish()
                    startActivity(Intent(this@VaccinationInformationActivity, ThankYouActivity::class.java))
                }
            }

        })

        binding.tvFullyVaccinated.setOnClickListener(object : View.OnClickListener{
            override fun onClick(p0: View?) {

                if (selectedVaccineType == -1) {
                    Common.showToast(this@VaccinationInformationActivity, "Please select vaccination type.")
                } else {
                    if(StaticData.moduleConfig.vpVACOCR){
                        StaticData.request.vaccineType = selectedVaccineType.toString()
                        finish()
                        startActivity(Intent(this@VaccinationInformationActivity, VaccinationRecognitionActivity::class.java))
                    } else {
                        finish()
                        startActivity(Intent(this@VaccinationInformationActivity, VaccinationCertificateActivity::class.java))
                    }

                }
            }

        })

    }


    private fun invokeGetActiveVaccineTypesApi() {
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
                AndroidNetworking.get(Constants.getActiveVaccineList)
                    .setTag(Constants.getActiveVaccineList)
                    .setPriority(Priority.HIGH)
                    .build()
                    .getAsObjectList(
                        GetActiveVaccineResponseItem::class.java,
                        object : ParsedRequestListener<ArrayList<GetActiveVaccineResponseItem>> {
                            override fun onResponse(response: ArrayList<GetActiveVaccineResponseItem>) {
                                pDialog.hide()
                                Log.d("response", "getActiveCountry size : " + response.size);
                                vaccineTypeModelList = response

                                vaccineTypeStringList = ArrayList()
                                for (item: GetActiveVaccineResponseItem in response) {
                                    vaccineTypeStringList.add(item.name)
                                }

                                binding.spVaccineType.setItem(vaccineTypeStringList)

                                binding.spVaccineType.setOnItemSelectedListener(object :
                                    AdapterView.OnItemSelectedListener {
                                    override fun onItemSelected(
                                        adapterView: AdapterView<*>?,
                                        view: View,
                                        position: Int,
                                        id: Long
                                    ) {
                                        selectedVaccineType = vaccineTypeModelList.get(position).id
                                    }

                                    override fun onNothingSelected(adapterView: AdapterView<*>?) {}
                                })


                            }

                            override fun onError(anError: ANError) {
                                pDialog.hide()
                                Common.showToast(
                                    applicationContext,
                                    "Error Code : " + anError.errorCode + ", Details : " + anError.errorDetail
                                )
                            }
                        })
            })
        }).start()




    }
}