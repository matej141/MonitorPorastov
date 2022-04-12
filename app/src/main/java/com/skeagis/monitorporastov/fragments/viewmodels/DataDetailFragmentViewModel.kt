package com.skeagis.monitorporastov.fragments.viewmodels

import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.skeagis.monitorporastov.fragments.viewmodels.base.DamagePhotosBaseViewModel
import com.skeagis.monitorporastov.model.DamageData

open class DataDetailFragmentViewModel : DamagePhotosBaseViewModel() {

    private val _bitmaps = MutableLiveData<MutableList<Bitmap>>()
    val bitmaps: LiveData<MutableList<Bitmap>> = _bitmaps

    private val _noPhotosToShow = MutableLiveData<Boolean>()
    val noPhotosToShow: LiveData<Boolean> = _noPhotosToShow

    lateinit var detailDamageDataItem: DamageData

    override fun setBitmaps(listOfBitmaps: MutableList<Bitmap>) {
        super.setBitmaps(listOfBitmaps)
        _bitmaps.postValue(listOfBitmaps)
    }

    private fun setNoPhotosToShow(value: Boolean) {
        _noPhotosToShow.postValue(value)
    }

    override fun setUpPreviouslyLoadedPhotos() {
        super.setUpPreviouslyLoadedPhotos()
        val bitmapList: MutableList<Bitmap>? = bitmaps.value
        setNoPhotosToShow(bitmapList.isNullOrEmpty())
    }

    override fun setNewlyLoadedPhotos() {
        super.setNewlyLoadedPhotos()
        setNoPhotosToShow(stringsOfPhotosList.isNullOrEmpty())
    }

}