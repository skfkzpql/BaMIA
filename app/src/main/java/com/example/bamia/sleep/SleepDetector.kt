//package com.example.bamia.sleep
//
//import android.content.Context
//import android.util.Log
//import com.example.bamia.notifications.NotificationHelper
//
///**
// * SleepDetector
// *
// * 5분 이상 아기의 눈이 닫힌 상태가 지속되면 수면으로 판단하고,
// * 수면 중에 일정 시간(5분) 동안 눈이 열리면 수면 종료로 판단하는 로직을 구현합니다.
// *
// * - updateEyesStatus(leftEyeProb, rightEyeProb) 를 주기적으로 호출하여 상태를 업데이트합니다.
// * - 상태 전이가 발생하면 NotificationHelper를 통해 알림을 전송합니다.
// */
//class SleepDetector(private val context: Context) {
//    // 현재 수면 상태: true면 수면 중, false면 깨어 있음.
//    private var isSleeping: Boolean = false
//    // 눈이 닫힌 상태가 시작된 시각 (수면 시작 타이머)
//    private var closedStartTime: Long? = null
//    // 눈이 열린 상태가 시작된 시각 (수면 종료 타이머)
//    private var openStartTime: Long? = null
//
//    // 5분 = 300,000 밀리초
//    private val thresholdMillis = 5 * 60 * 1000L
//
//    /**
//     * 왼쪽과 오른쪽 눈의 열림 확률을 업데이트합니다.
//     *
//     * - closedThreshold: 0.5 미만이면 눈이 닫힌 것으로 간주
//     * - openThreshold: 0.8 초과이면 눈이 열린 것으로 간주
//     *
//     * 수면 상태가 아닐 때 5분 이상 연속해서 눈이 닫히면 수면 시작.
//     * 수면 상태일 때 5분 이상 연속해서 눈이 열리면 수면 종료.
//     */
//    fun updateEyesStatus(leftEyeProb: Float?, rightEyeProb: Float?) {
//        val now = System.currentTimeMillis()
//        val closedThreshold = 0.5f
//        val openThreshold = 0.8f
//
//        // 만약 값이 null이면 타이머 초기화
//        if (leftEyeProb == null || rightEyeProb == null) {
//            closedStartTime = null
//            openStartTime = null
//            return
//        }
//
//        val eyesClosed = leftEyeProb < closedThreshold && rightEyeProb < closedThreshold
//        val eyesOpen = leftEyeProb > openThreshold && rightEyeProb > openThreshold
//
//        if (!isSleeping) {
//            // 깨어 있을 때: 눈이 닫혔다면 closedStartTime 기록
//            if (eyesClosed) {
//                if (closedStartTime == null) {
//                    closedStartTime = now
//                    Log.d("SleepDetector", "Eyes closed detected. Timer started.")
//                } else {
//                    if (now - closedStartTime!! >= thresholdMillis) {
//                        isSleeping = true
//                        Log.d("SleepDetector", "Sleep started (5분 이상 눈 감음)")
//                        NotificationHelper.notifySleepStateChange(context, true)
//                        // 수면 상태로 전환되면 열린 상태 타이머 초기화
//                        openStartTime = null
//                    }
//                }
//            } else {
//                // 눈이 열리면 타이머 초기화
//                closedStartTime = null
//            }
//        } else {
//            // 수면 중일 때: 눈이 열린 경우 openStartTime 기록
//            if (eyesOpen) {
//                if (openStartTime == null) {
//                    openStartTime = now
//                    Log.d("SleepDetector", "Eyes open detected during sleep. Timer started.")
//                } else {
//                    if (now - openStartTime!! >= thresholdMillis) {
//                        isSleeping = false
//                        Log.d("SleepDetector", "Sleep ended (5분 이상 눈 열림)")
//                        NotificationHelper.notifySleepStateChange(context, false)
//                        // 깨어난 상태로 전환되면 닫힘 타이머 초기화
//                        closedStartTime = null
//                    }
//                }
//            } else {
//                // 눈이 닫혀있으면 열린 상태 타이머 초기화
//                openStartTime = null
//            }
//        }
//    }
//
//    /**
//     * 현재 수면 상태를 반환합니다.
//     */
//    fun isSleeping(): Boolean = isSleeping
//}
