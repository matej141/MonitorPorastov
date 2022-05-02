package com.skeagis.monitorporastov.fragments.viewmodels

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.skeagis.monitorporastov.Utils
import com.skeagis.monitorporastov.Utils.createRequestBody
import com.skeagis.monitorporastov.adapters.AddOrUpdateRecordPhotosRVAdapter
import com.skeagis.monitorporastov.fragments.viewmodels.base_view_models.DamagePhotosBaseViewModel
import com.skeagis.monitorporastov.geoserver.factories.GeoserverDataFilterStringsFactory.createFilterStringByUniqueId
import com.skeagis.monitorporastov.geoserver.factories.GeoserverDataPostStringsFactory
import com.skeagis.monitorporastov.geoserver.factories.GeoserverPhotosPostStringsFactory
import com.skeagis.monitorporastov.geoserver.retrofit.GeoserverRetrofitAPI
import com.skeagis.monitorporastov.model.DamageData
import com.skeagis.monitorporastov.model.UsersData
import id.zelory.compressor.Compressor
import id.zelory.compressor.constraint.format
import id.zelory.compressor.constraint.quality
import id.zelory.compressor.constraint.size
import kotlinx.coroutines.*
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

class AddOrUpdateRecordFragmentViewModel : DamagePhotosBaseViewModel() {

    private val _isEditingData = MutableLiveData<Boolean>()
    val isEditingData: LiveData<Boolean> = _isEditingData
    private val maxSizeOfPhoto = 600

    private val _adapterOfPhotos = MutableLiveData<AddOrUpdateRecordPhotosRVAdapter>()
    val adapterOfPhotos: LiveData<AddOrUpdateRecordPhotosRVAdapter> = _adapterOfPhotos

    private val _updateSucceeded = MutableLiveData<Boolean>()
    val updateSucceeded: LiveData<Boolean> = _updateSucceeded

    private var nameOfDamageDataRecord = ""
    private var descriptionOfDamageDataRecord = ""
    private var damageType = ""

    private val _uncompletedNameWarning = MutableLiveData<Boolean>()
    val uncompletedNameWarning: LiveData<Boolean> = _uncompletedNameWarning

    private val _navigateToMapFragment = MutableLiveData<Boolean>()
    val navigateToMapFragment: LiveData<Boolean> = _navigateToMapFragment

    private val _adapterWasChanged = MutableLiveData<Boolean>()
    val adapterWasChanged: LiveData<Boolean> = _adapterWasChanged

    fun setAdapterOfPhotos(
        adapter:
        AddOrUpdateRecordPhotosRVAdapter,
    ) {
        _adapterOfPhotos.value = adapter
    }

    fun setExistingDamageData(damageData: DamageData) {
        if (loadedPhotos.value == true) {
            return
        }
        setDamageDataItem(damageData)
        setEditingDataValueAsTrue()

        prepareToLoadPhotos()
    }

    private fun setExistingDamageDataFromMapFragment(damageData: DamageData) {
        if (loadedPhotos.value == true) {
            return
        }
        setDamageDataItem(damageData)
        setEditingDataValueAsTrue()

        prepareToLoadPhotos()
    }

    fun setDamageDataFromMap(damageData: DamageData) {
        if (damageData.isInGeoserver) {
            setExistingDamageDataFromMapFragment(damageData)
            return
        }
        setDamageDataItem(damageData)
    }

    fun setNameOfDamageDataRecord(name: String) {
        nameOfDamageDataRecord = name.trim()
    }

    fun setDescriptionOfDamageDataRecord(description: String) {
        descriptionOfDamageDataRecord = description.trim()
    }

    fun setDamageType(type: String) {
        damageType = type.trim()
    }

    private fun setUncompletedNameWarning() {
        _uncompletedNameWarning.value = true
    }

    private fun setIfUpdateSucceeded(value: Boolean) {
        _updateSucceeded.value = value
    }

