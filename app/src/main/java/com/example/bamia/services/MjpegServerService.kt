package com.example.bamia.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.example.bamia.managers.NetworkManager
import com.example.bamia.streaming.FrameBuffer
import com.example.bamia.ui.ExpressionInfoHolder
import com.example.bamia.ui.SleepInfoHolder
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.newChunkedResponse
import fi.iki.elonen.NanoHTTPD.newFixedLengthResponse

class MjpegServerService : Service() {

    private var server: NanoHTTPD? = null
    private val port = 8080

    override fun onCreate() {
        super.onCreate()
        Log.d("MjpegServerService", "onCreate called")
        server = object : NanoHTTPD(port) {
            override fun serve(session: IHTTPSession): Response {
                // 새로운 엔드포인트: /expression
                if (session.uri == "/expression") {
                    val reqPin = session.parameters["pin"]?.firstOrNull() ?: ""
                    val networkManager = NetworkManager.getInstance(applicationContext)
                    if (reqPin != networkManager.pin) {
                        Log.d("MjpegServerService", "Invalid PIN for /expression")
                        return newFixedLengthResponse(
                            Response.Status.UNAUTHORIZED,
                            "text/plain",
                            "Invalid PIN"
                        )
                    }
                    val currentExpr = ExpressionInfoHolder.currentExpression
                    Log.d("MjpegServerService", "Serving expression: $currentExpr")
                    return newFixedLengthResponse(Response.Status.OK, "text/plain", currentExpr)
                }

                if (session.uri == "/sleep") {
                    val reqPin = session.parameters["pin"]?.firstOrNull() ?: ""
                    val networkManager = NetworkManager.getInstance(applicationContext)
                    if (reqPin != networkManager.pin) {
                        return newFixedLengthResponse(Response.Status.UNAUTHORIZED, "text/plain", "Invalid PIN")
                    }
                    val message = SleepInfoHolder.sleepMessage
                    // 옵션: 반환 후 SleepInfoHolder.sleepMessage를 초기화할 수 있음
                    SleepInfoHolder.sleepMessage = ""
                    Log.d("MjpegServerService", "Serving sleep message: $message")
                    return newFixedLengthResponse(Response.Status.OK, "text/plain", message)
                }

                // 기존 MJPEG 스트리밍 코드
                val reqPin = session.parameters["pin"]?.firstOrNull() ?: ""
                val networkManager = NetworkManager.getInstance(applicationContext)
                if (reqPin != networkManager.pin) {
                    Log.d("MjpegServerService", "Invalid PIN: $reqPin, expected: ${networkManager.pin}")
                    return newFixedLengthResponse(
                        Response.Status.UNAUTHORIZED,
                        "text/plain",
                        "Invalid PIN"
                    )
                }
                Log.d("MjpegServerService", "PIN validated, serving MJPEG stream")
                val response = newChunkedResponse(
                    Response.Status.OK,
                    "multipart/x-mixed-replace; boundary=--BoundaryString",
                    MJpegStream()
                )
                response.addHeader("Cache-Control", "no-cache")
                response.addHeader("Connection", "close")
                return response
            }
        }
        try {
            server?.start()
            Log.d("MjpegServerService", "MJPEG Server started on port $port")
        } catch (e: Exception) {
            Log.e("MjpegServerService", "Failed to start MJPEG server", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        server?.stop()
        Log.d("MjpegServerService", "MJPEG Server stopped")
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.d("MjpegServerService", "onBind called")
        return null
    }
}

class MJpegStream : java.io.InputStream() {
    private val boundary = "\r\n--BoundaryString\r\n"
    private var currentChunk: ByteArray? = null
    private var currentOffset: Int = 0

    override fun read(): Int = -1

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (currentChunk == null || currentOffset >= currentChunk!!.size) {
            val current = FrameBuffer.currentFrame
            if (current == null || current.isEmpty()) {
                Thread.sleep(100)
                return 0
            }
            val header = "Content-Type: image/jpeg\r\nContent-Length: ${current.size}\r\n\r\n"
            currentChunk = boundary.toByteArray() + header.toByteArray() + current
            currentOffset = 0
        }
        val remaining = currentChunk!!.size - currentOffset
        val toCopy = if (len < remaining) len else remaining
        System.arraycopy(currentChunk, currentOffset, b, off, toCopy)
        currentOffset += toCopy
        if (currentOffset >= currentChunk!!.size) {
            currentChunk = null
            Thread.sleep(50)
        }
        return toCopy
    }
}
