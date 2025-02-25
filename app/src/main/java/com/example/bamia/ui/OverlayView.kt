package com.example.bamia.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View

/**
 * 얼굴 인식 결과(경계 박스 및 표정 정보)를 화면에 오버레이로 그리는 커스텀 뷰.
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
    private val textPaint = Paint().apply {
        color = Color.RED
        textSize = 40f
    }

    /**
     * 얼굴 인식 결과를 업데이트하고 뷰를 다시 그립니다.
     */
    fun updateResults(results: List<FaceResult>) {
        faceResults = results
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (face in faceResults) {
            // 경계 사각형 그리기
            canvas.drawRect(face.boundingBox, boxPaint)
            // 텍스트: 표정, 좌우 눈 확률, 웃음 확률
            val text = "Expr: ${face.predictedExpression}\n" +
                    "L: ${face.leftEyeProbability?.let { String.format("%.2f", it) } ?: "N/A"} " +
                    "R: ${face.rightEyeProbability?.let { String.format("%.2f", it) } ?: "N/A"}\n" +
                    "Smile: ${face.smilingProbability?.let { String.format("%.2f", it) } ?: "N/A"}"
            // 텍스트를 경계 박스 위에 출력
            canvas.drawText(text, face.boundingBox.left.toFloat(), (face.boundingBox.top - 10).toFloat(), textPaint)
        }
    }
}