    private fun setNavigateToMapFragment(value: Boolean) {
        _navigateToMapFragment.value = value
    }

    private fun setAdapterWasChanged() {
        _adapterWasChanged.value = true
    }

    override fun setBitmaps(listOfBitmaps: MutableList<Bitmap>) {
        super.setBitmaps(listOfBitmaps)
        setBitmapsToAdapter(listOfBitmaps)
        setIndexesOfPhotosToAdapter()
    }

    private fun setBitmapsToAdapter(listOfBitmaps: MutableList<Bitmap>) {
        listOfBitmaps.forEach {
            addPhotoItemToAdapter(it)
            addEmptyHexStringToAdapter()
        }
    }

    private fun addPhotoItemToAdapter(bitmap: Bitmap) {
        adapterOfPhotos.value?.bitmaps?.add(bitmap)
        setAdapterWasChanged()
    }

    private fun addEmptyHexStringToAdapter() {
        val emptyString = ""
        addHexStringToAdapter(emptyString)
    }

    private fun addHexStringToAdapter(hexString: String) {
        adapterOfPhotos.value?.hexStrings?.add(hexString)
    }

    private fun setIndexesOfPhotosToAdapter() {
        damageDataItem.value?.indexesOfPhotos?.forEach {
            addIndexOfPhotoToAdapter(it)
        }
    }

    private fun addIndexOfPhotoToAdapter(index: Int) {
        adapterOfPhotos.value?.indexesOfPhotos?.add(index)
    }

    private fun addIndexOfNewPhotoToAdapter() {
        val indexOfNewPhoto = -1
        addIndexOfPhotoToAdapter(indexOfNewPhoto)
    }

    private fun setEditingDataValueAsTrue() {
        _isEditingData.value = true
    }


    fun addBitmapToAdapter(bitmap: Bitmap, context: Context) {
        val resizedBitmap = getResizedBitmap(bitmap)
        addPhotoItemToAdapter(resizedBitmap)
        notifyItemInsertedInAdapter()
        CoroutineScope(Dispatchers.Main).launch {
            addBitmapHexToAdapter(resizedBitmap, context)
            setAdapterWasChanged()
        }
    }

    private fun getResizedBitmap(image: Bitmap): Bitmap {
        var width = image.width
        var height = image.height
        val bitmapRatio = width.toFloat() / height.toFloat()
        if (bitmapRatio > 1) {
            width = maxSizeOfPhoto
            height = (width / bitmapRatio).toInt()
        } else {
            height = maxSizeOfPhoto
            width = (height * bitmapRatio).toInt()
        }
        return Bitmap.createScaledBitmap(image, width, height, true)
    }

    private fun notifyItemInsertedInAdapter() {
        val sizeOfPhotoItemsList = adapterOfPhotos.value?.bitmaps?.size
        if (sizeOfPhotoItemsList != null) {
            adapterOfPhotos.value?.notifyItemInserted(sizeOfPhotoItemsList - 1)
        }
    }

    private suspend fun addBitmapHexToAdapter(bitmap: Bitmap, context: Context) {
        val compressedByteArray = createCompressedByteArray(bitmap, context)
        val hexStringOfByteArray = createHexStringFromByteArray(compressedByteArray)
        addHexStringToAdapter(hexStringOfByteArray)
        addIndexOfNewPhotoToAdapter()
    }


    private suspend fun createCompressedByteArray(bitmap: Bitmap, context: Context): ByteArray {
        val imageFile = createImageFile(bitmap, context)
        val compressedFile = createCompressedFile(imageFile, context)
        val byteArrayFromFile = createByteArrayFromFile(compressedFile)
        deleteFiles(imageFile, compressedFile)
        return byteArrayFromFile
    }

    private fun createImageFile(bitmap: Bitmap, context: Context): File {
        val childName = "filename"
        val newImageFile = File(context.cacheDir, childName)
        newImageFile.createNewFile()
        val byteArray = createByteArrayFromBitmap(bitmap)
        val fileOutputStream = FileOutputStream(newImageFile)
        fileOutputStream.write(byteArray)
        fileOutputStream.flush()
        fileOutputStream.close()
        return newImageFile
    }

