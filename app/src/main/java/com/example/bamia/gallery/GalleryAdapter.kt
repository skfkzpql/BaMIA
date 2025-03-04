package com.example.bamia.gallery

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.example.bamia.R

class GalleryAdapter(
    private var images: List<SavedImage>,
    private val listener: OnItemInteractionListener
) : RecyclerView.Adapter<GalleryAdapter.GalleryViewHolder>() {

    interface OnItemInteractionListener {
        fun onItemClick(savedImage: SavedImage, position: Int)
        fun onItemLongClick(savedImage: SavedImage): Boolean
    }

    private val selectedItems = mutableSetOf<SavedImage>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GalleryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_gallery, parent, false)
        return GalleryViewHolder(view)
    }

    override fun onBindViewHolder(holder: GalleryViewHolder, position: Int) {
        val savedImage = images[position]
        holder.bind(savedImage, selectedItems.contains(savedImage), listener, position)
    }

    override fun getItemCount(): Int = images.size

    fun updateData(newImages: List<SavedImage>) {
        images = newImages
        selectedItems.clear()
        notifyDataSetChanged()
    }

    fun toggleSelection(image: SavedImage) {
        if (selectedItems.contains(image)) {
            selectedItems.remove(image)
        } else {
            selectedItems.add(image)
        }
        notifyDataSetChanged()
    }

    fun getSelectedItems(): Set<SavedImage> = selectedItems

    fun clearSelection() {
        selectedItems.clear()
        notifyDataSetChanged()
    }

    class GalleryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val thumbnail = itemView.findViewById<ImageView>(R.id.imageViewThumbnail)

        fun bind(savedImage: SavedImage, isSelected: Boolean, listener: OnItemInteractionListener, position: Int) {
            if (savedImage.uri != null) {
                thumbnail.setImageURI(savedImage.uri)
            } else if (savedImage.file != null) {
                thumbnail.setImageBitmap(BitmapFactory.decodeFile(savedImage.file.absolutePath))
            } else {
                thumbnail.setBackgroundColor(android.graphics.Color.DKGRAY)
            }
            // 선택 상태 표시: if selected, show ic_selected, else ic_unselected.
            val selectionIndicator = itemView.findViewById<ImageView>(R.id.ivSelectionIndicator)
            if (isSelected) {
                selectionIndicator.visibility = View.VISIBLE
                selectionIndicator.setImageResource(R.drawable.ic_select)
                itemView.alpha = 0.5f
            } else {
                selectionIndicator.visibility = View.GONE
                itemView.alpha = 1.0f
            }
            itemView.setOnClickListener {
                listener.onItemClick(savedImage, position)
            }
            itemView.setOnLongClickListener {
                listener.onItemLongClick(savedImage)
            }
        }
    }
}
