package com.example.bamia.gallery

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
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
)

object GalleryManager {
    private const val FOLDER_NAME = "BabyCamGallery"
    private const val IMAGE_PREFIX = "BaMIA"
    private const val IMAGE_FORMAT = "jpg"

    // 저장 시 사용할 포맷 (예: 20230315_123456)
    private val saveTimeFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    // 표시 시 사용할 포맷 (예: 2023-03-15 12:34)
    private val displayTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    // 파일명 형식: BaMIA_아기이름_YYYYMMdd_HHmmss_<expression>.jpg
    private fun generateFileName(babyName: String, captureExpression: String): String {
        val timeStamp = saveTimeFormat.format(Date())
        val sanitizedBabyName = babyName.replace(" ", "_")
        val sanitizedExpression = captureExpression.replace(" ", "_").lowercase(Locale.getDefault())
        return "${IMAGE_PREFIX}_${sanitizedBabyName}_${timeStamp}_${sanitizedExpression}.$IMAGE_FORMAT"
    }

    fun saveCapturedImage(context: Context, bitmap: android.graphics.Bitmap, babyName: String, captureExpression: String, callback: (filePath: String?) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveImageUsingMediaStore(context, bitmap, babyName, captureExpression, callback)
        } else {
            saveImageLegacy(context, bitmap, babyName, captureExpression, callback)
        }
    }

    private fun saveImageUsingMediaStore(context: Context, bitmap: android.graphics.Bitmap, babyName: String, captureExpression: String, callback: (filePath: String?) -> Unit) {
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
                if (bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, outStream)) {
                    callback(uri.toString())
                } else {
                    callback(null)
                }
            } ?: callback(null)
        } else {
            callback(null)
        }
    }

    private fun saveImageLegacy(context: Context, bitmap: android.graphics.Bitmap, babyName: String, captureExpression: String, callback: (filePath: String?) -> Unit) {
        val galleryDir = getGalleryDirectory(context)
        val fileName = generateFileName(babyName, captureExpression)
        val imageFile = File(galleryDir, fileName)
        try {
            FileOutputStream(imageFile).use { out ->
                if (bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)) {
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

    private fun getGalleryDirectory(context: Context): File {
        val externalFilesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val galleryDir = File(externalFilesDir, FOLDER_NAME)
        if (!galleryDir.exists()) {
            galleryDir.mkdirs()
        }
        return galleryDir
    }

    // 파싱 함수들: 파일명에서 각 항목 추출
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
            val galleryDir = getGalleryDirectory(context)
            galleryDir.listFiles { file ->
                file.extension.equals(IMAGE_FORMAT, ignoreCase = true)
            }?.sortedByDescending { it.lastModified() }?.map { file ->
                val displayName = file.name
                val babyName = parseBabyName(displayName)
                val captureExpression = parseCaptureExpression(displayName)
                val timestamp = parseTimestamp(displayName)
                SavedImage(uri = null, file = file, displayName = displayName, babyName = babyName, captureExpression = captureExpression, captureTimestamp = timestamp)
            } ?: emptyList()
        }
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

    fun filterSavedImages(context: Context, filterType: FilterType, filterValue: String): List<SavedImage> {
        val images = getSavedImages(context)
        return images.filter { savedImage ->
            when (filterType) {
                FilterType.BABY_NAME -> savedImage.babyName.equals(filterValue, ignoreCase = true)
                FilterType.YEAR -> savedImage.captureTimestamp.startsWith(filterValue)
                FilterType.MONTH -> savedImage.captureTimestamp.substring(0, 7).replace("-", "") == filterValue
                FilterType.DAY -> savedImage.captureTimestamp.substring(0, 10).replace("-", "") == filterValue
                FilterType.EXPRESSION -> savedImage.captureExpression.equals(filterValue, ignoreCase = true)
            }
        }
    }
}
