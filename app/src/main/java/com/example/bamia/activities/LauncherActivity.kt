package com.example.bamia.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * 모드 선택 및 시작 화면
 * SharedPreferences에 저장된 마지막 모드를 확인하여 해당 모드로 진입하고,
 * 저장된 기록이 없으면 모드 선택 다이얼로그를 표시합니다.
 */
class LauncherActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // SharedPreferences에 저장된 모드 확인
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val lastMode = prefs.getString("last_mode", null)

        if (lastMode == null) {
            showModeSelectionDialog()
        } else {
            when (lastMode) {
                "camera" -> startActivity(Intent(this, CameraModeActivity::class.java))
                "viewer" -> startActivity(Intent(this, ViewerActivity::class.java))
            }
            finish()
        }
    }

    private fun showModeSelectionDialog() {
        AlertDialog.Builder(this)
            .setTitle("모드 선택")
            .setMessage("카메라 모드와 뷰어 모드 중 하나를 선택하세요.")
            .setPositiveButton("카메라 모드") { _, _ ->
                saveMode("camera")
                startActivity(Intent(this, CameraModeActivity::class.java))
                finish()
            }
            .setNegativeButton("뷰어 모드") { _, _ ->
                saveMode("viewer")
                startActivity(Intent(this, ViewerActivity::class.java))
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun saveMode(mode: String) {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("last_mode", mode).apply()
    }
}
