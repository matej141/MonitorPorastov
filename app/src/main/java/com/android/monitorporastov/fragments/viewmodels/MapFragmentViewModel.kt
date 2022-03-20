package com.android.monitorporastov.fragments.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MapFragmentViewModel : ViewModel() {

    val iAmHere = MutableLiveData<Boolean>()

    public override fun onCleared() {
        super.onCleared()
    }
}