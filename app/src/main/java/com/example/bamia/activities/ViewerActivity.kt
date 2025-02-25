package com.example.bamia.activities

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.net.HttpURLConnection
import java.net.URL
import com.example.bamia.R
import com.example.bamia.managers.NetworkManager
import com.example.bamia.gallery.GalleryManager
import com.example.bamia.notifications.NotificationHelper
import com.example.bamia.services.NotificationPollingService

/**
 * ViewerActivity
 *
 * 뷰어 모드에서는 WebView를 통해 스트리밍을 받고,
 * 사용자가 캡쳐 버튼을 누르면 "viewer_request" captureExpression과 함께 아기 이름을 포함하여 이미지를 저장합니다.
 */
class ViewerActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var connectPanel: LinearLayout
    private lateinit var etIp: EditText
    private lateinit var etPin: EditText
    private lateinit var btnConnect: Button
    private lateinit var tvIp: TextView
    private lateinit var tvPin: TextView
    private lateinit var btnSettings: Button
    private lateinit var btnCaptureStream: Button
    private lateinit var tvExpressionOverlay: TextView
    private lateinit var networkManager: NetworkManager
    private var expressionHandler: Handler? = null
    private val expressionPollInterval = 1000L // 1초마다 폴링

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_viewer)

        webView = findViewById(R.id.webView)
        tvExpressionOverlay = findViewById(R.id.tvExpressionOverlay)
        connectPanel = findViewById(R.id.connectPanel)
        etIp = findViewById(R.id.etIp)
        etPin = findViewById(R.id.etPin)
        btnConnect = findViewById(R.id.btnConnect)
        tvIp = findViewById(R.id.tvIp)
        tvPin = findViewById(R.id.tvPin)
        btnSettings = findViewById(R.id.btnSettings)
        btnCaptureStream = findViewById(R.id.btnCaptureStream)

        networkManager = NetworkManager.getInstance(this)
        if (networkManager.ipAddress.isNotEmpty() && networkManager.pin.isNotEmpty()) {
            etIp.setText(networkManager.ipAddress)
            etPin.setText(networkManager.pin)
            tvIp.text = "IP: ${networkManager.ipAddress}"
            tvPin.text = "PIN: ${networkManager.pin}"
        }

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                1001)
        }

        btnConnect.setOnClickListener {
            val ip = etIp.text.toString().trim()
            val pin = etPin.text.toString().trim()
            if (ip.isNotEmpty() && pin.isNotEmpty()) {
                networkManager.ipAddress = ip
                networkManager.pin = pin
                tvIp.text = "IP: $ip"
                tvPin.text = "PIN: $pin"
                connectPanel.visibility = View.GONE
                initializeWebView(ip, pin)
                startExpressionPolling(ip, pin)
                startService(Intent(this, NotificationPollingService::class.java))
            }
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        btnCaptureStream.setOnClickListener {
            captureStreamImage()
        }
    }

    private fun initializeWebView(ip: String, pin: String) {
        val streamUrl = "http://$ip:8080/?pin=$pin"
        Log.d("ViewerActivity", "Loading MJPEG stream from URL: $streamUrl")
        val htmlData = """
            <html>
              <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                <style>
                  body { 
                      margin:0; 
                      padding:0; 
                      background:black; 
                      display: flex; 
                      justify-content: center; 
                      align-items: center; 
                      height: 100vh; 
                  }
                  img { 
                      width: auto; 
                      height: 100%; 
                      max-width: 100%; 
                      object-fit: contain; 
                  }
                </style>
              </head>
              <body>
                <img src="$streamUrl" alt="MJPEG Stream" id="streamImg"/>
              </body>
            </html>
        """.trimIndent()
        webView.settings.javaScriptEnabled = true
        webView.loadDataWithBaseURL(null, htmlData, "text/html", "UTF-8", null)
    }

    private fun captureStreamImage() {
        // WebView의 크기를 기반으로 새로운 Bitmap 생성
        val bitmap = Bitmap.createBitmap(webView.width, webView.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // WebView의 배경을 먼저 그린 후 내용을 캡쳐
        webView.draw(canvas)

        // SharedPreferences에서 아기 이름 불러오기
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val babyName = prefs.getString("camera_name", "DefaultName") ?: "DefaultName"

        // GalleryManager를 통해 이미지 저장 (captureExpression: "viewer_request")
        GalleryManager.saveCapturedImage(this, bitmap, babyName, "viewer_request") { filePath ->
            Log.d("ViewerActivity", "Stream image captured (viewer_request) and saved at: $filePath")
        }
    }

    // 주기적으로 /expression 엔드포인트에 요청하는 함수
    private fun startExpressionPolling(ip: String, pin: String) {
        expressionHandler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                fetchExpression(ip, pin) { expression ->
                    runOnUiThread {
                        tvExpressionOverlay.text = expression
                    }
                }
                expressionHandler?.postDelayed(this, expressionPollInterval)
            }
        }
        expressionHandler?.post(runnable)
    }

    // HTTP 요청을 통해 카메라 기기에서 표정 정보를 가져오는 함수
    private fun fetchExpression(ip: String, pin: String, callback: (String) -> Unit) {
        Thread {
            try {
                val url = URL("http://$ip:8080/expression?pin=$pin")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                val response = connection.inputStream.bufferedReader().readText().trim()
                // "unknown"이나 빈 문자열인 경우 "face not detected"로 처리
                val displayText = if (response.equals("unknown", ignoreCase = true) || response.isEmpty()) {
                    "face not detected"
                } else {
                    response
                }
                callback(displayText)
            } catch (e: Exception) {
                e.printStackTrace()
                callback(" ")
            }
        }.start()
    }


    override fun onDestroy() {
        super.onDestroy()
        expressionHandler?.removeCallbacksAndMessages(null)
    }
}
