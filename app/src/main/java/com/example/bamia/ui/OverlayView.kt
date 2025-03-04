package com.example.bamia.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View

/**
 * 얼굴 인식 결과(경계 박스 및 텍스트)를 그리는 커스텀 뷰.
 * - 얼굴이 감지되면 첫 번째 얼굴 주변에 경계 박스를 그리고,
 * - 케밥 메뉴 옵션에 따라 표정, 눈 열림 확률, 웃음 확률 정보를 화면의 왼쪽 상단에 고정하여 표시합니다.
 */
data class FaceResult(
    val boundingBox: Rect,
    val predictedExpression: String,
    val leftEyeProbability: Float?,
    val rightEyeProbability: Float?,
    val smilingProbability: Float?
)

class OverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var faceResults: List<FaceResult> = emptyList()

    private val boxPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    // 텍스트를 흰색으로 설정
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 40f
        isAntiAlias = true
    }
    // 텍스트 배경용 Paint: 회색 배경
    private val bgPaint = Paint().apply {
        color = Color.GRAY
    }

    /**
     * 얼굴 인식 결과 업데이트.
     */
    fun updateResults(results: List<FaceResult>) {
        faceResults = results
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (faceResults.isEmpty()) return

        // 첫 번째 얼굴만 사용
        val face = faceResults[0]
        // 얼굴 경계 박스 (옵션에 따라)
        if (ExpressionDisplayConfig.showFaceBox) {
            canvas.drawRect(face.boundingBox, boxPaint)
        }

        // 고정 위치 텍스트를 위한 문자열 구성 (옵션에 따라)
        var drawText = false
        val sb = StringBuilder()
        if (ExpressionDisplayConfig.showExpression) {
            sb.append("표정: ${face.predictedExpression}")
            drawText = true
        }
        if (ExpressionDisplayConfig.showEyeProbability) {
            if (drawText) sb.append("  ")
            sb.append("눈: L=${face.leftEyeProbability?.let { String.format("%.2f", it) } ?: "N/A"} " +
                    "R=${face.rightEyeProbability?.let { String.format("%.2f", it) } ?: "N/A"}")
            drawText = true
        }
        if (ExpressionDisplayConfig.showSmileProbability) {
            if (drawText) sb.append("  ")
            sb.append("웃음: ${face.smilingProbability?.let { String.format("%.2f", it) } ?: "N/A"}")
            drawText = true
        }

        if (drawText) {
            val text = sb.toString()
            val padding = 16f
            val textWidth = textPaint.measureText(text)
            val textHeight = textPaint.descent() - textPaint.ascent()
            // 고정 위치: preview 영역의 왼쪽 상단 (예, x=20, y=100)
            val x = 20f
            val y = 100f
            // 회색 배경 사각형
            canvas.drawRect(x - padding, y + textPaint.ascent() - padding, x + textWidth + padding, y + textPaint.descent() + padding, bgPaint)
            // 흰색 텍스트 출력
            canvas.drawText(text, x, y, textPaint)
        }
    }

}