    private fun createByteArrayFromBitmap(bitmap: Bitmap): ByteArray {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        byteArrayOutputStream.close()
        return byteArray
    }

    private suspend fun createCompressedFile(imageFile: File, context: Context): File {
        val compressedImageFileDeferred = CompletableDeferred<File>()
        CoroutineScope(Dispatchers.Main).launch {
            // https://github.com/zetbaitsu/Compressor
            val compressedImageFile: File = Compressor.compress(context, imageFile) {
                quality(80)
                format(Bitmap.CompressFormat.JPEG)
                size(1_000_000)
            }
            imageFile.delete()
            compressedImageFileDeferred.complete(compressedImageFile)

        }
        return compressedImageFileDeferred.await()
    }

    private suspend fun createByteArrayFromFile(file: File): ByteArray {
        val size: Long = file.length()
        val byteArrayDeferred = CompletableDeferred<ByteArray>()
        val byteArray = ByteArray(size.toInt())
        CoroutineScope(Dispatchers.IO).launch {
            kotlin.runCatching {
                val buf = BufferedInputStream(FileInputStream(file))
                buf.read(byteArray, 0, byteArray.size)
                buf.close()
            }
            byteArrayDeferred.complete(byteArray)
        }

        return byteArrayDeferred.await()
    }

    private fun deleteFiles(vararg files: File) {
        files.forEach { it.delete() }
    }

