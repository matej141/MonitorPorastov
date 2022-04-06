package com.android.monitorporastov.fragments.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.android.monitorporastov.fragments.viewmodels.base.MapBaseViewModel

class MapFragmentViewModel : MapBaseViewModel() {

    val iAmHere = MutableLiveData<Boolean>()

    public override fun onCleared() {
        super.onCleared()
    }
}