package com.android.monitorporastov.fragments.viewmodels.base

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.android.monitorporastov.viewmodels.BaseViewModel
import com.android.monitorporastov.GeoserverServiceAPI
import com.android.monitorporastov.viewmodels.MainSharedViewModelNew
import com.android.monitorporastov.RetroService
import com.android.monitorporastov.model.DamageData
import kotlinx.coroutines.launch

abstract class MapBaseViewModel: BaseViewModel() {
    var sharedViewModel: MainSharedViewModelNew? = null
    var viewLifecycleOwner: LifecycleOwner? = null
    private val _damageDataItem = MutableLiveData<DamageData>()
    val damageDataItem: LiveData<DamageData> = _damageDataItem

    fun setDamageDataItem(damageData: DamageData) {
        _damageDataItem.value = damageData
    }

    private fun initViewLifecycleOwner(viewLifecycleOwner: LifecycleOwner) {
        this.viewLifecycleOwner = viewLifecycleOwner
    }

    private fun initSharedViewModel(sharedViewModel: MainSharedViewModelNew) {
        this.sharedViewModel = sharedViewModel
    }

    open fun initViewModelMethods(
        sharedViewModel: MainSharedViewModelNew,
        viewLifecycleOwner: LifecycleOwner,
    ) {
        initSharedViewModel(sharedViewModel)
        initViewLifecycleOwner(viewLifecycleOwner)
        copyObservers()
        setToViewModel()
    }

    open fun copyObservers() {
        viewLifecycleOwner?.let {
            errorOccurred.observe(it) {
                sharedViewModel?.setErrorOccurred(it)
            }
        }
        viewLifecycleOwner?.let { it ->
            errorMessage.observe(it) {
                if (it != null) {
                    sharedViewModel?.onError(it)
                }
            }
        }
        viewLifecycleOwner?.let {
            unauthorisedErrorIsOccurred.observe(it) {
                sharedViewModel?.reportThatUnauthorisedErrorIsOccurred()
            }
        }
    }

    open fun setToViewModel() {
        sharedViewModel?.usernameCharArray?.value?.let { setUsernameCharArray(it) }
        sharedViewModel?.passwordCharArray?.value?.let { setPasswordCharArray(it) }
    }

    fun observeNetworkState(reloadFunction: suspend () -> Unit = { }) {

        viewLifecycleOwner?.let {
            sharedViewModel?.isNetworkAvailable?.observe(it) { isAvailable ->
                if (isAvailable) {
                    launch {
                        reloadFunction()
                    }
                }
                setNetworkAvailability(isAvailable)
            }
        }

    }

    fun getGeoserverServiceAPIWithGson(): GeoserverServiceAPI? {
        val geoserverServiceAPI: GeoserverServiceAPI? =
            usernameCharArray.value?.let { usernameChars ->
                passwordCharArray.value?.let { passwordChars ->
                    RetroService.getServiceWithGsonFactory(usernameChars,
                        passwordChars)
                }
            }
        if (geoserverServiceAPI == null) {
            informThatCredentialsWereNotLoaded()
        }
        return geoserverServiceAPI
    }


}