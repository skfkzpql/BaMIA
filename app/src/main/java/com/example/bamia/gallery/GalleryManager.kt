package com.example.bamia.gallery

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.io.Serializable
import java.util.*

enum class FilterType {
    BABY_NAME, YEAR, MONTH, DAY, EXPRESSION
}

data class SavedImage(
    val uri: Uri?,          // MediaStore 사용 시
    val file: File?,        // Legacy 방식 사용 시
    val displayName: String, // 파일명 전체 (예: BaMIA_아기이름_YYYYMMdd_HHmmss_<expression>.jpg)
    val babyName: String,
    val captureExpression: String,
    val captureTimestamp: String  // "yyyy-MM-dd HH:mm" 형식
) : Serializable

object GalleryManager {
    // 기존 "BabyCamGallery" -> "BaMIA"로 변경
    private const val FOLDER_NAME = "BaMIA"
    private const val IMAGE_PREFIX = "BaMIA" // 필요에 따라 변경
    private const val IMAGE_FORMAT = "jpg"
    private val saveTimeFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    private val displayTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    private fun generateFileName(babyName: String, captureExpression: String): String {
        val timeStamp = saveTimeFormat.format(Date())
        val sanitizedBabyName = babyName.replace(" ", "_")
        val sanitizedExpression = captureExpression.replace(" ", "_").lowercase(Locale.getDefault())
        return "${IMAGE_PREFIX}_${sanitizedBabyName}_${timeStamp}_${sanitizedExpression}.$IMAGE_FORMAT"
    }

