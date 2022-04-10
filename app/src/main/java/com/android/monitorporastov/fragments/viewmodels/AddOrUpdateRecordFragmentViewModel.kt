package com.android.monitorporastov.fragments.viewmodels

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.android.monitorporastov.Utils.createRequestBody
import com.android.monitorporastov.adapters.AddOrUpdateRecordPhotosRVAdapter
import com.android.monitorporastov.fragments.viewmodels.base.DamagePhotosBaseViewModel
import com.android.monitorporastov.geoserver.factories.GeoserverDataPostStringsFactory
import com.android.monitorporastov.geoserver.factories.GeoserverPhotosPostStringsFactory
import com.android.monitorporastov.model.DamageData
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


    init {
        val addDamageFragmentPhotosRVAdapter = AddOrUpdateRecordPhotosRVAdapter(mutableListOf())
        setAdapterOfPhotos(addDamageFragmentPhotosRVAdapter)
    }

    private fun setAdapterOfPhotos(
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
        nameOfDamageDataRecord = name.removeWhitespaces()
    }

    fun setDescriptionOfDamageDataRecord(description: String) {
        descriptionOfDamageDataRecord = description.removeWhitespaces()
    }

    fun setDamageType(type: String) {
        damageType = type.removeWhitespaces()
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

    private fun String.removeWhitespaces(): String {
        return filterNot { it.isWhitespace() }
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


    // --------
    fun addBitmapToAdapter(bitmap: Bitmap, context: Context) {
        val resizedBitmap = getResizedBitmap(bitmap)
        addPhotoItemToAdapter(resizedBitmap)
        notifyItemInsertedInAdapter()
        job = CoroutineScope(Dispatchers.Main).launch {
            addBitmapHexToAdapter(resizedBitmap, context)
            setAdapterWasChanged()
        }
    }

    // https://stackoverflow.com/questions/16954109/reduce-the-size-of-a-bitmap-to-a-specified-size-in-android
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
        // newImageFile.deleteOnExit() // vyskusat potom
        val byteArray = createByteArrayFromBitmap(bitmap)
        // https://stackoverflow.com/questions/11274715/save-bitmap-to-file-function
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
                size(1_097_152)
            }
            imageFile.delete()
            compressedImageFileDeferred.complete(compressedImageFile)

        }
        return compressedImageFileDeferred.await()
    }

    private suspend fun createByteArrayFromFile(file: File): ByteArray {
        // https://stackoverflow.com/questions/10039672/android-how-to-read-file-in-bytes
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
        // https://stackoverflow.com/questions/9655181/how-to-convert-a-byte-array-to-a-hex-string-in-java
        val hexArray = "0123456789ABCDEF".toCharArray()
        val hexChars = CharArray(bytes.size * 2)
        for (j in bytes.indices) {
            val v = bytes[j].toInt() and 0xFF

            hexChars[j * 2] = hexArray[v ushr 4]
            hexChars[j * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars)
    }

    fun saveData() {
        if (nameOfDamageDataRecord.isEmpty()) {
            setUncompletedNameWarning()
            return
        }
        val isEditingDataValue = isEditingData.value
        if (isEditingDataValue == true) {
            updateDataInGeoserver()
        } else {
            saveDataToGeoserver()
        }
    }

    private fun updateDataInGeoserver() {
        setLoading(true)
        job = CoroutineScope(Dispatchers.Main).launch {
            val resultsListDeferred: List<Deferred<Boolean>> =
                listOf(
                    async { updateDamageInfoInGeoserver() },
                    async { updatePhotosInGeoserver() }
                )
            val resultsList: List<Boolean> = resultsListDeferred.awaitAll()
            val ifUpdateSucceeded = checkIfCallsWereSucceeded(resultsList)

            setIfUpdateSucceeded(ifUpdateSucceeded)
            if (ifUpdateSucceeded) {
                onSucceededResult()
                updateSavedData()
            }
            setLoading(false)

        }
    }

    private fun checkIfCallsWereSucceeded(resultsList: List<Boolean>): Boolean {
        val predicate: (Boolean) -> Boolean = { it }
        return resultsList.all(predicate)
    }

    private fun onSucceededResult() {
        updateDataInSharedViewModel()
        whereToNavigateBack()
        setDataNotLoadedInSharedViewModel()
    }

    private fun setDataNotLoadedInSharedViewModel() {
        sharedViewModel?.setIfLoadedUserData(false)
    }

    private fun whereToNavigateBack() {
        val isItemDirectlyFromMap = damageDataItem.value?.isDirectlyFromMap == true
        setNavigateToMapFragment(isItemDirectlyFromMap)
    }

    private suspend fun updateDamageInfoInGeoserver(): Boolean {
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
        if (sharedViewModel?.selectedDamageDataItemFromMap?.value?.id ==
            sharedViewModel?.selectedDamageDataItem?.value?.id) {
            damageDataItem.value?.let { sharedViewModel?.selectDamageData(it) }
        }
        //sharedViewModel.updateSelectedItems(damageDataItem)
    }

    private fun saveDataToGeoserver() {
        setLoading(true)
        val uniqueId = createUniqueId()

        job = CoroutineScope(Dispatchers.Main).launch {
            val resultsListDeferred =
                listOf(
                    async { sendDamageDataToGeoserver(uniqueId) },
                    async { sendPhotosToGeoserver(uniqueId)
                    })
            resultsListDeferred.awaitAll()
            val resultsList: List<Boolean> = resultsListDeferred.awaitAll()
            val ifUpdateSucceeded = checkIfCallsWereSucceeded(resultsList)
            setIfUpdateSucceeded(ifUpdateSucceeded)
            if (ifUpdateSucceeded) {
                onSucceededResult()
            }
            setLoading(false)
        }
    }

    private fun getBitmapsFromAdapter(): MutableList<Bitmap> {
        return adapterOfPhotos.value?.bitmaps ?: mutableListOf()
    }

    private suspend fun sendDamageDataToGeoserver(uniqueId: String): Boolean {
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


}