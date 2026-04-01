package com.safeg.activities

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RenderEffect
import android.graphics.Shader
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.androidnetworking.AndroidNetworking
import com.androidnetworking.common.Priority
import com.androidnetworking.error.ANError
import com.androidnetworking.interfaces.JSONArrayRequestListener
import com.androidnetworking.interfaces.JSONObjectRequestListener
import com.androidnetworking.interfaces.OkHttpResponseListener
import com.androidnetworking.interfaces.ParsedRequestListener
import com.androidnetworking.interfaces.StringRequestListener
import com.awesomedialog.blennersilva.awesomedialoglibrary.AwesomeProgressDialog
import com.google.gson.Gson
import com.intellego.morphosmart.driver.DeviceException
import com.intellego.morphosmart.driver.DeviceProbe
import com.intellego.morphosmart.driver.MorphoSmart
import com.intellego.morphosmart.ilv.ILVErrorCode
import com.intellego.morphosmart.ilv.ILVResultCode
import com.safeg.Constants
import com.safeg.R
import com.safeg.StaticData
import com.safeg.cardreader.*
import com.safeg.databinding.ActivityCardDetailsBinding
import com.safeg.models.GetActiveCountryResponseItem
import com.safeg.models.GetActiveVehicleTypeResponseItem
import com.safeg.models.GetCityByStateResponseItem
import com.safeg.models.GetStateByCountryResponseItem
import com.safeg.utils.Common
import com.safeg.utils.Utils
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.io.FileDescriptor
import java.io.InputStream
import java.security.KeyStore
import java.security.SecureRandom
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import kotlin.math.log


