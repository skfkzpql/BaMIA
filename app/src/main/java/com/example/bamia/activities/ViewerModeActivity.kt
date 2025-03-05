package com.example.bamia.activities

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.webkit.WebView
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.bamia.R
import com.example.bamia.gallery.GalleryManager
import com.example.bamia.services.AudioStreamingService
import com.example.bamia.services.NotificationPollingService
import com.example.bamia.ui.OverlayView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ViewerModeActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var overlayView: OverlayView
    private lateinit var tvRemoteInfo: TextView
    private lateinit var btnKebab: ImageButton
    private lateinit var btnGallery: ImageButton
    private lateinit var btnCapture: ImageButton
    private lateinit var btnToggleMic: ImageButton

    // XML에 정의된 expressionOverlay (스트리밍 영역 내, WebView 위에 오버랩)
    private lateinit var expressionOverlay: TextView

    // 원격 연결 정보 (viewer_prefs에 저장된 값; 카메라 기기의 IP로 사용)
    private var remoteIp: String = ""
    private var remotePin: String = ""
    private var remoteBabyName: String = ""

    // 오디오 송신 토글 상태 (기본 OFF)
    private var isAudioSending: Boolean = false

    private val REQUEST_AUDIO_PERMISSION = 2001
    private val REQUEST_NOTIFICATION_PERMISSION = 3001

    // Expression polling handler (1초마다 카메라 기기에서 표정을 가져옴)
    private val expressionHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_viewer_mode)

        tvRemoteInfo = findViewById(R.id.tvRemoteInfo)
        btnKebab = findViewById(R.id.btnKebab)
        webView = findViewById(R.id.webView)
        overlayView = findViewById(R.id.overlayView)
        btnGallery = findViewById(R.id.btnGallery)
        btnCapture = findViewById(R.id.btnCapture)
        btnToggleMic = findViewById(R.id.btnToggleMic)
        // XML에 정의된 expressionOverlay를 가져옴
        expressionOverlay = findViewById(R.id.expressionOverlay)

        // topBar와는 별개이므로, expressionOverlay는 streamContainer 내에 있으므로 자동 배치됩니다.
        // 이제 별도의 addContentView() 호출은 제거합니다.

        val prefs = getSharedPreferences("viewer_prefs", Context.MODE_PRIVATE)
        remoteIp = prefs.getString("viewer_ip", "") ?: ""
        remotePin = prefs.getString("viewer_pin", "") ?: ""
        remoteBabyName = prefs.getString("camera_baby_name", "불러오는 중...") ?: "불러오는 중..."
        updateRemoteInfo()

        // 카메라 기기의 아기 이름 정보 요청 및 저장
        fetchCameraName()

        // 케밥 메뉴 설정 - 각 알림 항목 체크 상태를 SharedPreferences에 저장
        val kebabPopup = PopupMenu(this, btnKebab)
        with(kebabPopup.menu) {
            add(0, 1, 0, "수면 알림")
                .setCheckable(true)
                .setChecked(prefs.getBoolean("notify_sleep", true))
            add(0, 2, 1, "기상 알림")
                .setCheckable(true)
                .setChecked(prefs.getBoolean("notify_wakeup", true))
            add(0, 3, 2, "수면중 얼굴")
                .setCheckable(true)
                .setChecked(prefs.getBoolean("notify_face", true))
            add(0, 4, 3, "웃음 알림")
                .setCheckable(true)
                .setChecked(prefs.getBoolean("notify_smile", true))
            add(0, 5, 4, "울음 알림")
                .setCheckable(true)
                .setChecked(prefs.getBoolean("notify_cry", true))
            add(0, 6, 5, "모드변경")
            add(0, 7, 6, "연결 설정")
            add(0, 8, 7, "정보")
        }
        kebabPopup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    item.isChecked = !item.isChecked
                    prefs.edit().putBoolean("notify_sleep", item.isChecked).apply()
                    true
                }
                2 -> {
                    item.isChecked = !item.isChecked
                    prefs.edit().putBoolean("notify_wakeup", item.isChecked).apply()
                    true
                }
                3 -> {
                    item.isChecked = !item.isChecked
                    prefs.edit().putBoolean("notify_face", item.isChecked).apply()
                    true
                }
                4 -> {
                    item.isChecked = !item.isChecked
                    prefs.edit().putBoolean("notify_smile", item.isChecked).apply()
                    true
                }
                5 -> {
                    item.isChecked = !item.isChecked
                    prefs.edit().putBoolean("notify_cry", item.isChecked).apply()
                    true
                }
                6 -> {
                    startActivity(Intent(this, LauncherActivity::class.java))
                    finish()
                    true
                }
                7 -> {
                    val intent = Intent(this, ViewerConnectionActivity::class.java)
                    intent.putExtra("caller", "viewerMain")
                    startActivity(intent)
                    finish()
                    true
                }
                8 -> true
                else -> false
            }
        }
        btnKebab.setOnClickListener { kebabPopup.show() }

        // 스트리밍 URL 구성 및 WebView 초기화
        initializeWebView(remoteIp, remotePin)

        // 갤러리 버튼: 갤러리 액티비티로 이동 (필터 UI 숨김)
        btnGallery.setOnClickListener {
            val intent = Intent(this, com.example.bamia.gallery.GalleryActivity::class.java)
            intent.putExtra("hide_filter", true)
            startActivity(intent)
        }

        // 캡쳐 버튼: WebView의 현재 화면을 캡쳐하여 저장하고 Toast 메시지 표시
        btnCapture.setOnClickListener {
            val bitmap = Bitmap.createBitmap(webView.width, webView.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            webView.draw(canvas)
            com.example.bamia.gallery.GalleryManager.saveCapturedImage(this, bitmap, remoteBabyName, "viewer_capture") { filePath ->
                Toast.makeText(this, "캡쳐됨: $filePath", Toast.LENGTH_SHORT).show()
            }
        }

        // 마이크 토글 버튼: 오디오 송신 시작/중지
        btnToggleMic.setOnClickListener {
            if (!hasAudioPermission()) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO_PERMISSION
                )
                return@setOnClickListener
            }
            if (isAudioSending) {
                stopAudioStreaming()
                btnToggleMic.setImageResource(R.drawable.ic_audio_off)
            } else {
                startAudioStreaming()
                btnToggleMic.setImageResource(R.drawable.ic_audio_on)
            }
            isAudioSending = !isAudioSending
        }

        // 알림 권한 요청 (Android 13 이상)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_NOTIFICATION_PERMISSION
                )
            }
        }

        // NotificationPollingService 시작 (포그라운드 서비스)
        startNotificationPollingService()

        // 오디오 수신 시작
        if (hasAudioPermission()) {
            startAudioReceiving()
        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO_PERMISSION
            )
        }

        // 표정 폴링 시작 (1초마다 카메라 기기의 /expression 엔드포인트를 호출)
        startExpressionPolling()
    }

    private fun startNotificationPollingService() {
        val intent = Intent(this, NotificationPollingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun fetchCameraName() {
        Thread {
            try {
                val url = URL("http://$remoteIp:8080/camera_name?pin=$remotePin")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                val response = connection.inputStream.bufferedReader().readText().trim()
                connection.disconnect()
                runOnUiThread {
                    remoteBabyName = if (response.isNotEmpty()) response else "UNKNOWN"
                    val prefs = getSharedPreferences("viewer_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putString("camera_baby_name", remoteBabyName).apply()
                    updateRemoteInfo()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    remoteBabyName = "에러 발생"
                    updateRemoteInfo()
                }
            }
        }.start()
    }

    private fun updateRemoteInfo() {
        tvRemoteInfo.text = "IP: $remoteIp | PIN: $remotePin | 이름: $remoteBabyName"
    }

    private fun initializeWebView(ip: String, pin: String) {
        val streamUrl = "http://$ip:8080/?pin=$pin"
        webView.settings.javaScriptEnabled = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true
        webView.settings.setSupportZoom(false)
        webView.loadUrl(streamUrl)
    }

    // 오디오 송신 시작: ROLE은 "VIEWER"
    private fun startAudioStreaming() {
        val intent = Intent(this, AudioStreamingService::class.java)
        intent.putExtra("ACTION", "START_SENDING")
        intent.putExtra("ROLE", "VIEWER")
        intent.putExtra("REMOTE_IP", remoteIp)
        startService(intent)
    }

    // 오디오 송신 중지
    private fun stopAudioStreaming() {
        val intent = Intent(this, AudioStreamingService::class.java)
        intent.putExtra("ACTION", "STOP_SENDING")
        intent.putExtra("ROLE", "VIEWER")
        intent.putExtra("REMOTE_IP", remoteIp)
        startService(intent)
    }

    // 오디오 수신 시작: ROLE은 "VIEWER"
    private fun startAudioReceiving() {
        val intent = Intent(this, AudioStreamingService::class.java)
        intent.putExtra("ACTION", "START_RECEIVING")
        intent.putExtra("ROLE", "VIEWER")
        intent.putExtra("REMOTE_IP", remoteIp)
        startService(intent)
    }

    // 표정 폴링: 1초마다 /expression 엔드포인트 호출 후 매핑하여 오버레이 업데이트
    private fun startExpressionPolling() {
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                fetchExpression()
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(runnable)
    }

    private fun fetchExpression() {
        Thread {
            try {
                val prefs = getSharedPreferences("viewer_prefs", Context.MODE_PRIVATE)
                val ip = prefs.getString("viewer_ip", "") ?: ""
                val pin = prefs.getString("viewer_pin", "") ?: ""
                if (ip.isEmpty() || pin.isEmpty()) return@Thread
                val url = URL("http://$ip:8080/expression?pin=$pin")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                val response = connection.inputStream.bufferedReader().use { it.readText().trim() }
                connection.disconnect()
                // 매핑: 영어 표정을 한글로 변환, 빈 문자열이면 기존 값 유지
                val mappedExpression = when(response.lowercase(Locale.getDefault())) {
                    "neutral" -> "중립"
                    "happiness" -> "행복"
                    "sadness" -> "슬픔"
                    "surprise" -> "놀람"
                    "fear" -> "두려움"
                    "disgust" -> "혐오"
                    "anger" -> "화남"
                    "" -> expressionOverlay.text.toString()
                    else -> "얼굴 미감지"
                }
                runOnUiThread {
                    // XML에 정의된 expressionOverlay 업데이트 (계속 표시)
                    expressionOverlay.text = mappedExpression
                    expressionOverlay.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_AUDIO_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startAudioReceiving()
                } else {
                    MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)
                        .setTitle("권한 필요")
                        .setMessage("오디오 기능 사용을 위해 RECORD_AUDIO 권한이 필요합니다.")
                        .setPositiveButton("확인") { dialog, _ ->
                            dialog.dismiss()
                        }
                        .show()
                }
            }
            REQUEST_NOTIFICATION_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // 알림 권한 승인됨
                } else {
                    Toast.makeText(this, "알림 기능을 사용하려면 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
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
}
