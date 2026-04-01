package com.safeg.activities

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.media.Image
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.InputType
import android.util.Base64
import android.util.Log
import android.view.Surface
import android.view.View
import android.widget.EditText
import android.widget.ImageView
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
import com.safeg.utils.Utils
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
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.nio.ReadOnlyBufferException
import java.security.KeyStore
import java.security.SecureRandom
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

class FaceDetectionActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "FaceDetectionActivity"
        private const val PERMISSION_CODE = 1001
        private const val CAMERA_PERMISSION = Manifest.permission.CAMERA
    }

    private lateinit var binding: ActivityFaceDetectionBinding

    // CameraX
    private var cameraSelector: CameraSelector? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var previewUseCase: Preview? = null
    private var analysisUseCase: ImageAnalysis? = null
    private var lensFacing = CameraSelector.LENS_FACING_FRONT
    private var flipX = true
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    // Library classes — may fail to initialize; check with ::isInitialized
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

    // helper flag telling whether library is usable
    private var isLibraryReady = false


        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            binding = ActivityFaceDetectionBinding.inflate(layoutInflater)
            setContentView(binding.root)

            StaticData.base64_face = ""
            binding.previewView.scaleType = PreviewView.ScaleType.FIT_CENTER

            // Optional loading UI
//            binding.loadingView?.visibility = View.VISIBLE

            binding.addBtn.setOnClickListener { showAddFaceDialog() }
            binding.clearBtn.setOnClickListener { clearDatabasePrompt() }

            binding.ivBack.setOnClickListener {
                finish()
            }

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
//                    binding.loadingView?.visibility = View.GONE
                    startCameraIfReady()
                }
            }
        }

        private fun initObjectBox() {
            try {
                ObjectBoxStore.init(applicationContext)
                Log.i("Init", "ObjectBox OK")
            } catch (t: Throwable) {
                Log.w("Init", "ObjectBox failed: ${t.message}")
            }
        }

        private fun initMLComponents() {
            try {
                mediapipeFaceDetector = MediapipeFaceDetector(this)
                Log.i("Init", "Mediapipe OK")
            } catch (t: Throwable) {
                Log.e("Init", "Mediapipe failed", t)
            }

            try {
                faceNet = FaceNet(this)
                Log.i("Init", "FaceNet OK")
            } catch (t: Throwable) {
                Log.e("Init", "FaceNet failed", t)
            }

            try {
                faceSpoofDetector = FaceSpoofDetector(this)
                Log.i("Init", "SpoofDetector OK")
            } catch (t: Throwable) {
                Log.e("Init", "SpoofDetector failed", t)
            }
        }

        private fun initUseCases() {
            try {
                imagesVectorDB = ImagesVectorDB()
                personDB = PersonDB()

                System.loadLibrary("frlib")

                val licenseKey = "fK8dP-2rX9q-V7LsE-4YtQ1"
                if (!LicenseValidator.isLicenseValid(this, licenseKey)) {
                    throw IllegalStateException("Invalid License")
                }

                if (::mediapipeFaceDetector.isInitialized &&
                    ::faceNet.isInitialized &&
                    ::faceSpoofDetector.isInitialized) {

                    imageVectorUseCase = ImageVectorUseCase(
                        mediapipeFaceDetector,
                        faceSpoofDetector,
                        imagesVectorDB,
                        faceNet
                    )
                    personUseCase = PersonUseCase(personDB)
                    isLibraryReady = true
                } else {
                    Log.w("Init", "Some ML components not initialized")
                }

            } catch (t: Throwable) {
                Log.e("Init", "UseCases init failed", t)
            }
        }

        private fun startCameraIfReady() {
            if (isLibraryReady) {
//                switchCamera()
            } else {
                Toast.makeText(this, "Face engine failed to initialize.", Toast.LENGTH_LONG).show()
            }
        }

        private fun setupTimeoutWatcher() {
            countDownTimer = object : CountDownTimer(25_000L, 5000L) {
                override fun onTick(millisUntilFinished: Long) {}

                override fun onFinish() {
                    try {
                        if (!StaticData.base64_face.isNullOrEmpty()) {
                            showMyKadFailedToReadDialog()
                            cameraProvider?.unbindAll()
                        }
                    } catch (_: Throwable) {}
                }
            }
        }


    private fun switchCamera() {
        // Toggle between front/back camera
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            flipX = true
            CameraSelector.LENS_FACING_FRONT
        } else {
            flipX = false
            CameraSelector.LENS_FACING_BACK
        }
        // Rebind camera if already started
        try {
            bindAllCameraUseCases()
        } catch (t: Throwable) {
            Log.e(TAG, "switchCamera failed: ${t.message}", t)
        }
    }

    private fun showMyKadFailedToReadDialog() {
        val dialog = AwesomeInfoDialog(this)
        dialog.setTitle("Recognition Failed")
        dialog.setMessage("Failed to process face. Do you want to retry?")
        dialog.setColoredCircle(com.awesomedialog.blennersilva.awesomedialoglibrary.R.color.dialogErrorBackgroundColor)
        dialog.setDialogIconAndColor(
            com.awesomedialog.blennersilva.awesomedialoglibrary.R.drawable.ic_dialog_error,
            com.awesomedialog.blennersilva.awesomedialoglibrary.R.color.white
        )
        dialog.setCancelable(false)

//        dialog.setPositiveButtonText("Capture")
//        dialog.setPositiveButtonbackgroundColor(com.awesomedialog.blennersilva.awesomedialoglibrary.R.color.dialogErrorBackgroundColor)
//        dialog.setPositiveButtonTextColor(com.awesomedialog.blennersilva.awesomedialoglibrary.R.color.white)
//        dialog.setPositiveButtonClick(object : Closure {
//            override fun exec() {
//                dialog.hide()
//                startActivityForResult(Intent(MediaStore.ACTION_IMAGE_CAPTURE), 10002)
//            }
//        })

        dialog.setNegativeButtonText("Retry")
        dialog.setNegativeButtonbackgroundColor(com.awesomedialog.blennersilva.awesomedialoglibrary.R.color.dialogErrorBackgroundColor)
        dialog.setNegativeButtonTextColor(com.awesomedialog.blennersilva.awesomedialoglibrary.R.color.white)
        dialog.setNegativeButtonClick(object : Closure {
            override fun exec() {
                dialog.hide()
                setupCamera()
            }
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
        try {
            ObjectBoxStore.close() // if your library exposes close, call it
        } catch (_: Throwable) {}
    }

    // permissions
    private val requestCameraPermission: Unit
        get() {
            ActivityCompat.requestPermissions(this, arrayOf(CAMERA_PERMISSION), PERMISSION_CODE)
        }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        for (r in grantResults) {
            if (r == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
                return
            }
        }
        if (requestCode == PERMISSION_CODE) setupCamera()
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun startCamera() {
        if (ContextCompat.checkSelfPermission(this, CAMERA_PERMISSION) == PackageManager.PERMISSION_GRANTED) {
            setupCamera()
        } else {
            requestCameraPermission
        }
    }

    private fun setupCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindAllCameraUseCases()
            } catch (e: ExecutionException) {
                Log.e(TAG, "cameraProviderFuture error", e)
            } catch (e: InterruptedException) {
                Log.e(TAG, "cameraProviderFuture interrupted", e)
            }
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
        val builder = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        try { builder.setTargetRotation(rotation) } catch (_: Throwable) { builder.setTargetRotation(Surface.ROTATION_0) }
        analysisUseCase = builder.build()
        analysisUseCase!!.setAnalyzer(cameraExecutor) { image ->
            if (!pausedForSave) {
                analyzeFrame(image)
            } else {
                image.close()
            }
        }
        try { cameraProvider!!.bindToLifecycle(this, cameraSelector!!, analysisUseCase) } catch (e: Exception) { Log.e(TAG, "bindAnalysis", e) }
    }

    private val rotation: Int get() = binding.previewView.display.rotation

    @SuppressLint("UnsafeOptInUsageError")
    private fun analyzeFrame(image: ImageProxy) {
        try {
            val mediaImage: Image? = image.image
            if (mediaImage == null) {
                image.close()
                return
            }

            // Keep full frame bitmap (used by Add). DO NOT recycle this bitmap.
            bitmapFull = try {
                BitmapUtils.getBitmap(image)
            } catch (t: Throwable) {
                // fallback: convert manually
                toBitmap(mediaImage)
            }

            // Make upright bitmap for library (rotate + mirror as needed)
            val rotationDegrees = image.imageInfo.rotationDegrees
            val fullBitmap = bitmapFull!! //rotateAndMirrorIfNeeded(bitmapFull!!, rotationDegrees, flipX)

//            val fullBitmap =
//                if (flipX || rotationDegrees != 0)
//                    rotateAndMirrorIfNeeded(bitmapFull!!, rotationDegrees, flipX)
//                else
//                    bitmapFull!!

            // If library isn't ready, don't call into it — just update UI and return.
            if (!isLibraryReady) {
                runOnUiThread {
                    binding.tvDetectionText.text = "Library not ready"
                }
                image.close()
                return
            }

            // run recognition on background
            lifecycleScope.launch(Dispatchers.Default) {
                try {
                    // imageVectorUseCase.getNearestPersonName is likely suspend; if not, still safe here
                    val raw = try {
                        imageVectorUseCase.getNearestPersonName(fullBitmap, false)
                    } catch (t: Throwable) {
                        Log.w(TAG, "getNearestPersonName call failed: ${t.message}")
                        null
                    }

                    val resultsList = extractResultsList(raw)
                    withContext(Dispatchers.Main) {
                        if (resultsList.isNotEmpty() ) {
                            if (!StaticData.base64_face.isNullOrEmpty()) {
                                val first = resultsList[0]
                                val personName =
                                    readFieldString(first, "personName") ?: readFieldString(
                                        first,
                                        "name"
                                    ) ?: "unknown"
                                binding.tvDetectionText.text = personName
                                if (personName != "Not recognized" && !activityStarted) {
                                    activityStarted = true
                                    if (StaticData.invitation) {
                                        // upload face image
                                        Common.showToast(
                                            applicationContext,
                                            "Image saved successfully, Proceed to collect card."
                                        )
                                        finish()
                                        startActivity(
                                            Intent(
                                                this@FaceDetectionActivity,
                                                CollectCardActivity::class.java
                                            )
                                        )
                                    } else {
                                        finish()
                                        startActivity(
                                            Intent(
                                                this@FaceDetectionActivity,
                                                VisitUpdateDetailsActivity::class.java
                                            )
                                        )
                                    }
                                }
//                            Toast.makeText(this@FaceDetectionActivity, "Recognized: $personName", Toast.LENGTH_SHORT).show()

                            val bbox = readFieldRect(first, "boundingBox")
                            if (bbox != null) {
                                try {
//                                    val scaleX = binding.previewView.width.toFloat() / fullBitmap.height.toFloat()
//                                    val scaleY = binding.previewView.height.toFloat() / fullBitmap.width.toFloat()

                                    // todo below is suggested by chatgpt
                                    val scaleX =
                                        binding.previewView.width.toFloat() / fullBitmap.width.toFloat()
                                    val scaleY =
                                        binding.previewView.height.toFloat() / fullBitmap.height.toFloat()

                                    binding.graphicOverlay.draw(bbox, scaleX, scaleY, personName)
                                } catch (_: Throwable) {
                                }
                            }
                        }
                            // logic to add face
                            if (StaticData.base64_face.isNullOrEmpty() && resultsList.isNotEmpty()) {
                                if (!faceDetectedOnce) {
                                    faceDetectedOnce = true
                                    if (!addFaceScheduled) {
                                        addFaceScheduled = true
                                        Handler(Looper.getMainLooper()).postDelayed({
                                            if (StaticData.base64_face.isNullOrEmpty()) {
                                                showAddFaceDialog()
                                            }
                                        }, 2000) // 3 seconds
                                    }
                                }
                            }

                        } else {
                            binding.tvDetectionText.text = "No Face Detected!"
                            try {
                                // optional: clear overlay if your GraphicOverlay supports a clear method
                                // binding.graphicOverlay.clear()
                            } catch (_: Throwable) {}
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

    /**
     * Show add-face dialog and save using library usecases (safe / background).
     */
    private fun showAddFaceDialog() {
        pausedForSave = true

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Add Face")

        // Inflate custom layout
        val dialogView = layoutInflater.inflate(com.safeg.R.layout.dialog_add_face, null)
        val nameInput = dialogView.findViewById<EditText>(com.safeg.R.id.etName)
        val imgPreview = dialogView.findViewById<ImageView>(com.safeg.R.id.ivPreview)

        // Set preview image
        val previewBmp = bitmapFull?.copy(Bitmap.Config.ARGB_8888, false)
            ?: getBitmapFromPreview(binding.previewView)

        if (previewBmp != null) {
            imgPreview.setImageBitmap(previewBmp)
        }

        builder.setView(dialogView)
        nameInput.setText(StaticData.request.ic)
        builder.setPositiveButton("ADD") { _, _ ->
            nameInput.setText(StaticData.request.ic?.takeIf { it.isNotBlank() } ?: StaticData.request.passport)
            val name = nameInput.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "Please enter IC number", Toast.LENGTH_SHORT).show()
                pausedForSave = false
                return@setPositiveButton
            }

            if (!isLibraryReady) {
                Toast.makeText(this, "Library not available — cannot save face", Toast.LENGTH_SHORT).show()
                pausedForSave = false
                return@setPositiveButton
            }

            lifecycleScope.launch {
                try {
                    val personId = withContext(Dispatchers.IO) {
                        try {
                            personUseCase.addPerson(name, 1L)
                            1L
                        } catch (t: Throwable) {
                            -1L
                        }
                    }

                    if (previewBmp == null) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@FaceDetectionActivity, "No image available", Toast.LENGTH_SHORT).show()
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
                        withContext(Dispatchers.Default) {
                            imageVectorUseCase.addImage(personId, name, uri)
                        }
                        true
                    } catch (t: Throwable) {
                        false
                    }

                    withContext(Dispatchers.Main) {
                        if (addOk) {
                            activityStarted = true
                            val base64Image = bitmapToBase64(previewBmp)
                            StaticData.base64_face = base64Image

                            if (StaticData.invitation) {
                                // upload face image
                                uploadVendorPassImage()
                            } else {
                            Toast.makeText(
                                this@FaceDetectionActivity,
                                "Face saved for $name",
                                Toast.LENGTH_SHORT
                            ).show()
                            finish()
                            startActivity(
                                Intent(
                                    this@FaceDetectionActivity,
                                    VisitUpdateDetailsActivity::class.java
                                )
                            )
                        }
                        }
                        else Toast.makeText(this@FaceDetectionActivity, "Failed to save face", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    pausedForSave = false
                }
            }
        }

        builder.setNegativeButton("Cancel") { d, _ ->
            pausedForSave = false
            d.cancel()
        }

        builder.show()
    }

    /**
     * Attempt to clear DB via PersonUseCase; try multiple strategies safely.
     */
    private fun clearDatabasePrompt() {
        val dlg = AlertDialog.Builder(this)
            .setTitle("Clear faces")
            .setMessage("This will clear all saved faces in the library DB. Continue?")
            .setPositiveButton("Yes") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        try {
                            val cls = personUseCase.javaClass
                            val m: Method = cls.getMethod("deleteAll")
                            m.invoke(personUseCase)
                        } catch (nsme: NoSuchMethodException) {
                            try {
                                val cls = personUseCase.javaClass
                                val getAll: Method = cls.getMethod("getAll")
                                val list = getAll.invoke(personUseCase)
                                if (list is Iterable<*>) {
                                    val delete: Method? = try {
                                        cls.getMethod("delete", Long::class.javaPrimitiveType)
                                    } catch (e: NoSuchMethodException) {
                                        null
                                    }
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
                        withContext(Dispatchers.Main) { Toast.makeText(this@FaceDetectionActivity, "Database cleared (attempted)", Toast.LENGTH_SHORT).show() }
                    } catch (t: Throwable) {
                        Log.e(TAG, "clear DB error", t)
                        withContext(Dispatchers.Main) { Toast.makeText(this@FaceDetectionActivity, "Failed to clear DB", Toast.LENGTH_SHORT).show() }
                    }
                }
            }
            .setNegativeButton("No", null)
            .create()
        dlg.show()
    }

    // ------------------ Utilities ------------------

    // Local rotate+mirror helper (safe, avoids missing BitmapUtils methods)
    private fun rotateAndMirrorIfNeeded(src: Bitmap, rotationDegrees: Int, flipX: Boolean): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(rotationDegrees.toFloat())
        matrix.postScale(if (flipX) -1f else 1f, 1f)
        // DO NOT recycle src here — camera pipeline owns it
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    // Best-effort: get bitmap from PreviewView (fallback; may be null on some devices)
    private fun getBitmapFromPreview(previewView: PreviewView): Bitmap? {
        // Try to use BitmapUtils helper if available
        try {
            val cls = BitmapUtils::class.java
            val m = cls.getMethod("getBitmapFromPreview", PreviewView::class.java)
            val bmp = m.invoke(null, previewView)
            if (bmp is Bitmap) return bmp
        } catch (_: Throwable) {}

        // Last resort: try drawing the view to a bitmap
        return try {
            val width = previewView.width.coerceAtLeast(1)
            val height = previewView.height.coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val c = Canvas(bitmap)
            previewView.draw(c)
            bitmap
        } catch (t: Throwable) {
            Log.w(TAG, "getBitmapFromPreview failed: ${t.message}")
            null
        }
    }

    // Convert Image (YUV) -> Bitmap (copied from your original code style)
    private fun toBitmap(image: Image): Bitmap {
        val nv21 = YUV_420_888toNV21(image)
        val yuvImage = android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 75, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
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
        if (rowStride == width) {
            yBuffer[nv21, 0, ySize]
            pos += ySize
        } else {
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
            try {
                val savePixel = vBuffer[1]
                // safe no-op test -- avoid ReadOnlyBufferException from earlier code
            } catch (_: ReadOnlyBufferException) {
            }
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

    // Extract results list from multiple possible return shapes
    private fun extractResultsList(raw: Any?): List<Any> {
        if (raw == null) return emptyList()
        try {
            if (raw is Collection<*>) {
                @Suppress("UNCHECKED_CAST")
                return raw.filterNotNull() as List<Any>
            }
            val cls = raw.javaClass
            val pairGetSecond = try { cls.getMethod("getSecond") } catch (_: Throwable) { null }
            if (pairGetSecond != null) {
                val second = pairGetSecond.invoke(raw)
                if (second is Collection<*>) return second.filterNotNull() as List<Any>
            }
            try {
                val f: Field = cls.getDeclaredField("results")
                f.isAccessible = true
                val v = f.get(raw)
                if (v is Collection<*>) return v.filterNotNull() as List<Any>
            } catch (_: Throwable) {}
            try {
                val f: Field = cls.getDeclaredField("matches")
                f.isAccessible = true
                val v = f.get(raw)
                if (v is Collection<*>) return v.filterNotNull() as List<Any>
            } catch (_: Throwable) {}
        } catch (t: Throwable) {
            Log.w(TAG, "extractResultsList: ${t.message}")
        }
        return emptyList()
    }

    // Try to read a String field by name
    private fun readFieldString(obj: Any?, name: String): String? {
        if (obj == null) return null
        try {
            val f = obj.javaClass.getDeclaredField(name)
            f.isAccessible = true
            val v = f.get(obj)
            return v?.toString()
        } catch (_: Throwable) {
            try {
                val m = obj.javaClass.getMethod("get" + name.replaceFirstChar { it.uppercase() })
                val v = m.invoke(obj)
                return v?.toString()
            } catch (_: Throwable) {}
        }
        return null
    }

    // Try to read a Rect field by name
    private fun readFieldRect(obj: Any?, name: String): Rect? {
        if (obj == null) return null
        try {
            val f = obj.javaClass.getDeclaredField(name)
            f.isAccessible = true
            val v = f.get(obj)
            if (v is Rect) return v
            val left = try { obj.javaClass.getDeclaredField("left").getInt(obj) } catch (_: Throwable) { null }
            val top = try { obj.javaClass.getDeclaredField("top").getInt(obj) } catch (_: Throwable) { null }
            val right = try { obj.javaClass.getDeclaredField("right").getInt(obj) } catch (_: Throwable) { null }
            val bottom = try { obj.javaClass.getDeclaredField("bottom").getInt(obj) } catch (_: Throwable) { null }
            if (left is Int && top is Int && right is Int && bottom is Int) return Rect(left, top, right, bottom)
        } catch (_: Throwable) {}
        return null
    }

    private var faceDetectedOnce = false
    private var addFaceScheduled = false


    private fun bitmapToBase64(bitmap: Bitmap): String {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
        val bytes = baos.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun uploadVendorPassImage() {
        var photo = "data:image/png;base64,"+StaticData.base64_face

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
                val okHttpClient = builder
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .build()
                AndroidNetworking.initialize(applicationContext, okHttpClient)
            } catch (thorowable : Throwable){
                Common.showToast(
                    applicationContext,
                    thorowable.message
                )
            }
            runOnUiThread(Runnable {
                AndroidNetworking.post(Constants.uploadVendorPassPhotoMobile)
                    .setTag(Constants.uploadVendorPassPhotoMobile)
                    .setPriority(Priority.HIGH)
                    .addJSONObjectBody(JSONObject().put("staffNo", StaticData.request.ic).put("photo", photo))
                    .build()
                    .getAsJSONObject(object : JSONObjectRequestListener {
                        override fun onResponse(response: JSONObject?) {
                            if(response!!.getString("status").compareTo("OK") == 0){
                                println("tttttttttttttttt")
                                Common.showToast(
                                    applicationContext,
                                    "Image saved successfully, Proceed to collect card."
                                )

                                finish()
                                startActivity(
                                    Intent(
                                        this@FaceDetectionActivity,
                                        CollectCardActivity::class.java
                                    )
                                )
                            } else {
                                Common.showToast(
                                    applicationContext,
                                    "Error saving image: " + response!!.getString("status")
                                )
                            }
                        }

                        override fun onError(anError: ANError?) {
                            Log.e("Init", "Initialization failed: ${anError?.errorBody}", anError)

                            Common.showToast(
                                applicationContext,
                                "Error Code : " + anError?.errorCode + ", Details : " + anError?.errorDetail
                            )
                        }
                    })
            })
        }).start()
    }


}
