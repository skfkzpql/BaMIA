package com.example.bamia.streamingcontrol

import android.content.Context
import android.util.Log
import com.example.bamia.streaming.FrameBuffer

/**
 * StreamingController
 *
 * 스트리밍 시작/종료, 카메라 활성화/비활성화, 절전모드(수면 모드) 전환을 관리합니다.
 * - startStreaming()/stopStreaming() : 스트리밍 활성/비활성 상태를 관리합니다.
 * - toggleStreaming() : 스트리밍 상태를 전환합니다.
 * - setSleepMode()/toggleSleepMode() : 절전모드(수면 모드) 상태를 전환합니다.
 *
 * 이 클래스는 CameraModeActivity 등에서 UI와 연동하여 호출할 수 있습니다.
 */
object StreamingController {
    private var streamingActive = false
    private var sleepModeActive = false

    /**
     * 스트리밍을 시작합니다.
     * (필요한 경우 추가 초기화 작업을 수행할 수 있습니다.)
     */
    fun startStreaming(context: Context) {
        streamingActive = true
        Log.d("StreamingController", "Streaming started")
        // 예시: 현재 프레임 버퍼에 임시 데이터를 초기화할 수 있습니다.
        // FrameBuffer.currentFrame = ... (실제 카메라 프리뷰가 업데이트되는 로직과 연동)
    }

    /**
     * 스트리밍을 중지합니다.
     */
    fun stopStreaming(context: Context) {
        streamingActive = false
        Log.d("StreamingController", "Streaming stopped")
        // 스트리밍 중지를 위해 프레임 버퍼 초기화
        FrameBuffer.currentFrame = null
    }

    /**
     * 스트리밍 상태를 토글합니다.
     */
    fun toggleStreaming(context: Context) {
        if (streamingActive) {
            stopStreaming(context)
        } else {
            startStreaming(context)
        }
    }

    /**
     * 현재 스트리밍 활성 상태를 반환합니다.
     */
    fun isStreamingActive(): Boolean = streamingActive

    /**
     * 절전모드(수면 모드)를 설정합니다.
     * 절전모드일 때는 화면은 어둡게 처리되지만, 스트리밍은 계속 동작합니다.
     */
    fun setSleepMode(context: Context, on: Boolean) {
        sleepModeActive = on
        if (on) {
            Log.d("StreamingController", "Sleep mode activated")
        } else {
            Log.d("StreamingController", "Sleep mode deactivated")
        }
        // 필요에 따라 UI나 서비스에 상태 변경을 알리는 코드를 추가할 수 있습니다.
    }

    /**
     * 절전모드(수면 모드)를 토글합니다.
     */
    fun toggleSleepMode(context: Context) {
        setSleepMode(context, !sleepModeActive)
    }

    /**
     * 현재 절전모드 상태를 반환합니다.
     */
    fun isSleepModeActive(): Boolean = sleepModeActive
}
