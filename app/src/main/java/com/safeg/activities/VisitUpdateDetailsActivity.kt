package com.safeg.activities

import Reason
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.androidnetworking.AndroidNetworking
import com.androidnetworking.common.Priority
import com.androidnetworking.error.ANError
import com.androidnetworking.interfaces.JSONObjectRequestListener
import com.androidnetworking.interfaces.ParsedRequestListener
import com.awesomedialog.blennersilva.awesomedialoglibrary.AwesomeProgressDialog
import com.safeg.Constants
import com.safeg.R
import com.safeg.StaticData
import com.safeg.adapters.StaffAdapter
import com.safeg.databinding.ActivityVisitUpdatedDetailsBinding
import com.safeg.models.*
import com.safeg.utils.Common
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.io.InputStream
import java.security.KeyStore
import java.security.SecureRandom
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory


class VisitUpdateDetailsActivity : AppCompatActivity(), View.OnClickListener {


    private lateinit var binding: ActivityVisitUpdatedDetailsBinding

    lateinit var locationModelList: ArrayList<GetLocationResponseItem>
    lateinit var companyList: ArrayList<Company>
    lateinit var locationStringList: ArrayList<String>
    lateinit var companyStringList: ArrayList<String>
    lateinit var reasonsList: ArrayList<Reason>
    lateinit var visitorTypesList: ArrayList<Reason>
    lateinit var adapter: StaffAdapter
    var selectedLocation: Int = -1
    var selectedLocationn: String = ""
    var selectedReason: String = ""
    var selectedVisitorType: String = ""



    lateinit var subLocationModelList: ArrayList<GetSubLocationResponseItem>
    lateinit var subLocationStringList: ArrayList<String>
    var selectedSubLocation: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityVisitUpdatedDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivSearch.setOnClickListener {
//            if (binding.etSearch.text.toString().isEmpty()) {
//                Common.showToast(this, "Search field is empty.")
//            } else {
                invokeGetVisitUpdateDetailsApi(binding.etSearch.text.toString())
//            }
        }

