package com.example.bamia.activities

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.ConnectivityManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.bamia.R
import com.example.bamia.managers.NetworkManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * 모드 선택 및 시작 화면 (전체화면 모드 선택)
 */
class LauncherActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 시스템 표시줄 및 내비게이션 바 색상 검정으로 설정
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        setContentView(R.layout.activity_mode_selection)

        // WIFI 연결 여부 체크 (간단하게 IP가 "0.0.0.0"이면 연결되지 않은 것으로 간주)
        val networkManager = NetworkManager.getInstance(this)
        val tvWifiWarning = findViewById<android.widget.TextView>(R.id.tvWifiWarning)
        if (networkManager.ipAddress == "0.0.0.0") {
            tvWifiWarning.visibility = android.view.View.VISIBLE
        }

        // 버튼 클릭 리스너 설정
        findViewById<android.widget.Button>(R.id.btnCameraMode).setOnClickListener {
            // 카메라 모드 시작 → 설정 화면으로 이동
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
        }

        findViewById<android.widget.Button>(R.id.btnViewerMode).setOnClickListener {
            // 뷰어 모드 시작 → 뷰어 화면으로 이동
            startActivity(Intent(this, ViewerConnectionActivity::class.java))
            finish()
        }
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
}
