package com.example.bamia.activities

import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.graphics.Matrix
import android.media.Image
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

object ImageUtil {
    @OptIn(ExperimentalGetImage::class)
    fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val mediaImage = imageProxy.image ?: return null
        return yuv420ToBitmap(mediaImage, imageProxy.width, imageProxy.height, imageProxy.planes)?.let { bmp ->
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            if (rotationDegrees != 0) {
                val matrix = Matrix()
                matrix.postRotate(rotationDegrees.toFloat())
                Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
            } else {
                bmp
            }
        }
    }

    fun yuv420ToBitmap(image: Image, width: Int, height: Int, planes: Array<ImageProxy.PlaneProxy>): Bitmap? {
        return try {
            val yBuffer = planes[0].buffer
            val uBuffer = planes[1].buffer
            val vBuffer = planes[2].buffer

            val yRowStride = planes[0].rowStride
            val uvRowStride = planes[1].rowStride
            val uvPixelStride = planes[1].pixelStride

            val bitmap = Bitmap.createBitmap(width, height, Config.ARGB_8888)
            val pixels = IntArray(width * height)

            for (j in 0 until height) {
                for (i in 0 until width) {
                    val yIndex = j * yRowStride + i
                    val y = 0xff and yBuffer.get(yIndex).toInt()
                    val uvIndex = (j / 2) * uvRowStride + (i / 2) * uvPixelStride
                    val u = 0xff and uBuffer.get(uvIndex).toInt()
                    val v = 0xff and vBuffer.get(uvIndex).toInt()

                    var r = (y + 1.370705f * (v - 128)).toInt()
                    var g = (y - 0.337633f * (u - 128) - 0.698001f * (v - 128)).toInt()
                    var b = (y + 1.732446f * (u - 128)).toInt()
                    r = r.coerceIn(0, 255)
                    g = g.coerceIn(0, 255)
                    b = b.coerceIn(0, 255)
                    pixels[j * width + i] = -0x1000000 or (r shl 16) or (g shl 8) or b
                }
            }
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
