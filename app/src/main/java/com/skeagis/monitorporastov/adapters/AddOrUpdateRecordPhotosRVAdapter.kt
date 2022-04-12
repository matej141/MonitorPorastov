package com.skeagis.monitorporastov.adapters

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.skeagis.monitorporastov.databinding.PhotoListItemBinding

/**
 * RecyclerViewAdapter pre prácu s fotografiami vo fragmente (AddOrUpdateRecordFragment),
 * kde používateľ mení alebo upravuje údaje o poškodení porastov.
 */
class AddOrUpdateRecordPhotosRVAdapter(
    var bitmaps: MutableList<Bitmap>,
) :
    RecyclerView.Adapter<AddOrUpdateRecordPhotosRVAdapter.ViewHolder>() {

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