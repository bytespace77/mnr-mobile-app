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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import com.androidnetworking.AndroidNetworking
import com.androidnetworking.common.Priority
import com.androidnetworking.error.ANError
import com.androidnetworking.interfaces.JSONObjectRequestListener
import com.androidnetworking.interfaces.ParsedRequestListener
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
import com.safeg.utils.DataCache
import com.safeg.utils.SslUtils
import com.safeg.utils.Utils
import org.json.JSONObject
import java.io.FileDescriptor
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

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

    private var pendingStateId: Int = -1
    private var pendingCityId: Int = -1
    private var isAutoSelectingState = false

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

    private fun String?.safeValue(): String = if (this == null || this == "null") "" else this

    private fun detectAndSetForeignerStatus() {
        val ic = StaticData.request.ic.trim()
        val passport = StaticData.request.passport.trim()
        when {
            passport.isNotBlank() && ic.isBlank() -> StaticData.isForeigner = true
            ic.isNotBlank() && ic.length == 12 && ic.all { it.isDigit() } -> StaticData.isForeigner = false
            ic.isNotBlank() && (ic.length != 12 || !ic.all { it.isDigit() }) -> {
                StaticData.isForeigner = true
                StaticData.request.passport = ic
                StaticData.request.ic = ""
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startTimer()
        binding = ActivityCardDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        mUtils = Utils()

        detectAndSetForeignerStatus()

        val montserratBold = ResourcesCompat.getFont(this, R.font.montserrat_bold)
        listOf(binding.spCountry, binding.spState, binding.spCity, binding.spResident, binding.spGender, binding.spVinCat, binding.spVinType).forEach { spinner ->
            try { spinner.typeface = montserratBold } catch (_: Throwable) {}
        }

        binding.llDateOfBirth.setOnClickListener { dateOfBirthClicked() }
        binding.tvDateOfBirth.setOnClickListener { dateOfBirthClicked() }
        binding.ivDateOfBirth.setOnClickListener { dateOfBirthClicked() }
        binding.tvDateOfBirthTitle.setOnClickListener { dateOfBirthClicked() }
        binding.llPlksExpiry.setOnClickListener { plksExpiryClicked() }
        binding.tvPlksExpiry.setOnClickListener { plksExpiryClicked() }
        binding.ivPlksExpiry.setOnClickListener { plksExpiryClicked() }
        binding.tvPlksExpiryTitle.setOnClickListener { plksExpiryClicked() }

        if (!StaticData.base64_mykad.isNullOrEmpty()) binding.ivCard.setImageBitmap(mUtils.base64ToBitmap(StaticData.base64_mykad))
        if (!StaticData.request.fullName.isNullOrEmpty()) binding.etCardHolderName.setText(StaticData.request.fullName)
        if (!StaticData.request.add1.isNullOrEmpty()) binding.etAddress.setText(binding.etAddress.text.toString() + StaticData.request.add1)
        if (!StaticData.request.add2.isNullOrEmpty()) binding.etAddress.setText(binding.etAddress.text.toString() + "\n" + StaticData.request.add2)
        if (!StaticData.request.add3.isNullOrEmpty()) binding.etAddress.setText(binding.etAddress.text.toString() + "\n" + StaticData.request.add3)
        if (!StaticData.request.postalCode.isNullOrEmpty()) binding.etPostalCode.setText(StaticData.request.postalCode)
        if (!StaticData.request.birthday.isNullOrEmpty()) binding.tvDateOfBirth.setText(StaticData.request.birthday)

        if (StaticData.isForeigner) {
            binding.tvTitle.setText("Passport Details")
            binding.tvIcNumber.setText("Passport Number")
            binding.llResident.visibility = View.GONE
            binding.viewResident.visibility = View.GONE
            binding.llCountry.visibility = View.VISIBLE
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
            binding.llResident.visibility = View.GONE
            binding.viewResident.visibility = View.GONE
            binding.llCountry.visibility = View.GONE
            binding.dottedBox.visibility = View.VISIBLE
            binding.viewVinType.visibility = View.GONE
            binding.cvCard.visibility = View.VISIBLE
            binding.viewVinCat.visibility = View.GONE
            binding.viewGender.visibility = View.GONE
            binding.llVinType.visibility = View.GONE
            binding.llVinCat.visibility = View.GONE
            binding.llGender.visibility = View.GONE
            binding.llIc.visibility = View.VISIBLE
            binding.viewAddress.visibility = View.VISIBLE
            binding.llAddress.visibility = View.VISIBLE
        }

        invokeGetVehicleTypeApi()
        invokeGetActiveCountryApi()
        initResidentDropdown()
        initGenderDropdown()
        initVehicleCategories()

        binding.ivBack.setOnClickListener { finish() }

        binding.tvCaptrure.setOnClickListener {
            startActivityForResult(Intent(MediaStore.ACTION_IMAGE_CAPTURE), 10002)
        }

        binding.tvUploadPhoto.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            intent.type = "image/*"
            startActivityForResult(Intent.createChooser(intent, "Select Picture"), 10001)
        }

        binding.rlNext.setOnClickListener {
            when {
                binding.etCardHolderName.text.toString().isEmpty() ->
                    Common.showToast(this, "Please Enter Card Holder Name", Common.ToastType.WARNING)
                !StaticData.isForeigner && binding.etCardNumber.text.toString().isEmpty() ->
                    Common.showToast(this, "Please Enter ${binding.tvIcNumber.text}", Common.ToastType.WARNING)
                !StaticData.isForeigner && binding.etAddress.text.toString().isEmpty() ->
                    Common.showToast(this, "Please Enter Address", Common.ToastType.WARNING)
                binding.etContactNumber.text.toString().isEmpty() ->
                    Common.showToast(this, "Please Enter Contact Number", Common.ToastType.WARNING)
                binding.etCompanyName.text.toString().isEmpty() ->
                    Common.showToast(this, "Please Enter Company Name", Common.ToastType.WARNING)
                StaticData.isForeigner && binding.etCardNumber.text.toString().isEmpty() ->
                    Common.showToast(this, "Please Enter Passport Number", Common.ToastType.WARNING)
                !StaticData.isForeigner && binding.tvDateOfBirth.text.toString().isEmpty() ->
                    Common.showToast(this, "Please Enter Date of Birth", Common.ToastType.WARNING)
                selectedCountry == -1 ->
                    Common.showToast(this, "Please Select Country", Common.ToastType.WARNING)
                !StaticData.isForeigner && selectedState == -1 ->
                    Common.showToast(this, "Please Select State", Common.ToastType.WARNING)
                !StaticData.isForeigner && selectedCity == -1 ->
                    Common.showToast(this, "Please Select City", Common.ToastType.WARNING)
                !StaticData.isForeigner && binding.etPostalCode.text.toString().isEmpty() ->
                    Common.showToast(this, "Please Enter Postal Code", Common.ToastType.WARNING)
                StaticData.isForeigner && binding.etRegNo.text.toString().isEmpty() ->
                    Common.showToast(this, "Please Enter Vehicle Registration No", Common.ToastType.WARNING)
                else -> {
                    val addressLines = mUtils.getLines(binding.etAddress.text.toString())
                    when {
                        addressLines.size == 3 -> { StaticData.request.add1 = addressLines[0]; StaticData.request.add2 = addressLines[1]; StaticData.request.add3 = addressLines[2] }
                        addressLines.size == 2 -> { StaticData.request.add1 = addressLines[0]; StaticData.request.add2 = addressLines[1]; StaticData.request.add3 = "" }
                        addressLines.size == 1 -> { StaticData.request.add1 = addressLines[0]; StaticData.request.add2 = ""; StaticData.request.add3 = "" }
                        else -> { StaticData.request.add1 = ""; StaticData.request.add2 = ""; StaticData.request.add3 = "" }
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
                    if (StaticData.isForeigner) {
                        StaticData.request.passport = binding.etCardNumber.text.toString()
                        StaticData.request.passportIssueCountry = selectedCountry
                        StaticData.request.resident = "Foreigner"
                    } else {
                        StaticData.request.ic = binding.etCardNumber.text.toString()
                        StaticData.request.passport = ""
                        StaticData.request.passportIssueCountry = 1
                        StaticData.request.resident = "Local"
                    }
                    StaticData.request.maxHoursForVisitorPassPerIcOnThisMonth = 999
                    StaticData.request.plksExpiry = binding.tvPlksExpiry.text.toString()
                    StaticData.request.postalCode = binding.etPostalCode.text.toString()
                    StaticData.request.sex = selectedGender
                    StaticData.request.state = selectedState.toString()
                    startActivity(Intent(applicationContext, FaceDetectionActivity::class.java))
                }
            }
        }

        try { deviceProbe = DeviceProbe(this.baseContext) } catch (e: DeviceException) { e.printStackTrace() }

        readCardAsyncManager = ReadCardAsyncTaskManager(this, this)
        readCardAsyncManager!!.handleRetainedTask(lastNonConfigurationInstance)
        verifyFpAsyncManager = VerifyFpAsyncTaskManager(this, this)
        verifyFpAsyncManager!!.handleRetainedTask(lastNonConfigurationInstance)
        readCardVerifyFpAsyncTaskManager = ReadCardVerifyFpAsyncTaskManager(this, this)
        readCardVerifyFpAsyncTaskManager!!.handleRetainedTask(lastNonConfigurationInstance)

        binding.tvScanKad.setOnClickListener { onReadMyKad() }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            binding.cvCard.setRenderEffect(RenderEffect.createBlurEffect(20f, 20f, Shader.TileMode.CLAMP))
        }

        var debounceRunnable: Runnable? = null
        val debounceHandler = Handler()

        binding.etCardNumber.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                debounceRunnable?.let { debounceHandler.removeCallbacks(it) }
                debounceRunnable = Runnable {
                    val number = s?.toString()?.trim()
                    if (!number.isNullOrEmpty()) {
                        if (!StaticData.isForeigner && number.length >= 10) invokeCheckIcExist(number)
                        else if (StaticData.isForeigner && number.length >= 5) invokeCheckPassportExist(number)
                    }
                }
                debounceHandler.postDelayed(debounceRunnable!!, 500)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        if (StaticData.isForeigner) {
            val passportNo = StaticData.request.passport.takeIf { it.isNotBlank() }
                ?: StaticData.request.ic.takeIf { it.isNotBlank() } ?: ""
            binding.etCardNumber.setText(passportNo)
            if (passportNo.isNotBlank()) invokeCheckPassportExist(passportNo)
        } else {
            val icNo = StaticData.request.ic.takeIf { it.isNotBlank() } ?: ""
            binding.etCardNumber.setText(icNo)
            if (icNo.isNotBlank()) invokeCheckIcExist(icNo)
            try {
                val lastDigit = Common.parseInt(icNo.substring(icNo.length - 1, icNo.length))
                if (lastDigit % 2 == 1) binding.spGender.setSelection(0)
                else binding.spGender.setSelection(1)
            } catch (err: Throwable) {}
        }
    }

    private fun startTimer() {
        runnable = Runnable { finish() }
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
                val path: Uri? = data?.data
                val parcelFileDescriptor = contentResolver.openFileDescriptor(path!!, "r")
                val fileDescriptor: FileDescriptor = parcelFileDescriptor!!.fileDescriptor
                val bitmap = BitmapFactory.decodeFileDescriptor(fileDescriptor)
                parcelFileDescriptor.close()
                StaticData.base64_mykad = Utils().bitmapToBase64(bitmap)
                binding.ivCard.setImageBitmap(bitmap)
            }
        } catch (throwable: Throwable) {}
    }

    private fun dateOfBirthClicked() {
        val c = Calendar.getInstance()
        var month = c.get(Calendar.MONTH)
        var day = c.get(Calendar.DAY_OF_MONTH)
        var year = c.get(Calendar.YEAR)
        if (binding.tvDateOfBirth.text.toString().isNotEmpty()) {
            try {
                val date = SimpleDateFormat(Constants.date_format).parse(binding.tvDateOfBirth.text.toString())
                c.timeInMillis = date.time
                month = c.get(Calendar.MONTH); day = c.get(Calendar.DAY_OF_MONTH); year = c.get(Calendar.YEAR)
            } catch (e: ParseException) { e.printStackTrace() }
        }
        DatePickerDialog(this, { _, y, m, d ->
            c.set(y, m, d)
            binding.tvDateOfBirth.setText(SimpleDateFormat(Constants.date_format, Locale.US).format(Date(c.timeInMillis)))
        }, year, month, day).show()
    }

    private fun plksExpiryClicked() {
        val c = Calendar.getInstance()
        var month = c.get(Calendar.MONTH)
        var day = c.get(Calendar.DAY_OF_MONTH)
        var year = c.get(Calendar.YEAR)
        if (binding.tvPlksExpiry.text.toString().isNotEmpty()) {
            try {
                val date = SimpleDateFormat(Constants.date_format).parse(binding.tvPlksExpiry.text.toString())
                c.timeInMillis = date.time
                month = c.get(Calendar.MONTH); day = c.get(Calendar.DAY_OF_MONTH); year = c.get(Calendar.YEAR)
            } catch (e: ParseException) { e.printStackTrace() }
        }
        DatePickerDialog(this, { _, y, m, d ->
            c.set(y, m, d)
            binding.tvPlksExpiry.setText(SimpleDateFormat(Constants.date_format, Locale.US).format(Date(c.timeInMillis)))
        }, year, month, day).show()
    }

    private fun initResidentDropdown() {
        residentStringList = arrayListOf("Local", "Foreigner")
        binding.spResident.setItem(residentStringList)
        binding.spResident.typeface = ResourcesCompat.getFont(this, R.font.montserrat_bold)
        binding.spResident.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(a: AdapterView<*>?, v: View, p: Int, i: Long) { selectedResident = residentStringList[p] }
            override fun onNothingSelected(a: AdapterView<*>?) {}
        })
    }

    private fun initGenderDropdown() {
        genderStringList = arrayListOf("Male", "Female")
        binding.spGender.setItem(genderStringList)
        binding.spGender.typeface = ResourcesCompat.getFont(this, R.font.montserrat_bold)
        binding.spGender.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(a: AdapterView<*>?, v: View, p: Int, i: Long) { selectedGender = genderStringList[p] }
            override fun onNothingSelected(a: AdapterView<*>?) {}
        })
    }

    private fun initVehicleCategories() {
        genderStringList = arrayListOf("public", "cargo", "non-cargo")
        binding.spVinCat.setItem(genderStringList)
        binding.spVinCat.typeface = ResourcesCompat.getFont(this, R.font.montserrat_bold)
        binding.spVinCat.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(a: AdapterView<*>?, v: View, p: Int, i: Long) { selectedVehicleCategory = genderStringList[p] }
            override fun onNothingSelected(a: AdapterView<*>?) {}
        })
    }

    private fun invokeGetVehicleTypeApi() {
        val cached = DataCache.getVehicleTypes(this)
        if (cached != null) {
            vinTypeModelList = cached
            vehicleTypeStringList = ArrayList()
            for (item in cached) vehicleTypeStringList.add(item.name)
            binding.spVinType.setItem(vehicleTypeStringList)
            binding.spVinType.typeface = ResourcesCompat.getFont(this, R.font.montserrat_bold)
            applyVehicleTypeSelection()
            return
        }
        val pDialog = AwesomeProgressDialog(this).apply { setCancelable(false); setTitle("Please wait"); setMessage(""); setColoredCircle(R.color.pherosi); show() }
        Thread {
            AndroidNetworking.initialize(applicationContext, SslUtils.trustAllClient())
            runOnUiThread {
                AndroidNetworking.get(Constants.getActiveVehicleType).setTag(Constants.getActiveVehicleType).setPriority(Priority.HIGH).build()
                    .getAsObjectList(GetActiveVehicleTypeResponseItem::class.java, object : ParsedRequestListener<ArrayList<GetActiveVehicleTypeResponseItem>> {
                        override fun onResponse(response: ArrayList<GetActiveVehicleTypeResponseItem>) {
                            pDialog.hide()
                            DataCache.saveVehicleTypes(applicationContext, response)
                            vinTypeModelList = response
                            vehicleTypeStringList = ArrayList()
                            for (item in response) vehicleTypeStringList.add(item.name)
                            binding.spVinType.setItem(vehicleTypeStringList)
                            binding.spVinType.typeface = ResourcesCompat.getFont(this@CardDetailsActivity, R.font.montserrat_bold)
                            applyVehicleTypeSelection()
                        }
                        override fun onError(anError: ANError) { pDialog.hide(); Common.showToast(applicationContext, "Failed to Load Vehicle Types", Common.ToastType.ERROR) }
                    })
            }
        }.start()
    }

    private fun applyVehicleTypeSelection() {
        try { binding.spVinType.setSelection(0) } catch (t: Throwable) {}
        binding.spVinType.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(a: AdapterView<*>?, v: View, p: Int, i: Long) { selectedvehicleType = vinTypeModelList[p].id }
            override fun onNothingSelected(a: AdapterView<*>?) {}
        })
    }

    private fun invokeGetActiveCountryApi() {
        val cached = DataCache.getCountries(this)
        if (cached != null) {
            countryModelList = cached
            countryStringList = ArrayList()
            for (item in cached) countryStringList.add(item.name)
            binding.spCountry.setItem(countryStringList)
            binding.spCountry.typeface = ResourcesCompat.getFont(this, R.font.montserrat_bold)
            applyCountrySelection()
            return
        }
        val pDialog = AwesomeProgressDialog(this).apply { setCancelable(false); setTitle("Please wait"); setMessage(""); setColoredCircle(R.color.pherosi); show() }
        Thread {
            AndroidNetworking.initialize(applicationContext, SslUtils.trustAllClient())
            runOnUiThread {
                AndroidNetworking.get(Constants.getActiveCountry).setTag(Constants.getActiveCountry).setPriority(Priority.HIGH).build()
                    .getAsObjectList(GetActiveCountryResponseItem::class.java, object : ParsedRequestListener<ArrayList<GetActiveCountryResponseItem>> {
                        override fun onResponse(response: ArrayList<GetActiveCountryResponseItem>) {
                            pDialog.hide()
                            DataCache.saveCountries(applicationContext, response)
                            countryModelList = response
                            countryStringList = ArrayList()
                            for (item in response) countryStringList.add(item.name)
                            binding.spCountry.setItem(countryStringList)
                            binding.spCountry.typeface = ResourcesCompat.getFont(this@CardDetailsActivity, R.font.montserrat_bold)
                            applyCountrySelection()
                        }
                        override fun onError(anError: ANError) { pDialog.hide(); Common.showToast(applicationContext, "Failed to Load Countries", Common.ToastType.ERROR) }
                    })
            }
        }.start()
    }

    private fun applyCountrySelection() {
        try {
            if (!StaticData.isForeigner) {
                val malaysiaPos = countryModelList.indexOfFirst { it.name.contains("Malaysia", ignoreCase = true) }
                if (malaysiaPos >= 0) {
                    binding.spCountry.setSelection(malaysiaPos)
                    selectedCountry = countryModelList[malaysiaPos].id
                } else {
                    binding.spCountry.setSelection(0)
                    selectedCountry = countryModelList[0].id
                }
            } else {
                binding.spCountry.setSelection(0)
                selectedCountry = countryModelList[0].id
            }
        } catch (t: Throwable) {}

        binding.spCountry.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(a: AdapterView<*>?, v: View, p: Int, i: Long) {
                selectedCountry = countryModelList[p].id
                if (!StaticData.isForeigner) invokeGetStateByCountryApi(selectedCountry)
            }
            override fun onNothingSelected(a: AdapterView<*>?) {}
        })
    }

    private fun invokeCheckIcExist(icNumber: String) {
        val pDialog = AwesomeProgressDialog(this).apply { setCancelable(false); setTitle("Please wait"); setMessage(""); setColoredCircle(R.color.pherosi); show() }
        Thread {
            AndroidNetworking.initialize(applicationContext, SslUtils.trustAllClient())
            runOnUiThread {
                AndroidNetworking.get(Constants.getdetails).addQueryParameter("icno", icNumber).setTag(Constants.getdetails).setPriority(Priority.HIGH).build()
                    .getAsJSONObject(object : JSONObjectRequestListener {
                        override fun onResponse(response: JSONObject) {
                            pDialog.hide()
                            Log.d("IC_CHECK_RESPONSE", response.toString())
                            try {
                                StaticData.base64_face = response.optString("photo", "").safeValue()
                                val fullName = response.optString("fullName", "").safeValue()
                                val contactNo = response.optString("contactNo", "").safeValue()
                                val email = response.optString("email", "").safeValue()
                                val add1 = response.optString("add1", "").safeValue()
                                val postcode = response.optString("postcode", "").safeValue()
                                val cityId = response.optJSONObject("city")?.optInt("id", -1) ?: -1
                                val stateId = response.optJSONObject("state")?.optInt("id", -1) ?: -1
                                val companyName = response.optString("companyName", "").safeValue()
                                val regNum = response.optString("regNum", "").safeValue()

                                if (fullName.isNotEmpty()) binding.etCardHolderName.setText(fullName)
                                if (contactNo.isNotEmpty()) binding.etContactNumber.setText(contactNo)
                                if (email.isNotEmpty()) binding.etEmail.setText(email)
                                if (add1.isNotEmpty()) binding.etAddress.setText(add1)
                                if (companyName.isNotEmpty()) binding.etCompanyName.setText(companyName)
                                if (regNum.isNotEmpty()) binding.etRegNo.setText(regNum)
                                if (postcode.isNotEmpty()) binding.etPostalCode.setText(postcode)

                                if (fullName.isNotEmpty()) {
                                    // ✅ Set immediately — not async
                                    pendingStateId = stateId
                                    pendingCityId = cityId
                                    selectedState = stateId
                                    selectedCity = cityId

                                    if (::countryModelList.isInitialized) {
                                        val malaysiaPos = countryModelList.indexOfFirst { it.name.contains("Malaysia", ignoreCase = true) }
                                        if (malaysiaPos >= 0) {
                                            binding.spCountry.setSelection(malaysiaPos)
                                            selectedCountry = countryModelList[malaysiaPos].id
                                        }
                                    }

                                    if (stateId >= 0) invokeGetStateByCountryApiAndSelect(1, stateId, cityId)
                                    Common.showToast(applicationContext, "Details Loaded Successfully", Common.ToastType.SUCCESS)
                                }
                            } catch (e: Exception) {
                                Log.e("PARSE_ERROR", "Error: ${e.message}")
                                Common.showToast(applicationContext, "Error Loading Details", Common.ToastType.ERROR)
                            }
                        }
                        override fun onError(anError: ANError) {
                            pDialog.hide()
                            try {
                                val errorMessage = anError.errorBody?.let { JSONObject(it) }?.optString("message", "") ?: ""
                                if (errorMessage.isNotBlank()) Common.showToast(applicationContext, errorMessage, Common.ToastType.ERROR)
                            } catch (e: Exception) {}
                        }
                    })
            }
        }.start()
    }

    private fun invokeGetStateByCountryApiAndSelect(countryId: Int, stateId: Int, cityId: Int) {
        val cached = DataCache.getStates(this, countryId)
        if (cached != null) {
            stateModelList = cached
            stateStringList = ArrayList()
            for (item in cached) stateStringList.add(item.name)
            binding.spState.setItem(stateStringList)
            binding.spState.typeface = ResourcesCompat.getFont(this, R.font.montserrat_bold)
            val statePos = stateModelList.indexOfFirst { it.id == stateId }
            if (statePos >= 0) {
                selectedState = stateId
                isAutoSelectingState = true
                binding.spState.post { binding.spState.setSelection(statePos); isAutoSelectingState = false }
            }
            pendingStateId = -1 // ✅ reset after used
            setupStateListener()
            invokeGetCityByStateApiAndSelect(stateId, cityId)
            return
        }

        val pDialog = AwesomeProgressDialog(this).apply { setCancelable(false); setTitle("Please wait"); setMessage(""); setColoredCircle(R.color.pherosi); show() }
        Thread {
            AndroidNetworking.initialize(applicationContext, SslUtils.trustAllClient())
            runOnUiThread {
                AndroidNetworking.get(Constants.getStateByCountry + countryId).setTag("state_select_$countryId").setPriority(Priority.HIGH).build()
                    .getAsObjectList(GetStateByCountryResponseItem::class.java, object : ParsedRequestListener<ArrayList<GetStateByCountryResponseItem>> {
                        override fun onResponse(response: ArrayList<GetStateByCountryResponseItem>) {
                            pDialog.hide()
                            DataCache.saveStates(applicationContext, countryId, response)
                            stateModelList = response
                            stateStringList = ArrayList()
                            for (item in response) stateStringList.add(item.name)
                            binding.spState.setItem(stateStringList)
                            binding.spState.typeface = ResourcesCompat.getFont(this@CardDetailsActivity, R.font.montserrat_bold)
                            val statePos = stateModelList.indexOfFirst { it.id == stateId }
                            if (statePos >= 0) {
                                selectedState = stateId
                                isAutoSelectingState = true
                                binding.spState.post { binding.spState.setSelection(statePos); isAutoSelectingState = false }
                            }
                            pendingStateId = -1 // ✅ reset after used
                            setupStateListener()
                            if (stateId >= 0) invokeGetCityByStateApiAndSelect(stateId, cityId)
                        }
                        override fun onError(anError: ANError) { pDialog.hide(); Common.showToast(applicationContext, "Failed to Load States", Common.ToastType.ERROR) }
                    })
            }
        }.start()
    }

    private fun invokeGetCityByStateApiAndSelect(stateId: Int, cityId: Int) {
        val cached = DataCache.getCities(this, stateId)
        if (cached != null) {
            cityModelList = cached
            cityStringList = ArrayList()
            for (item in cached) cityStringList.add(item.name)
            binding.spCity.setItem(cityStringList)
            binding.spCity.typeface = ResourcesCompat.getFont(this, R.font.montserrat_bold)
            applyCityAutoSelect(cityId)
            setupCityListener()
            return
        }

        val pDialog = AwesomeProgressDialog(this).apply { setCancelable(false); setTitle("Please wait"); setMessage(""); setColoredCircle(R.color.pherosi); show() }
        Thread {
            AndroidNetworking.initialize(applicationContext, SslUtils.trustAllClient())
            runOnUiThread {
                AndroidNetworking.get(Constants.getCityByState + stateId).setTag("city_select_$stateId").setPriority(Priority.HIGH).build()
                    .getAsObjectList(GetCityByStateResponseItem::class.java, object : ParsedRequestListener<ArrayList<GetCityByStateResponseItem>> {
                        override fun onResponse(response: ArrayList<GetCityByStateResponseItem>) {
                            pDialog.hide()
                            DataCache.saveCities(applicationContext, stateId, response)
                            cityModelList = response
                            cityStringList = ArrayList()
                            for (item in response) cityStringList.add(item.name)
                            binding.spCity.setItem(cityStringList)
                            binding.spCity.typeface = ResourcesCompat.getFont(this@CardDetailsActivity, R.font.montserrat_bold)
                            applyCityAutoSelect(cityId)
                            setupCityListener()
                        }
                        override fun onError(anError: ANError) { pDialog.hide(); Common.showToast(applicationContext, "Failed to Load Cities", Common.ToastType.ERROR) }
                    })
            }
        }.start()
    }

    // ✅ Auto-select city — set selectedCity immediately, reset pending after used
    private fun applyCityAutoSelect(cityId: Int) {
        val cityPos = if (cityId >= 0) {
            cityModelList.indexOfFirst { it.id == cityId }
        } else {
            cityStringList.indexOfFirst { it.equals(selectedCityName, ignoreCase = true) }
        }
        if (cityPos >= 0) {
            selectedCity = cityModelList[cityPos].id
            binding.spCity.post { binding.spCity.setSelection(cityPos) }
        }
        pendingCityId = -1 // ✅ reset after used
    }

    private fun setupCityListener() {
        binding.spCity.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(a: AdapterView<*>?, v: View, p: Int, i: Long) { selectedCity = cityModelList[p].id }
            override fun onNothingSelected(a: AdapterView<*>?) {}
        })
    }

    private fun setupStateListener() {
        binding.spState.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(a: AdapterView<*>?, v: View, p: Int, i: Long) {
                if (isAutoSelectingState) return
                selectedState = stateModelList[p].id
                if (pendingCityId >= 0) invokeGetCityByStateApiAndSelect(stateModelList[p].id, pendingCityId)
                else invokeGetCityByStateApi(stateModelList[p].id)
            }
            override fun onNothingSelected(a: AdapterView<*>?) {}
        })
    }

    private fun invokeCheckPassportExist(passportNumber: String) {
        val pDialog = AwesomeProgressDialog(this).apply { setCancelable(false); setTitle("Please wait"); setMessage(""); setColoredCircle(R.color.pherosi); show() }
        Thread {
            AndroidNetworking.initialize(applicationContext, SslUtils.trustAllClient())
            runOnUiThread {
                AndroidNetworking.get(Constants.getdetails).addQueryParameter("icno", passportNumber).setTag(Constants.getdetails).setPriority(Priority.HIGH).build()
                    .getAsJSONObject(object : JSONObjectRequestListener {
                        override fun onResponse(response: JSONObject) {
                            pDialog.hide()
                            try {
                                val fullName = response.optString("fullName", "").safeValue()
                                val contactNo = response.optString("contactNo", "").safeValue()
                                val email = response.optString("email", "").safeValue()
                                val companyName = response.optString("companyName", "").safeValue()
                                val regNum = response.optString("regNum", "").safeValue()
                                val photo = response.optString("photo", "").safeValue()
                                if (fullName.isNotEmpty()) binding.etCardHolderName.setText(fullName)
                                if (contactNo.isNotEmpty()) binding.etContactNumber.setText(contactNo)
                                if (email.isNotEmpty()) binding.etEmail.setText(email)
                                if (companyName.isNotEmpty()) binding.etCompanyName.setText(companyName)
                                if (regNum.isNotEmpty()) binding.etRegNo.setText(regNum)
                                if (photo.isNotEmpty()) StaticData.base64_face = photo
                                if (fullName.isNotEmpty()) Common.showToast(applicationContext, "Passport Details Loaded", Common.ToastType.SUCCESS)
                            } catch (e: Exception) {
                                Common.showToast(applicationContext, "Error Loading Details", Common.ToastType.ERROR)
                            }
                        }
                        override fun onError(anError: ANError) { pDialog.hide() }
                    })
            }
        }.start()
    }

    private fun invokeGetStateByCountryApi(countryId: Int) {
        val cached = DataCache.getStates(this, countryId)
        if (cached != null) {
            stateModelList = cached
            stateStringList = ArrayList()
            for (item in cached) stateStringList.add(item.name)
            binding.spState.setItem(stateStringList)
            binding.spState.typeface = ResourcesCompat.getFont(this, R.font.montserrat_bold)
            applyStateSelection()
            return
        }
        val pDialog = AwesomeProgressDialog(this).apply { setCancelable(false); setTitle("Please wait"); setMessage(""); setColoredCircle(R.color.pherosi); show() }
        Thread {
            AndroidNetworking.initialize(applicationContext, SslUtils.trustAllClient())
            runOnUiThread {
                AndroidNetworking.get(Constants.getStateByCountry + countryId).setTag(Constants.getStateByCountry + countryId).setPriority(Priority.HIGH).build()
                    .getAsObjectList(GetStateByCountryResponseItem::class.java, object : ParsedRequestListener<ArrayList<GetStateByCountryResponseItem>> {
                        override fun onResponse(response: ArrayList<GetStateByCountryResponseItem>) {
                            pDialog.hide()
                            DataCache.saveStates(applicationContext, countryId, response)
                            stateModelList = response
                            stateStringList = ArrayList()
                            for (item in response) stateStringList.add(item.name)
                            binding.spState.setItem(stateStringList)
                            binding.spState.typeface = ResourcesCompat.getFont(this@CardDetailsActivity, R.font.montserrat_bold)
                            applyStateSelection()
                        }
                        override fun onError(anError: ANError) { pDialog.hide(); Common.showToast(applicationContext, "Failed to Load States", Common.ToastType.ERROR) }
                    })
            }
        }.start()
    }

    private fun applyStateSelection() {
        if (pendingStateId >= 0) {
            val statePos = stateModelList.indexOfFirst { it.id == pendingStateId }
            if (statePos >= 0) {
                selectedState = pendingStateId
                isAutoSelectingState = true
                binding.spState.post { binding.spState.setSelection(statePos); isAutoSelectingState = false }
            }
            pendingStateId = -1 // ✅ reset after used
        }
        setupStateListener()
    }

    private fun invokeGetCityByStateApi(stateId: Int) {
        val cached = DataCache.getCities(this, stateId)
        if (cached != null) {
            cityModelList = cached
            cityStringList = ArrayList()
            for (item in cached) cityStringList.add(item.name)
            binding.spCity.setItem(cityStringList)
            binding.spCity.typeface = ResourcesCompat.getFont(this, R.font.montserrat_bold)
            applyCitySelection()
            return
        }
        val pDialog = AwesomeProgressDialog(this).apply { setCancelable(false); setTitle("Please wait"); setMessage(""); setColoredCircle(R.color.pherosi); show() }
        Thread {
            AndroidNetworking.initialize(applicationContext, SslUtils.trustAllClient())
            runOnUiThread {
                AndroidNetworking.get(Constants.getCityByState + stateId).setTag(Constants.getCityByState + stateId).setPriority(Priority.HIGH).build()
                    .getAsObjectList(GetCityByStateResponseItem::class.java, object : ParsedRequestListener<ArrayList<GetCityByStateResponseItem>> {
                        override fun onResponse(response: ArrayList<GetCityByStateResponseItem>) {
                            pDialog.hide()
                            DataCache.saveCities(applicationContext, stateId, response)
                            cityModelList = response
                            cityStringList = ArrayList()
                            for (item in response) cityStringList.add(item.name)
                            binding.spCity.setItem(cityStringList)
                            binding.spCity.typeface = ResourcesCompat.getFont(this@CardDetailsActivity, R.font.montserrat_bold)
                            applyCitySelection()
                        }
                        override fun onError(anError: ANError) { pDialog.hide(); Common.showToast(applicationContext, "Failed to Load Cities", Common.ToastType.ERROR) }
                    })
            }
        }.start()
    }

    private fun applyCitySelection() {
        try {
            if (pendingCityId >= 0) {
                val cityPos = cityModelList.indexOfFirst { it.id == pendingCityId }
                if (cityPos >= 0) {
                    selectedCity = pendingCityId
                    binding.spCity.post { binding.spCity.setSelection(cityPos) }
                }
                pendingCityId = -1 // ✅ reset after used
            } else if (selectedCity >= 0) {
                val cityPos = cityModelList.indexOfFirst { it.id == selectedCity }
                if (cityPos >= 0) binding.spCity.post { binding.spCity.setSelection(cityPos) }
            }
            if (!selectedCityName.isNullOrEmpty()) {
                val pos = cityStringList.indexOfFirst { it.equals(selectedCityName, ignoreCase = true) }
                if (pos >= 0) { selectedCity = cityModelList[pos].id; binding.spCity.setSelection(pos) }
            }
        } catch (t: Throwable) {}
        setupCityListener()
    }

    fun onReadMyKad() {
        if (morphoSmart == null) {
            val usbManager = baseContext.getSystemService(USB_SERVICE) as UsbManager
            if (deviceProbe == null || deviceProbe!!.usbDevice == null) { MsgBox("No smart card reader attached to the system"); return }
            morphoSmart = MorphoSmart(usbManager, deviceProbe!!.usbDevice, this)
        }
        try {
            morphoSmart!!.open()
            readCardAsyncManager!!.setupTask(ReadCardTask(resources, morphoSmart, true))
        } catch (e: DeviceException) { MsgBox("Error opening smartcard reader") }
    }

    override fun onTaskComplete(task: ReadCardTask) {
        try {
            val readCardResult = task.get()
            if (readCardResult.isSuccessful) {
                val cardHolderInfo = readCardResult.personalInfo
                binding.etCardHolderName.setText(cardHolderInfo.name)
                binding.etCardNumber.setText(cardHolderInfo.nric)
                binding.etAddress.setText("${cardHolderInfo.address1}, ${cardHolderInfo.address2}, ${cardHolderInfo.address3}")
                val date = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(cardHolderInfo.dateOfBirth)
                binding.tvDateOfBirth.setText(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date))
                selectedCityName = cardHolderInfo.city
                val statePos = stateStringList.indexOfFirst { it.equals(cardHolderInfo.state, ignoreCase = true) }
                if (statePos >= 0) {
                    selectedState = stateModelList[statePos].id
                    binding.spState.post { binding.spState.setSelection(statePos) }
                    invokeGetCityByStateApiAndSelect(stateModelList[statePos].id, -1)
                }
                binding.etPostalCode.setText(cardHolderInfo.postcode)
                binding.spGender.setSelection(if (cardHolderInfo.gender == "M") 0 else 1)
                binding.ivCard.setImageBitmap(BitmapFactory.decodeByteArray(cardHolderInfo.photo, 0, cardHolderInfo.photo.size))
            } else MsgBox("Failed to read MyKad")
        } catch (e: Exception) { MsgBox(e.message) }
    }

    override fun onTaskComplete(task: VerifyFPTask) {
        try {
            val result = task.get()
            val morpho = result.morphoSmartResult
            val msg = when (morpho.errorCode) {
                ILVErrorCode.ILV_OK -> if (morpho.resultCode == ILVResultCode.ILVSTS_HIT) "Fingerprint matches fingerprint in MyKad" else "Fingerprint does not match fingerprint in MyKad"
                ILVErrorCode.ILVERR_INVALID_MINUTIAE -> "Invalid fingerprint miniature"
                ILVErrorCode.ILVERR_TIMEOUT -> "Fingerprint verification operation timed out"
                ILVErrorCode.ILVERR_CMDE_ABORTED -> "Fingerprint verification operation aborted"
                ILVErrorCode.ILVERR_MYKAD -> result.errorMessage
                ILVErrorCode.ILVERR_LICENSE_REG_FAILED -> "Fingerprint SDK activation failed. Make sure tablet is connected to internet."
                ILVErrorCode.ILVERR_INVALID_LICENSE -> "Fingerprint SDK activation failed due to invalid or missing license"
                else -> "Fingerprint verification operation encountered an error"
            }
            MsgBox(msg)
        } catch (e: Exception) { e.printStackTrace() } finally { morphoSmart?.close() }
    }

    override fun onTaskComplete(task: ReadCardVerifyFpTask) {
        try {
            val readCardResult = task.get()
            if (readCardResult.readCardResult.isSuccessful) {
                val morpho = readCardResult.verifyFPResult.morphoSmartResult
                val verifyMsg = when (morpho.errorCode) {
                    ILVErrorCode.ILV_OK -> if (morpho.resultCode == ILVResultCode.ILVSTS_HIT) "Fingerprint matches fingerprint in MyKad" else "Fingerprint does not match fingerprint in MyKad"
                    ILVErrorCode.ILVERR_INVALID_MINUTIAE -> "Invalid fingerprint miniature"
                    ILVErrorCode.ILVERR_TIMEOUT -> "Fingerprint verification operation timed out"
                    ILVErrorCode.ILVERR_CMDE_ABORTED -> "Fingerprint verification operation aborted"
                    ILVErrorCode.ILVERR_MYKAD -> readCardResult.verifyFPResult.errorMessage
                    ILVErrorCode.ILVERR_LICENSE_REG_FAILED -> "Fingerprint SDK activation failed. Make sure tablet is connected to internet"
                    ILVErrorCode.ILVERR_INVALID_LICENSE -> "Fingerprint SDK activation failed due to invalid or missing license"
                    else -> "Fingerprint verification operation encountered an error"
                }
                MsgBox("Fingerprint Verification Result: $verifyMsg\n${Gson().toJson(readCardResult.readCardResult.personalInfo)}")
            } else MsgBox("Failed to read MyKad")
        } catch (e: Exception) { MsgBox(e.message) }
    }

    fun MsgBox(response: String?) {
        AlertDialog.Builder(this).setCancelable(false).setMessage(response)
            .setNegativeButton("OK") { d, _ -> d.cancel() }.create().show()
    }
}