        binding.rlMainPage.setOnClickListener {
            finish()
            startActivity(Intent(applicationContext, WelcomeActivity::class.java))
        }
        binding.ivBack.setOnClickListener {
            finish()
        }
        binding.rlNext.setOnClickListener {
            if (selectedReason.isEmpty()) {
                Common.showToast(this, "Please enter reason.")
            } else if (selectedVisitorType == "") {
                Common.showToast(this, "Please select visitor type.")
            }
//            else if (selectedSubLocation == -1) {
//                Common.showToast(this, "Please select Sub location.")
//            }
            else {
//                StaticData.request.staffNo = binding.tvStaffId.text.toString()
//                StaticData.request.contactNoOfPersonVisited = binding.tvMobileNumber.text.toString()
//                StaticData.request.nameOfPersonVisited = binding.tvName.text.toString()
                StaticData.request.reason = selectedReason
                if (!selectedVisitorType.isNullOrEmpty()) {
                    StaticData.request.visitorTypeId = Integer.parseInt(selectedVisitorType)
                }
//                StaticData.request.selectedLocation.add(SelectedLocationItem(selectedLocation))
//                StaticData.request.selectedSubLocation = "" + selectedSubLocation
//                if(StaticData.moduleConfig.vpVaccine){
//                    finish()
//                    startActivity(Intent(applicationContext, VaccinationInformationActivity::class.java))
//                } else if(StaticData.moduleConfig.vpVACOCR){
//                    finish()
//                    startActivity(Intent(applicationContext, VaccinationRecognitionActivity::class.java))
//                } else {
                    finish()
                    startActivity(Intent(applicationContext, ThankYouActivity::class.java))
//                }


            }
        }
//        invokeGetLocationApi()
        invokeGetReasonsApi()
        invokeGetVisitorTypesApi()
//        invokeGetCompaniesApi()
    }


    private fun invokeGetVisitUpdateDetailsApi(search: String) {
        val pDialog = AwesomeProgressDialog(this)
        pDialog.setCancelable(false)
        pDialog.setCancelable(false)
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
                AndroidNetworking.post(Constants.getStaffPassByStaffNoOrName)
                    .setTag(Constants.getStaffPassByStaffNoOrName)
                    .setPriority(Priority.HIGH)
//                    .addQueryParameter("keyword", search)
                    .addJSONObjectBody(JSONObject().put("username", search))
                    .build()
                    .getAsObjectList(
                        GetStaffPassByStaffNoOrNameResponseItem::class.java,
                        object : ParsedRequestListener<List<GetStaffPassByStaffNoOrNameResponseItem>> {
                            override fun onResponse(response: List<GetStaffPassByStaffNoOrNameResponseItem>) {
                                pDialog.hide()
                                if (response.size > 0) {
//                                val staffList = mutableListOf(
//                                    Staff("John Doe", "+60 123 456 789", "ST12345"),
//                                    Staff("Jane Smith", "+60 987 654 321", "ST98765"),
//                                    Staff("Adam Lee", "+60 112 233 445", "ST67890")
//                                )
//
                                val adapter = StaffAdapter(response.toMutableList()) { staff ->
                                    // delete clicked
                                    StaticData.request.nameOfPersonVisited = staff.name
                                    StaticData.request.contactNoOfPersonVisited = staff.mobileNo
                                    StaticData.request.staffNo = staff.username
                                    binding.etSearch.setText(StaticData.request.nameOfPersonVisited)
//                                    adapter.notifyDataSetChanged()
                                }
//
                                binding.rvStaffList.layoutManager = LinearLayoutManager(this@VisitUpdateDetailsActivity)
                                binding.rvStaffList.adapter = adapter
//                                binding.tvName.setText(response.name.trim())
//                                binding.tvStaffId.setText(response.username.trim())
//                                binding.tvMobileNumber.setText(if (response.mobileNo != null) response.mobileNo.trim() else "")
                                    binding.llVisitDetails.visibility = View.VISIBLE
                                } else {
//                                    Common.showToast(
//                                        applicationContext,
//                                        ""
//                                    )
                                }
                            }

                            override fun onError(anError: ANError) {
                                print(anError.errorDetail)
                                pDialog.hide()
                                Common.showToast(
                                    applicationContext,
                                    "No Recordss found." + anError.errorDetail
                                )

                                val staffList = mutableListOf(
                                    GetStaffPassByStaffNoOrNameResponseItem(),
                                    GetStaffPassByStaffNoOrNameResponseItem(),
                                    GetStaffPassByStaffNoOrNameResponseItem()
                                )

                                val adapter = StaffAdapter(staffList) { staff ->
                                    // delete clicked
                                    staffList.remove(staff)
                                    adapter.notifyDataSetChanged()
                                }

                                binding.rvStaffList.layoutManager = LinearLayoutManager(applicationContext)
                                binding.rvStaffList.adapter = adapter
                            }
                        })
            })

        }).start()


    }


    override fun onClick(view: View?) {
        when (view?.id) {

        }
    }

    private fun invokeGetLocationApi() {
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
                AndroidNetworking.get(Constants.getLocationAccessList)
                    .setTag(Constants.getLocationAccessList)
                    .setPriority(Priority.HIGH)
                    .build()
                    .getAsObjectList(
                        GetLocationResponseItem::class.java,
                        object : ParsedRequestListener<ArrayList<GetLocationResponseItem>> {
                            override fun onResponse(response: ArrayList<GetLocationResponseItem>) {
                                pDialog.hide()
                                Log.d("response", "getLocationAccessList size : " + response.size)
                                locationModelList = response

                                locationStringList = ArrayList()
                                for (item: GetLocationResponseItem in response) {
                                    if (item.name != null)
                                        locationStringList.add(item.name)
                                }

                                binding.spLocation.setItem(locationStringList)

                                binding.spLocation.setOnItemSelectedListener(object :
                                    AdapterView.OnItemSelectedListener {
                                    override fun onItemSelected(
                                        adapterView: AdapterView<*>?,
                                        view: View,
                                        position: Int,
                                        id: Long
                                    ) {

                                        selectedLocation = locationModelList.get(position).id



                                        invokeGetSubLocationsByLocationApi(locationModelList.get(position).id)
                                    }

                                    override fun onNothingSelected(adapterView: AdapterView<*>?) {}
                                })


                            }

                            override fun onError(anError: ANError) {
                                pDialog.hide()
                                Common.showToast(
                                    applicationContext,
                                    "Error Code2 : " + anError.errorCode + ", Details : " + anError.errorDetail
                                )
                            }
                        })
            })
        }).start()
    }

    private fun invokeGetSubLocationsByLocationApi(locationAccessId: Int) {
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
                AndroidNetworking.post(Constants.getActiveSubLocationAssetList)
                    .addJSONObjectBody(JSONObject().put("locationAccessId", locationAccessId))
                    .setTag(Constants.getActiveSubLocationAssetList)
                    .setPriority(Priority.HIGH)
                    .build()
                    .getAsObjectList(
                        GetSubLocationResponseItem::class.java,
                        object : ParsedRequestListener<ArrayList<GetSubLocationResponseItem>> {
                            override fun onResponse(response: ArrayList<GetSubLocationResponseItem>) {
                                pDialog.hide()
                                Log.d("response", "getActiveCountry size : " + response.size)

                                subLocationModelList = response

                                subLocationStringList = ArrayList()
                                for (item: GetSubLocationResponseItem in response) {
                                    subLocationStringList.add(item.name)
                                }

                                binding.spSubLocation.setItem(subLocationStringList)

                                binding.spSubLocation.setOnItemSelectedListener(object :
                                    AdapterView.OnItemSelectedListener {
                                    override fun onItemSelected(
                                        adapterView: AdapterView<*>?,
                                        view: View,
                                        position: Int,
                                        id: Long
                                    ) {
                                        selectedSubLocation = subLocationModelList.get(position).id
                                    }

                                    override fun onNothingSelected(adapterView: AdapterView<*>?) {}
                                })


                            }

                            override fun onError(anError: ANError) {
                                pDialog.hide()
                                Common.showToast(
                                    applicationContext,
                                    "Error Code3 : " + anError.errorCode + ", Details : " + anError.errorDetail
                                )
                            }
                        })
            })
        }).start()
    }

    private fun invokeGetReasonsApi() {
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
                AndroidNetworking.post(Constants.getVisitReasons)
                    .addJSONObjectBody(JSONObject())
                    .setTag(Constants.getVisitReasons)
                    .setPriority(Priority.HIGH)
                    .build()
                    .getAsJSONObject(object : JSONObjectRequestListener {
                        override fun onResponse(response: JSONObject) {
                            pDialog.hide()
                            Log.d("response", "getVisitReasons size : " + response.toString())
//                                locationModelList = response

                            reasonsList = ArrayList()
                            val listArray = response.optJSONArray("list")
                            val locationStringList = ArrayList<String>()

                            if (listArray != null && listArray.length() > 0) {
                                for (i in 0 until listArray.length()) {
                                    val innerArray = listArray.optJSONArray(i)
                                    if (innerArray != null && innerArray.length() > 0) {
                                        for (j in 0 until innerArray.length()) {
                                            val itemObject = innerArray.optJSONObject(j)
                                            Log.d("DBG_", "onResponse: " + itemObject)
                                            val name = itemObject.optString("name")
                                            val id = itemObject.optString("id")
                                            val a = Reason()
                                            a.id = id
                                            a.name = name
                                            if (!name.isNullOrEmpty()) {
                                                reasonsList.add(a)

                                            }
                                        }
                                    }
                                }
                            }
                            Log.d("DBG_", "onResponse: " + reasonsList.get(0))
                            binding.spReason.setItem(reasonsList.map { it.name })

                            binding.spReason.setOnItemSelectedListener(object :
                                AdapterView.OnItemSelectedListener {
                                override fun onItemSelected(
                                    adapterView: AdapterView<*>?,
                                    view: View,
                                    position: Int,
                                    id: Long
                                ) {

                                    selectedReason = reasonsList.get(position).id

                                    Log.d("TAG", "onItemSelected: " + reasonsList.get(position))
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

    private fun invokeGetVisitorTypesApi() {
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
                AndroidNetworking.get(Constants.visitorTypes)
                    .setTag(Constants.visitorTypes)
                    .setPriority(Priority.HIGH)
                    .build()
                    .getAsJSONObject(object : JSONObjectRequestListener {
                        override fun onResponse(response: JSONObject) {
                            pDialog.hide()
                            Log.d("response", "getVisitTypes size : " + response.toString())
//                                locationModelList = response

                            visitorTypesList = ArrayList()
                            val listArray = response.optJSONArray("data")
                            val locationStringList = ArrayList<String>()

                            if (listArray != null && listArray.length() > 0) {
                                        for (j in 0 until listArray.length()) {
                                            val itemObject = listArray.optJSONObject(j)
                                            Log.d("DBG_", "onResponse: " + itemObject)
                                            val name = itemObject.optString("visitor_type")
                                            val id = itemObject.optString("id")
                                            val a = Reason()
                                            a.id = id
                                            a.name = name
                                            if (!name.isNullOrEmpty()) {
                                                visitorTypesList.add(a)

                                            }
                                }
                            }
                            Log.d("DBG_", "onResponse: " + visitorTypesList.get(0))
                            binding.spLocation.setItem(visitorTypesList.map { it.name })

                            binding.spLocation.setOnItemSelectedListener(object :
                                AdapterView.OnItemSelectedListener {
                                override fun onItemSelected(
                                    adapterView: AdapterView<*>?,
                                    view: View,
                                    position: Int,
                                    id: Long
                                ) {

                                    selectedVisitorType = visitorTypesList.get(position).id

                                    Log.d("TAG", "onItemSelected: " + visitorTypesList.get(position))
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

    private fun invokeGetCompaniesApi() {
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
                AndroidNetworking.get(Constants.getStaffPassByStaffNoOrName)
                    .setTag(Constants.getStaffPassByStaffNoOrName)
                    .setPriority(Priority.HIGH)
                    .build()
                    .getAsObjectList(
                        Company::class.java,
                        object : ParsedRequestListener<ArrayList<Company>> {
                            override fun onResponse(response: ArrayList<Company>) {
                                pDialog.hide()
                                Log.d("response", "getStaffPassByStaffNoOrName size : " + response.size)
                                companyList= response

                                companyStringList = ArrayList()
                                for (item: Company in response) {
                                    if (item.name != null)
                                        companyStringList.add(item.name)
                                }

                                binding.spCompany.setItem(companyStringList)

                                binding.spCompany.setOnItemClickListener(object :
                                    AdapterView.OnItemClickListener {
                                    override fun onItemClick(
                                        adapterView: AdapterView<*>?,
                                        view: View,
                                        position: Int,
                                        id: Long
                                    ) {

//                                        selectedLocationn = companyList.get(position).name
                                        StaticData.request.contactNoOfPersonVisited = response.get(position).contactNoOfPersonVisited
                                        StaticData.request.nameOfPersonVisited = response.get(position).name
                                        StaticData.request.cpnID = response.get(position).id
                                        binding.llVisitDetails.visibility = View.VISIBLE

                                    }

//                                    override fun o(adapterView: AdapterView<*>?) {}
                                })


                            }

                            override fun onError(anError: ANError) {
                                pDialog.hide()
                                Common.showToast(
                                    applicationContext,
                                    "Error Code1 : " + anError.errorCode + ", Details : " + anError.errorDetail
                                )
                            }
                        })
            })
        }).start()
    }
}
