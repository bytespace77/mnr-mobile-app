package com.safeg.activities

import Reason
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
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
import com.safeg.utils.SslUtils
import org.json.JSONObject

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

    private lateinit var montserrat: Typeface

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityVisitUpdatedDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        montserrat = ResourcesCompat.getFont(this, R.font.montserrat_bold) ?: Typeface.DEFAULT

        binding.ivSearch.setOnClickListener {
            invokeGetVisitUpdateDetailsApi(binding.etSearch.text.toString())
        }

        binding.rlMainPage.setOnClickListener {
            finish()
            startActivity(Intent(applicationContext, WelcomeActivity::class.java))
        }

        binding.ivBack.setOnClickListener { finish() }

        binding.rlNext.setOnClickListener {
            when {
                selectedReason.isEmpty() ->
                    Common.showToast(this, "Please Select a Reason", Common.ToastType.WARNING)
                selectedVisitorType.isEmpty() ->
                    Common.showToast(this, "Please Select Visitor Type", Common.ToastType.WARNING)
                else -> {
                    StaticData.request.reason = selectedReason
                    StaticData.request.visitorTypeId = selectedVisitorType.toIntOrNull() ?: 0
                    finish()
                    val intent = Intent(applicationContext, ThankYouActivity::class.java)
                    intent.putExtra(ThankYouActivity.EXTRA_FLOW, ThankYouActivity.FLOW_WALKIN)
                    startActivity(intent)
                }
            }
        }

        invokeGetReasonsApi()
        invokeGetVisitorTypesApi()
    }

    private fun invokeGetVisitUpdateDetailsApi(search: String) {
        if (search.isBlank()) {
            Common.showToast(this, "Please enter a name or staff ID", Common.ToastType.WARNING)
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
                AndroidNetworking.post(Constants.getStaffPassByStaffNoOrName)
                    .setTag(Constants.getStaffPassByStaffNoOrName)
                    .setPriority(Priority.HIGH)
                    .addJSONObjectBody(JSONObject().put("username", search))
                    .build()
                    .getAsObjectList(
                        GetStaffPassByStaffNoOrNameResponseItem::class.java,
                        object : ParsedRequestListener<List<GetStaffPassByStaffNoOrNameResponseItem>> {
                            override fun onResponse(response: List<GetStaffPassByStaffNoOrNameResponseItem>) {
                                pDialog.hide()
                                if (response.isNotEmpty()) {
                                    val adapter = StaffAdapter(response.toMutableList()) { staff ->
                                        StaticData.request.nameOfPersonVisited = staff.name
                                        StaticData.request.contactNoOfPersonVisited = staff.mobileNo
                                        StaticData.request.staffNo = staff.username
                                        binding.etSearch.setText(StaticData.request.nameOfPersonVisited)
                                    }
                                    binding.rvStaffList.layoutManager = LinearLayoutManager(this@VisitUpdateDetailsActivity)
                                    binding.rvStaffList.adapter = adapter
                                    binding.llVisitDetails.visibility = View.VISIBLE
                                } else {
                                    Common.showToast(applicationContext, "No Records Found", Common.ToastType.ERROR)
                                }
                            }
                            override fun onError(anError: ANError) {
                                pDialog.hide()
                                Common.showToast(applicationContext, "No Records Found", Common.ToastType.ERROR)
                            }
                        })
            }
        }.start()
    }

    override fun onClick(view: View?) {}

    private fun invokeGetReasonsApi() {
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
                AndroidNetworking.post(Constants.getVisitReasons)
                    .addJSONObjectBody(JSONObject())
                    .setTag(Constants.getVisitReasons)
                    .setPriority(Priority.HIGH)
                    .build()
                    .getAsJSONObject(object : JSONObjectRequestListener {
                        override fun onResponse(response: JSONObject) {
                            pDialog.hide()
                            reasonsList = ArrayList()
                            val listArray = response.optJSONArray("list")
                            if (listArray != null) {
                                for (i in 0 until listArray.length()) {
                                    val innerArray = listArray.optJSONArray(i)
                                    if (innerArray != null) {
                                        for (j in 0 until innerArray.length()) {
                                            val obj = innerArray.optJSONObject(j)
                                            val name = obj?.optString("name") ?: ""
                                            val id = obj?.optString("id") ?: ""
                                            if (name.isNotEmpty()) {
                                                val r = Reason()
                                                r.id = id
                                                r.name = name
                                                reasonsList.add(r)
                                            }
                                        }
                                    }
                                }
                            }
                            binding.spReason.setItem(reasonsList.map { it.name })
                            binding.spReason.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
                                override fun onItemSelected(a: AdapterView<*>?, v: View, p: Int, i: Long) {
                                    selectedReason = reasonsList[p].id
                                }
                                override fun onNothingSelected(a: AdapterView<*>?) {}
                            })
                        }
                        override fun onError(anError: ANError) {
                            pDialog.hide()
                            Common.showToast(applicationContext, "Failed to Load Reasons", Common.ToastType.ERROR)
                        }
                    })
            }
        }.start()
    }

    private fun invokeGetVisitorTypesApi() {
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
                AndroidNetworking.get(Constants.visitorTypes)
                    .setTag(Constants.visitorTypes)
                    .setPriority(Priority.HIGH)
                    .build()
                    .getAsJSONObject(object : JSONObjectRequestListener {
                        override fun onResponse(response: JSONObject) {
                            pDialog.hide()
                            visitorTypesList = ArrayList()
                            val listArray = response.optJSONArray("data")
                            if (listArray != null) {
                                for (j in 0 until listArray.length()) {
                                    val obj = listArray.optJSONObject(j)
                                    val name = obj?.optString("visitor_type") ?: ""
                                    val id = obj?.optString("id") ?: ""
                                    if (name.isNotEmpty()) {
                                        val r = Reason()
                                        r.id = id
                                        r.name = name
                                        visitorTypesList.add(r)
                                    }
                                }
                            }
                            // ✅ spLocation is the visitor type spinner per XML
                            binding.spLocation.setItem(visitorTypesList.map { it.name })
                            binding.spLocation.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
                                override fun onItemSelected(a: AdapterView<*>?, v: View, p: Int, i: Long) {
                                    selectedVisitorType = visitorTypesList[p].id
                                }
                                override fun onNothingSelected(a: AdapterView<*>?) {}
                            })
                        }
                        override fun onError(anError: ANError) {
                            pDialog.hide()
                            Common.showToast(applicationContext, "Failed to Load Visitor Types", Common.ToastType.ERROR)
                        }
                    })
            }
        }.start()
    }
}