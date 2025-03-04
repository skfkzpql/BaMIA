package com.example.bamia.activities

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
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
import com.example.bamia.ui.ExpressionDisplayConfig
import com.example.bamia.ui.ExpressionInfoHolder
import com.example.bamia.ui.FaceResult
import com.example.bamia.ui.OverlayView
import com.example.bamia.ui.SleepInfoHolder
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

@Suppress("DEPRECATION")
class CameraModeActivity : AppCompatActivity() {

    // 상단 영역
    private lateinit var tvNetworkInfo: TextView
    private lateinit var btnKebab: ImageButton

    // Preview 및 오버레이 영역
    private lateinit var previewView: PreviewView
    private lateinit var overlayView: OverlayView

    // 하단 버튼들 (수정된 단일 바)
    private lateinit var btnGallery: ImageButton
    private lateinit var btnCapture: ImageButton
    private lateinit var btnSwitchCamera: ImageButton

    // Camera 및 스트리밍 관련
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var isStreamingActive = true

    private val expressionHistory = mutableListOf<String>()
    private var lastEvaluationTime = System.currentTimeMillis()


    private var capturedExpressionsToday = mutableSetOf<String>()
    private var currentCaptureDate: String = getCurrentDate()

    private var lastStableExpression: String? = null
    private var stableStartTime: Long = System.currentTimeMillis()

    // 절전 모드 관련
    private var isSleepModeActive = false
    private var sleepOverlay: FrameLayout? = null

    // ML Kit 얼굴 감지 및 표정 분석
    private val faceDetectorOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .build()
    private val faceDetector = FaceDetection.getClient(faceDetectorOptions)
    private lateinit var expressionAnalyzer: ExpressionAnalyzer
    private lateinit var logManager: LogManager
    private lateinit var networkManager: NetworkManager

