package com.android.monitorporastov.adapters

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.android.monitorporastov.databinding.PhotoListItemBinding

/**
 * RecyclerViewAdapter pre prácu s fotografiami vo fragmente (AddDamageFragment),
 * kde používateľ mení alebo upravuje údaje o poškodení porastov.
 */
class PhotosRecyclerViewAdapter(
    var values: MutableList<PhotoItem>,
) :
    RecyclerView.Adapter<PhotosRecyclerViewAdapter.ViewHolder>() {

    val bitmaps = mutableListOf<Bitmap>()
    val hesStrings = mutableListOf<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding =
            PhotoListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = values[position]
        if (item.image is Uri) {
            holder.photoImage.setImageURI(item.image as Uri?)
            val bitmap = (holder.photoImage.drawable as BitmapDrawable).bitmap
            bitmaps.add(bitmap)
        }
        if (item.image is Bitmap) {
            holder.photoImage.setImageBitmap(item.image)
            bitmaps.add(item.image)
        }
        holder.deleteButton.setOnClickListener {
            values.removeAt(position)
            bitmaps.removeAt(position)
            hesStrings.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    fun addHexString(hexString: String) {
        hesStrings.add(hexString)
    }

    override fun getItemCount() = values.size

    inner class ViewHolder(binding: PhotoListItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        val photoImage: ImageView = binding.photoImage
        val deleteButton: ImageButton = binding.deleteButton
    }

}

data class PhotoItem(val image: Any?)