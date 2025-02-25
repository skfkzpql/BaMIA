package com.example.bamia.streaming

import android.util.Log

/**
 * 현재 프레임 버퍼를 관리하는 싱글턴 객체.
 * MJPEG 스트리밍 시 최신 프레임을 저장하고, 업데이트 시 로그를 출력합니다.
 */
object FrameBuffer {
    @Volatile
    var currentFrame: ByteArray? = null
        set(value) {
            field = value
            Log.d("FrameBuffer", "New frame updated, size: ${value?.size ?: 0} bytes")
        }
}
