package com.android.monitorporastov.adapters

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.android.monitorporastov.adapters.models.PhotoItem
import com.android.monitorporastov.databinding.PhotoListItemBinding

/**
 * RecyclerViewAdapter pre prácu s fotografiami vo fragmente (AddDamageFragment),
 * kde používateľ mení alebo upravuje údaje o poškodení porastov.
 */
class AddDamageFragmentPhotosRVAdapter(
    var bitmaps: MutableList<Bitmap>,
) :
    RecyclerView.Adapter<AddDamageFragmentPhotosRVAdapter.ViewHolder>() {

    var hexStrings = mutableListOf<String>()
    val indexesOfPhotos = mutableListOf<Int>()
    val deletedIndexes = mutableListOf<Int>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding =
            PhotoListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = bitmaps[position]

        holder.photoImage.setImageBitmap(item)
            //bitmaps.add(item.image)

        holder.deleteButton.setOnClickListener {
            //photoItems.removeAt(holder.adapterPosition)
            bitmaps.removeAt(holder.adapterPosition)
            hexStrings.removeAt(holder.adapterPosition)
            val deletedIndex = indexesOfPhotos[holder.adapterPosition]
            deletedIndexes.add(deletedIndex)
            indexesOfPhotos.removeAt(holder.adapterPosition)
            notifyItemRemoved(holder.adapterPosition)
            //notifyItemRangeChanged(0, photoItems.size)
        }
    }

    fun addHexString(hexString: String) {
        hexStrings.add(hexString)
    }

    override fun getItemCount() = bitmaps.size

    inner class ViewHolder(binding: PhotoListItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        val photoImage: ImageView = binding.photoImage
        val deleteButton: ImageButton = binding.deleteButton
    }

}
