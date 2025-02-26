package com.example.bamia.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.PorterDuff
import android.graphics.Rect
import android.media.Image
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.example.bamia.R
import com.example.bamia.analysis.ExpressionAnalyzer
import com.example.bamia.gallery.GalleryActivity
import com.example.bamia.gallery.GalleryManager
import com.example.bamia.managers.LogManager
import com.example.bamia.managers.NetworkManager
import com.example.bamia.services.MjpegServerService
import com.example.bamia.streaming.FrameBuffer
import com.example.bamia.streamingcontrol.StreamingController
import com.example.bamia.ui.ExpressionInfoHolder
import com.example.bamia.ui.FaceResult
import com.example.bamia.ui.OverlayView
import com.example.bamia.ui.SleepInfoHolder
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import com.google.common.util.concurrent.ListenableFuture

/**
 * CameraModeActivity
 *
 * 카메라 프리뷰, 얼굴 감지 및 표정 분석, 스트리밍 제어, 절전 모드, 그리고 갤러리로 이동하는 기능을 제공합니다.
 */
class CameraModeActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlayView: OverlayView
    private lateinit var tvNetworkInfo: TextView
    private lateinit var btnSwitchCamera: ImageButton
    private lateinit var btnCapture: ImageButton
    private lateinit var btnSettings: ImageButton
    private lateinit var btnLogs: ImageButton
    private lateinit var btnStreamControl: ImageButton
    private lateinit var btnSleepMode: ImageButton
    private lateinit var btnGallery: ImageButton  // 갤러리 이동 버튼

    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var cameraExecutor = Executors.newSingleThreadExecutor()

    private var isStreamingActive = false
    private var isSleepModeActive = false

    private var sleepStartTime: Long? = null
    private var isSleeping: Boolean = false

    private val faceDetectorOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .build()
    private val faceDetector = FaceDetection.getClient(faceDetectorOptions)

    private lateinit var expressionAnalyzer: ExpressionAnalyzer
    private lateinit var logManager: LogManager
    private lateinit var networkManager: NetworkManager

    private lateinit var sleepOverlay: View

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        Log.d("CameraModeActivity", "Permission callback: $permissions")
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            finish()
        }
    }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val FILENAME_FORMAT = "yyyyMMdd_HHmmss"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_mode)

        previewView = findViewById(R.id.previewView)
        overlayView = findViewById(R.id.overlayView)
        tvNetworkInfo = findViewById(R.id.tvNetworkInfo)
        btnSwitchCamera = findViewById(R.id.btnSwitchCamera)
        btnCapture = findViewById(R.id.btnCapture)
        btnSettings = findViewById(R.id.btnSettings)
        btnLogs = findViewById(R.id.btnLogs)
        btnStreamControl = findViewById(R.id.btnStreamControl)
        btnSleepMode = findViewById(R.id.btnSleepMode)
        btnGallery = findViewById(R.id.btnGallery) // 갤러리 버튼 초기화

        networkManager = NetworkManager.getInstance(this)
        logManager = LogManager.getInstance(applicationContext)
        expressionAnalyzer = ExpressionAnalyzer(applicationContext)

        updateNetworkInfo()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions.launch(REQUIRED_PERMISSIONS)
        }

        val sleepOverlayLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
            // 터치 시 절전 모드 해제
            setOnTouchListener { _, _ ->
                exitSleepMode()
                true
            }
        }

        // 안내 문구 TextView 생성
        val instructionText = TextView(this).apply {
            text = "절전모드를 해제하려면 클릭하세요"
            setTextColor(Color.DKGRAY)  // 아주 어두운 회색
            textSize = 24f  // 적절한 크기로 설정 (필요에 따라 조정)
            // 중앙 정렬
            setPadding(0, 0, 0, 0)
            gravity = android.view.Gravity.CENTER
        }

        // FrameLayout에 TextView 추가 (전체 영역 중앙에 배치)
        sleepOverlayLayout.addView(
            instructionText,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = android.view.Gravity.CENTER
            }
        )

        // 최상위 컨테이너에 절전 오버레이 추가
        (findViewById<View>(android.R.id.content) as? ViewGroup)?.addView(
            sleepOverlayLayout, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        sleepOverlay = sleepOverlayLayout

        btnSwitchCamera.setOnClickListener { toggleCamera() }
        btnCapture.setOnClickListener { capturePhoto() }
        btnSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        btnLogs.setOnClickListener { startActivity(Intent(this, LogsActivity::class.java)) }
        btnStreamControl.setOnClickListener { toggleStreaming() }
        btnSleepMode.setOnClickListener { toggleSleepMode() }
        btnGallery.setOnClickListener { startActivity(Intent(this, GalleryActivity::class.java)) }

        startService(Intent(this, MjpegServerService::class.java))
        Log.d("CameraModeActivity", "MJPEG Server Service started.")
    }

    override fun onResume() {
        super.onResume()
        updateNetworkInfo()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        faceDetector.close()
        expressionAnalyzer.close()
        stopService(Intent(this, MjpegServerService::class.java))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera() {
        Log.d("CameraModeActivity", "startCamera called")
        val cameraProviderFuture: ListenableFuture<ProcessCameraProvider> =
            ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder().build()
            imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build().also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageProxy(imageProxy)
                    }
                }
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture,
                    imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e("CameraModeActivity", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        val bitmap = imageProxyToBitmap(imageProxy)
        if (bitmap != null && isStreamingActive) {
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
            FrameBuffer.currentFrame = baos.toByteArray()
            Log.d("CameraModeActivity", "FrameBuffer updated, size: ${FrameBuffer.currentFrame?.size ?: 0} bytes")
        }
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            faceDetector.process(inputImage)
                .addOnSuccessListener { faces ->
                    val faceResults = mutableListOf<FaceResult>()
                    val bitmapInputImage = InputImage.fromBitmap(bitmap ?: return@addOnSuccessListener, 0)
                    val overlayWidth = overlayView.width
                    val overlayHeight = overlayView.height
                    val imageWidth = bitmap?.width ?: 1
                    val imageHeight = bitmap?.height ?: 1
                    val scaleX = overlayWidth.toFloat() / imageWidth
                    val scaleY = overlayHeight.toFloat() / imageHeight

                    // 테스트용 임계값: 눈 감음 3초
                    val sleepDetectionThreshold = 3000L  // 3초
                    val currentTime = System.currentTimeMillis()

                    // 눈 상태 및 얼굴 검출 로직
                    if (faces.isNotEmpty()) {
                        // 얼굴이 검출됨 → 첫 번째 얼굴 기준 처리
                        val face = faces[0]
                        val leftEyeProb = face.leftEyeOpenProbability
                        val rightEyeProb = face.rightEyeOpenProbability

                        if (leftEyeProb != null && rightEyeProb != null) {
                            val eyesClosed = leftEyeProb < 0.5 && rightEyeProb < 0.5
                            if (eyesClosed) {
                                // 눈이 감겼을 경우
                                if (!isSleeping) {
                                    if (sleepStartTime == null) {
                                        sleepStartTime = currentTime
                                        Log.d("CameraModeActivity", "Eyes closed detected, starting sleep timer at ${getCurrentTimeStamp()}")
                                    } else {
                                        val elapsed = currentTime - sleepStartTime!!
                                        if (elapsed >= sleepDetectionThreshold) {
                                            isSleeping = true
                                            isSleeping = true
                                            // 아기 이름 가져오기
                                            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
                                            val babyName = prefs.getString("camera_name", "DefaultName") ?: "DefaultName"
                                            // 현재 시간을 "HH시 mm분" 형식으로 변환
                                            val detectedTime = sleepStartTime!! + sleepDetectionThreshold
                                            val formattedTime = SimpleDateFormat("HH시 mm분", Locale.getDefault()).format(Date(detectedTime))
                                            val message = "$babyName 이 $formattedTime 에 수면을 시작했습니다"
                                            SleepInfoHolder.sleepMessage = message
                                            Log.d("CameraModeActivity", "Sleep detected: 눈 감음 - ${sleepDetectionThreshold} ms 지속 at ${getCurrentTimeStamp()}")
                                        }
                                    }
                                }
                            } else { // 눈이 뜨면
                                if (isSleeping) {
                                    val sleepDuration = currentTime - (sleepStartTime ?: currentTime)
                                    val formattedTime = SimpleDateFormat("HH시 mm분", Locale.getDefault()).format(Date())
                                    val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
                                    val babyName = prefs.getString("camera_name", "DefaultName") ?: "DefaultName"
                                    SleepInfoHolder.sleepMessage = "$babyName 이 $formattedTime 에 기상했습니다"
                                    Log.d("CameraModeActivity", "Awake detected: 눈 뜸 - 기상 메시지 설정 at ${getCurrentTimeStamp()}")
                                    isSleeping = false
                                    sleepStartTime = null
                                } else {
                                    sleepStartTime = null
                                }
                            }
                        }
                    } else {
                        // 얼굴 미검출 시: 만약 이전에 수면 상태였다면 알림
                        ExpressionInfoHolder.currentExpression = "unknown"
                        if (isSleeping) {
                            val formattedTime = SimpleDateFormat("HH시 mm분", Locale.getDefault()).format(Date())
                            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
                            val babyName = prefs.getString("camera_name", "DefaultName") ?: "DefaultName"
                            SleepInfoHolder.sleepMessage = "수면 중 $babyName 의 얼굴이 감지되지 않습니다. $formattedTime"
                            Log.d("CameraModeActivity", "face not detected while sleeping:  ${getCurrentTimeStamp()}")
                            isSleeping = false
                            sleepStartTime = null
                        }
                    }

                    // 기존의 얼굴별 표정 및 오버레이 처리
                    for (face in faces) {
                        val originalBox = face.boundingBox
                        val transformedBox = if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
                            Rect(
                                (overlayWidth - (originalBox.right * scaleX)).toInt(),
                                (originalBox.top * scaleY).toInt(),
                                (overlayWidth - (originalBox.left * scaleX)).toInt(),
                                (originalBox.bottom * scaleY).toInt()
                            )
                        } else {
                            Rect(
                                (originalBox.left * scaleX).toInt(),
                                (originalBox.top * scaleY).toInt(),
                                (originalBox.right * scaleX).toInt(),
                                (originalBox.bottom * scaleY).toInt()
                            )
                        }
                        val expression = expressionAnalyzer.analyzeExpression(bitmapInputImage, originalBox)
                        // 얼굴이 검출되었으나 표정이 'unknown'이면 업데이트하지 않음
                        if (expression.lowercase(Locale.getDefault()) != "unknown" && expression.isNotEmpty()) {
                            ExpressionInfoHolder.currentExpression = expression
                        }
                        val leftEyeProb = face.leftEyeOpenProbability
                        val rightEyeProb = face.rightEyeOpenProbability
                        val smilingProb = face.smilingProbability

                        val faceResult = FaceResult(
                            boundingBox = transformedBox,
                            predictedExpression = expression,
                            leftEyeProbability = leftEyeProb,
                            rightEyeProbability = rightEyeProb,
                            smilingProbability = smilingProb
                        )
                        faceResults.add(faceResult)

                        // 자동 캡쳐: 얼굴 표정이 새롭게 감지된 경우
                        if (!logManager.isExpressionLoggedToday(expression)) {
                            logManager.logEvent("Expression detected: $expression at ${getCurrentTimeStamp()}")
                            capturePhotoFromBitmap(bitmap, expression)
                        }
                    }
                    runOnUiThread { overlayView.updateResults(faceResults) }
                }
                .addOnCompleteListener { imageProxy.close() }
        } else {
            imageProxy.close()
        }
    }

    @OptIn(ExperimentalGetImage::class)
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val mediaImage = imageProxy.image ?: return null
        return yuv420ToBitmap(mediaImage, imageProxy.width, imageProxy.height, imageProxy.planes)
            ?.let { bmp ->
                val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                if (rotationDegrees != 0) {
                    val matrix = Matrix()
                    matrix.postRotate(rotationDegrees.toFloat())
                    Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
                } else {
                    bmp
                }
            }
    }

    private fun yuv420ToBitmap(image: Image, width: Int, height: Int, planes: Array<ImageProxy.PlaneProxy>): Bitmap? {
        try {
            val yBuffer = planes[0].buffer
            val uBuffer = planes[1].buffer
            val vBuffer = planes[2].buffer

            val yRowStride = planes[0].rowStride
            val uvRowStride = planes[1].rowStride
            val uvPixelStride = planes[1].pixelStride

            val bitmap = Bitmap.createBitmap(width, height, Config.ARGB_8888)
            val pixels = IntArray(width * height)

            for (j in 0 until height) {
                for (i in 0 until width) {
                    val yIndex = j * yRowStride + i
                    val y = 0xff and yBuffer.get(yIndex).toInt()
                    val uvIndex = (j / 2) * uvRowStride + (i / 2) * uvPixelStride
                    val u = 0xff and uBuffer.get(uvIndex).toInt()
                    val v = 0xff and vBuffer.get(uvIndex).toInt()

                    var r = (y + 1.370705f * (v - 128)).toInt()
                    var g = (y - 0.337633f * (u - 128) - 0.698001f * (v - 128)).toInt()
                    var b = (y + 1.732446f * (u - 128)).toInt()
                    r = r.coerceIn(0, 255)
                    g = g.coerceIn(0, 255)
                    b = b.coerceIn(0, 255)
                    pixels[j * width + i] = -0x1000000 or (r shl 16) or (g shl 8) or b
                }
            }
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            return bitmap
        } catch (e: Exception) {
            Log.e("CameraModeActivity", "Error in yuv420ToBitmap conversion", e)
            return null
        }
    }

    // Manual capture: 사용자가 캡쳐 버튼을 누른 경우 -> captureReason: "camera_request"
    private fun capturePhoto() {
        capturePhotoFromBitmap(previewView.bitmap, "camera_request")
    }

    // 캡쳐된 Bitmap을 GalleryManager를 통해 저장하며, captureReason을 전달
    private fun capturePhotoFromBitmap(bitmap: Bitmap?, captureExpression: String) {
        if (bitmap != null) {
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            val babyName = prefs.getString("camera_name", "DefaultName") ?: "DefaultName"
            GalleryManager.saveCapturedImage(this, bitmap, babyName, captureExpression) { filePath ->
                logManager.logEvent("Photo captured ($captureExpression): $filePath at ${getCurrentTimeStamp()}")
            }
        } else {
            Log.e("CameraModeActivity", "capturePhotoFromBitmap: Bitmap is null")
        }
    }

    private fun toggleCamera() {
        cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA)
            CameraSelector.DEFAULT_FRONT_CAMERA
        else
            CameraSelector.DEFAULT_BACK_CAMERA
        startCamera()
    }

    private fun toggleStreaming() {
        isStreamingActive = !isStreamingActive
        updateStreamButtonUI()
    }

    private fun updateStreamButtonUI() {
        runOnUiThread {
            if (isStreamingActive) {
                btnStreamControl.setImageResource(R.drawable.stream_on)
                btnStreamControl.setColorFilter(Color.RED, PorterDuff.Mode.SRC_IN)
            } else {
                btnStreamControl.setImageResource(R.drawable.stream_off)
                btnStreamControl.clearColorFilter()
            }
        }
    }

    private fun enterSleepMode() {
        isSleepModeActive = true
        // 카메라 프리뷰와 오버레이 숨김
        previewView.visibility = View.INVISIBLE
        overlayView.visibility = View.INVISIBLE
        // 절전 오버레이를 전체 화면에 표시
        sleepOverlay.visibility = View.VISIBLE
        // StreamingController 또는 관련 로직에 절전 모드 활성 상태를 알림
        StreamingController.setSleepMode(this, true)
        Log.d("CameraModeActivity", "절전 모드 활성화")
    }

    private fun exitSleepMode() {
        isSleepModeActive = false
        // 카메라 프리뷰와 오버레이 다시 표시
        previewView.visibility = View.VISIBLE
        overlayView.visibility = View.VISIBLE
        // 절전 오버레이 숨김
        sleepOverlay.visibility = View.GONE
        StreamingController.setSleepMode(this, false)
        Log.d("CameraModeActivity", "절전 모드 해제")
    }

    // 절전 모드 버튼 클릭 시 호출하는 함수
    private fun toggleSleepMode() {
        if (isSleepModeActive) {
            exitSleepMode()
        } else {
            enterSleepMode()
        }
    }
    private fun updateNetworkInfo() {
        tvNetworkInfo.text = "IP: ${networkManager.ipAddress} | PIN: ${networkManager.pin}"
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, "BabyCam").apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists()) mediaDir else filesDir
    }

    private fun getCurrentTimeStamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    }

    private fun formatDuration(durationMillis: Long): String {
        val totalSeconds = durationMillis / 1000
        return when {
            totalSeconds < 60 -> "$totalSeconds 분"
            totalSeconds < 3600 -> {
                val minutes = totalSeconds / 60
                val seconds = totalSeconds % 60
                "$minutes 분 $seconds 초"
            }
            else -> {
                val hours = totalSeconds / 3600
                val minutes = (totalSeconds % 3600) / 60
                val seconds = totalSeconds % 60
                "$hours 시간 $minutes 분 $seconds 초"
            }
        }
    }

}
