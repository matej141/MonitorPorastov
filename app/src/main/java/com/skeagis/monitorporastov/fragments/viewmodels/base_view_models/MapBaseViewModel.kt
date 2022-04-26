package com.skeagis.monitorporastov.fragments.viewmodels.base_view_models

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.skeagis.monitorporastov.apps_view_models.BaseViewModel
import com.skeagis.monitorporastov.geoserver.retrofit.GeoserverRetrofitAPI
import com.skeagis.monitorporastov.apps_view_models.MainSharedViewModel
import com.skeagis.monitorporastov.geoserver.retrofit.GeoserverRetrofitBuilder
import com.skeagis.monitorporastov.model.DamageData
import kotlinx.coroutines.launch

abstract class MapBaseViewModel: BaseViewModel() {
    var sharedViewModel: MainSharedViewModel? = null
    var viewLifecycleOwner: LifecycleOwner? = null
    private val _damageDataItem = MutableLiveData<DamageData>()
    val damageDataItem: LiveData<DamageData> = _damageDataItem

    fun setDamageDataItem(damageData: DamageData) {
        _damageDataItem.value = damageData
    }

    private fun initViewLifecycleOwner(viewLifecycleOwner: LifecycleOwner) {
        this.viewLifecycleOwner = viewLifecycleOwner
    }

    private fun initSharedViewModel(sharedViewModel: MainSharedViewModel) {
        this.sharedViewModel = sharedViewModel
    }

    open fun initViewModelMethods(
        sharedViewModel: MainSharedViewModel,
        viewLifecycleOwner: LifecycleOwner,
    ) {
        initSharedViewModel(sharedViewModel)
        initViewLifecycleOwner(viewLifecycleOwner)
        copyObservers()
        setToViewModel()
    }

    open fun copyObservers() {
        viewLifecycleOwner?.let { lifecycleOwner ->
            errorOccurred.observe(lifecycleOwner) {
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

    fun getGeoserverServiceAPIWithGson(): GeoserverRetrofitAPI? {
        val geoserverRetrofitAPI: GeoserverRetrofitAPI? =
            usernameCharArray.value?.let { usernameChars ->
                passwordCharArray.value?.let { passwordChars ->
                    GeoserverRetrofitBuilder.getServiceWithGsonFactory(usernameChars,
                        passwordChars)
                }
            }
        if (geoserverRetrofitAPI == null) {
            informThatCredentialsWereNotLoaded()
        }
        return geoserverRetrofitAPI
    }


}