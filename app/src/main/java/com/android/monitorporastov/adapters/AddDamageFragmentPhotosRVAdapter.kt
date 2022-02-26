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
class AddDamageFragmentPhotosRVAdapter :
    RecyclerView.Adapter<AddDamageFragmentPhotosRVAdapter.ViewHolder>() {

    var photoItems =  mutableListOf<PhotoItem>()
    val bitmaps = mutableListOf<Bitmap>()
    val hexStrings = mutableListOf<String>()
    val indexesOfPhotos = mutableListOf<Int>()
    val deletedIndexes = mutableListOf<Int>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding =
            PhotoListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = photoItems[position]
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
            photoItems.removeAt(position)
            bitmaps.removeAt(position)
            hexStrings.removeAt(position)
            val deletedIndex = indexesOfPhotos[position]
            deletedIndexes.add(deletedIndex)
            indexesOfPhotos.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    fun addHexString(hexString: String) {
        hexStrings.add(hexString)
    }

    override fun getItemCount() = photoItems.size

    inner class ViewHolder(binding: PhotoListItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        val photoImage: ImageView = binding.photoImage
        val deleteButton: ImageButton = binding.deleteButton
    }

}

data class PhotoItem(val image: Any?)