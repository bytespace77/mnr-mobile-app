package com.safeg.activities

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.media.Image
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.util.Size
import android.view.Surface
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.androidnetworking.AndroidNetworking
import com.androidnetworking.common.Priority
import com.androidnetworking.error.ANError
import com.androidnetworking.interfaces.JSONObjectRequestListener
import com.awesomedialog.blennersilva.awesomedialoglibrary.AwesomeInfoDialog
import com.awesomedialog.blennersilva.awesomedialoglibrary.interfaces.Closure
import com.safeg.StaticData
import com.safeg.databinding.ActivityFaceDetectionBinding
import com.safeg.utils.BitmapUtils
import com.safeg.utils.SslUtils
import com.ml.frlib.facenet_android.data.ImagesVectorDB
import com.ml.frlib.facenet_android.data.ObjectBoxStore
import com.ml.frlib.facenet_android.data.PersonDB
import com.ml.frlib.facenet_android.domain.ImageVectorUseCase
import com.ml.frlib.facenet_android.domain.PersonUseCase
import com.ml.frlib.facenet_android.domain.embeddings.FaceNet
import com.ml.frlib.facenet_android.domain.face_detection.FaceSpoofDetector
import com.ml.frlib.facenet_android.domain.face_detection.MediapipeFaceDetector
import com.ml.frlib.frlib.LicenseValidator
import com.safeg.Constants
import com.safeg.R
import com.safeg.utils.Common
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.nio.ReadOnlyBufferException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors

class FaceDetectionActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "FaceDetectionActivity"
        private const val PERMISSION_CODE = 1001
        private const val CAMERA_PERMISSION = Manifest.permission.CAMERA
    }

    private lateinit var binding: ActivityFaceDetectionBinding
    private var cameraSelector: CameraSelector? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var previewUseCase: Preview? = null
    private var analysisUseCase: ImageAnalysis? = null
    private var lensFacing = CameraSelector.LENS_FACING_FRONT
    private var flipX = true
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private lateinit var mediapipeFaceDetector: MediapipeFaceDetector
    private lateinit var faceNet: FaceNet
    private lateinit var faceSpoofDetector: FaceSpoofDetector
    private lateinit var imagesVectorDB: ImagesVectorDB
    private lateinit var imageVectorUseCase: ImageVectorUseCase
    private lateinit var personDB: PersonDB
    private lateinit var personUseCase: PersonUseCase

    private var activityStarted = false
    private var bitmapFull: Bitmap? = null
    private var pausedForSave = false
    private lateinit var countDownTimer: CountDownTimer
    private var isLibraryReady = false
    private var faceDetectedOnce = false
    private var addFaceScheduled = false
    private var frameCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFaceDetectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        StaticData.base64_face = ""
        binding.previewView.scaleType = PreviewView.ScaleType.FIT_CENTER

        binding.addBtn.setOnClickListener { showAddFaceDialog() }
        binding.clearBtn.setOnClickListener { clearDatabasePrompt() }
        binding.ivBack.setOnClickListener { finish() }

        initializeLibraryAsync()
        setupTimeoutWatcher()
    }

    private fun initializeLibraryAsync() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                initObjectBox()
                initMLComponents()
                initUseCases()
            } catch (t: Throwable) {
                Log.e("Init", "Initialization failed: ${t.message}", t)
            }
            withContext(Dispatchers.Main) {
                startCameraIfReady()
            }
        }
    }

    private fun initObjectBox() {
        try { ObjectBoxStore.init(applicationContext); Log.i("Init", "ObjectBox OK") }
        catch (t: Throwable) { Log.w("Init", "ObjectBox failed: ${t.message}") }
    }

    private fun initMLComponents() {
        try { mediapipeFaceDetector = MediapipeFaceDetector(this); Log.i("Init", "Mediapipe OK") } catch (t: Throwable) { Log.e("Init", "Mediapipe failed", t) }
        try { faceNet = FaceNet(this); Log.i("Init", "FaceNet OK") } catch (t: Throwable) { Log.e("Init", "FaceNet failed", t) }
        try { faceSpoofDetector = FaceSpoofDetector(this); Log.i("Init", "SpoofDetector OK") } catch (t: Throwable) { Log.e("Init", "SpoofDetector failed", t) }
    }

    private fun initUseCases() {
        try {
            imagesVectorDB = ImagesVectorDB()
            personDB = PersonDB()
            System.loadLibrary("frlib")
            if (!LicenseValidator.isLicenseValid(this, "fK8dP-2rX9q-V7LsE-4YtQ1")) throw IllegalStateException("Invalid License")
            if (::mediapipeFaceDetector.isInitialized && ::faceNet.isInitialized && ::faceSpoofDetector.isInitialized) {
                imageVectorUseCase = ImageVectorUseCase(mediapipeFaceDetector, faceSpoofDetector, imagesVectorDB, faceNet)
                personUseCase = PersonUseCase(personDB)
                isLibraryReady = true
            } else Log.w("Init", "Some ML components not initialized")
        } catch (t: Throwable) { Log.e("Init", "UseCases init failed", t) }
    }

    private fun startCameraIfReady() {}

    private fun setupTimeoutWatcher() {
        countDownTimer = object : CountDownTimer(Long.MAX_VALUE, 5000L) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {}
        }
    }

    private fun showMyKadFailedToReadDialog() {
        val dialog = AwesomeInfoDialog(this)
        dialog.setTitle("Recognition Failed")
        dialog.setMessage("Failed to process face. Do you want to retry?")
        dialog.setColoredCircle(com.awesomedialog.blennersilva.awesomedialoglibrary.R.color.dialogErrorBackgroundColor)
        dialog.setDialogIconAndColor(com.awesomedialog.blennersilva.awesomedialoglibrary.R.drawable.ic_dialog_error, com.awesomedialog.blennersilva.awesomedialoglibrary.R.color.white)
        dialog.setCancelable(false)
        dialog.setNegativeButtonText("Retry")
        dialog.setNegativeButtonbackgroundColor(com.awesomedialog.blennersilva.awesomedialoglibrary.R.color.dialogErrorBackgroundColor)
        dialog.setNegativeButtonTextColor(com.awesomedialog.blennersilva.awesomedialoglibrary.R.color.white)
        dialog.setNegativeButtonClick(object : Closure {
            override fun exec() { dialog.hide(); setupCamera() }
        })
        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        startCamera()
        countDownTimer.start()
    }

    override fun onPause() {
        super.onPause()
        countDownTimer.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        try { ObjectBoxStore.close() } catch (_: Throwable) {}
    }

    private val requestCameraPermission: Unit
        get() { ActivityCompat.requestPermissions(this, arrayOf(CAMERA_PERMISSION), PERMISSION_CODE) }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        for (r in grantResults) {
            if (r == PackageManager.PERMISSION_DENIED) { Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show(); return }
        }
        if (requestCode == PERMISSION_CODE) setupCamera()
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun startCamera() {
        if (ContextCompat.checkSelfPermission(this, CAMERA_PERMISSION) == PackageManager.PERMISSION_GRANTED) setupCamera()
        else requestCameraPermission
    }

    private fun setupCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindAllCameraUseCases()
            } catch (e: ExecutionException) { Log.e(TAG, "cameraProviderFuture error", e) }
            catch (e: InterruptedException) { Log.e(TAG, "cameraProviderFuture interrupted", e) }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindAllCameraUseCases() {
        cameraProvider?.unbindAll()
        bindPreviewUseCase()
        bindAnalysisUseCase()
    }

    private fun bindPreviewUseCase() {
        if (cameraProvider == null) return
        previewUseCase?.let { cameraProvider!!.unbind(it) }
        val builder = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
        try { builder.setTargetRotation(rotation) } catch (_: Throwable) { builder.setTargetRotation(Surface.ROTATION_0) }
        previewUseCase = builder.build()
        previewUseCase!!.setSurfaceProvider(binding.previewView.surfaceProvider)
        try { cameraProvider!!.bindToLifecycle(this, cameraSelector!!, previewUseCase) } catch (e: Exception) { Log.e(TAG, "bindPreview", e) }
    }

    private fun bindAnalysisUseCase() {
        if (cameraProvider == null) return
        analysisUseCase?.let { cameraProvider!!.unbind(it) }
        val builder = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetResolution(Size(640, 480))
        try { builder.setTargetRotation(rotation) } catch (_: Throwable) { builder.setTargetRotation(Surface.ROTATION_0) }
        analysisUseCase = builder.build()
        analysisUseCase!!.setAnalyzer(cameraExecutor) { image ->
            if (!pausedForSave) analyzeFrame(image) else image.close()
        }
        try { cameraProvider!!.bindToLifecycle(this, cameraSelector!!, analysisUseCase) } catch (e: Exception) { Log.e(TAG, "bindAnalysis", e) }
    }

    private val rotation: Int get() = binding.previewView.display.rotation

    @SuppressLint("UnsafeOptInUsageError")
    private fun analyzeFrame(image: ImageProxy) {
        frameCount++
        if (frameCount % 3 != 0) { image.close(); return }

        try {
            val mediaImage: Image? = image.image
            if (mediaImage == null) { image.close(); return }

            bitmapFull = try { BitmapUtils.getBitmap(image) } catch (t: Throwable) { toBitmap(mediaImage) }

            val fullBitmap = bitmapFull ?: run { image.close(); return }

            if (!isLibraryReady) { image.close(); return }

            lifecycleScope.launch(Dispatchers.Default) {
                try {
                    val raw = try {
                        imageVectorUseCase.getNearestPersonName(fullBitmap, false)
                    } catch (t: Throwable) {
                        Log.w(TAG, "getNearestPersonName failed: ${t.message}")
                        null
                    }

                    val resultsList = extractResultsList(raw)

                    withContext(Dispatchers.Main) {
                        if (resultsList.isNotEmpty()) {
                            if (!StaticData.base64_face.isNullOrEmpty()) {
                                val first = resultsList[0]
                                val personName = readFieldString(first, "personName")
                                    ?: readFieldString(first, "name") ?: "unknown"
                                binding.tvDetectionText.text = personName

                                if (personName != "Not recognized" && !activityStarted) {
                                    activityStarted = true
                                    when {
                                        StaticData.isVvip -> {
                                            Common.showToast(applicationContext, "VVIP Identity Verified", Common.ToastType.SUCCESS)
                                            finish()
                                            startActivity(Intent(this@FaceDetectionActivity, CollectCardActivity::class.java))
                                        }
                                        // ✅ Invitation — uploadVendorPassImage handles navigation
                                        StaticData.invitation -> { /* no-op */ }
                                        StaticData.collectCard -> {
                                            Common.showToast(applicationContext, "Face captured. Please collect your card.")
                                            finish()
                                            startActivity(Intent(this@FaceDetectionActivity, CollectCardActivity::class.java))
                                        }
                                        else -> {
                                            finish()
                                            startActivity(Intent(this@FaceDetectionActivity, VisitUpdateDetailsActivity::class.java))
                                        }
                                    }
                                }

                                val bbox = readFieldRect(first, "boundingBox")
                                if (bbox != null) {
                                    try {
                                        val scaleX = binding.previewView.width.toFloat() / fullBitmap.width.toFloat()
                                        val scaleY = binding.previewView.height.toFloat() / fullBitmap.height.toFloat()
                                        binding.graphicOverlay.draw(bbox, scaleX, scaleY, personName)
                                    } catch (_: Throwable) {}
                                }
                            }

                            if (StaticData.base64_face.isNullOrEmpty()) {
                                if (!faceDetectedOnce) {
                                    faceDetectedOnce = true
                                    if (!addFaceScheduled) {
                                        addFaceScheduled = true
                                        Handler(Looper.getMainLooper()).postDelayed({
                                            if (StaticData.base64_face.isNullOrEmpty() && !pausedForSave) {
                                                showAddFaceDialog()
                                            }
                                        }, 800)
                                    }
                                }
                            }
                        } else {
                            binding.tvDetectionText.text = "No Face Detected!"
                        }
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "recognition error", t)
                } finally {
                    try { image.close() } catch (_: Throwable) {}
                }
            }
        } catch (t: Throwable) {
            try { image.close() } catch (_: Throwable) {}
            Log.e(TAG, "analyzeFrame outer error", t)
        }
    }

    private fun showAddFaceDialog() {
        pausedForSave = true

        val dialogView = layoutInflater.inflate(R.layout.dialog_add_face, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.etName)
        val imgPreview = dialogView.findViewById<ImageView>(R.id.ivPreview)
        val tvFullName = dialogView.findViewById<TextView>(R.id.tvFullName)
        val btnCancel = dialogView.findViewById<TextView>(R.id.btnCancel)
        val btnConfirm = dialogView.findViewById<TextView>(R.id.btnConfirm)

        val previewBmp = bitmapFull?.copy(Bitmap.Config.ARGB_8888, false)
            ?: getBitmapFromPreview(binding.previewView)
        if (previewBmp != null) imgPreview.setImageBitmap(previewBmp)

        val fullName = StaticData.request.fullName.takeIf { it.isNotBlank() }
            ?: StaticData.vvipName.takeIf { it.isNotBlank() }
            ?: "—"
        tvFullName.text = fullName

        val icNumber = StaticData.request.ic.takeIf { it.isNotBlank() }
            ?: StaticData.request.passport.takeIf { it.isNotBlank() }
            ?: StaticData.vvipIc.takeIf { it.isNotBlank() }
            ?: ""
        nameInput.setText(icNumber)

        val dialog = AlertDialog.Builder(this, R.style.AppTheme_Dialog)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.show()

        btnCancel.setOnClickListener {
            pausedForSave = false
            dialog.dismiss()
        }

        btnConfirm.setOnClickListener {
            val name = nameInput.text.toString().trim()
            if (name.isEmpty()) {
                Common.showToast(this, "Please Enter IC Number", Common.ToastType.WARNING)
                return@setOnClickListener
            }

            if (!isLibraryReady) {
                Common.showToast(this, "Library Not Available", Common.ToastType.ERROR)
                pausedForSave = false
                return@setOnClickListener
            }

            dialog.dismiss()

            lifecycleScope.launch {
                var skipPausedReset = false
                try {
                    val personId = withContext(Dispatchers.IO) {
                        try { personUseCase.addPerson(name, 1L); 1L } catch (t: Throwable) { -1L }
                    }

                    if (previewBmp == null) {
                        withContext(Dispatchers.Main) {
                            Common.showToast(this@FaceDetectionActivity, "No Image Available", Common.ToastType.ERROR)
                        }
                        return@launch
                    }

                    val tmpFile = File(cacheDir, "face_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(tmpFile).use { fos ->
                        previewBmp.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                        fos.flush()
                    }

                    val uri = Uri.fromFile(tmpFile)
                    val addOk = try {
                        withContext(Dispatchers.Default) { imageVectorUseCase.addImage(personId, name, uri) }
                        true
                    } catch (t: Throwable) {
                        Log.e(TAG, "addImage failed: ${t.message}")
                        false
                    }

                    withContext(Dispatchers.Main) {
                        if (addOk) {
                            activityStarted = true
                            StaticData.base64_face = bitmapToBase64(previewBmp)

                            when {
                                StaticData.isVvip -> {
                                    Common.showToast(this@FaceDetectionActivity, "VVIP face registered. Please collect your card.")
                                    finish()
                                    startActivity(Intent(this@FaceDetectionActivity, CollectCardActivity::class.java))
                                }
                                StaticData.collectCard -> {
                                    Common.showToast(this@FaceDetectionActivity, "Face captured. Please collect your card.")
                                    finish()
                                    startActivity(Intent(this@FaceDetectionActivity, CollectCardActivity::class.java))
                                }
                                StaticData.invitation -> {
                                    // ✅ Keep camera paused — uploadVendorPassImage handles navigation
                                    skipPausedReset = true
                                    cameraProvider?.unbindAll()
                                    Common.showToast(this@FaceDetectionActivity, "Uploading face image...", Common.ToastType.INFO)
                                    uploadVendorPassImage()
                                }
                                else -> {
                                    Toast.makeText(this@FaceDetectionActivity, "Face saved", Toast.LENGTH_SHORT).show()
                                    finish()
                                    startActivity(Intent(this@FaceDetectionActivity, VisitUpdateDetailsActivity::class.java))
                                }
                            }
                        } else {
                            Toast.makeText(this@FaceDetectionActivity, "Failed to save face", Toast.LENGTH_SHORT).show()
                            addFaceScheduled = false
                        }
                    }
                } finally {
                    if (!skipPausedReset) pausedForSave = false
                }
            }
        }
    }

    private fun clearDatabasePrompt() {
        AlertDialog.Builder(this)
            .setTitle("Clear faces")
            .setMessage("This will clear all saved faces in the library DB. Continue?")
            .setPositiveButton("Yes") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        try {
                            val m: Method = personUseCase.javaClass.getMethod("deleteAll")
                            m.invoke(personUseCase)
                        } catch (nsme: NoSuchMethodException) {
                            try {
                                val getAll: Method = personUseCase.javaClass.getMethod("getAll")
                                val list = getAll.invoke(personUseCase)
                                if (list is Iterable<*>) {
                                    val delete: Method? = try { personUseCase.javaClass.getMethod("delete", Long::class.javaPrimitiveType) } catch (_: Throwable) { null }
                                    if (delete != null) {
                                        for (p in list) {
                                            try {
                                                val idField = try { p!!.javaClass.getField("id") } catch (_: Throwable) { null }
                                                val idVal = idField?.getLong(p) ?: -1L
                                                if (idVal >= 0L) delete.invoke(personUseCase, idVal)
                                            } catch (_: Throwable) {}
                                        }
                                    }
                                }
                            } catch (_: Throwable) {}
                        }
                        withContext(Dispatchers.Main) { Toast.makeText(this@FaceDetectionActivity, "Database cleared", Toast.LENGTH_SHORT).show() }
                    } catch (t: Throwable) {
                        withContext(Dispatchers.Main) { Toast.makeText(this@FaceDetectionActivity, "Failed to clear DB", Toast.LENGTH_SHORT).show() }
                    }
                }
            }
            .setNegativeButton("No", null)
            .create().show()
    }

    private fun getBitmapFromPreview(previewView: PreviewView): Bitmap? {
        try {
            val m = BitmapUtils::class.java.getMethod("getBitmapFromPreview", PreviewView::class.java)
            val bmp = m.invoke(null, previewView)
            if (bmp is Bitmap) return bmp
        } catch (_: Throwable) {}
        return try {
            val bitmap = Bitmap.createBitmap(previewView.width.coerceAtLeast(1), previewView.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            previewView.draw(Canvas(bitmap))
            bitmap
        } catch (t: Throwable) { null }
    }

    private fun toBitmap(image: Image): Bitmap {
        val nv21 = YUV_420_888toNV21(image)
        val yuvImage = android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 75, out)
        return BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
    }

    private fun YUV_420_888toNV21(image: Image): ByteArray {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val uvSize = width * height / 4
        val nv21 = ByteArray(ySize + uvSize * 2)
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer
        var rowStride = image.planes[0].rowStride
        assert(image.planes[0].pixelStride == 1)
        var pos = 0
        if (rowStride == width) { yBuffer[nv21, 0, ySize]; pos += ySize }
        else {
            var yBufferPos = -rowStride.toLong()
            while (pos < ySize) {
                yBufferPos += rowStride.toLong()
                yBuffer.position(yBufferPos.toInt())
                yBuffer[nv21, pos, width]
                pos += width
            }
        }
        rowStride = image.planes[2].rowStride
        val pixelStride = image.planes[2].pixelStride
        assert(rowStride == image.planes[1].rowStride)
        assert(pixelStride == image.planes[1].pixelStride)
        if (pixelStride == 2 && rowStride == width && uBuffer[0] == vBuffer[1]) {
            try { val savePixel = vBuffer[1] } catch (_: ReadOnlyBufferException) {}
        }
        for (row in 0 until height / 2) {
            for (col in 0 until width / 2) {
                val vuPos = col * pixelStride + row * rowStride
                nv21[pos++] = vBuffer[vuPos]
                nv21[pos++] = uBuffer[vuPos]
            }
        }
        return nv21
    }

    private fun extractResultsList(raw: Any?): List<Any> {
        if (raw == null) return emptyList()
        try {
            if (raw is Collection<*>) { @Suppress("UNCHECKED_CAST") return raw.filterNotNull() as List<Any> }
            val cls = raw.javaClass
            val pairGetSecond = try { cls.getMethod("getSecond") } catch (_: Throwable) { null }
            if (pairGetSecond != null) {
                val second = pairGetSecond.invoke(raw)
                if (second is Collection<*>) return second.filterNotNull() as List<Any>
            }
            try { val f: Field = cls.getDeclaredField("results"); f.isAccessible = true; val v = f.get(raw); if (v is Collection<*>) return v.filterNotNull() as List<Any> } catch (_: Throwable) {}
            try { val f: Field = cls.getDeclaredField("matches"); f.isAccessible = true; val v = f.get(raw); if (v is Collection<*>) return v.filterNotNull() as List<Any> } catch (_: Throwable) {}
        } catch (t: Throwable) { Log.w(TAG, "extractResultsList: ${t.message}") }
        return emptyList()
    }

    private fun readFieldString(obj: Any?, name: String): String? {
        if (obj == null) return null
        try { val f = obj.javaClass.getDeclaredField(name); f.isAccessible = true; return f.get(obj)?.toString() } catch (_: Throwable) {}
        try { return obj.javaClass.getMethod("get${name.replaceFirstChar { it.uppercase() }}").invoke(obj)?.toString() } catch (_: Throwable) {}
        return null
    }

    private fun readFieldRect(obj: Any?, name: String): Rect? {
        if (obj == null) return null
        try {
            val f = obj.javaClass.getDeclaredField(name); f.isAccessible = true
            val v = f.get(obj); if (v is Rect) return v
            val l = try { obj.javaClass.getDeclaredField("left").getInt(obj) } catch (_: Throwable) { null }
            val t = try { obj.javaClass.getDeclaredField("top").getInt(obj) } catch (_: Throwable) { null }
            val r = try { obj.javaClass.getDeclaredField("right").getInt(obj) } catch (_: Throwable) { null }
            val b = try { obj.javaClass.getDeclaredField("bottom").getInt(obj) } catch (_: Throwable) { null }
            if (l is Int && t is Int && r is Int && b is Int) return Rect(l, t, r, b)
        } catch (_: Throwable) {}
        return null
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }

    private fun uploadVendorPassImage() {
        val photo = "data:image/png;base64," + StaticData.base64_face
        val staffNo = StaticData.request.ic

        // ✅ Debug toast 1
        Common.showToast(applicationContext, "Upload starting — staffNo:$staffNo", Common.ToastType.INFO)

        Thread {
            AndroidNetworking.initialize(applicationContext, SslUtils.trustAllClient())
            runOnUiThread {
                AndroidNetworking.post(Constants.uploadVendorPassPhotoMobile)
                    .setTag(Constants.uploadVendorPassPhotoMobile)
                    .setPriority(Priority.HIGH)
                    .addJSONObjectBody(JSONObject().put("staffNo", staffNo).put("photo", photo))
                    .build()
                    .getAsJSONObject(object : JSONObjectRequestListener {
                        override fun onResponse(response: JSONObject?) {
                            if (isFinishing || isDestroyed) return
                            // ✅ Debug toast 2
                            Common.showToast(applicationContext, "Upload response: ${response?.optString("status")}", Common.ToastType.INFO)
                            if (response!!.getString("status").compareTo("OK") == 0) {
                                Common.showToast(applicationContext, "Image Saved — Proceed to Collect Card", Common.ToastType.SUCCESS)
                                finish()
                                startActivity(Intent(this@FaceDetectionActivity, CollectCardActivity::class.java))
                            } else {
                                Common.showToast(applicationContext, "Error Saving Image: ${response.optString("status")}", Common.ToastType.ERROR)
                                pausedForSave = false
                            }
                        }
                        override fun onError(anError: ANError?) {
                            if (isFinishing || isDestroyed) return
                            // ✅ Debug toast 3
                            Common.showToast(applicationContext, "Upload error: ${anError?.errorCode} ${anError?.errorDetail}", Common.ToastType.ERROR)
                            pausedForSave = false
                        }
                    })
            }
        }.start()
    }
}