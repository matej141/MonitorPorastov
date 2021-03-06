package com.skeagis.monitorporastov.adapters

import android.content.Context
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.skeagis.monitorporastov.adapters.utils.DialogForDisplayPhotoDetail
import com.skeagis.monitorporastov.databinding.DataDetailPhotoListItemBinding

/**
 * RecyclerViewAdapter pre prácu s fotografiami vo fragmente (DataDetailFragment),
 * zobrazujúcom detail o poškodení.
 */
class DataDetailPhotosRVAdapter(
    var bitmaps: List<Bitmap>,
    val context: Context,
) :
    RecyclerView.Adapter<DataDetailPhotosRVAdapter.ViewHolder>() {

    var deletePhotoClickable = true

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding =
            DataDetailPhotoListItemBinding.inflate(LayoutInflater.from(parent.context),
                parent,
                false)
        return ViewHolder(binding)
    }

    fun setIfDeletePhotoClickable(value: Boolean) {
        deletePhotoClickable = value
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = bitmaps[position]
        holder.photoImage.setImageBitmap(item)
        holder.photoImage.setOnClickListener {
            if (!deletePhotoClickable) {
                return@setOnClickListener
            }
            DialogForDisplayPhotoDetail.showDialogOfPhotoDetail(holder.photoImage, context)
        }
    }

    override fun getItemCount() = bitmaps.size

    inner class ViewHolder(binding: DataDetailPhotoListItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        val photoImage: ImageView = binding.photoImage
    }
}