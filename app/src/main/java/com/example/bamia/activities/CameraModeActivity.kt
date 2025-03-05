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
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.example.bamia.R
import com.example.bamia.analysis.ExpressionAnalyzer
import com.example.bamia.gallery.GalleryActivity
import com.example.bamia.gallery.GalleryManager
import com.example.bamia.managers.LogManager
import com.example.bamia.managers.NetworkManager
import com.example.bamia.services.AudioStreamingService
import com.example.bamia.services.MjpegServerService
import com.example.bamia.streaming.FrameBuffer
import com.example.bamia.streamingcontrol.StreamingController
import com.example.bamia.ui.ExpressionDisplayConfig
import com.example.bamia.ui.ExpressionInfoHolder
import com.example.bamia.ui.FaceResult
import com.example.bamia.ui.OverlayView
import com.example.bamia.activities.ImageUtil
import com.example.bamia.ui.SleepInfoHolder
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

@Suppress("DEPRECATION")
class CameraModeActivity : AppCompatActivity() {

    // UI 요소
    private lateinit var tvNetworkInfo: TextView
    private lateinit var btnKebab: ImageButton
    private lateinit var previewView: PreviewView
    private lateinit var overlayView: OverlayView
    private lateinit var btnGallery: ImageButton
    private lateinit var btnCapture: ImageButton
    private lateinit var btnSwitchCamera: ImageButton

    // 카메라 및 스트리밍 관련
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var isStreamingActive = true

    // 자동 캡쳐 관련
    private var capturedExpressionsToday = mutableSetOf<String>()
    private var currentCaptureDate: String = getCurrentDate()
    private var lastStableExpression: String? = null
    private var stableStartTime: Long = System.currentTimeMillis()

    // 절전 모드 관련
    private var isSleepModeActive = false
    private var sleepOverlay: FrameLayout? = null

    // ML Kit 및 분석 관련
    private val faceDetectorOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .build()
    private val faceDetector = FaceDetection.getClient(faceDetectorOptions)
    private lateinit var expressionAnalyzer: ExpressionAnalyzer
    private lateinit var logManager: LogManager
    private lateinit var networkManager: NetworkManager

    // 오디오 스트리밍 관련
    private var viewerIp: String = ""
    private var isAudioSending: Boolean = true

    private var expressionStartTime: Long? = null
    private var lastExpression: String? = null
    private var happinessAlertTriggered = false
    private var sadnessAlertTriggered = false
    private var isSleepNotified = false
    private var eyeClosedStartTime: Long? = null
    private var eyeOpenStartTime: Long? = null
    private var faceUndetectedNotified = false

