package com.skeagis.monitorporastov.adapters

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RelativeLayout
import androidx.core.graphics.drawable.toBitmap
import androidx.recyclerview.widget.RecyclerView
import com.skeagis.monitorporastov.databinding.DataDetailPhotoListItemBinding

/**
 * RecyclerViewAdapter pre prácu s fotografiami vo fragmente (DataDetailFragment),
 * zobrazujúcom detail o poškodení.
 */
class DataDetailPhotosRVAdapter(
    private var values: List<Bitmap>,
    val context: Context,
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
        val drawable = holder.photoImage.drawable
        val bitmap = drawable.toBitmap()
        holder.photoImage.setOnClickListener {
            showImage(bitmap)
        }
    }

    override fun getItemCount() = values.size

    inner class ViewHolder(binding: DataDetailPhotoListItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        val photoImage: ImageView = binding.photoImage
    }


    private fun showImage(bitmap: Bitmap) {
        val dialog = createDialog(bitmap)
        dialog.show()
    }

    private fun createDialog(bitmap: Bitmap): Dialog {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(
            ColorDrawable(Color.TRANSPARENT))
        addImageViewToDialog(dialog, bitmap)
        dialog.window?.setLayout(FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT)
        return dialog
    }

    private fun addImageViewToDialog(dialog: Dialog, bitmap: Bitmap) {
        val imageView = createImageView(bitmap)
        dialog.addContentView(imageView, RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT))

    }

    private fun createImageView(bitmap: Bitmap): ImageView {
        val imageView = ImageView(context)
        imageView.setImageBitmap(bitmap)
        return imageView
    }
}