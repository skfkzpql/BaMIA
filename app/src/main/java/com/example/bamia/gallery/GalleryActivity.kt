package com.example.bamia.gallery

import android.app.AlertDialog
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.bamia.R

class GalleryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: GalleryAdapter
    private lateinit var spinnerFilterType: Spinner
    private lateinit var etFilterValue: EditText
    private lateinit var btnApplyFilter: Button

    private var currentImages: List<SavedImage> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)

        spinnerFilterType = findViewById(R.id.spinnerFilterType)
        etFilterValue = findViewById(R.id.etFilterValue)
        btnApplyFilter = findViewById(R.id.btnApplyFilter)
        recyclerView = findViewById(R.id.recyclerViewGallery)
        recyclerView.layoutManager = GridLayoutManager(this, 3)

        // 스피너에 필터 옵션 설정
        val filterOptions = arrayOf("전체", "아기이름", "연도", "월", "일", "표정")
        spinnerFilterType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, filterOptions)

        currentImages = GalleryManager.getSavedImages(this)
        adapter = GalleryAdapter(currentImages) { savedImage ->
            // 삭제 버튼 클릭 시에만 삭제 확인 다이얼로그 표시
            AlertDialog.Builder(this)
                .setTitle("이미지 삭제")
                .setMessage("이 이미지를 삭제하시겠습니까?\n파일명: ${savedImage.displayName}")
                .setPositiveButton("삭제") { _, _ ->
                    GalleryManager.deleteImage(this, savedImage)
                    refreshGallery()
                }
                .setNegativeButton("취소", null)
                .show()
        }
        recyclerView.adapter = adapter

        btnApplyFilter.setOnClickListener {
            val filterTypeText = spinnerFilterType.selectedItem.toString()
            val filterValue = etFilterValue.text.toString().trim()
            currentImages = if (filterTypeText == "전체" || filterValue.isEmpty()) {
                GalleryManager.getSavedImages(this)
            } else {
                val filterType = when (filterTypeText) {
                    "아기이름" -> FilterType.BABY_NAME
                    "연도" -> FilterType.YEAR
                    "월" -> FilterType.MONTH
                    "일" -> FilterType.DAY
                    "표정" -> FilterType.EXPRESSION
                    else -> null
                }
                filterType?.let {
                    GalleryManager.filterSavedImages(this, it, filterValue)
                } ?: GalleryManager.getSavedImages(this)
            }
            adapter.updateData(currentImages)
        }
    }

    private fun refreshGallery() {
        currentImages = GalleryManager.getSavedImages(this)
        adapter.updateData(currentImages)
    }

    class GalleryAdapter(
        private var images: List<SavedImage>,
        private val onDeleteClick: (SavedImage) -> Unit
    ) : RecyclerView.Adapter<GalleryAdapter.GalleryViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GalleryViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_gallery, parent, false)
            return GalleryViewHolder(view)
        }

        override fun onBindViewHolder(holder: GalleryViewHolder, position: Int) {
            val savedImage = images[position]
            holder.bind(savedImage, onDeleteClick)
        }

        override fun getItemCount(): Int = images.size

        fun updateData(newImages: List<SavedImage>) {
            images = newImages
            notifyDataSetChanged()
        }

        class GalleryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val imageView: ImageView = itemView.findViewById(R.id.imageViewThumbnail)
            private val tvDisplayName: TextView = itemView.findViewById(R.id.tvCaptureReason)
            private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDeleteImage)

            fun bind(savedImage: SavedImage, onDeleteClick: (SavedImage) -> Unit) {
                // 이미지 표시: MediaStore URI 또는 legacy 파일 경로 사용
                if (savedImage.uri != null) {
                    imageView.setImageURI(savedImage.uri)
                } else if (savedImage.file != null) {
                    imageView.setImageBitmap(BitmapFactory.decodeFile(savedImage.file.absolutePath))
                }
                // 파일명을 그대로 표시 (예: BaMIA_아기이름_YYYYMMdd_HHmmss_<expression>.jpg)
                tvDisplayName.text = savedImage.displayName

                // 이미지 클릭 시 전체 화면으로 크게 보기
                imageView.setOnClickListener {
                    val context = itemView.context
                    val dialog = AlertDialog.Builder(context).create()
                    val fullImageView = ImageView(context)
                    fullImageView.adjustViewBounds = true
                    fullImageView.scaleType = ImageView.ScaleType.FIT_CENTER
                    if (savedImage.uri != null) {
                        fullImageView.setImageURI(savedImage.uri)
                    } else if (savedImage.file != null) {
                        fullImageView.setImageBitmap(BitmapFactory.decodeFile(savedImage.file.absolutePath))
                    }
                    dialog.setView(fullImageView)
                    dialog.show()
                }

                // 삭제 버튼 클릭 시 삭제 확인 다이얼로그 (GalleryActivity의 onDeleteClick 호출)
                btnDelete.setOnClickListener { onDeleteClick(savedImage) }
            }
        }
    }
}
