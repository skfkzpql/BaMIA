package com.example.bamia.activities

import android.content.Context
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.bamia.R
import java.io.File

/**
 * 로그 조회 화면
 * 내부 저장소에 기록된 로그 파일(camera_mode_log.txt)을 읽어 화면에 표시합니다.
 */
class LogsActivity : AppCompatActivity() {

    private lateinit var tvLogs: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logs)

        tvLogs = findViewById(R.id.tvLogs)
        val logFile = File(filesDir, "camera_mode_log.txt")
        tvLogs.text = if (logFile.exists()) logFile.readText() else "No logs available."
    }
}
