package com.example.bamia.services

import android.app.Service
import android.content.Context
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
    private var lastNotifiedEvent: String = ""

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
                val prefs = getSharedPreferences("viewer_prefs", Context.MODE_PRIVATE)
                val ip = prefs.getString("viewer_ip", "") ?: ""
                val pin = prefs.getString("viewer_pin", "") ?: ""
                if (ip.isEmpty() || pin.isEmpty()) {
                    Log.w("NotificationPollingService", "뷰어 연결 정보가 누락되었습니다.")
                    return@Thread
                }

                val url = URL("http://$ip:8080/sleep?pin=$pin")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 3000
                connection.readTimeout = 3000

                val maxResponseSize = 1024 // bytes
                val reader = connection.inputStream.bufferedReader()
                val sb = StringBuilder()
                var totalRead = 0
                while (true) {
                    val line = reader.readLine() ?: break
                    totalRead += line.length
                    if (totalRead > maxResponseSize) break
                    sb.append(line)
                }
                val response = sb.toString().trim()
                connection.disconnect()

                if (response.isNotEmpty() && !response.contains("--BoundaryString")) {
                    Log.d("NotificationPollingService", "알림 메시지 수신: $response")
                    // 중복 알림 방지: 마지막 전송 이벤트와 동일하면 무시
                    if (response == lastNotifiedEvent) {
                        return@Thread
                    }

                    // 케밥 메뉴 설정에 따라 필터링
                    val shouldNotify = when(response) {
                        "수면" -> prefs.getBoolean("notify_sleep", true)
                        "기상" -> prefs.getBoolean("notify_wakeup", true)
                        "얼굴 미감지" -> prefs.getBoolean("notify_face", true)
                        "행복" -> prefs.getBoolean("notify_smile", true)
                        "슬픔" -> prefs.getBoolean("notify_cry", true)
                        else -> false
                    }
                    if (shouldNotify) {
                        NotificationHelper.notifyCameraEvent(this, response)
                        lastNotifiedEvent = response
                        // "얼굴 미감지"의 경우 한 번만 알림을 보낸 후, SleepInfoHolder를 초기화해서 이후 전송을 막습니다.
                        if(response == "얼굴 미감지"){
                            // SleepInfoHolder.sleepMessage를 비워 연속 알림을 막음
                            com.example.bamia.ui.SleepInfoHolder.sleepMessage = ""
                        }
                    }
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