    // 제스처 감지: 더블 탭과 스와이프(플링)
    private val cameraGestureDetector by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 100
            private val SWIPE_VELOCITY_THRESHOLD = 100
            override fun onDoubleTap(e: MotionEvent): Boolean {
                toggleSleepMode()
                return true
            }
            override fun onFling(
                e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float
            ): Boolean {
                if (e1 == null || e2 == null) return false
                val deltaY = e2.y - e1.y
                if (Math.abs(deltaY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                    toggleCamera()
                    return true
                }
                return false
            }
        })
    }

    // 권한 요청 런처
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            startCamera()
        } else {
            finish()
        }
    }

    private val expressionPollInterval = 1000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_mode)

        // XML 요소 초기화
        previewView = findViewById(R.id.previewView)
        overlayView = findViewById(R.id.overlayView)
        tvNetworkInfo = findViewById(R.id.tvNetworkInfo)
        btnKebab = findViewById(R.id.btnKebab)
        btnGallery = findViewById(R.id.btnGallery)
        btnCapture = findViewById(R.id.btnCapture)
        btnSwitchCamera = findViewById(R.id.btnSwitchCamera)

        // Manager, Analyzer 초기화
        networkManager = NetworkManager.getInstance(this)
        logManager = LogManager.getInstance(applicationContext)
        expressionAnalyzer = ExpressionAnalyzer(applicationContext)

        updateIpInfo()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionsLauncher.launch(REQUIRED_PERMISSIONS)
        }

        // 하단 버튼 클릭 리스너 설정
        btnGallery.setOnClickListener { startActivity(Intent(this, GalleryActivity::class.java)) }
        btnCapture.setOnClickListener { capturePhoto() }
        btnSwitchCamera.setOnClickListener { toggleCamera() }

        // 케밥 메뉴 설정
        val kebabPopup = PopupMenu(this, btnKebab)
        with(kebabPopup.menu) {
            add(0, 1, 0, "얼굴 인식 표시").setCheckable(true).setChecked(ExpressionDisplayConfig.showFaceBox)
            add(0, 2, 1, "표정 분석").setCheckable(true).setChecked(ExpressionDisplayConfig.showExpression)
            add(0, 3, 2, "눈 뜸 확률").setCheckable(true).setChecked(ExpressionDisplayConfig.showEyeProbability)
            add(0, 4, 3, "웃음 확률").setCheckable(true).setChecked(ExpressionDisplayConfig.showSmileProbability)
            add(0, 5, 4, "모드 변경")
            add(0, 6, 5, "절전 모드")
            add(0, 7, 6, "로그 확인")
            add(0, 8, 7, "설정")
            add(0, 9, 8, "정보")
        }
        kebabPopup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    ExpressionDisplayConfig.showFaceBox = !item.isChecked
                    item.isChecked = ExpressionDisplayConfig.showFaceBox
                    true
                }
                2 -> {
                    ExpressionDisplayConfig.showExpression = !item.isChecked
                    item.isChecked = ExpressionDisplayConfig.showExpression
                    true
                }
                3 -> {
                    ExpressionDisplayConfig.showEyeProbability = !item.isChecked
                    item.isChecked = ExpressionDisplayConfig.showEyeProbability
                    true
                }
                4 -> {
                    ExpressionDisplayConfig.showSmileProbability = !item.isChecked
                    item.isChecked = ExpressionDisplayConfig.showSmileProbability
                    true
                }
                5 -> { startActivity(Intent(this, LauncherActivity::class.java)); finish(); true }
                6 -> { toggleSleepMode(); true }
                7 -> { startActivity(Intent(this, LogsActivity::class.java)); true }
                8 -> { startActivity(Intent(this, SettingsActivity::class.java).apply { putExtra("caller", "camera") }); true }
                9 -> { /* 정보 화면 구현 예정 */ true }
                else -> false
            }
        }
        btnKebab.setOnClickListener { kebabPopup.show() }

        // 절전 모드 오버레이 설정
        setupSleepOverlay()

        // MJPEG 서버 서비스 시작
        startService(Intent(this, MjpegServerService::class.java))

        // 표정 정보 폴링 시작
        startExpressionPolling(networkManager.ipAddress, networkManager.pin)
    }

    private fun updateIpInfo() {
        tvNetworkInfo.text = "IP: ${networkManager.ipAddress} | PIN: ${networkManager.pin} | 이름: ${getBabyName()}"
    }

    private fun getBabyName(): String {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return prefs.getString("camera_name", "BABY") ?: "BABY"
    }

    private fun startCamera() {
        Log.d("CameraModeActivity", "startCamera called")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
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
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, imageAnalyzer)
            } catch (exc: Exception) {
                Log.e("CameraModeActivity", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        val bitmap = ImageUtil.imageProxyToBitmap(imageProxy)
        if (bitmap != null && isStreamingActive) {
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
            FrameBuffer.currentFrame = baos.toByteArray()
            Log.d("CameraModeActivity", "FrameBuffer updated, size: ${FrameBuffer.currentFrame?.size ?: 0}")
        }
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            faceDetector.process(inputImage)
                .addOnSuccessListener { faces ->
                    val faceResults = mutableListOf<FaceResult>()
                    if (faces.isNotEmpty()) {
                        val face = faces[0]
                        // 모델로부터 표정 결과(예: "happiness", "sadness", "neutral", "surprise", "fear", "disgust", "anger")를 받음.
                        val currentFrameExpression = expressionAnalyzer.analyzeExpression(
                            InputImage.fromBitmap(bitmap ?: return@addOnSuccessListener, 0),
                            face.boundingBox
                        )
                        // 즉각적인 오버레이에는 현재 결과를 표시
                        ExpressionInfoHolder.currentExpression = currentFrameExpression

                        val currentTime = System.currentTimeMillis()
                        // 만약 이전과 표정이 달라졌다면 타이머를 초기화
                        if (lastStableExpression == null || lastStableExpression != currentFrameExpression) {
                            lastStableExpression = currentFrameExpression
                            stableStartTime = currentTime
                        } else {
                            // 같은 표정이 연속하여 나타난 경우
                            if (currentTime - stableStartTime >= 1000L) { // 1초 이상 유지되면
                                // 날짜가 바뀌었으면 캡쳐된 표정 set 초기화
                                val today = getCurrentDate()
                                if (today != currentCaptureDate) {
                                    capturedExpressionsToday.clear()
                                    currentCaptureDate = today
                                }
                                // 해당 표정이 "face not detected"가 아니고, 아직 캡쳐되지 않았다면
                                if (currentFrameExpression != "face not detected" &&
                                    !capturedExpressionsToday.contains(currentFrameExpression)) {
                                    autoCapturePhoto(currentFrameExpression)
                                    capturedExpressionsToday.add(currentFrameExpression)
                                }
                                // 타이머 재설정 (1초마다 자동 캡쳐 조건을 체크)
                                stableStartTime = currentTime
                            }
                        }

                        // 얼굴 영역 좌표 변환
                        val overlayWidth = overlayView.width
                        val overlayHeight = overlayView.height
                        val imageWidth = bitmap?.width ?: 1
                        val imageHeight = bitmap?.height ?: 1
                        val scaleX = overlayWidth.toFloat() / imageWidth
                        val scaleY = overlayHeight.toFloat() / imageHeight
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
                        val leftEyeProb = face.leftEyeOpenProbability
                        val rightEyeProb = face.rightEyeOpenProbability
                        val smilingProb = face.smilingProbability
                        val faceResult = FaceResult(
                            boundingBox = transformedBox,
                            predictedExpression = currentFrameExpression, // 즉각 결과
                            leftEyeProbability = leftEyeProb,
                            rightEyeProbability = rightEyeProb,
                            smilingProbability = smilingProb
                        )
                        faceResults.add(faceResult)
                    }
                    runOnUiThread { overlayView.updateResults(faceResults) }
                }
                .addOnCompleteListener { imageProxy.close() }
        } else {
            imageProxy.close()
        }
    }

    /**
     * decideExpression()는 얼굴의 smilingProbability 값을 기준으로 간단하게 표정을 결정합니다.
     * 필요에 따라 이 함수를 확장하여 더 정밀한 표정 판단 로직을 구현할 수 있습니다.
     */
    private fun decideExpression(face: Face, rawExpression: String): String {
        return when {
            face.smilingProbability != null && face.smilingProbability > 0.8f -> "happy"
            face.smilingProbability != null && face.smilingProbability < 0.3f -> "cry"
            else -> "face not detected"
        }
    }

    private fun autoCapturePhoto(expression: String) {
        runOnUiThread {
            val bitmap: Bitmap = previewView.bitmap ?: return@runOnUiThread
            val babyName = getBabyName()
            GalleryManager.saveCapturedImage(this, bitmap, babyName, expression) { filePath ->
                logManager.logEvent("Auto-captured ($expression): $filePath at ${getCurrentTimeStamp()}")
            }
        }
    }

    private fun getCurrentTimeStamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    }

    private fun getCurrentDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    @OptIn(ExperimentalGetImage::class)
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        event?.let { cameraGestureDetector.onTouchEvent(it) }
        return super.onTouchEvent(event)
    }

    // 사진 캡쳐
    private fun capturePhoto() {
        val bitmap: Bitmap = previewView.bitmap ?: return
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val babyName = prefs.getString("camera_name", "BABY") ?: "BABY"
        GalleryManager.saveCapturedImage(this, bitmap, babyName, "camera_request") { filePath ->
            logManager.logEvent("Photo captured (camera_request): $filePath at ${getCurrentTimeStamp()}")
        }
    }

    private fun toggleCamera() {
        cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA)
            CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        startCamera()
    }

    private fun startExpressionPolling(ip: String, pin: String) {
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                fetchExpression(ip, pin) { expression ->
                    // 별도 TextView 대신 오버레이에 그리도록 함
                    // (OverlayView에서 설정 옵션에 따라 텍스트가 그려집니다.)
                }
                handler.postDelayed(this, expressionPollInterval)
            }
        }
        handler.post(runnable)
    }

    private fun fetchExpression(ip: String, pin: String, callback: (String) -> Unit) {
        Thread {
            try {
                val url = java.net.URL("http://$ip:8080/expression?pin=$pin")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                val response = connection.inputStream.bufferedReader().readText().trim()
                val displayText = if (response.equals("unknown", ignoreCase = true) || response.isEmpty())
                    "face not detected" else response
                callback(displayText)
            } catch (e: Exception) {
                e.printStackTrace()
                callback(" ")
            }
        }.start()
    }

    private fun setupSleepOverlay() {
        sleepOverlay = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            visibility = android.view.View.GONE
        }
        val sleepDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                exitSleepMode()
                return true
            }
        })
        sleepOverlay?.setOnTouchListener { view, event ->
            sleepDetector.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_UP) view.performClick()
            true
        }
        val instructionText = TextView(this).apply {
            text = "두 번 눌러 절전모드를 해제하세요"
            setTextColor(Color.LTGRAY)
            textSize = 24f
            gravity = android.view.Gravity.CENTER
        }
        (sleepOverlay as FrameLayout).addView(
            instructionText,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply { gravity = android.view.Gravity.CENTER }
        )
        (findViewById<ViewGroup>(android.R.id.content))?.addView(
            sleepOverlay, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun toggleSleepMode() {
        if (isSleepModeActive) {
            exitSleepMode()
        } else {
            enterSleepMode()
        }
    }

    private fun enterSleepMode() {
        isSleepModeActive = true
        previewView.visibility = android.view.View.INVISIBLE
        overlayView.visibility = android.view.View.INVISIBLE
        sleepOverlay?.visibility = android.view.View.VISIBLE
        StreamingController.setSleepMode(this, true)
        Log.d("CameraModeActivity", "Sleep mode activated")
    }

    private fun exitSleepMode() {
        isSleepModeActive = false
        previewView.visibility = android.view.View.VISIBLE
        overlayView.visibility = android.view.View.VISIBLE
        sleepOverlay?.visibility = android.view.View.GONE
        StreamingController.setSleepMode(this, false)
        Log.d("CameraModeActivity", "Sleep mode deactivated")
    }


    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    @Suppress("MissingSuperCall")
    override fun onBackPressed() {
        MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)
            .setTitle("종료 확인")
            .setMessage("종료하시겠습니까?")
            .setPositiveButton("종료") { _, _ ->
                finishAffinity()
            }
            .setNegativeButton("취소") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }


    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
