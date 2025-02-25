package com.example.bamia.analysis

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import androidx.annotation.WorkerThread
import com.google.mlkit.vision.common.InputImage
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * TensorFlow Lite를 사용하여 얼굴의 표정을 분석합니다.
 * assets 폴더 내의 "expression_model.tflite" 모델을 사용하며, AffectNet 7 클래스 중 하나를 반환합니다.
 */
class ExpressionAnalyzer(context: Context) {

    private var interpreter: Interpreter

    private val affectnetClasses = arrayOf(
        "Anger", "Disgust", "Fear", "Happiness", "Neutral", "Sadness", "Surprise"
    )

    init {
        interpreter = Interpreter(loadModelFile(context, "expression_model.tflite"))
    }

    private fun loadModelFile(context: Context, modelFilename: String): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd(modelFilename)
        val fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = fileInputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            assetFileDescriptor.startOffset,
            assetFileDescriptor.declaredLength
        )
    }

    /**
     * 얼굴 영역을 확장(20% 마진 추가) 후 크롭하여 전처리한 뒤 모델 추론을 수행합니다.
     * 입력 이미지는 224×224 크기로 리사이즈되며, AffectNet 7 클래스 중 하나를 반환합니다.
     */
    @WorkerThread
    fun analyzeExpression(image: InputImage, faceBoundingBox: Rect): String {
        val bitmap = image.bitmapInternal ?: run {
            Log.e("ExpressionAnalyzer", "InputImage does not contain an internal bitmap")
            return "unknown"
        }

        val marginFactor = 0.2f
        val expandedLeft = faceBoundingBox.left - (faceBoundingBox.width() * marginFactor).toInt()
        val expandedTop = faceBoundingBox.top - (faceBoundingBox.height() * marginFactor).toInt()
        val expandedRight = faceBoundingBox.right + (faceBoundingBox.width() * marginFactor).toInt()
        val expandedBottom = faceBoundingBox.bottom + (faceBoundingBox.height() * marginFactor).toInt()

        val clampedLeft = expandedLeft.coerceAtLeast(0)
        val clampedTop = expandedTop.coerceAtLeast(0)
        val clampedRight = expandedRight.coerceAtMost(bitmap.width)
        val clampedBottom = expandedBottom.coerceAtMost(bitmap.height)
        val expandedRect = Rect(clampedLeft, clampedTop, clampedRight, clampedBottom)

        val faceBitmap = Bitmap.createBitmap(
            bitmap,
            expandedRect.left,
            expandedRect.top,
            expandedRect.width(),
            expandedRect.height()
        )

        val inputSize = 224
        val resizedBitmap = Bitmap.createScaledBitmap(faceBitmap, inputSize, inputSize, false)

        val inputBuffer = convertBitmapToByteBuffer(resizedBitmap)
        val output = Array(1) { FloatArray(7) }
        interpreter.run(inputBuffer, output)
        Log.d("ExpressionAnalyzer", "TFLite raw output: ${output[0].joinToString(", ")}")
        val maxIndex = output[0].indices.maxByOrNull { output[0][it] } ?: -1
        return if (maxIndex in affectnetClasses.indices) affectnetClasses[maxIndex] else "unknown"
    }

    /**
     * Bitmap의 픽셀 데이터를 float 배열로 변환.
     * 각 픽셀에 대해 Blue, Green, Red 순서로 추출 후, 각각의 평균값(103.939, 116.779, 123.68)을 빼서 정규화합니다.
     */
    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val inputSize = 224
        val byteBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        byteBuffer.order(ByteOrder.nativeOrder())
        val intValues = IntArray(inputSize * inputSize)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (pixel in intValues) {
            byteBuffer.putFloat(((pixel and 0xFF).toFloat() - 103.939f))
            byteBuffer.putFloat((((pixel shr 8) and 0xFF).toFloat() - 116.779f))
            byteBuffer.putFloat((((pixel shr 16) and 0xFF).toFloat() - 123.68f))
        }
        byteBuffer.rewind()
        return byteBuffer
    }

    fun close() {
        interpreter.close()
    }
}