    // API ≥ Q: MediaStore를 이용하여 Pictures/BaMIA에 저장
    private fun saveImageUsingMediaStore(context: Context, bitmap: Bitmap, babyName: String, captureExpression: String, callback: (filePath: String?) -> Unit) {
        val resolver = context.contentResolver
        val fileName = generateFileName(babyName, captureExpression)
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + FOLDER_NAME)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            resolver.openOutputStream(uri)?.use { outStream ->
                if (bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outStream)) {
                    callback(uri.toString())
                } else {
                    callback(null)
                }
            } ?: callback(null)
        } else {
            callback(null)
        }
    }

    // API < Q: 저장 경로를 기기의 공용 갤러리 (Pictures/BaMIA)로 지정
    private fun saveImageLegacy(context: Context, bitmap: Bitmap, babyName: String, captureExpression: String, callback: (filePath: String?) -> Unit) {
        val galleryDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), FOLDER_NAME)
        if (!galleryDir.exists()) {
            galleryDir.mkdirs()
        }
        val fileName = generateFileName(babyName, captureExpression)
        val imageFile = File(galleryDir, fileName)
        try {
            FileOutputStream(imageFile).use { out ->
                if (bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)) {
                    // 갤러리 업데이트를 위한 MediaScanner 연결 필요
                    callback(imageFile.absolutePath)
                } else {
                    callback(null)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            callback(null)
        }
    }

    // 외부에서 호출 가능한 이미지 저장 함수
    fun saveCapturedImage(context: Context, bitmap: Bitmap, babyName: String, captureExpression: String, callback: (filePath: String?) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveImageUsingMediaStore(context, bitmap, babyName, captureExpression, callback)
        } else {
            saveImageLegacy(context, bitmap, babyName, captureExpression, callback)
        }
    }

    // 갤러리 조회: Pictures/BaMIA 폴더에 저장된 이미지만 반환
    fun getSavedImages(context: Context): List<SavedImage> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val savedImages = mutableListOf<SavedImage>()
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME
            )
            val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
            val selectionArgs = arrayOf("%${Environment.DIRECTORY_PICTURES}${File.separator}$FOLDER_NAME%")
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
            context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val displayName = cursor.getString(nameColumn)
                    val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    val babyName = parseBabyName(displayName)
                    val captureExpression = parseCaptureExpression(displayName)
                    val timestamp = parseTimestamp(displayName)
                    savedImages.add(SavedImage(uri = contentUri, file = null, displayName = displayName, babyName = babyName, captureExpression = captureExpression, captureTimestamp = timestamp))
                }
            }
            savedImages
        } else {
            val galleryDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), FOLDER_NAME)
            galleryDir.listFiles { file -> file.extension.equals(IMAGE_FORMAT, ignoreCase = true) }?.sortedByDescending { it.lastModified() }?.map { file ->
                val displayName = file.name
                val babyName = parseBabyName(displayName)
                val captureExpression = parseCaptureExpression(displayName)
                val timestamp = parseTimestamp(displayName)
                SavedImage(uri = null, file = file, displayName = displayName, babyName = babyName, captureExpression = captureExpression, captureTimestamp = timestamp)
            } ?: emptyList()
        }
    }

    private fun getGalleryDirectory(context: Context): File {
        val externalFilesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val galleryDir = File(externalFilesDir, FOLDER_NAME)
        if (!galleryDir.exists()) {
            galleryDir.mkdirs()
        }
        return galleryDir
    }

    // 파일명에서 각 항목을 추출하는 함수
    // 파일명 형식: BaMIA_아기이름_YYYYMMdd_HHmmss_<expression>.jpg
    private fun parseBabyName(name: String): String {
        val parts = name.split("_")
        return if (parts.size >= 2) parts[1] else ""
    }

    private fun parseTimestamp(name: String): String {
        return try {
            val parts = name.split("_")
            if (parts.size >= 4) {
                val ts = parts[2] + "_" + parts[3].substringBeforeLast(".")
                val date = saveTimeFormat.parse(ts)
                if (date != null) displayTimeFormat.format(date) else ""
            } else ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun parseCaptureExpression(name: String): String {
        val parts = name.split("_")
        return if (parts.size >= 5) parts[4].substringBeforeLast(".") else ""
    }

    fun deleteImage(context: Context, savedImage: SavedImage): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            savedImage.uri?.let { uri ->
                val rowsDeleted = context.contentResolver.delete(uri, null, null)
                rowsDeleted > 0
            } ?: false
        } else {
            savedImage.file?.delete() ?: false
        }
    }

    // 필터링 기능: 필터 타입에 따라 이미지 목록을 필터링한다.
    // 특히, EXPRESSION의 경우, 저장된 captureExpression(영어로 저장된 값)을 한국어 필터값으로 매핑하여 비교한다.
    fun filterSavedImages(context: Context, filterType: FilterType, filterValue: String): List<SavedImage> {
        val images = getSavedImages(context)
        return images.filter { savedImage ->
            when (filterType) {
                FilterType.BABY_NAME -> savedImage.babyName.trim().equals(filterValue.trim(), ignoreCase = true)
                FilterType.YEAR -> savedImage.captureTimestamp.startsWith(filterValue)
                FilterType.MONTH -> savedImage.captureTimestamp.substring(0, 7).replace("-", "").equals(filterValue.trim(), ignoreCase = true)
                FilterType.DAY -> savedImage.captureTimestamp.substring(0, 10).replace("-", "").equals(filterValue.trim(), ignoreCase = true)
                FilterType.EXPRESSION -> {
                    val mapped = mapExpressionToKorean(savedImage.captureExpression)
                    mapped.trim().equals(filterValue.trim(), ignoreCase = true)
                }
            }
        }
    }

    private fun mapExpressionToKorean(expression: String): String {
        return when (expression.trim().lowercase(Locale.getDefault())) {
            "neutral" -> "중립"
            "happiness", "happy" -> "행복"
            "sadness", "sad" -> "슬픔"
            "surprise" -> "놀람"
            "fear" -> "두려움"
            "disgust" -> "혐오"
            "anger" -> "분노"
            "camera" -> "캡쳐"
            else -> expression.trim()  // 매핑되지 않으면 원래 값을 반환
        }
    }
}