class CardDetailsActivity : AppCompatActivity(), OnReadCardTaskCompleteListener,
    OnVerifyFpTaskCompleteListener, OnReadCardVerifyFpTaskCompleteListener {

    lateinit var mUtils: Utils
    lateinit var reasonsStringList: ArrayList<String>
    lateinit var countryModelList: ArrayList<GetActiveCountryResponseItem>
    lateinit var vinTypeModelList: ArrayList<GetActiveVehicleTypeResponseItem>
    lateinit var countryStringList: ArrayList<String>
    lateinit var vehicleTypeStringList: ArrayList<String>
    var selectedCountry: Int = -1
    var selectedvehicleType: Int = -1
    private lateinit var runnable: Runnable
    private val handler = Handler()

    lateinit var stateModelList: ArrayList<GetStateByCountryResponseItem>
    lateinit var stateStringList: ArrayList<String>
    var selectedState: Int = -1

    lateinit var cityModelList: ArrayList<GetCityByStateResponseItem>
    lateinit var cityStringList: ArrayList<String>
    var selectedCity: Int = -1

    lateinit var plksExpiryStringList: ArrayList<String>
    var selectedPlksExpiry: String = ""

    lateinit var residentStringList: ArrayList<String>
    var selectedResident: String = ""

    lateinit var genderStringList: ArrayList<String>
    var selectedGender: String = ""
    var selectedVehicleCategory: String = ""
    var selectedVehicleType: String = ""

    private lateinit var binding: ActivityCardDetailsBinding

    private var morphoSmart: MorphoSmart? = null

    private var readCardAsyncManager: ReadCardAsyncTaskManager? = null
    private var verifyFpAsyncManager: VerifyFpAsyncTaskManager? = null
    private var readCardVerifyFpAsyncTaskManager: ReadCardVerifyFpAsyncTaskManager? = null
    private var deviceProbe: DeviceProbe? = null
    var selectedCityName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        startTimer();
        binding = ActivityCardDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mUtils = Utils()

        binding.llDateOfBirth.setOnClickListener {
            dateOfBirthClicked()
        }

        binding.tvDateOfBirth.setOnClickListener {
            dateOfBirthClicked()
        }

        binding.ivDateOfBirth.setOnClickListener {
            dateOfBirthClicked()
        }

        binding.tvDateOfBirthTitle.setOnClickListener {
            dateOfBirthClicked()
        }

        binding.llPlksExpiry.setOnClickListener {
            plksExpiryClicked()
        }

        binding.tvPlksExpiry.setOnClickListener {
            plksExpiryClicked()
        }

        binding.ivPlksExpiry.setOnClickListener {
            plksExpiryClicked()
        }

        binding.tvPlksExpiryTitle.setOnClickListener {
            plksExpiryClicked()
        }

        if (!StaticData.base64_mykad.isNullOrEmpty()) {
            binding.ivCard.setImageBitmap(mUtils.base64ToBitmap(StaticData.base64_mykad));
        }

        if (!StaticData.request.fullName.isNullOrEmpty()) {
            binding.etCardHolderName.setText(StaticData.request.fullName)
        }

        if (!StaticData.request.ic.isNullOrEmpty()) {
            binding.etCardNumber.setText(StaticData.request.ic)
            try {
                val lastDigit = Common.parseInt(binding.etCardNumber.text.toString().substring(binding.etCardNumber.text.toString().length - 1, binding.etCardNumber.text.toString().length))
                if(lastDigit % 2 == 1){
                    binding.spGender.setSelection(0)
                } else {
                    binding.spGender.setSelection(1)
                }
            } catch (err : Throwable){

            }
        }

        if(!StaticData.request.add1.isNullOrEmpty()){
            binding.etAddress.setText(binding.etAddress.text.toString()+ StaticData.request.add1);
        }

        if(!StaticData.request.add2.isNullOrEmpty()){
            binding.etAddress.setText(binding.etAddress.text.toString()+"\n"+ StaticData.request.add2);
        }

        if(!StaticData.request.add3.isNullOrEmpty()){
            binding.etAddress.setText(binding.etAddress.text.toString()+"\n"+ StaticData.request.add3);
        }

        if(!StaticData.request.postalCode.isNullOrEmpty()){
            binding.etPostalCode.setText(StaticData.request.postalCode);
        }

        if(!StaticData.request.birthday.isNullOrEmpty()){
            binding.tvDateOfBirth.setText(StaticData.request.birthday)
        }

        if (StaticData.isForeigner) {
            binding.tvTitle.setText("Passport Details")
            binding.tvIcNumber.setText("Passport Number")
            binding.llResident.visibility = View.GONE
            binding.viewResident.visibility = View.GONE
            binding.llCountry.visibility = View.VISIBLE
//            binding.viewCountry.visibility = View.VISIBLE
            binding.llDateOfBirth.visibility = View.GONE
            binding.llPlksExpiry.visibility = View.GONE
            binding.llState.visibility = View.GONE
            binding.llCity.visibility = View.GONE
            binding.llPostalCode.visibility = View.GONE
            binding.cvCard.visibility = View.GONE
            binding.tvScanKad.visibility = View.GONE
            binding.dottedBox.visibility = View.GONE

            binding.viewVinType.visibility = View.GONE
            binding.viewVinCat.visibility = View.GONE
            binding.viewGender.visibility = View.GONE
            binding.llVinType.visibility = View.GONE
            binding.viewAddress.visibility = View.GONE
            binding.llAddress.visibility = View.GONE
            binding.llVinCat.visibility = View.GONE
            binding.llGender.visibility = View.GONE
        } else {
            binding.tvTitle.setText("MyKad Details")
            binding.tvIcNumber.setText("IC Number")
            binding.llPlksExpiry.visibility = View.GONE
//            binding.viewPlksExpiry.visibility = View.GONE
            binding.llResident.visibility = View.GONE
            binding.viewResident.visibility = View.GONE
            binding.llCountry.visibility = View.GONE
            binding.dottedBox.visibility = View.VISIBLE
//            binding.viewCountry.visibility = View.GONE
            binding.viewVinType.visibility = View.GONE
            binding.cvCard.visibility = View.VISIBLE

            binding.viewVinCat.visibility = View.GONE
            binding.viewGender.visibility = View.GONE
            binding.llVinType.visibility = View.GONE
            binding.llVinCat.visibility = View.GONE
            binding.llGender.visibility = View.GONE
            binding.llIc.visibility = View.VISIBLE
//            binding.viewIcNumber.visibility = View.VISIBLE
            binding.viewAddress.visibility = View.VISIBLE
            binding.llAddress.visibility = View.VISIBLE
        }

        invokeGetVehicleTypeApi()

        invokeGetActiveCountryApi()

        initResidentDropdown()

        initGenderDropdown()

        initVehicleCategories()

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
//                if (StaticData.base64_mykad.isNullOrEmpty()) {
//                    if (StaticData.isForeigner) {
//                        Common.showToast(
//                            this@CardDetailsActivity,
//                            "Please upload or capture valid passport."
//                        )
//                    } else {
//                        Common.showToast(
//                            this@CardDetailsActivity,
//                            "Please upload or capture valid MyKad."
//                        )
//                    }
//                } else
                    if (binding.etCardHolderName.text.toString().isEmpty()) {
                    Common.showToast(this@CardDetailsActivity, "Please enter card holder name.")
                } else if (!StaticData.isForeigner && binding.etCardNumber.text.toString().isEmpty()) {
                    Common.showToast(
                        this@CardDetailsActivity,
                        "Please enter " + binding.tvIcNumber.text.toString() + "."
                    )
                } else if (!StaticData.isForeigner && binding.etAddress.text.toString().isEmpty()) {
                    Common.showToast(this@CardDetailsActivity, "Please enter address.")
                } else if (binding.etContactNumber.text.toString().isEmpty()) {
                    Common.showToast(this@CardDetailsActivity, "Please enter contact number.")
                }
                    else if (binding.etCompanyName.text.toString().isEmpty()) {
                    Common.showToast(this@CardDetailsActivity, "Please enter company name.")
                }
                    else if (StaticData.isForeigner && binding.etCardNumber.text.toString().isEmpty()) {
                        Common.showToast(this@CardDetailsActivity, "Please enter passport number.")
                    }
//                    else if (binding.etEmail.text.toString().isEmpty()) {
//                    Common.showToast(this@CardDetailsActivity, "Please enter email.")
//                }
                    else if (!StaticData.isForeigner && binding.tvDateOfBirth.text.toString().isEmpty()) {
                    Common.showToast(this@CardDetailsActivity, "Please enter date of birth.")
                }
//                    else if (StaticData.isForeigner && binding.tvPlksExpiry.text.toString().isEmpty()) {
//                    Common.showToast(this@CardDetailsActivity, "Please enter PLKs Expiry.")
//                }
                    else if (selectedCountry == -1) {
                    Common.showToast(this@CardDetailsActivity, "Please select country.")
                } else if (!StaticData.isForeigner && selectedState == -1) {
                    Common.showToast(this@CardDetailsActivity, "Please select state.")
                } else if (!StaticData.isForeigner && selectedCity == -1) {
                    Common.showToast(this@CardDetailsActivity, "Please select city.")
                } else if (!StaticData.isForeigner && binding.etPostalCode.text.toString().isEmpty()) {
                    Common.showToast(this@CardDetailsActivity, "Please enter postal code.")
                }
                    else if (StaticData.isForeigner && binding.etRegNo.text.toString().isEmpty()) {
                        Common.showToast(this@CardDetailsActivity, "Please enter vehicle registration No.")
                    }
                //else if (selectedPlksExpiry.isEmpty()) {
                 //   Common.showToast(this@CardDetailsActivity, "Please select Plks expiry.")
                //}
                //else if (selectedResident.isEmpty()) {
                //    Common.showToast(this@CardDetailsActivity, "Please select Resident.")
                //}
//                else if (selectedGender.isEmpty()) {
//                    Common.showToast(this@CardDetailsActivity, "Please select Gender.")
//                } else if (selectedVehicleCategory.isEmpty()) {
//                        Common.showToast(this@CardDetailsActivity, "Please select Vehicle Category.")
//                    }
//                    else if (selectedvehicleType == -1) {
//                        Common.showToast(this@CardDetailsActivity, "Please select Vehicle Type.")
//                    }
                    else {
                    val addressLines = mUtils.getLines(binding.etAddress.text.toString())
                    if (addressLines.size > 0) {
                        if (addressLines.size == 3) {
                            StaticData.request.add1 = addressLines.get(0)
                            StaticData.request.add2 = addressLines.get(1)
                            StaticData.request.add3 = addressLines.get(2)
                        } else if (addressLines.size == 2) {
                            StaticData.request.add1 = addressLines.get(0)
                            StaticData.request.add2 = addressLines.get(1)
                            StaticData.request.add3 = ""
                        } else if (addressLines.size == 1) {
                            StaticData.request.add1 = addressLines.get(0)
                            StaticData.request.add2 = ""
                            StaticData.request.add3 = ""
                        }
                    } else {
                        StaticData.request.add1 = ""
                        StaticData.request.add2 = ""
                        StaticData.request.add3 = ""
                    }
                    StaticData.request.birthday = binding.tvDateOfBirth.text.toString()
                    StaticData.request.city = selectedCity.toString()
                    StaticData.request.clientVisitor = "true"
                    StaticData.request.contactNo = binding.etContactNumber.text.toString()
                    StaticData.request.country = selectedCountry
                    StaticData.request.vinType = selectedvehicleType
                    StaticData.request.vinCat = selectedVehicleCategory
                    StaticData.request.regNum = binding.etRegNo.text.toString()
                    StaticData.request.cpnName = binding.etCompanyName.text.toString()
                    StaticData.request.cpnRegID = "123456789012"
                    StaticData.request.email = binding.etEmail.text.toString()
                    StaticData.request.fullName = binding.etCardHolderName.text.toString()
                    // "fullyVaccineFlag": "YES", //fully vaccinated or not, based on Mysejahtera Malaysia
                    if (StaticData.isForeigner) {
//                        StaticData.request.ic = ""
                        StaticData.request.passport = binding.etCardNumber.text.toString()
                        StaticData.request.passportIssueCountry = selectedCountry;
                        StaticData.request.resident = "Foreigner"
                    } else {
                        StaticData.request.ic = binding.etCardNumber.text.toString()
                        StaticData.request.passport = ""
                        StaticData.request.passportIssueCountry = 1; // for malaysian
                        StaticData.request.resident = "Local"
                    }
                    // "lastDoseDate": "2021-12-13", //vaccination last dose date, based on Mysejahtera Malaysia
                    // "licenseInfo": [],//leave it empty
                    StaticData.request.maxHoursForVisitorPassPerIcOnThisMonth = 999;

                    StaticData.request.plksExpiry = binding.tvPlksExpiry.text.toString()
                    StaticData.request.postalCode = binding.etPostalCode.text.toString()

                    // "riskStatus": " Low Risk No Symptom", //covid19 risk status,based on Mysejahtera Malaysia
                    StaticData.request.sex = selectedGender

                    StaticData.request.state = selectedState.toString()

                    // "vPortPassValidityPeriod": [ //Site visit period (RANGE/SINGLE), if SINGLE, data must be the same day, only allow change time, refer to Get Operating HOurs List
                    // {
                    //     "dateVisitFrom": {},
                    //     "dateVisitFromString": "2022/03/23 17:26:56",
                    //     "dateVisitTo": {},
                    //     "dateVisitToString": "2022/03/25 17:27:01",
                    //     "num": 1, //array number
                    //     "type": "RANGE" //(RANGE/SINGLE)
                    // }
                    // ],

                    // "vaccinationStatus": " Fully Vaccinated", //vaccination status,based on Mysejahtera Malaysia
                    // "vaccineType": "2", //refer to Get Active Vaccine List API
                    // "contactNoOfPersonVisited": "0179574256", //visited person's contact number
                    // "nameOfPersonVisited": "Marco", //visited person's name
                    // "reason": "discussion", //purpose of visit


                    if(StaticData.moduleConfig.vpFacial){
//                        finish()
                        startActivity(Intent(applicationContext, FaceDetectionActivity::class.java))
                    } else {
//                        finish()
                        startActivity(Intent(applicationContext, FaceDetectionActivity::class.java))//.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK))
                    }


                }
            }
        })

        // readmykad
        try {
            deviceProbe = DeviceProbe(this.baseContext)
        } catch (e: DeviceException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
        }

        readCardAsyncManager = ReadCardAsyncTaskManager(this, this)
        readCardAsyncManager!!
            .handleRetainedTask(lastNonConfigurationInstance)

        verifyFpAsyncManager = VerifyFpAsyncTaskManager(this, this)
        verifyFpAsyncManager!!
            .handleRetainedTask(lastNonConfigurationInstance)

        readCardVerifyFpAsyncTaskManager = ReadCardVerifyFpAsyncTaskManager(this, this)
        readCardVerifyFpAsyncTaskManager!!
            .handleRetainedTask(lastNonConfigurationInstance)

        binding.tvScanKad.setOnClickListener {
            onReadMyKad()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            binding.cvCard.setRenderEffect(
                RenderEffect.createBlurEffect(20f, 20f, Shader.TileMode.CLAMP)
            )
        }

        // listener for ic text
         var debounceRunnable: Runnable? = null
         val handler = Handler()

        binding.etCardNumber.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                debounceRunnable?.let { handler.removeCallbacks(it) }

                debounceRunnable = Runnable {
                    val icNumber = s?.toString()?.trim()
                    if (!icNumber.isNullOrEmpty() && icNumber.length >= 10) {

                        invokeCheckIcExist(icNumber)
                    }
                }

                handler.postDelayed(debounceRunnable!!, 500) // Wait 500ms after typing stops
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.etCardNumber.setText(StaticData.request.ic);
    }

    // Start the timer (1 minute delay)
    private fun startTimer() {
        runnable = Runnable {
            // Move back to the previous screen after 1 minute of inactivity
            finish()  // or finish() to close the current activity
        }

        // Delay the action for 60,000 milliseconds (1 minute)
        handler.postDelayed(runnable, 240000)

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        try {
            if (requestCode == 10002) {
                val bitmap: Bitmap = (data?.extras?.get("data") as Bitmap?)!!
                StaticData.base64_mykad = Utils().bitmapToBase64(bitmap)
                binding.ivCard.setImageBitmap(bitmap)
            }
            if (requestCode == 10001) {
                var path: Uri? = null
                path = data?.data
                val parcelFileDescriptor = contentResolver.openFileDescriptor(path!!, "r")
                val fileDescriptor: FileDescriptor = parcelFileDescriptor!!.fileDescriptor
                val bitmap = BitmapFactory.decodeFileDescriptor(fileDescriptor)
                parcelFileDescriptor.close()

                StaticData.base64_mykad = Utils().bitmapToBase64(bitmap)
                binding.ivCard.setImageBitmap(bitmap)
            }
        } catch (throwablae: Throwable) {

        }
    }

    private fun dateOfBirthClicked() {
        val c: Calendar = Calendar.getInstance()
        var month: Int = c.get(Calendar.MONTH)
        var day: Int = c.get(Calendar.DAY_OF_MONTH)
        var year: Int = c.get(Calendar.YEAR)

        if (!binding.tvDateOfBirth.text.toString().isEmpty()) {
            val format = SimpleDateFormat(Constants.date_format)
            try {
                var date = format.parse(binding.tvDateOfBirth.text.toString())
                c.timeInMillis = date.time
                month = c.get(Calendar.MONTH)
                day = c.get(Calendar.DAY_OF_MONTH)
                year = c.get(Calendar.YEAR)
            } catch (e: ParseException) {
                e.printStackTrace()
            }
        }


        val datePickerDialog = DatePickerDialog(
            this@CardDetailsActivity,
            { view, year, month, dayOfMonth ->

                c.set(year, month, dayOfMonth);

                val df = SimpleDateFormat(Constants.date_format, Locale.US)
                val time: String = df.format(Date(c.timeInMillis))
                binding.tvDateOfBirth.setText(time)
            },
            year,
            month,
            day
        )
        datePickerDialog.show()
    }

    private fun plksExpiryClicked() {
        val c: Calendar = Calendar.getInstance()
        var month: Int = c.get(Calendar.MONTH)
        var day: Int = c.get(Calendar.DAY_OF_MONTH)
        var year: Int = c.get(Calendar.YEAR)

        if (!binding.tvPlksExpiry.text.toString().isEmpty()) {
            val format = SimpleDateFormat(Constants.date_format)
            try {
                var date = format.parse(binding.tvPlksExpiry.text.toString())
                c.timeInMillis = date.time
                month = c.get(Calendar.MONTH)
                day = c.get(Calendar.DAY_OF_MONTH)
                year = c.get(Calendar.YEAR)
            } catch (e: ParseException) {
                e.printStackTrace()
            }
        }


        val datePickerDialog = DatePickerDialog(
            this@CardDetailsActivity,
            { view, year, month, dayOfMonth ->

                c.set(year, month, dayOfMonth);

                val df = SimpleDateFormat(Constants.date_format, Locale.US)
                val time: String = df.format(Date(c.timeInMillis))
                binding.tvPlksExpiry.setText(time)
            },
            year,
            month,
            day
        )
        datePickerDialog.show()
    }

    private fun initResidentDropdown() {
        residentStringList = ArrayList()
        residentStringList.add("Local")
        residentStringList.add("Foreigner")

        binding.spResident.setItem(residentStringList)

        binding.spResident.setOnItemSelectedListener(object :
            AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                adapterView: AdapterView<*>?,
                view: View,
                position: Int,
                id: Long
            ) {

                selectedResident = residentStringList.get(position)
            }

            override fun onNothingSelected(adapterView: AdapterView<*>?) {}
        })
    }

    private fun initGenderDropdown() {
        genderStringList = ArrayList()
        genderStringList.add("Male")
        genderStringList.add("Female")

        binding.spGender.setItem(genderStringList)

        binding.spGender.setOnItemSelectedListener(object :
            AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                adapterView: AdapterView<*>?,
                view: View,
                position: Int,
                id: Long
            ) {

                selectedGender = genderStringList.get(position)
            }

            override fun onNothingSelected(adapterView: AdapterView<*>?) {}
        })
    }

    private fun initVehicleCategories() {
        genderStringList = ArrayList()
        genderStringList.add("public")
        genderStringList.add("cargo")
        genderStringList.add("non-cargo")

        binding.spVinCat.setItem(genderStringList)

        binding.spVinCat.setOnItemSelectedListener(object :
            AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                adapterView: AdapterView<*>?,
                view: View,
                position: Int,
                id: Long
            ) {

                selectedVehicleCategory = genderStringList.get(position)
            }

            override fun onNothingSelected(adapterView: AdapterView<*>?) {}
        })
    }

    private fun invokeGetVehicleTypeApi() {
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
                AndroidNetworking.get(Constants.getActiveVehicleType)
                    .setTag(Constants.getActiveVehicleType)
                    .setPriority(Priority.HIGH)
                    .build()
                    .getAsObjectList(
                        GetActiveVehicleTypeResponseItem::class.java,
                        object : ParsedRequestListener<ArrayList<GetActiveVehicleTypeResponseItem>> {
                            override fun onResponse(response: ArrayList<GetActiveVehicleTypeResponseItem>) {
                                pDialog.hide()
                                Log.d("response", "getActiveVehicleType size : " + response.size)
                                vinTypeModelList = response

                                vehicleTypeStringList = ArrayList()
                                for (item: GetActiveVehicleTypeResponseItem in response) {
                                    vehicleTypeStringList.add(item.name)
                                }

                                binding.spVinType.setItem(vehicleTypeStringList)

                                try {
                                    if(!StaticData.request.state.isNullOrEmpty() || !StaticData.isForeigner){
                                        binding.spVinType.setSelection(0)
                                    }

                                } catch (thorwable : Throwable){

                                }


                                binding.spVinType.setOnItemSelectedListener(object :
                                    AdapterView.OnItemSelectedListener {
                                    override fun onItemSelected(
                                        adapterView: AdapterView<*>?,
                                        view: View,
                                        position: Int,
                                        id: Long
                                    ) {

                                        selectedvehicleType = vinTypeModelList.get(position).id

                                    }

                                    override fun onNothingSelected(adapterView: AdapterView<*>?) {}
                                })

                                Log.d("response", "getActiveVehicleType size : " + response.size)
                            }

                            override fun onError(anError: ANError) {
                                Log.d("response", "getActiveVehicleType size : " + "response.size")
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
    private fun invokeGetActiveCountryApi() {
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
                AndroidNetworking.get(Constants.getActiveCountry)
                    .setTag(Constants.getActiveCountry)
                    .setPriority(Priority.HIGH)
                    .build()
                    .getAsObjectList(
                        GetActiveCountryResponseItem::class.java,
                        object : ParsedRequestListener<ArrayList<GetActiveCountryResponseItem>> {
                            override fun onResponse(response: ArrayList<GetActiveCountryResponseItem>) {
                                pDialog.hide()
                                Log.d("response", "getActiveCountry size : " + response.size)
                                countryModelList = response

                                countryStringList = ArrayList()
                                for (item: GetActiveCountryResponseItem in response) {
                                    countryStringList.add(item.name)
                                }

                                binding.spCountry.setItem(countryStringList)

                                try {
                                    if(!StaticData.request.state.isNullOrEmpty() || !StaticData.isForeigner){
                                        binding.spCountry.setSelection(0)
                                    }

                                } catch (thorwable : Throwable){

                                }


                                binding.spCountry.setOnItemSelectedListener(object :
                                    AdapterView.OnItemSelectedListener {
                                    override fun onItemSelected(
                                        adapterView: AdapterView<*>?,
                                        view: View,
                                        position: Int,
                                        id: Long
                                    ) {

                                        selectedCountry = countryModelList.get(position).id
                                        //invokeGetStateByCountryApi(countryModelList.get(position).id)

                                        invokeGetStateByCountryApi(1)
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
    private fun invokeCheckIcExist(icNumber: String) {
        val pDialog = AwesomeProgressDialog(this)
        pDialog.setCancelable(false)
        pDialog.setTitle("Please wait")
        pDialog.setMessage("")
        pDialog.setColoredCircle(R.color.pherosi)
        pDialog.show()

        Thread(Runnable {
            try {
                val trustStore = KeyStore.getInstance("PKCS12")
                val In: InputStream = getResources().openRawResource(R.raw.server)
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
                    sslCtx.socketFactory
                )
                val builder = OkHttpClient.Builder()
                builder.sslSocketFactory(sslCtx.socketFactory)
                val okHttpClient = builder.build()
                AndroidNetworking.initialize(applicationContext, okHttpClient)
            } catch (thorowable: Throwable) {
                runOnUiThread {
                    pDialog.hide()
                    Common.showToast(applicationContext, thorowable.message)
                }
                return@Runnable
            }

            runOnUiThread(Runnable {
                AndroidNetworking.get(Constants.getdetails)
                    .addQueryParameter("icno", icNumber)
                    .setTag(Constants.getdetails)
                    .setPriority(Priority.HIGH)
                    .build()
                    .getAsJSONObject(object : JSONObjectRequestListener {
                        override fun onResponse(response: JSONObject) {
                            pDialog.hide()
                            Log.d("IC_CHECK_RESPONSE", response.toString())

                            try {
                                // Parse the response
StaticData.base64_face = response.optString("photo", "")
                                val fullName = response.optString("fullName", "")
                                val contactNo = response.optString("contactNo", "")
                                val email = response.optString("email", "")

                                // Address fields
                                val add1 = response.optString("add1", "")
                                val add2 = response.optString("add2", "")
                                val add3 = response.optString("add3", "")
                                val postcode = response.optString("postcode", "")

                                // Location objects
                                val cityId = response.optJSONObject("city")?.optInt("id", -1) ?: -1
                                val stateId =
                                    response.optJSONObject("state")?.optInt("id", -1) ?: -1
                                val countryId =
                                    response.optJSONObject("country")?.optInt("id", -1) ?: -1


                                // Company info
                                val companyName = response.optString("companyName", "")
                                val regNum = response.optString("regNum", "")

                                // Passport info (nullable)
                                val passportNo = response.optString("passportNo", null)
                                val passportIssueCountry =
                                    response.optString("passportIssueCountry", null)

                                // Now you can use these values as needed
                                // For example, populate UI fields:
                                if (!fullName.isNullOrEmpty()) {
                                    binding.etCardHolderName.setText(fullName)
                                }
                                if (!contactNo.isNullOrEmpty()) {
                                    binding.etContactNumber.setText(contactNo)
                                }
                                binding.etEmail.setText(email)
                                if (!add1.isNullOrEmpty()) {
                                    binding.etAddress.setText(add1)
                                }
                                binding.etCompanyName.setText(companyName)
                                if (!regNum.isNullOrEmpty()) {
                                binding.etRegNo.setText(regNum)
                            }
                                if (!postcode.isNullOrEmpty()) {
                                    binding.etPostalCode.setText(postcode)
                                }

                                if (!fullName.isNullOrEmpty()) {
                                    selectedState = stateId
                                    selectedCity = cityId
                                    invokeGetCityByStateApi(stateId)
//                                    val selectedStatePos =
//                                        stateModelList.indexOfFirst { it.id == stateId }
//                                    binding.spState.post {
//                                        binding.spState.setSelection(selectedStatePos)
//                                    }
//                                (binding.spState.adapter as? ArrayAdapter<*>)?.notifyDataSetChanged()
//                                    val selectedCityPos =
//                                        cityModelList.indexOfFirst { it.id == cityId }
//                                    binding.spCity.post {
//                                        binding.spCity.setSelection(selectedCityPos)
//                                    }
                                }
                                // You might want to set city, state, country spinners here
                                // based on the IDs you received

                                Common.showToast(applicationContext, "IC details loaded successfully")

                            } catch (e: Exception) {
                                Log.e("PARSE_ERROR", "Error parsing response: ${e.message}")
                                Common.showToast(applicationContext, "Error parsing response")
                            }
                        }

                        override fun onError(anError: ANError) {
                            pDialog.hide()
                            Log.e("IC_CHECK_ERROR", "Error: ${anError.message}")

                            try {
                                // Try to get error message from response if available
                                val errorResponse = anError.errorBody?.let { JSONObject(it) }
                                val errorMessage = errorResponse?.optString("message", anError.message)
                                    ?: "Failed to check IC"

                                Common.showToast(applicationContext, errorMessage)

                                // mocking response to test login
//                                val response = JSONObject().apply {
//                                    put("icNo", "vsbsb")
//                                    put("passportNo", JSONObject.NULL)
//                                    put("passportIssueCountry", JSONObject.NULL)
//                                    put("add1", "SBBSBS")
//                                    put("add2", "")
//                                    put("add3", "")
//                                    put("postcode", "555")
//                                    put("city", JSONObject().apply { put("id", 95) })
//                                    put("state", JSONObject().apply { put("id", 8) })
//                                    put("country", JSONObject().apply { put("id", 1) })
//                                    put("fullName", "ZGSB")
//                                    put("contactNo", "84948")
//                                    put("companyRegId", "superadmin")
//                                    put("companyName", "ADMIN")
//                                    put("regNum", "ZVBS")
//                                    put("email", "tzn@gmail.com")
//                                }
//
//                                val fullName = response.getString("fullName")
//                                val contactNo = response.getString("contactNo")
//                                val email = response.getString("email")
//
//                                // Address fields
//                                val add1 = response.getString("add1")
//                                val add2 = response.optString("add2", "")
//                                val add3 = response.optString("add3", "")
//                                val postcode = response.getString("postcode")
//
//                                // Location objects
//                                val cityId = response.getJSONObject("city").getInt("id")
//                                val stateId = response.getJSONObject("state").getInt("id")
//                                val countryId = response.getJSONObject("country").getInt("id")
//
//                                // Company info
//                                val companyName = response.getString("companyName")
//                                val regNum = response.getString("regNum")
//
//                                // Passport info (nullable)
//                                val passportNo = response.optString("passportNo", null)
//                                val passportIssueCountry = response.optString("passportIssueCountry", null)
//
//                                // Now you can use these values as needed
//                                // For example, populate UI fields:
//
//                                binding.etCardHolderName.setText(fullName)
//                                binding.etContactNumber.setText(contactNo)
//                                binding.etEmail.setText(email)
//                                binding.etAddress.setText(add1)
//                                binding.etCompanyName.setText(companyName)
//                                binding.etRegNo.setText(regNum)
//                                binding.etPostalCode.setText(postcode)
//
//                                selectedState = stateId
//                                invokeGetCityByStateApi(stateId)
//                                selectedCity = cityId
//                                val selectedStatePos = stateModelList.indexOfFirst { it.id == stateId }
//                                binding.spState.post {
//                                    binding.spState.setSelection(selectedStatePos)
//                                }
////                                (binding.spState.adapter as? ArrayAdapter<*>)?.notifyDataSetChanged()
//                                val selectedCityPos = cityModelList.indexOfFirst { it.id == cityId }
//                                binding.spCity.post {
//                                    binding.spCity.setSelection(selectedCityPos)
//                                }

                            } catch (e: Exception) {
                                Common.showToast(
                                    applicationContext,
                                    "Error Code3: ${anError.errorCode}, Details: ${anError.errorDetail}"
                                )
                            }
                        }
                    })
            })
        }).start()
    }

    private fun invokeGetStateByCountryApi(countryId: Int) {

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
                AndroidNetworking.get(Constants.getStateByCountry+countryId)
                    .setTag(Constants.getStateByCountry + countryId)
                    .setPriority(Priority.HIGH)
                    .build()
                    .getAsObjectList(
                        GetStateByCountryResponseItem::class.java,
                        object : ParsedRequestListener<ArrayList<GetStateByCountryResponseItem>> {
                            override fun onResponse(response: ArrayList<GetStateByCountryResponseItem>) {
                                pDialog.hide()
                                Log.d("response", "getActiveCountry size : " + response.size)

                                stateModelList = response

                                stateStringList = ArrayList()
                                for (item: GetStateByCountryResponseItem in response) {
                                    stateStringList.add(item.name)
                                }

                                binding.spState.setItem(stateStringList)
                                try {
                                    if(!StaticData.request.state.isNullOrEmpty()){
                                        binding.spState.setSelection(stateStringList.indexOf(StaticData.request.state));
                                    }
                                } catch (throwable : Throwable){

                                }




                                binding.spState.setOnItemSelectedListener(object :
                                    AdapterView.OnItemSelectedListener {
                                    override fun onItemSelected(
                                        adapterView: AdapterView<*>?,
                                        view: View,
                                        position: Int,
                                        id: Long
                                    ) {
//                                        Common.showToast(
//                                            applicationContext,
//                                            "invoke " + stateModelList.get(position).id
//                                        )
                                        selectedState = stateModelList.get(position).id
                                        invokeGetCityByStateApi(stateModelList.get(position).id)
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

    private fun invokeGetCityByStateApi(stateId: Int) {
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
                AndroidNetworking.get(Constants.getCityByState + stateId)
                    .setTag(Constants.getCityByState + stateId)
                    .setPriority(Priority.HIGH)
                    .build()
                    .getAsObjectList(
                        GetCityByStateResponseItem::class.java,
                        object : ParsedRequestListener<ArrayList<GetCityByStateResponseItem>> {
                            override fun onResponse(response: ArrayList<GetCityByStateResponseItem>) {
                                pDialog.hide()
                                Log.d("response", "getActiveCountry size : " + response.size)

                                cityModelList = response

                                cityStringList = ArrayList()
                                for (item: GetCityByStateResponseItem in response) {
                                    cityStringList.add(item.name)
                                }

                                binding.spCity.setItem(cityStringList)

                                try {
                                    if (selectedCity >= 0) {
//                                        Common.showToast(
//                                            applicationContext,
//                                            "Error Check3 : " + StaticData.request.city
//                                        )
                                        val selectedCityPos = cityModelList.indexOfFirst { it.id == selectedCity }
                                        binding.spCity.post {
                                            binding.spCity.setSelection(selectedCityPos)
                                        }
                                    }
//                                    Common.showToast(
//                                        applicationContext,
//                                        "Error Check : " + selectedCityName
//                                    )
                                    if (!selectedCityName.isNullOrEmpty()) {
//                                        Common.showToast(
//                                            applicationContext,
//                                            "Error Check2 : " + selectedCityName
//                                        )
                                            binding.spCity.setSelection(
                                                cityStringList.indexOf(
                                                    selectedCityName
                                                )
                                            )
                                        selectedCity = cityModelList.get(cityStringList.indexOf(selectedCityName)).id
                                        }
                                    if(!StaticData.request.city.isNullOrEmpty()){
//                                        Common.showToast(
//                                            applicationContext,
//                                            "Error Check1 : " + StaticData.request.city
//                                        )
                                        binding.spCity.setSelection(cityStringList.indexOf(StaticData.request.city));
                                    }
                                } catch (throwable : Throwable){

                                }

                                binding.spCity.setOnItemSelectedListener(object :
                                    AdapterView.OnItemSelectedListener {
                                    override fun onItemSelected(
                                        adapterView: AdapterView<*>?,
                                        view: View,
                                        position: Int,
                                        id: Long
                                    ) {

                                        selectedCity = cityModelList.get(position).id
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

    fun onReadMyKad() {
        if (morphoSmart == null) {
            val usbManager = this.baseContext
                .getSystemService(USB_SERVICE) as UsbManager
            if (deviceProbe == null) {
                MsgBox("No smart card reader attached to the system")
                return
            }
            if (deviceProbe!!.usbDevice == null) {
                MsgBox("No smart card reader attached to the system")
                return
            }
            morphoSmart = MorphoSmart(
                usbManager,
                deviceProbe!!.usbDevice, this
            )
        }

        try {
            morphoSmart!!.open()
            readCardAsyncManager!!.setupTask(
                ReadCardTask(
                    resources,
                    morphoSmart, true
                )
            )
        } catch (e: DeviceException) {
            e.printStackTrace()
            MsgBox("Error opening smartcard reader")
        }
    }

    override fun onTaskComplete(task: ReadCardTask) {
        try {
            val readCardResult = task.get()
            if (readCardResult.isSuccessful) {
                val cardHolderInfo = readCardResult.personalInfo
                binding.etCardHolderName.setText(cardHolderInfo.name);
                binding.etCardNumber.setText(cardHolderInfo.nric);
                binding.etAddress.setText(cardHolderInfo.address1 + ", " + cardHolderInfo.address2 + ", " + cardHolderInfo
                    .address3)
                val inputFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val outputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

                val date = inputFormat.parse(cardHolderInfo.dateOfBirth)
                val outputDateString = outputFormat.format(date)
                binding.tvDateOfBirth.setText((outputDateString))
                selectedCityName = cardHolderInfo.city
                binding.spState.post {
                    binding.spState.setSelection(stateStringList.indexOf(cardHolderInfo.state))
                }

//                Common.showToast(
//                    applicationContext,
//                    "Error Checking : " + selectedCityName
//                )
//                binding.spCity.setSelection(cityStringList.indexOf(cardHolderInfo.city))
                binding.etPostalCode.setText(cardHolderInfo.postcode)
                binding.spGender.setSelection(if (cardHolderInfo.gender == "M") 0 else 1)
                binding.ivCard.setImageBitmap(BitmapFactory.decodeByteArray(cardHolderInfo.photo, 0, cardHolderInfo.photo.size))
                //Gson gson = new Gson();
                val gson = Gson()
                //.setExclusionStrategies(new GsonExcludeStrategy())
                //.create();
//                MsgBox(gson.toJson(cardHolderInfo))
                return
            } else {
                MsgBox("Failed to read MyKad")
            }
        } catch (e: Exception) {
            MsgBox(e.message)
        }
    }

    override fun onTaskComplete(task: VerifyFPTask) {
        try {
            val verifyFPResult = task.get()
            val morphosmartResult = verifyFPResult
                .morphoSmartResult
            if (morphosmartResult.errorCode == ILVErrorCode.ILV_OK) {
                if (morphosmartResult.resultCode == ILVResultCode.ILVSTS_HIT) {
                    MsgBox("Fingerprint matches fingerprint in MyKad")
                } else {
                    MsgBox("Fingerprint does not match fingerprint in MyKad")
                }
            } else if (morphosmartResult.errorCode == ILVErrorCode.ILVERR_INVALID_MINUTIAE) {
                MsgBox("Invalid fingerprint miniature")
            } else if (morphosmartResult.errorCode == ILVErrorCode.ILVERR_TIMEOUT) {
                MsgBox("Fingerprint verification operation timed out")
            } else if (morphosmartResult.errorCode == ILVErrorCode.ILVERR_CMDE_ABORTED) {
                MsgBox("Fingerprint verification operation aborted")
            } else if (morphosmartResult.errorCode == ILVErrorCode.ILVERR_MYKAD) {
                MsgBox(verifyFPResult.errorMessage)
            } else if (morphosmartResult.errorCode == ILVErrorCode.ILVERR_LICENSE_REG_FAILED) {
                MsgBox("Fingerprint SDK activation failed. Make sure tablet is connected to internet.")
            } else if (morphosmartResult.errorCode == ILVErrorCode.ILVERR_INVALID_LICENSE) {
                MsgBox("Fingerprint SDK activation failed due to invalid or missing license")
            } else {
                MsgBox("Fingerprint verification operation encountered an error")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            if (morphoSmart != null) {
                morphoSmart!!.close()
            }
        }
    }

    override fun onTaskComplete(task: ReadCardVerifyFpTask) {
        try {
            val readCardResult = task.get()
            if (readCardResult.readCardResult.isSuccessful) {
                val cardHolderInfo = readCardResult.readCardResult.personalInfo
                val morphosmartResult = readCardResult.verifyFPResult.morphoSmartResult
                var verifyFpResult = ""
                verifyFpResult = if (morphosmartResult.errorCode == ILVErrorCode.ILV_OK) {
                    if (morphosmartResult.resultCode == ILVResultCode.ILVSTS_HIT) {
                        "Fingerprint matches fingerprint in MyKad"
                        //MsgBox("Fingerprint matches fingerprint in MyKad");
                    } else {
                        "Fingerprint does not match fingerprint in MyKad"
                        //MsgBox("Fingerprint does not match fingerprint in MyKad");
                    }
                } else if (morphosmartResult.errorCode == ILVErrorCode.ILVERR_INVALID_MINUTIAE) {
                    "Invalid fingerprint miniature"
                    //MsgBox("Invalid fingerprint miniature");
                } else if (morphosmartResult.errorCode == ILVErrorCode.ILVERR_TIMEOUT) {
                    "Fingerprint verification operation timed out"
                    //MsgBox("Fingerprint verification operation timed out");
                } else if (morphosmartResult.errorCode == ILVErrorCode.ILVERR_CMDE_ABORTED) {
                    "Fingerprint verification operation aborted"
                    //MsgBox("Fingerprint verification operation aborted");
                } else if (morphosmartResult.errorCode == ILVErrorCode.ILVERR_MYKAD) {
                    readCardResult.verifyFPResult.errorMessage
                    //MsgBox(readCardResult.getVerifyFPResult().getErrorMessage());
                } else if (morphosmartResult.errorCode == ILVErrorCode.ILVERR_LICENSE_REG_FAILED) {
                    "Fingerprint SDK activation failed. Make sure tablet is connected to internet"
                    //MsgBox("Fingerprint SDK activation failed. Make sure tablet is connected to internet.");
                } else if (morphosmartResult.errorCode == ILVErrorCode.ILVERR_INVALID_LICENSE) {
                    "Fingerprint SDK activation failed due to invalid or missing license"
                    //MsgBox("Fingerprint SDK activation failed due to invalid or missing license");
                } else {
                    "Fingerprint verification operation encountered an error"
                    //MsgBox("Fingerprint verification operation encountered an error");
                }
                val gson = Gson()

                //Display card data
                val result = """
                Fingerprint Verification Result: $verifyFpResult
                ${gson.toJson(cardHolderInfo)}
                """.trimIndent()
                //MsgBox(gson.toJson(cardHolderInfo));
                MsgBox(result)
                return
            } else {
                MsgBox("Failed to read MyKad")
            }
        } catch (e: Exception) {
            MsgBox(e.message)
        }
    }

    fun MsgBox(response: String?) {
        val builder = AlertDialog.Builder(this)
        builder.setCancelable(false) // This blocks the 'BACK' button
        builder.setMessage(response)
        builder.setNegativeButton(
            "OK"
        ) { dialog, id -> dialog.cancel() }
        val alertDialog = builder.create()
        alertDialog.show()
    }



}