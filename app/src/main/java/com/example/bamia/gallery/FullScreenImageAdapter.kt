package com.example.bamia.gallery

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.example.bamia.R

class FullScreenImageAdapter(private var images: List<SavedImage>) : RecyclerView.Adapter<FullScreenImageAdapter.FullScreenViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FullScreenViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_full_screen_image, parent, false)
        return FullScreenViewHolder(view)
    }

    override fun onBindViewHolder(holder: FullScreenViewHolder, position: Int) {
        val image = images[position]
        if (image.uri != null) {
            holder.fullImageView.setImageURI(image.uri)
        } else if (image.file != null) {
            holder.fullImageView.setImageBitmap(BitmapFactory.decodeFile(image.file.absolutePath))
        } else {
            // 기본 배경색 사용 (예: 안드로이드 dark gray)
            holder.fullImageView.setBackgroundColor(android.graphics.Color.DKGRAY)
        }
    }

    override fun getItemCount(): Int = images.size

    fun updateData(newImages: List<SavedImage>) {
        images = newImages
        notifyDataSetChanged()
    }

    class FullScreenViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val fullImageView: ImageView = itemView.findViewById(R.id.fullImageView)
    }
}
