package com.example.bamia.activities

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.bamia.R
import com.example.bamia.gallery.GalleryManager
import com.example.bamia.gallery.SavedImage
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

class FullScreenImageActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_full_screen_image) // 전체화면 레이아웃

        val imageView = findViewById<ImageView>(R.id.fullImageView)
        val btnBack = findViewById<ImageButton>(R.id.btnBackFullScreen)
        val tvFileName = findViewById<TextView>(R.id.tvFileName)
        val btnDelete = findViewById<ImageButton>(R.id.btnDeleteFullScreen)

        val displayName = intent.getStringExtra("displayName") ?: "파일명 없음"
        tvFileName.text = displayName

        btnBack.setOnClickListener { finish() }

        val imagePath = intent.getStringExtra("imagePath")
        if (imagePath != null) {
            if (imagePath.startsWith("content://")) {
                imageView.setImageURI(Uri.parse(imagePath))
            } else {
                val bitmap = BitmapFactory.decodeFile(imagePath)
                imageView.setImageBitmap(bitmap)
            }
        }

        findViewById<ImageButton>(R.id.btnDeleteFullScreen).setOnClickListener {
            MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)
                .setTitle("삭제 확인")
                .setMessage("이 이미지를 삭제하시겠습니까?")
                .setPositiveButton("삭제") { _, _ ->
                    val imagePath = intent.getStringExtra("imagePath")
                    if (imagePath != null) {
                        val deleted = if (imagePath.startsWith("content://")) {
                            GalleryManager.deleteImage(
                                this,
                                SavedImage(
                                    uri = Uri.parse(imagePath),
                                    file = null,
                                    displayName = "",
                                    babyName = "",
                                    captureExpression = "",
                                    captureTimestamp = ""
                                )
                            )
                        } else {
                            GalleryManager.deleteImage(
                                this,
                                SavedImage(
                                    uri = null,
                                    file = File(imagePath),
                                    displayName = "",
                                    babyName = "",
                                    captureExpression = "",
                                    captureTimestamp = ""
                                )
                            )
                        }
                        if (deleted) {
                            setResult(RESULT_OK)
                            finish()  // 삭제 성공 시 전체화면 Activity 종료
                        } else {
                            // 삭제 실패 시 토스트 메시지 등 추가 처리
                            MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)
                                .setTitle("삭제 실패")
                                .setMessage("이미지 삭제에 실패했습니다.")
                                .setPositiveButton("확인", null)
                                .show()
                        }
                    }
                }
                .setNegativeButton("취소") { dialog, _ -> dialog.dismiss() }
                .show()
        }

    }
}
