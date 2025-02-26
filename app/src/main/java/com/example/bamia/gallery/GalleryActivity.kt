package com.example.bamia.gallery

import android.app.AlertDialog
import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
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
    private lateinit var etSearch: EditText
    private lateinit var btnFilter: ImageButton

    private var currentImages: List<SavedImage> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)

        spinnerFilterType = findViewById(R.id.spinnerFilterType)
        etSearch = findViewById(R.id.etSearch)
        btnFilter = findViewById(R.id.btnFilter)
        recyclerView = findViewById(R.id.recyclerViewGallery)
        recyclerView.layoutManager = GridLayoutManager(this, 3)

        val filterOptions = arrayOf("전체", "아기이름", "연도", "월", "일", "표정")
        spinnerFilterType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, filterOptions)

        currentImages = GalleryManager.getSavedImages(this)
        adapter = GalleryAdapter(currentImages) { savedImage ->
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

        btnFilter.setOnClickListener {
            spinnerFilterType.performClick()
        }

        // 🔹 필터 선택 시 즉시 적용
        spinnerFilterType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                applyFilter()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 🔹 검색어 입력 시 실시간 검색
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilter()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // 🔹 엔터 키 입력 시 검색 실행 (줄바꿈 X)
        etSearch.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                applyFilter()
                return@setOnKeyListener true
            }
            false
        }
    }

    // 🔹 필터 + 검색 적용
    private fun applyFilter() {
        val filterTypeText = spinnerFilterType.selectedItem.toString()
        val searchQuery = etSearch.text.toString().trim()

        currentImages = GalleryManager.getSavedImages(this).filter { image ->
            val matchesFilter = when (filterTypeText) {
                "아기이름" -> image.displayName.contains("아기이름", ignoreCase = true)
                "연도" -> image.displayName.contains(Regex("\\d{4}")) // 연도 필터 (YYYY 형식)
                "월" -> image.displayName.contains(Regex("\\d{4}년\\d{2}월")) // 연도+월 필터
                "일" -> image.displayName.contains(Regex("\\d{4}년\\d{2}월\\d{2}일")) // 연도+월+일 필터
                "표정" -> image.displayName.contains("표정", ignoreCase = true)
                else -> true
            }
            val matchesSearch = searchQuery.isEmpty() || image.displayName.contains(searchQuery, ignoreCase = true)
            matchesFilter && matchesSearch
        }

        adapter.updateData(currentImages)
    }

    private fun refreshGallery() {
        currentImages = GalleryManager.getSavedImages(this)
        adapter.updateData(currentImages)
    }

    // 🔹 GalleryAdapter 클래스 내부에 포함
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
                if (savedImage.uri != null) {
                    imageView.setImageURI(savedImage.uri)
                } else if (savedImage.file != null) {
                    imageView.setImageBitmap(BitmapFactory.decodeFile(savedImage.file.absolutePath))
                }

                tvDisplayName.text = savedImage.displayName

                imageView.setOnClickListener {
                    val context = itemView.context
                    val dialog = AlertDialog.Builder(context).create()
                    val fullImageView = ImageView(context).apply {
                        adjustViewBounds = true
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        if (savedImage.uri != null) {
                            setImageURI(savedImage.uri)
                        } else if (savedImage.file != null) {
                            setImageBitmap(BitmapFactory.decodeFile(savedImage.file.absolutePath))
                        }
                    }
                    dialog.setView(fullImageView)
                    dialog.show()
                }

                btnDelete.setOnClickListener { onDeleteClick(savedImage) }
            }
        }
    }
}
