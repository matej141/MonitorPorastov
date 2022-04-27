package com.skeagis.monitorporastov.fragments.viewmodels

import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.skeagis.monitorporastov.adapters.DataDetailPhotosRVAdapter
import com.skeagis.monitorporastov.fragments.viewmodels.base_view_models.DamagePhotosBaseViewModel
import com.skeagis.monitorporastov.model.DamageData

open class DataDetailFragmentViewModel : DamagePhotosBaseViewModel() {

    private val _bitmaps = MutableLiveData<MutableList<Bitmap>>()
    val bitmaps: LiveData<MutableList<Bitmap>> = _bitmaps

    private val _noPhotosToShow = MutableLiveData<Boolean>()
    val noPhotosToShow: LiveData<Boolean> = _noPhotosToShow

    private val _adapterOfDetailOfPhotos = MutableLiveData<DataDetailPhotosRVAdapter>()
    val adapterOfDetailOfPhotos: LiveData<DataDetailPhotosRVAdapter> = _adapterOfDetailOfPhotos

    lateinit var detailDamageDataItem: DamageData

    override fun setBitmaps(listOfBitmaps: MutableList<Bitmap>) {
        super.setBitmaps(listOfBitmaps)
        _bitmaps.postValue(listOfBitmaps)
        setBitmapsOfAdapter(listOfBitmaps)
        setNoPhotosToShow(listOfBitmaps.isNullOrEmpty())
    }

    private fun setBitmapsOfAdapter(listOfBitmaps: MutableList<Bitmap>) {
        val adapter = _adapterOfDetailOfPhotos.value
        if (adapter != null) {
            adapter.bitmaps = listOfBitmaps
            _adapterOfDetailOfPhotos.postValue(adapter!!)
        }
    }

    fun setImageInAdapterNonClickable() {
        _adapterOfDetailOfPhotos.value?.setIfDeletePhotoClickable(false)
    }

    fun setAdapterOfDetailsOfPhotos(
        adapter:
        DataDetailPhotosRVAdapter,
    ) {
        _adapterOfDetailOfPhotos.value = adapter
    }

    private fun setNoPhotosToShow(value: Boolean) {
        _noPhotosToShow.postValue(value)
    }

    override fun setNewlyLoadedPhotos() {
        super.setNewlyLoadedPhotos()
        setNoPhotosToShow(stringsOfPhotosList.isNullOrEmpty())
    }

}