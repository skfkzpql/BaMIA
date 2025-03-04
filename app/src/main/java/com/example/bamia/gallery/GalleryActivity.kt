package com.example.bamia.gallery

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.bamia.R
import com.example.bamia.activities.FullScreenImageActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class GalleryActivity : AppCompatActivity(), GalleryAdapter.OnItemInteractionListener {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: GalleryAdapter
    private var imageList: MutableList<SavedImage> = mutableListOf()
    private var selectionMode = false

    private var currentFilter: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)

        findViewById<ImageButton>(R.id.btnBackGallery).setOnClickListener {
            finish()
        }

        recyclerView = findViewById(R.id.recyclerViewGallery)
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val minItemWidth = (200 * displayMetrics.density).toInt()
        val spanCount = (screenWidth / minItemWidth).coerceIn(3, 7)
        recyclerView.layoutManager = GridLayoutManager(this, spanCount)

        loadImages()
        adapter = GalleryAdapter(imageList, this)
        recyclerView.adapter = adapter

        setupFilterButtons()

        // 하단 오버레이의 삭제 버튼 이벤트 설정
        findViewById<ImageButton>(R.id.btnDeleteSelectedOverlay).setOnClickListener {
            if (adapter.getSelectedItems().isNotEmpty()) {
                MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)
                    .setTitle("삭제 확인")
                    .setMessage("선택한 이미지를 삭제하시겠습니까?")
                    .setPositiveButton("삭제"){ _, _ ->
                        adapter.getSelectedItems().forEach { image ->
                            GalleryManager.deleteImage(this, image)
                        }
                        loadImages()
                        adapter.updateData(imageList)
                        selectionMode = false
                        updateSelectionOverlay(false)
                    }
                    .setNegativeButton("취소", null)
                    .show()
            }
        }
    }

    override fun onBackPressed() {
        if (selectionMode) {
            // 선택 모드 취소: 오버레이 숨기고 선택 상태 해제
            selectionMode = false
            adapter.clearSelection()
            updateSelectionOverlay(false)
            // 만약 필터가 적용 중이면, 기존 필터 상태 유지(또는 전체 이미지로 복귀)
            if (currentFilter != null) {
                // 필터를 그대로 유지하려면, updateData(filteredImages) 호출
                val filteredImages = GalleryManager.filterSavedImages(this, FilterType.EXPRESSION, currentFilter!!)
                adapter.updateData(filteredImages)
            } else {
                loadImages()
                adapter.updateData(imageList)
            }
        } else {
            super.onBackPressed()
        }
    }

    private fun loadImages() {
        imageList = GalleryManager.getSavedImages(this).toMutableList()
        Log.d("GalleryActivity", "Loaded images count: ${imageList.size}")
    }

    private fun setupFilterButtons() {
        // 각 필터 버튼을 가져와 onClickListener를 설정
        val btnNeutral = findViewById<Button>(R.id.btnNeutral)
        val btnHappy = findViewById<Button>(R.id.btnHappy)
        val btnSad = findViewById<Button>(R.id.btnSad)
        val btnSurprised = findViewById<Button>(R.id.btnSurprised)
        val btnScared = findViewById<Button>(R.id.btnScared)
        val btnDisgust = findViewById<Button>(R.id.btnDisgust)
        val btnAngry = findViewById<Button>(R.id.btnAngry)
        val btnCaptureFilter = findViewById<Button>(R.id.btnCaptureFilter)

        // 버튼 클릭 시 필터 적용
        btnNeutral.setOnClickListener { toggleFilter("중립", it as Button) }
        btnHappy.setOnClickListener { toggleFilter("행복", it as Button) }
        btnSad.setOnClickListener { toggleFilter("슬픔", it as Button) }
        btnSurprised.setOnClickListener { toggleFilter("놀람", it as Button) }
        btnScared.setOnClickListener { toggleFilter("두려움", it as Button) }
        btnDisgust.setOnClickListener { toggleFilter("혐오", it as Button) }
        btnAngry.setOnClickListener { toggleFilter("분노", it as Button) }
        btnCaptureFilter.setOnClickListener { toggleFilter("캡쳐", it as Button) }
    }

    private fun toggleFilter(filterValue: String, selectedButton: Button) {
        if (currentFilter != null && currentFilter.equals(filterValue, ignoreCase = true)) {
            // 필터가 이미 적용되어 있으면 해제
            currentFilter = null
            loadImages()
            adapter.updateData(imageList)
            resetFilterButtonsUI()
        } else {
            currentFilter = filterValue
            val filteredImages = GalleryManager.filterSavedImages(this, FilterType.EXPRESSION, filterValue)
            adapter.updateData(filteredImages)
            resetFilterButtonsUI()
            // 선택된 버튼을 강조: 흰색 배경, 검정 텍스트
            selectedButton.setBackgroundColor(resources.getColor(android.R.color.white))
            selectedButton.setTextColor(resources.getColor(android.R.color.black))
        }
    }

    // 모든 필터 버튼의 UI를 기본 스타일로 되돌림
    private fun resetFilterButtonsUI() {
        val filterArea = findViewById<LinearLayout>(R.id.filterArea)
        for (i in 0 until filterArea.childCount) {
            val child = filterArea.getChildAt(i)
            if (child is Button) {
                // 기본 상태: 원래 selector 배경 및 흰색 텍스트
                child.setBackgroundResource(R.drawable.filter_button_selector)
                child.setTextColor(resources.getColor(android.R.color.white))
            }
        }
    }

    private fun updateSelectionOverlay(isSelectionMode: Boolean) {
        // 오버레이 (하단 삭제 버튼)을 보이거나 숨김 처리
        val overlay = findViewById<View>(R.id.selectionOverlay)
        overlay.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        if (selectionMode) {
            menuInflater.inflate(R.menu.gallery_menu, menu)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_delete -> {
                if (adapter.getSelectedItems().isNotEmpty()) {
                    MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)
                        .setTitle("삭제 확인")
                        .setMessage("선택한 이미지를 삭제하시겠습니까?")
                        .setPositiveButton("삭제") { _, _ ->
                            adapter.getSelectedItems().forEach { image ->
                                GalleryManager.deleteImage(this, image)
                            }
                            loadImages()
                            adapter.updateData(imageList)
                            selectionMode = false
                        }
                        .setNegativeButton("취소", null)
                        .show()
                }
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    // GalleryAdapter.OnItemInteractionListener 구현
    override fun onItemClick(savedImage: SavedImage, position: Int) {
        if (selectionMode) {
            adapter.toggleSelection(savedImage)
            if (adapter.getSelectedItems().isEmpty()) {
                selectionMode = false
                updateSelectionOverlay(false)
            } else {
                updateSelectionOverlay(true)
            }
        } else {
            val intent = Intent(this, FullScreenImageActivity::class.java)
            val imagePath = savedImage.uri?.toString() ?: savedImage.file?.absolutePath
            intent.putExtra("imagePath", imagePath)
            intent.putExtra("displayName", savedImage.displayName)
            startActivity(intent)
        }
    }

    override fun onItemLongClick(savedImage: SavedImage): Boolean {
        if (!selectionMode) {
            selectionMode = true
            adapter.toggleSelection(savedImage)
            updateSelectionOverlay(true)
        }
        return true
    }

    override fun onResume() {
        super.onResume()
        // FullScreenImageActivity에서 삭제 후 돌아왔을 때 항상 갤러리 목록을 새로 로드
        loadImages()
        adapter.updateData(imageList)
    }
}
