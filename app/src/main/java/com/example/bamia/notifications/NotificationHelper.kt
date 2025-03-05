package com.example.bamia.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * NotificationHelper
 *
 * 로컬 알림 생성 및 관리를 담당합니다.
 * 표정 변화, 울음/웃음, 수면 시작/종료, 얼굴 미검출 등의 이벤트에 대해 알림을 표시합니다.
 */
object NotificationHelper {
    const val CHANNEL_ID: String = "bamia_notifications"
    private const val CHANNEL_NAME = "BaMIA Notifications"
    private const val CHANNEL_DESC = "Notifications for BaMIA events"

    fun initNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_DESC
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200)  // 예: 0ms 대기 후 200ms 진동
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(context: Context, title: String, message: String, notificationId: Int) {
        initNotificationChannel(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        if (ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } else {
            Log.w("NotificationHelper", "POST_NOTIFICATIONS permission not granted.")
        }
    }

    // 범용 이벤트 알림 (예: 수면, 울음, 기타 이벤트)
    fun notifyEvent(context: Context, message: String) {
        showNotification(context, "알림", message, 104)
    }


    fun notifySleepStarted(context: Context, message: String) {
        initNotificationChannel(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("수면 알림")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 200))  // 진동 패턴 추가

        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(context).notify(104, builder.build())
        } else {
            Log.w("NotificationHelper", "POST_NOTIFICATIONS permission not granted.")
        }
    }

    // NotificationHelper.kt 내에 추가/수정
    fun notifyCameraEvent(context: Context, event: String) {
        initNotificationChannel(context)
        val prefs = context.getSharedPreferences("viewer_prefs", Context.MODE_PRIVATE)
        // 카메라 기기의 아기 이름은 예를 들어 "camera_baby_name" 키에 저장되어 있다고 가정합니다.
        val cameraBabyName = prefs.getString("camera_baby_name", "BABY") ?: "BABY"
        val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        val (triple, id) = when (event) {
            "수면" -> Triple("수면 알림", "$cameraBabyName 이(가) 잠들었어요. ($currentTime)", "sleep_group") to 100
            "기상" -> Triple("기상 알림", "$cameraBabyName 이(가) 일어났어요. ($currentTime)", "sleep_group") to 100
            "얼굴 미감지" -> Triple("얼굴 미감지 알림", "$cameraBabyName 이(가) 수면 중에 얼굴 인식에 실패했습니다. ($currentTime)", "sleep_group") to 100
            "행복" -> Triple("웃음 알림", "$cameraBabyName 이(가) 웃고 있어요. ($currentTime)", "expression_group") to 200
            "슬픔" -> Triple("울음 알림", "$cameraBabyName 이(가) 울고 있어요. ($currentTime)", "expression_group") to 200
            else -> Triple("알림", event, "default_group") to (event.hashCode() and 0xFFFF)
        }
        val (title, message, groupKey) = triple
        val notifId = id

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setGroup(groupKey)

        // 고정 ID를 사용해서 기존 알림을 업데이트하도록 함
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(context).notify(notifId, builder.build())
        } else {
            Log.w("NotificationHelper", "POST_NOTIFICATIONS permission not granted.")
        }
    }

}
