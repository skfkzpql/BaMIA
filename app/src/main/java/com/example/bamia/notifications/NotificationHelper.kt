package com.example.bamia.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.bamia.R

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
}
