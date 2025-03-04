package com.example.bamia.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import com.example.bamia.R
import com.example.bamia.ui.OverlayView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ViewerModeActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var overlayView: OverlayView
    private lateinit var tvRemoteInfo: TextView
    private lateinit var btnKebab: ImageButton
    private lateinit var btnGallery: ImageButton
    private lateinit var btnCapture: ImageButton
    private lateinit var btnSwitchCamera: ImageButton

    // 원격 연결 정보 (viewer_prefs에 저장된 값)
    private var remoteIp: String = ""
    private var remotePin: String = ""
    private var remoteBabyName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_viewer_mode)

        tvRemoteInfo = findViewById(R.id.tvRemoteInfo)
        btnKebab = findViewById(R.id.btnKebab)
        webView = findViewById(R.id.webView)
        overlayView = findViewById(R.id.overlayView)
        btnGallery = findViewById(R.id.btnGallery)
        btnCapture = findViewById(R.id.btnCapture)
        btnSwitchCamera = findViewById(R.id.btnSwitchCamera)

        // 뷰어 연결 정보 로드
        val prefs = getSharedPreferences("viewer_prefs", Context.MODE_PRIVATE)
        remoteIp = prefs.getString("viewer_ip", "") ?: ""
        remotePin = prefs.getString("viewer_pin", "") ?: ""
        // 원격 아기 이름 정보가 있다면 로드, 없으면 기본값 사용
        remoteBabyName = prefs.getString("viewer_baby_name", "REMOTE") ?: "REMOTE"

        updateRemoteInfo()

        // 케밥 메뉴 설정
        val kebabPopup = PopupMenu(this, btnKebab)
        with(kebabPopup.menu) {
            add(0, 1, 0, "수면 알림").setCheckable(true)
            add(0, 2, 1, "기상 알림").setCheckable(true)
            add(0, 3, 2, "수면중 얼굴").setCheckable(true)
            add(0, 4, 3, "웃음 알림").setCheckable(true)
            add(0, 5, 4, "울음 알림").setCheckable(true)
            add(0, 6, 5, "모드변경")
            add(0, 7, 6, "연결 설정")
            add(0, 8, 7, "정보")
        }
        kebabPopup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1, 2, 3, 4, 5 -> { item.isChecked = !item.isChecked; true }
                6 -> { // 모드 변경: 모드 선택 화면으로 돌아가기
                    startActivity(Intent(this, LauncherActivity::class.java))
                    finish()
                    true
                }
                7 -> { // 연결 설정: Viewer 연결 화면으로 이동 (caller = "viewerMain")
                    val intent = Intent(this, ViewerConnectionActivity::class.java)
                    intent.putExtra("caller", "viewerMain")
                    startActivity(intent)
                    finish()
                    true
                }
                8 -> { /* 정보 화면 처리 예정 */ true }
                else -> false
            }
        }
        btnKebab.setOnClickListener { kebabPopup.show() }

        // 스트리밍 URL 구성 및 WebView 초기화
        initializeWebView(remoteIp, remotePin)

        // 하단 바 버튼 (갤러리, 촬영, 전환) - 실제 동작은 필요에 따라 구현
        btnGallery.setOnClickListener { /* 갤러리 열기 */ }
        btnCapture.setOnClickListener { /* 스크린샷 캡쳐 */ }
        btnSwitchCamera.setOnClickListener { /* 모드 전환 또는 다른 동작 */ }
    }

    private fun updateRemoteInfo() {
        tvRemoteInfo.text = "IP: $remoteIp | PIN: $remotePin | 이름: $remoteBabyName"
    }

    private fun initializeWebView(ip: String, pin: String) {
        val streamUrl = "http://$ip:8080/?pin=$pin"
        webView.settings.javaScriptEnabled = true
        webView.loadUrl(streamUrl)
    }

    override fun onBackPressed() {
        super.onBackPressed()
        // 종료 확인 대화상자
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