    private fun createHexStringFromByteArray(bytes: ByteArray): String {
        val hexArray = createHexArray()
        val hexChars = createHexChars(bytes)
        for (j in bytes.indices) {
            val v = bytes[j].toInt() and 0xFF

            hexChars[j * 2] = hexArray[v ushr 4]
            hexChars[j * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars)
    }

    private fun createHexArray(): CharArray {
        return "0123456789ABCDEF".toCharArray()
    }

    private fun createHexChars(bytes: ByteArray): CharArray {
        return CharArray(bytes.size * 2)
    }

    fun saveData() {
        if (sharedViewModel?.isNetworkAvailable?.value == false || blockedClicking) {
            return
        }
        if (nameOfDamageDataRecord.isEmpty()) {
            setUncompletedNameWarning()
            return
        }
        _adapterOfPhotos.value?.setIfDeletePhotoClickable(false)
        val uniqueId = createUniqueId()
        val isEditingDataValue = isEditingData.value
        if (isEditingDataValue == true) {

            observeNetworkState { updateDataInGeoserver() }
        } else {
            observeNetworkState { saveDataToGeoserver(uniqueId) }
        }
    }

    private fun updateDataInGeoserver() {
        setLoading(true)
        CoroutineScope(Dispatchers.Main).launch {
            val resultsListDeferred: List<Deferred<Boolean>> =
                listOf(
                    async { updateDamageInfoInGeoserver() },
                    async { updatePhotosInGeoserver() }
                )
            if (checkIfShouldUpdate()) {
                setIfUpdateSucceeded(true)
                onSucceededResult()
                return@launch
            }
            val resultsList: List<Boolean> = resultsListDeferred.awaitAll()
            val ifUpdateSucceeded = Utils.checkIfCallsWereSucceeded(resultsList)

            setIfUpdateSucceeded(ifUpdateSucceeded)
            if (ifUpdateSucceeded) {
                onSucceededResult()
                removeObserverOfNetwork()
            }

        }
    }

    private fun onSucceededResult() {
        updateDataInSharedViewModel()
        whereToNavigateBack()
        setDataNotLoadedInSharedViewModel()
        updateSavedData()
        setLoading(false)
    }

    private fun setDataNotLoadedInSharedViewModel() {
        sharedViewModel?.setIfLoadedUserData(false)
        sharedViewModel?.setIfLoadedMapLayerWithUserData(false)
    }

    private fun whereToNavigateBack() {
        val isItemDirectlyFromMap = damageDataItem.value?.isDirectlyFromMap == true
        setNavigateToMapFragment(isItemDirectlyFromMap)
    }

    private suspend fun updateDamageInfoInGeoserver(): Boolean {
        if (compareIfLocalDamageDataItemWithDataInGeoserverAreEqual()) {
            return true
        }
        val updateDamageDataString = createUpdateDamageDataString()
        if (updateDamageDataString.isEmpty()) {
            return true
        }
        val requestBody = createRequestBody(updateDamageDataString)
        return postToGeoserver(requestBody)
    }

    private fun createUpdateDamageDataString(): String {
        val updatedDamageData = createNewDamageDataItem()
        val originalDamageData = damageDataItem.value ?: return ""
        return GeoserverDataPostStringsFactory.createUpdateDamageDataString(originalDamageData,
            updatedDamageData)
    }

    private fun createNewDamageDataItem(): DamageData {
        val originalData = damageDataItem.value
        val damageDataItem = originalData?.copy() ?: DamageData()
        damageDataItem.nazov = nameOfDamageDataRecord
        damageDataItem.typ_poskodenia = damageType
        damageDataItem.popis_poskodenia = descriptionOfDamageDataRecord
        damageDataItem.pouzivatel = getUserName()
        damageDataItem.datetime = getActualDateTime()
        return damageDataItem
    }

    private fun getUserName(): String {
        return String(usernameCharArray.value!!)
    }

    private fun getActualDateTime(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun updateSavedData() {
        damageDataItem.value?.nazov = nameOfDamageDataRecord
        damageDataItem.value?.typ_poskodenia = damageType
        damageDataItem.value?.popis_poskodenia = descriptionOfDamageDataRecord
        val adapterBitmaps = getBitmapsFromAdapter()
        damageDataItem.value?.bitmaps = adapterBitmaps
        setIfLoadedPhotos(false)
    }

    private suspend fun updatePhotosInGeoserver(): Boolean {
        if (!compareIfLocalAndRemoteListsOfBitmapIndexesAreEqual()) {
            return true
        }
        val updatePhotosTransactionString = createUpdatePhotosTransactionString()
        if (updatePhotosTransactionString.isEmpty()) {
            return true
        }
        val requestBody = createRequestBody(updatePhotosTransactionString)
        return postToGeoserver(requestBody)
    }

    private fun createUpdatePhotosTransactionString(): String {
        val deletedIndexesList = getDeletedIndexesList().toList()
        val hexOfPhotosList = getHexOfPhotosList().toList()
        val uniqueId: String = getUniqueIdOfData()
        return GeoserverPhotosPostStringsFactory.createUpdatePhotosTransactionString(
            deletedIndexesList,
            hexOfPhotosList,
            uniqueId)
    }

    private fun getDeletedIndexesList(): MutableList<Int> {
        return adapterOfPhotos.value?.deletedIndexes ?: return mutableListOf()
    }

    private fun getHexOfPhotosList(): MutableList<String> {
        return adapterOfPhotos.value?.hexStrings ?: return mutableListOf()
    }

    private fun getUniqueIdOfData(): String {
        return damageDataItem.value?.unique_id ?: ""
    }

    private fun updateDataInSharedViewModel() {
        val idOfSelectedDamageDataItemFromMap =
            sharedViewModel?.selectedDamageDataItemFromMap?.value?.id
        val idOfSelectedDamageDataItem = sharedViewModel?.selectedDamageDataItem?.value?.id
        if (idOfSelectedDamageDataItemFromMap == null && idOfSelectedDamageDataItem == null) {
            return
        }
        if (idOfSelectedDamageDataItemFromMap ==
            idOfSelectedDamageDataItem
        ) {
            damageDataItem.value?.let { sharedViewModel?.selectDamageData(it) }
        }
    }

    private fun saveDataToGeoserver(uniqueId: String) {
        setLoading(true)
        CoroutineScope(Dispatchers.Main).launch {
            val resultsListDeferred =
                listOf(
                    async { sendDamageDataToGeoserver(uniqueId) },
                    async {
                        sendPhotosToGeoserver(uniqueId)
                    })
            if (checkIfItemIsInGeoserver(uniqueId)) {
                onSucceededResult()
                setIfUpdateSucceeded(true)
                return@launch
            }
            val resultsList: List<Boolean> = resultsListDeferred.awaitAll()
            val ifUpdateSucceeded = Utils.checkIfCallsWereSucceeded(resultsList)
            setIfUpdateSucceeded(ifUpdateSucceeded)
            if (ifUpdateSucceeded) {
                onSucceededResult()
                removeObserverOfNetwork()
            }
        }
    }

    private fun getBitmapsFromAdapter(): MutableList<Bitmap> {
        return adapterOfPhotos.value?.bitmaps ?: mutableListOf()
    }

    private suspend fun sendDamageDataToGeoserver(uniqueId: String): Boolean {
        if (checkIfItemIsInGeoserver(uniqueId)) {
            return true
        }
        val insertDataTransactionString = createInsertDataTransactionString(uniqueId)
        val requestBody = createRequestBody(insertDataTransactionString)
        return postToGeoserver(requestBody)
    }

    private fun createInsertDataTransactionString(uniqueId: String): String {
        val newDamageData = createNewDamageDataItem()
        newDamageData.unique_id = uniqueId
        return GeoserverDataPostStringsFactory.createInsertDataTransactionString(newDamageData)
    }

    private fun createUniqueId(): String {
        val uuid = UUID.randomUUID().toString()
        val username = getUserName()
        return "$username:$uuid"
    }

    private suspend fun sendPhotosToGeoserver(uniqueId: String): Boolean {
        if (checkIfItemIsInGeoserver(uniqueId)) {
            return true
        }
        if (getBitmapsFromAdapter().isEmpty()) {
            return true
        }
        val insertPhotosTransactionString = createInsertPhotosTransactionString(uniqueId)
        val requestBody = createRequestBody(insertPhotosTransactionString)
        return postToGeoserver(requestBody)
    }

    private fun createInsertPhotosTransactionString(uniqueId: String): String {
        val hexOfPhotosList = getHexOfPhotosList().toList()
        return GeoserverPhotosPostStringsFactory.createInsertPhotosTransactionString(hexOfPhotosList,
            uniqueId)
    }

    private suspend fun checkIfItemIsInGeoserver(uniqueId: String): Boolean {
        return getDamageDataItemFromGeoServerByUniqueId(uniqueId) != null
    }


    private suspend fun compareIfLocalDamageDataItemWithDataInGeoserverAreEqual(): Boolean {
        val localDamageDataItem = damageDataItem.value ?: return false
        val uniqueId = damageDataItem.value?.unique_id ?: return false
        val damageDataItemFromGeoserver =
            getDamageDataItemFromGeoServerByUniqueId(uniqueId) ?: return false
        if (localDamageDataItem == damageDataItemFromGeoserver) {
            return true
        }
        return false
    }

    private suspend fun compareIfLocalAndRemoteListsOfBitmapIndexesAreEqual(): Boolean {
        val uniqueId = damageDataItem.value?.unique_id ?: return false
        val localIndexes = damageDataItem.value?.indexesOfPhotos ?: return false
        val remoteIndexes = getIndexesOfPhotosFromGeoserver(uniqueId) ?: return false
        if (localIndexes == remoteIndexes) {
            return true
        }
        return false
    }

    private suspend fun checkIfShouldUpdate(): Boolean {
        return compareIfLocalDamageDataItemWithDataInGeoserverAreEqual() &&
                !compareIfLocalAndRemoteListsOfBitmapIndexesAreEqual()
    }

}