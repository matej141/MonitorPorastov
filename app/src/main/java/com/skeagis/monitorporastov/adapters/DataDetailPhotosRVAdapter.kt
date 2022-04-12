package com.skeagis.monitorporastov.adapters

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.skeagis.monitorporastov.databinding.DataDetailPhotoListItemBinding

/**
 * RecyclerViewAdapter pre prácu s fotografiami vo fragmente (DataDetailFragment),
 * zobrazujúcom detail o poškodení.
 */
class DataDetailPhotosRVAdapter(
    var values: List<Bitmap>,
) :
    RecyclerView.Adapter<DataDetailPhotosRVAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding =
            DataDetailPhotoListItemBinding.inflate(LayoutInflater.from(parent.context),
                parent,
                false)
        return ViewHolder(binding)

    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = values[position]
        holder.photoImage.setImageBitmap(item)
    }

    override fun getItemCount() = values.size

    inner class ViewHolder(binding: DataDetailPhotoListItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        val photoImage: ImageView = binding.photoImage
    }

}