    // 제스처 감지 (더블 탭: 절전 모드, 스와이프: 카메라 전환)
    private val cameraGestureDetector by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 100
            private val SWIPE_VELOCITY_THRESHOLD = 100
            override fun onDoubleTap(e: MotionEvent): Boolean {
                toggleSleepMode()
                return true
            }
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val deltaY = e2.y - e1.y
                if (kotlin.math.abs(deltaY) > SWIPE_THRESHOLD && kotlin.math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                    toggleCamera()
                    return true
                }
                return false
            }
        })
    }

    // 권한 요청
    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            initCameraAndAudio()
        } else {
            finish()
        }
    }

    private val expressionPollInterval = 1000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_mode)

        initViews()
        initManagers()
        updateIpInfo()

        viewerIp = getSharedPreferences("viewer_prefs", Context.MODE_PRIVATE)
            .getString("viewer_ip", "") ?: ""

        if (allPermissionsGranted()) {
            initCameraAndAudio()
        } else {
            requestPermissionsLauncher.launch(REQUIRED_PERMISSIONS)
        }

        setupButtonListeners()
        setupKebabMenu()
        setupSleepOverlay()
        // 영상 스트리밍 서비스 시작 (이미 구현된 MjpegServerService)
        startService(Intent(this, MjpegServerService::class.java))
        startExpressionPolling(networkManager.ipAddress, networkManager.pin)
    }

    private fun initViews() {
        previewView = findViewById(R.id.previewView)
        overlayView = findViewById(R.id.overlayView)
        tvNetworkInfo = findViewById(R.id.tvNetworkInfo)
        btnKebab = findViewById(R.id.btnKebab)
        btnGallery = findViewById(R.id.btnGallery)
        btnCapture = findViewById(R.id.btnCapture)
        btnSwitchCamera = findViewById(R.id.btnSwitchCamera)
    }

    private fun initManagers() {
        networkManager = NetworkManager.getInstance(this)
        logManager = LogManager.getInstance(applicationContext)
        expressionAnalyzer = ExpressionAnalyzer(applicationContext)
    }

    private fun updateIpInfo() {
        tvNetworkInfo.text = "IP: ${networkManager.ipAddress} | PIN: ${networkManager.pin} | 이름: ${getBabyName()}"
    }

    private fun getBabyName(): String {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return prefs.getString("camera_name", "BABY") ?: "BABY"
    }

    private fun setupButtonListeners() {
        btnGallery.setOnClickListener { startActivity(Intent(this, GalleryActivity::class.java)) }
        btnCapture.setOnClickListener { capturePhoto() }
        btnSwitchCamera.setOnClickListener { toggleCamera() }
    }

    private fun setupKebabMenu() {
        val kebabPopup = PopupMenu(this, btnKebab).apply {
            menu.apply {
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
            setOnMenuItemClickListener { item ->
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
                    5 -> {
                        startActivity(Intent(this@CameraModeActivity, LauncherActivity::class.java))
                        finish()
                        true
                    }
                    6 -> { toggleSleepMode(); true }
                    7 -> { startActivity(Intent(this@CameraModeActivity, LogsActivity::class.java)); true }
                    8 -> { startActivity(Intent(this@CameraModeActivity, SettingsActivity::class.java).apply { putExtra("caller", "camera") }); true }
                    9 -> true
                    else -> false
                }
            }
        }
        btnKebab.setOnClickListener { kebabPopup.show() }
    }

    private fun setupSleepOverlay() {
        sleepOverlay = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
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
        sleepOverlay?.addView(
            instructionText,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT).apply {
                gravity = android.view.Gravity.CENTER
            }
        )
        (findViewById<ViewGroup>(android.R.id.content))?.addView(
            sleepOverlay, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
    }

    private fun initCameraAndAudio() {
        startCamera()
        // 카메라 기기는 항상 오디오 송신 및 수신
        startAudioStreaming()
        startAudioReceiving()
    }

    private fun startCamera() {
        Log.d("CameraModeActivity", "startCamera called")
        // PreviewView의 scaleType 변경 (XML에서 설정해도 되고, 코드로 설정해도 됩니다.)
        previewView.scaleType = PreviewView.ScaleType.FIT_CENTER

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder().build()
            imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build().apply {
                    setAnalyzer(cameraExecutor) { imageProxy ->
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
            val baos = ByteArrayOutputStream().apply { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, this) }
            FrameBuffer.currentFrame = baos.toByteArray()
            Log.d("CameraModeActivity", "FrameBuffer updated, size: ${FrameBuffer.currentFrame?.size ?: 0}")
        }
        val mediaImage = imageProxy.image
        if (mediaImage != null && bitmap != null) {
            val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            faceDetector.process(inputImage)
                .addOnSuccessListener { faces ->
                    val currentTime = System.currentTimeMillis()
                    val faceResults = mutableListOf<FaceResult>()
                    if (faces.isNotEmpty()) {
                        val face = faces[0]
                        faceUndetectedNotified = false
                        val leftEyeProb = face.rightEyeOpenProbability ?: 1f
                        val rightEyeProb = face.leftEyeOpenProbability ?: 1f

                        // 눈 감김/눈 뜸 로직 (수면/기상)
                        if (leftEyeProb < 0.3 && rightEyeProb < 0.3) {
                            if (eyeClosedStartTime == null) {
                                eyeClosedStartTime = currentTime
                            } else if (currentTime - eyeClosedStartTime!! >= 3000 && !isSleepNotified) {
                                SleepInfoHolder.sleepMessage = "수면"
                                isSleepNotified = true
                                faceUndetectedNotified = false // 새 수면 상태 시작 시 얼굴 미감지 플래그 초기화
                                eyeOpenStartTime = null
                            }
                        } else {
                            eyeClosedStartTime = null
                            if (leftEyeProb > 0.7 && rightEyeProb > 0.7) {
                                if (eyeOpenStartTime == null) {
                                    eyeOpenStartTime = currentTime
                                } else if (currentTime - eyeOpenStartTime!! >= 3000 && isSleepNotified) {
                                    SleepInfoHolder.sleepMessage = "기상"
                                    isSleepNotified = false
                                    faceUndetectedNotified = false
                                    eyeOpenStartTime = null
                                }
                            } else {
                                eyeOpenStartTime = null
                            }
                        }

                        // 얼굴이 검출되었으므로 얼굴 미감지 관련 플래그 초기화
                        // (얼굴이 보이면 계속해서 미감지 알림은 보내지 않음)
                        faceUndetectedNotified = false

                        // 표정 분석: analyzeExpression()가 영어 문자열 반환 ("Happiness", "Sadness", 등)
                        val currentExpression = expressionAnalyzer.analyzeExpression(
                            InputImage.fromBitmap(bitmap, 0),
                            face.boundingBox
                        )

                        // 동일 표정 유지 시 알림 처리 (1초 이상 지속)
                        if (lastExpression != currentExpression) {
                            expressionStartTime = currentTime
                            lastExpression = currentExpression
                            happinessAlertTriggered = false
                            sadnessAlertTriggered = false
                        } else {
                            if (expressionStartTime != null && currentTime - expressionStartTime!! >= 300) {
                                ExpressionInfoHolder.currentExpression = currentExpression
                                if (currentExpression.equals("Happiness", ignoreCase = true) && !happinessAlertTriggered) {
                                    SleepInfoHolder.sleepMessage = "행복"
                                    happinessAlertTriggered = true
                                } else if (currentExpression.equals("Sadness", ignoreCase = true) && !sadnessAlertTriggered) {
                                    SleepInfoHolder.sleepMessage = "슬픔"
                                    sadnessAlertTriggered = true
                                }
                            }
                        }

                        // 얼굴 경계 박스 및 오버레이 업데이트 (카메라 기기)
                        val transformedBox = transformBoundingBox(face.boundingBox, bitmap.width, bitmap.height)
                        val faceResult = FaceResult(
                            boundingBox = transformedBox,
                            predictedExpression = currentExpression,
                            leftEyeProbability = leftEyeProb,
                            rightEyeProbability = rightEyeProb,
                            smilingProbability = face.smilingProbability
                        )
                        faceResults.add(faceResult)
                    } else {
                        // 얼굴이 검출되지 않은 경우: 수면 상태에서 얼굴 미감지가 처음 발생할 때만 알림
                        if (isSleepNotified && !faceUndetectedNotified) {
                            SleepInfoHolder.sleepMessage = "얼굴 미감지"
                            faceUndetectedNotified = true
                        }
                    }
                    runOnUiThread { overlayView.updateResults(faceResults) }
                }
                .addOnCompleteListener { imageProxy.close() }
        } else {
            imageProxy.close()
        }
    }

    private fun transformBoundingBox(originalBox: Rect, imageWidth: Int, imageHeight: Int): Rect {
        // PreviewView의 실제 크기 (보이는 영역)
        val viewWidth = previewView.width.toFloat()
        val viewHeight = previewView.height.toFloat()
        // 원본 이미지를 view에 FIT_CENTER로 맞출 때 사용되는 scale 값
        val scale = minOf(viewWidth / imageWidth, viewHeight / imageHeight)
        // letterbox(빈 영역) 계산: view에 이미지가 가운데 배치되므로 생기는 오프셋
        val offsetX = (viewWidth - imageWidth * scale) / 2f
        val offsetY = (viewHeight - imageHeight * scale) / 2f

        return if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
            // 프론트 카메라는 좌우 미러링 적용
            val left = (viewWidth - (offsetX + originalBox.right * scale)).toInt()
            val top = (offsetY + originalBox.top * scale).toInt()
            val right = (viewWidth - (offsetX + originalBox.left * scale)).toInt()
            val bottom = (offsetY + originalBox.bottom * scale).toInt()
            Rect(left, top, right, bottom)
        } else {
            val left = (offsetX + originalBox.left * scale).toInt()
            val top = (offsetY + originalBox.top * scale).toInt()
            val right = (offsetX + originalBox.right * scale).toInt()
            val bottom = (offsetY + originalBox.bottom * scale).toInt()
            Rect(left, top, right, bottom)
        }
    }

    private fun autoCapturePhoto(expression: String) {
        runOnUiThread {
            previewView.bitmap?.let { bitmap ->
                GalleryManager.saveCapturedImage(this, bitmap, getBabyName(), expression) { filePath ->
                    logManager.logEvent("Auto-captured ($expression): $filePath at ${getCurrentTimeStamp()}")
                }
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

    private fun capturePhoto() {
        previewView.bitmap?.let { bitmap ->
            val babyName = getBabyName()
            GalleryManager.saveCapturedImage(this, bitmap, babyName, "camera_request") { filePath ->
                // 기존 로그 기록 외에 토스트 메시지 추가
                Toast.makeText(this, "사진이 저장되었습니다.", Toast.LENGTH_SHORT).show()
                logManager.logEvent("Photo captured (camera_request): $filePath at ${getCurrentTimeStamp()}")
            }
        }
    }


    private fun toggleCamera() {
        cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA)
            CameraSelector.DEFAULT_FRONT_CAMERA
        else CameraSelector.DEFAULT_BACK_CAMERA
        startCamera()
    }

    private fun startExpressionPolling(ip: String, pin: String) {
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                fetchExpression(ip, pin) { expression ->
                    // 필요 시 OverlayView 업데이트
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

    private fun toggleSleepMode() {
        if (isSleepModeActive) exitSleepMode() else enterSleepMode()
    }

    private fun enterSleepMode() {
        isSleepModeActive = true
        previewView.visibility = View.INVISIBLE
        overlayView.visibility = View.INVISIBLE
        sleepOverlay?.visibility = View.VISIBLE
        StreamingController.setSleepMode(this, true)
        Log.d("CameraModeActivity", "Sleep mode activated")
    }

    private fun exitSleepMode() {
        isSleepModeActive = false
        previewView.visibility = View.VISIBLE
        overlayView.visibility = View.VISIBLE
        sleepOverlay?.visibility = View.GONE
        StreamingController.setSleepMode(this, false)
        Log.d("CameraModeActivity", "Sleep mode deactivated")
    }


    // 오디오 송신 시작
    // 카메라 기기는 ROLE "CAMERA", REMOTE_IP는 뷰어 기기의 IP를 전달하여, 자신의 마이크 음성을 뷰어로 전송
    private fun startAudioStreaming() {
        val intent = Intent(this, AudioStreamingService::class.java).apply {
            putExtra("ACTION", "START_SENDING")
            putExtra("ROLE", "CAMERA")
            putExtra("REMOTE_IP", viewerIp)
        }
        Log.d("CameraModeActivity", "Starting audio streaming (sending) to viewer IP: $viewerIp at ${System.currentTimeMillis()}")
        startService(intent)
    }

    // 오디오 수신 시작
    // 카메라 기기는 ROLE "CAMERA", REMOTE_IP는 뷰어 기기에서 송신한 음성이 도착하는 IP를 전달하여, 해당 음성을 수신
    private fun startAudioReceiving() {
        val intent = Intent(this, AudioStreamingService::class.java).apply {
            putExtra("ACTION", "START_RECEIVING")
            putExtra("ROLE", "CAMERA")
            putExtra("REMOTE_IP", viewerIp)
        }
        Log.d("CameraModeActivity", "Starting audio receiving, expecting from viewer IP: $viewerIp at ${System.currentTimeMillis()}")
        startService(intent)
    }

    private fun allPermissionsGranted() =
        REQUIRED_PERMISSIONS.all { ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED }

    @Suppress("MissingSuperCall")
    override fun onBackPressed() {
        MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)
            .setTitle("종료 확인")
            .setMessage("종료하시겠습니까?")
            .setPositiveButton("종료") { _, _ -> finishAffinity() }
            .setNegativeButton("취소") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        startService(Intent(this, AudioStreamingService::class.java).apply {
            putExtra("ACTION", "STOP_ALL")
        })
    }
}
