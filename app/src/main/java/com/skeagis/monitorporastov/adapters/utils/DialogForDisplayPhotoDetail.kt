package com.skeagis.monitorporastov.adapters.utils

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RelativeLayout
import androidx.core.graphics.drawable.toBitmap

object DialogForDisplayPhotoDetail {

    fun showDialogOfPhotoDetail(imageView: ImageView, context: Context) {
        val bitmap = getBitmapFromImageView(imageView)
        val dialog = createDialog(bitmap, context)
        dialog.show()
    }

    private fun getBitmapFromImageView(imageView: ImageView): Bitmap {
        val drawable = imageView.drawable
        return drawable.toBitmap()
    }

    private fun createDialog(bitmap: Bitmap, context: Context): Dialog {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(
            ColorDrawable(Color.TRANSPARENT))
        addImageViewToDialog(dialog, bitmap, context)
        dialog.window?.setLayout(FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT)
        return dialog
    }

    private fun createImageView(bitmap: Bitmap, context: Context): ImageView {
        val imageView = ImageView(context)
        imageView.setImageBitmap(bitmap)
        return imageView
    }

    private fun addImageViewToDialog(dialog: Dialog, bitmap: Bitmap, context: Context) {
        val imageView = createImageView(bitmap, context)
        dialog.addContentView(imageView, RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT))

    }


}