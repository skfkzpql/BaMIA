package com.example.bamia.managers

import android.content.Context
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 로그 기록 및 파일 관리를 담당합니다.
 * 로그 파일(camera_mode_log.txt)에 기록하고, 당일 처음 감지된 표정만 기록하도록 합니다.
 */
class LogManager private constructor(private val context: Context) {

    private val logFile: File = File(context.filesDir, "camera_mode_log.txt")
    private val loggedExpressions = mutableSetOf<String>()
    private var currentDate: String = getCurrentDate()

    init {
        if (!logFile.exists()) {
            logFile.createNewFile()
        }
    }

    fun logEvent(event: String) {
        val timestamp = getCurrentTimestamp()
        val logEntry = "$timestamp: $event\n"
        PrintWriter(FileWriter(logFile, true)).use { it.print(logEntry) }
        if (event.startsWith("Auto-captured")) {
            val expression = event.substringAfter("Expression detected: ").substringBefore(" at ")
            loggedExpressions.add(expression)
        }
    }

    fun isExpressionLoggedToday(expression: String): Boolean {
        val today = getCurrentDate()
        if (today != currentDate) {
            currentDate = today
            loggedExpressions.clear()
        }
        return loggedExpressions.contains(expression)
    }

    private fun getCurrentDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    private fun getCurrentTimestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    }

    companion object {
        @Volatile private var instance: LogManager? = null

        fun getInstance(context: Context): LogManager =
            instance ?: synchronized(this) {
                instance ?: LogManager(context.applicationContext).also { instance = it }
            }
    }
}
