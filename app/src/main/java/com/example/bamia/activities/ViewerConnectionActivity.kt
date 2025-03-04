package com.example.bamia.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.bamia.R

class ViewerConnectionActivity : AppCompatActivity() {

    private lateinit var etIp: EditText
    private lateinit var etPin: EditText
    private lateinit var btnConnect: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var btnBack: ImageButton

    // "caller" extra: "launcher" (모드 선택 화면 진입) 또는 "viewerMain" (Viewer 메인에서 연결 화면으로 진입)
    private var caller: String = "launcher"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_viewer_connection)

        etIp = findViewById(R.id.etIp)
        etPin = findViewById(R.id.etPin)
        btnConnect = findViewById(R.id.btnConnect)
        progressBar = findViewById(R.id.progressBar)
        btnBack = findViewById(R.id.btnBack)

        caller = intent.getStringExtra("caller") ?: "launcher"

        // 이전에 저장한 뷰어 연결 정보 로드 (없으면 빈 칸)
        val prefs = getSharedPreferences("viewer_prefs", Context.MODE_PRIVATE)
        etIp.setText(prefs.getString("viewer_ip", ""))
        etPin.setText(prefs.getString("viewer_pin", ""))

        btnConnect.setOnClickListener {
            val ip = etIp.text.toString().trim()
            val pin = etPin.text.toString().trim()
            if (ip.isEmpty() || pin.isEmpty()) {
                Toast.makeText(this, "IP와 PIN을 입력하세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.edit().putString("viewer_ip", ip).putString("viewer_pin", pin).apply()
            progressBar.visibility = View.VISIBLE
            btnConnect.isEnabled = false

            Thread {
                val connected = checkServerConnection(ip, pin)
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    btnConnect.isEnabled = true
                    if (connected) {
                        // 연결 성공 시 Viewer 메인 화면으로 이동
                        startActivity(Intent(this, ViewerModeActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(this, "서버 연결에 실패했습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()
        }

        btnBack.setOnClickListener {
            if (caller == "launcher") {
                startActivity(Intent(this, LauncherActivity::class.java))
            } else {
                startActivity(Intent(this, ViewerModeActivity::class.java))
            }
            finish()
        }
    }

    private fun checkServerConnection(ip: String, pin: String): Boolean {
        return try {
            val url = java.net.URL("http://$ip:8080/expression?pin=$pin")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            connection.requestMethod = "GET"
            val responseCode = connection.responseCode
            connection.disconnect()
            responseCode == 200
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    @Deprecated("This method has been deprecated in favor of using the\n      {@link OnBackPressedDispatcher} via {@link #getOnBackPressedDispatcher()}.\n      The OnBackPressedDispatcher controls how back button events are dispatched\n      to one or more {@link OnBackPressedCallback} objects.")
    override fun onBackPressed() {
        super.onBackPressed()
        if (caller == "launcher") {
            startActivity(Intent(this, LauncherActivity::class.java))
        } else {
            startActivity(Intent(this, ViewerModeActivity::class.java))
        }
        finish()
    }
}
