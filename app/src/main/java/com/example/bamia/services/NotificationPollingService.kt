package com.example.bamia.services

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.bamia.R
import com.example.bamia.managers.NetworkManager
import com.example.bamia.notifications.NotificationHelper
import java.net.HttpURLConnection
import java.net.URL

class NotificationPollingService : Service() {

    private lateinit var handler: Handler
    private val pollingInterval = 1000L  // 1초마다 폴링
    private lateinit var networkManager: NetworkManager
    private var isPolling = false

    override fun onCreate() {
        super.onCreate()
        // 알림 채널을 명시적으로 초기화
        NotificationHelper.initNotificationChannel(this)
        handler = Handler(Looper.getMainLooper())
        networkManager = NetworkManager.getInstance(this)
        startForegroundServiceNotification()
        startPolling()
    }

    // 포그라운드 서비스 알림 (반드시 보여야 하므로)
    private fun startForegroundServiceNotification() {
        val notification = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ID)
            .setContentTitle("알림 폴링 서비스")
            .setContentText("뷰어 기기가 알림 이벤트를 폴링 중입니다.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()
        startForeground(101, notification)
    }

    private fun startPolling() {
        isPolling = true
        handler.post(object : Runnable {
            override fun run() {
                if (!isPolling) return
                pollNotificationEndpoint()
                handler.postDelayed(this, pollingInterval)
            }
        })
    }

    private fun pollNotificationEndpoint() {
        Thread {
            try {
                val ip = networkManager.ipAddress
                val pin = networkManager.pin
                // 예를 들어 /sleep 엔드포인트를 사용 (서버에서 수면/기상 이벤트만 반환)
                val url = URL("http://$ip:8080/sleep?pin=$pin")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 3000
                connection.readTimeout = 3000

                // 최대 응답 크기를 1KB로 제한
                val maxResponseSize = 1024 // bytes
                val reader = connection.inputStream.bufferedReader()
                val stringBuilder = StringBuilder()
                var totalRead = 0
                while (true) {
                    val currentLine = reader.readLine() ?: break
                    totalRead += currentLine.length
                    if (totalRead > maxResponseSize) {
                        break
                    }
                    stringBuilder.append(currentLine)
                }
                val response = stringBuilder.toString().trim()
                connection.disconnect()

                // 만약 응답이 비어있거나, MJPEG 스트림의 일부(예: --BoundaryString)가 포함되어 있다면 무시
                if (response.isNotEmpty() && !response.contains("--BoundaryString")) {
                    Log.d("NotificationPollingService", "알림 메시지 수신: $response")
                    // 수면 이벤트(수면 시작 혹은 기상 이벤트)일 때만 알림을 발생시킴
                    NotificationHelper.notifySleepStarted(this, response)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }




    private fun pollSleepEndpoint() {
        Thread {
            try {
                val ip = networkManager.ipAddress
                val pin = networkManager.pin
                val url = URL("http://$ip:8080/sleep?pin=$pin")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 3000
                connection.readTimeout = 3000

                // use 블록을 사용해 inputStream이 자동으로 닫히도록 함
                val response = connection.inputStream.bufferedReader().use { it.readText().trim() }
                connection.disconnect()

                if (response.isNotEmpty()) {
                    Log.d("NotificationPollingService", "수면 메시지 수신: $response")
                    NotificationHelper.notifySleepStarted(this, response)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isPolling = false
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
