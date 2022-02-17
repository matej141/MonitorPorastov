package com.android.monitorporastov.fragments

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.MediaStore.ACTION_IMAGE_CAPTURE
import android.util.Log
import android.view.*
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.android.monitorporastov.*
import com.android.monitorporastov.adapters.PhotoItem
import com.android.monitorporastov.adapters.PhotosRecyclerViewAdapter
import com.android.monitorporastov.databinding.FragmentAddDamageBinding
import com.android.monitorporastov.model.DamageData
import com.android.monitorporastov.placeholder.PlaceholderContent
import com.android.monitorporastov.placeholder.PlaceholderItem
import id.zelory.compressor.Compressor
import id.zelory.compressor.constraint.format
import id.zelory.compressor.constraint.quality
import id.zelory.compressor.constraint.size
import kotlinx.coroutines.*
import okhttp3.*
import java.io.*
import java.util.*
import java.util.concurrent.TimeUnit


/**
 * Fragment slúžiaci na zadanie údajov o poškodení (aj fotografií) a uloženie poškodenia.
 */
class AddDamageFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private var dataItem: PlaceholderItem? = null
    private var editData = false  // či pridávame nové poškodenie, alebo meníme existujúce
    private val viewModel: ListViewModel by activityViewModels()

    private var _binding: FragmentAddDamageBinding? = null

    private val binding get() = _binding!!
    private var adapterOfPhotos = PhotosRecyclerViewAdapter(mutableListOf())

    private lateinit var listOfDamageType: Array<String>

    private var perimeter: Double? = null
    private var area: Double? = null
    private lateinit var newItem: DamageData
    private val maxSizeOfPhoto = 600
//    private lateinit var userName: String   // dokoncit!!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        arguments?.let {
            if (it.containsKey(ARG_PERIMETER_ID)) {
                perimeter = it.getDouble(ARG_PERIMETER_ID)
            }
            if (it.containsKey(ARG_AREA_ID)) {
                area = it.getDouble(ARG_AREA_ID)
            }
            if (it.containsKey(ARG_DATA_ITEM_ID)) {
                dataItem =
                    PlaceholderContent.ITEM_MAP[it.getInt(DataDetailFragment.ARG_DATA_ITEM_ID)]
                editData = true
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAddDamageBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = binding.itemList
        setupRecycleView()
        setUpListeners()
        if (editData) {
            setUpExistingContent()
        }
        listOfDamageType = resources.getStringArray(R.array.damages)
        viewModel.newItem.observe(viewLifecycleOwner, Observer { newItem ->
            newItem?.let {
                this.newItem = it
            }
        })
//        viewModel.username.observe(viewLifecycleOwner, Observer { username ->
//            username?.let {
//                this.username = it
//            }
//        })  // dokoncit!!!
    }

    /**
     * Ak chceme iba upraviť informácie, pomocou tejto metódy zobrazíme existujúce dáta.
     */
    private fun setUpExistingContent() {
        binding.addDataName.editText?.setText(dataItem?.name)
        binding.addDataDamageType.setText(dataItem?.damageType)
        binding.addDataDescription.editText?.setText(dataItem?.info)
        dataItem?.photos?.forEach { adapterOfPhotos.values.add(PhotoItem(it)) }
    }

    /**
     * Metóda nastavuje listenery všetkým buttonom.
     */
    private fun setUpListeners() {
        binding.galleryButton.setOnClickListener {
            choosePhoto()
        }
        binding.cameraButton.setOnClickListener {
            takePhoto()
        }

        binding.addDataDamageType.setOnClickListener {
            choiceAD()
        }
        binding.saveDamage.setOnClickListener {
            saveData()
        }
    }

    private fun setupRecycleView() {
        recyclerView.adapter = adapterOfPhotos
    }

    /**
     * Uloženie dát.
     */
    private fun saveData() {
        if (binding.addDataName.editText?.length() ?: 0 == 0) {
            warningAD()
            return
        }
        val item = createPlaceholderItem()
        createItem()
        // ak používateľ iba upravoval dáta, upravené dáta uloží a naviguje ho naspäť
        // na fragment zobrazujúci detail o poškodení.
        if (editData) {
            if (item != null) {
                PlaceholderContent.changeItem(item, item.id)
            }
            navigateToItemDetail()
            // ak používateľ pridával nové poškodenie, dáta uloží a naviguje ho naspäť
            // na mapový fragment.
        } else {
            if (item != null) {
                saveDataToGeoserver()
                PlaceholderContent.addItem(item)
            }

        }
    }

    private fun createStringFromPoints(): String {
        val geoPoints = newItem.coordinates
        var str = ""
        geoPoints.forEach { str += "${it.latitude},${it.longitude} " }
        str += "${geoPoints[0].latitude},${geoPoints[0].longitude}"

        return str
    }

    private fun createInsertDataTransactionText(): String {
        val stringFromPoints = createStringFromPoints()
        return "<Transaction xmlns=\"http://www.opengis.net/wfs\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
                "xsi:schemaLocation=\"http://opengeo.org/geoserver_skuska http://services.skeagis.sk:7492/geoserver/wfs?SERVICE=WFS&amp;REQUEST=DescribeFeatureType&amp;VERSION=1.0.0&amp;TYPENAME=geoserver_skuska:porasty\" " +
                "xmlns:gml=\"http://www.opengis.net/gml\" version=\"1.0.0\" service=\"WFS\" " +
                "xmlns:geoserver_skuska=\"http://opengeo.org/geoserver_skuska\">" +
                "<Insert xmlns=\"http://www.opengis.net/wfs\">" +
                "<porasty xmlns=\"http://opengeo.org/geoserver_skuska\">" +
                "<nazov xmlns=\"http://opengeo.org/geoserver_skuska\">${newItem.nazov}</nazov>" +
                "<pouzivatel xmlns=\"http://opengeo.org/geoserver_skuska\">dano</pouzivatel>" +
                "<typ_poskodenia xmlns=\"http://opengeo.org/geoserver_skuska\">${newItem.typ_poskodenia}</typ_poskodenia>" +
                "<popis_poskodenia xmlns=\"http://opengeo.org/geoserver_skuska\">${newItem.popis_poskodenia}</popis_poskodenia>" +
                "<obvod xmlns=\"http://opengeo.org/geoserver_skuska\">${newItem.obvod}</obvod>" +
                "<obsah xmlns=\"http://opengeo.org/geoserver_skuska\">${newItem.obsah}</obsah>" +
                "<unique_id xmlns=\"http://opengeo.org/geoserver_skuska\">${newItem.unique_id}</unique_id>" +
                "<geom xmlns=\"http://opengeo.org/geoserver_skuska\"><gml:Polygon srsName=\"urn:ogc:def:crs:EPSG::4326\">" +
                "<gml:outerBoundaryIs><gml:LinearRing><gml:coordinates cs=\",\" ts=\" \">" +
                stringFromPoints +
                "</gml:coordinates></gml:LinearRing></gml:outerBoundaryIs></gml:Polygon></geom></porasty></Insert></Transaction>"
    }

    private fun createRequestBody(requestBodyText: String): RequestBody {
        return RequestBody.create(MediaType.parse("text/xml"), requestBodyText)
    }

    private fun createOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(BasicAuthInterceptor("dano", "test"))
            .connectTimeout(100, TimeUnit.SECONDS)
            .readTimeout(100, TimeUnit.SECONDS)
            .writeTimeout(100, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(0, 5, TimeUnit.MINUTES))
            .protocols(listOf(Protocol.HTTP_1_1))
            .build()

    private fun getFeatureIdFromResponseString(responseString: String): Int {
        if (responseString.contains("fid=")) {
            var string = responseString.substringAfter("fid=\"")
            string = string.substringAfter(".")
            string = string.substringBefore("\"")
            return string.toInt()
        }
        return -1
    }

    private fun createPhotoStrings(): String {
        val photoHexStrings = adapterOfPhotos.hesStrings
        var photoStrings = ""
        photoHexStrings.forEach {
            val line =
                "   <fotografia xmlns=\"http://opengeo.org/geoserver_skuska\">$it</fotografia>\n " +
                        "   <unique_id xmlns=\"http://opengeo.org/geoserver_skuska\">${newItem.unique_id}</unique_id>\n"
            photoStrings += line
        }
        return photoStrings
    }

    private fun createInsertPhotosTransactionText(): String {
        val photoStrings = createPhotoStrings()
        return "<Transaction xmlns=\"http://www.opengis.net/wfs\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:geoserver_skuska=\"http://opengeo.org/geoserver_skuska\" xmlns:gml=\"http://www.opengis.net/gml\" service=\"WFS\" xsi:schemaLocation=\"http://opengeo.org/geoserver_skuska http://services.skeagis.sk:7492/geoserver/wfs?SERVICE=WFS&amp;REQUEST=DescribeFeatureType&amp;VERSION=1.0.0&amp;TYPENAME=geoserver_skuska:fotografie\" version=\"1.0.0\">\n" +
                "    <Insert xmlns=\"http://www.opengis.net/wfs\">\n" +
                "        <fotografie xmlns=\"http://opengeo.org/geoserver_skuska\">\n" +
                "        $photoStrings" +
                "        </fotografie>\n" +
                "    </Insert>\n" +
                "</Transaction>"
    }

    // https://dev.to/rohitjakhar/hide-keyboard-in-android-using-kotlin-in-20-second-18gp
    // mozno uzitocne na editText https://stackoverflow.com/questions/52469649/kotlin-hide-soft-keyboard-on-android-8
    private fun Fragment.hideKeyboard() {
        view?.let { activity?.hideKeyboard(it) }
    }

    fun Activity.hideKeyboard() {
        hideKeyboard(currentFocus ?: View(this))
    }

    private fun Context.hideKeyboard(view: View) {
        val inputMethodManager =
            getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun createUniqueId(): String {
        val uuid = UUID.randomUUID().toString()
        return "dano:$uuid"
    }

    private fun saveDataToGeoserver() {
        hideKeyboard()
        val uniqueId = createUniqueId()
        newItem.unique_id = uniqueId

        CoroutineScope(Dispatchers.Main).launch {
            binding.progressBar.visibility = View.VISIBLE
//            val operation = async(Dispatchers.IO) {
//                sendInfoToGeoserver()
//                sendPhotosToGeoserver()
//            }
//            operation.await()
            val list =
                listOf(
                    async { sendDetailInfoToGeoserver() },
                    async {
                        if (adapterOfPhotos.bitmaps.isNotEmpty()) {
                            sendPhotosToGeoserver()
                        }
                    })
            list.awaitAll()
            binding.progressBar.visibility = View.GONE
            navigateToMap()
        }
    }

    private suspend fun sendDataToGeoserver(requestBody: RequestBody): Boolean {
        val deferredBoolean = CompletableDeferred<Boolean>()
        val okHttpClient = createOkHttpClient()
        val service = RetroService.getServiceWithScalarsFactory(okHttpClient)
        CoroutineScope(Dispatchers.IO).launch {
            val response = service.postToGeoserver(requestBody)
            withContext(Dispatchers.Main) {
                if (response.isSuccessful) {
                    // binding.progressBar.visibility = View.GONE
                    val r: String? = response.body()

                    Log.d("MODELL", "Success!!!!!!!!!!!!!!!!!!!")
                    if (r != null) {
                        Log.d("MODELL", r)
                    }
                    if (r != null && r.contains("SUCCESS")) {
                        Log.d("MODELL", "Fotky úspešné....")
                    }
                    deferredBoolean.complete(true)
                } else {
                    Log.d("MODELL", "Error: ${response.message()}")
                    deferredBoolean.complete(false)

                }
            }
        }
        return deferredBoolean.await()
    }

    private suspend fun sendDetailInfoToGeoserver(): Boolean {
        val requestBody = createRequestBody(createInsertDataTransactionText())
        return sendDataToGeoserver(requestBody)
    }

    private suspend fun sendPhotosToGeoserver(): Boolean {
        val requestBody = createRequestBody(createInsertPhotosTransactionText())
        return sendDataToGeoserver(requestBody)
    }

    /**
     * Naviguje používateľa na fragment zobrazujúci detail o poškodení.
     */
    private fun navigateToItemDetail() {
        val bundle = Bundle()
        val navController = findNavController()
        dataItem?.let {
            bundle.putInt(
                DataDetailFragment.ARG_DATA_ITEM_ID,
                it.id
            )
            navController.navigate(R.id.action_add_measure_fragment_TO_data_detail_fragment,
                bundle)
        }
    }

    /**
     * Naviguje používateľa na naspäť na mapový fragment.
     */
    private fun navigateToMap() {
        val navController = findNavController()
        navController.previousBackStackEntry?.savedStateHandle?.set("key", true)
        navController.popBackStack()
    }

    private fun createItem() {
        val name = binding.addDataName.editText?.text.toString()
        val damageType = binding.addDataDamageType.text.toString()
        val info = binding.addDataDescription.editText?.text.toString()
        newItem.nazov = name
        newItem.typ_poskodenia = damageType
        newItem.popis_poskodenia = info
    }

    /**
     * Zo zadaných údajov vytvorí PlaceholderItem .
     */
    private fun createPlaceholderItem(): PlaceholderItem? {
        val name = binding.addDataName.editText?.text.toString()
        val damageType = binding.addDataDamageType.text.toString()
        val info = binding.addDataDescription.editText?.text.toString()
        val photos = adapterOfPhotos.bitmaps
        val id = PlaceholderContent.ITEMS_COUNT
        if (editData) {
            return dataItem?.let {
                PlaceholderItem(it.id,
                    name,
                    damageType,
                    info,
                    photos,
                    it.perimeter,
                    it.area)
            }
        }

        val placeholderItem =
            perimeter?.let {
                area?.let { it1 ->
                    PlaceholderItem(id, name, damageType, info, photos, it,
                        it1)
                }
            }

        return placeholderItem
    }

    /**
     * Zobrazuje pouužívateľovi alert dialog, ktorý ho upozorňuje, že nevybral typ poškodenia.
     */
    private fun warningAD() {
        AlertDialog.Builder(requireContext())
            .setTitle("Nekompletné údaje")
            .setMessage("Musíte zadať aspoň názov poškodenia")
            .setNegativeButton("Ok") { dialog, _ -> dialog.cancel() }
            .create()
            .show()
    }

    /**
     * Zobrazuje pouužívateľovi alert dialog umožňujúci vybrať typ poškodenia.
     */
    private fun choiceAD() {
        AlertDialog.Builder(requireContext())
            .setTitle("Vyberte typ poškodenia:")
            .setSingleChoiceItems(listOfDamageType, -1) { dialogInterface, i ->
                binding.addDataDamageType.setText(listOfDamageType[i])
                dialogInterface.dismiss()
            }
            .create()
            .show()
    }


    /**
     * Použitie fotoaparátu - launcher.
     */
    private var resultTakePhotoLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult())
        { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data: Intent? = result.data
                val bitmap = data?.extras?.get("data") as Bitmap
                addBitmapToAdapter(bitmap)
            }
        }

    /**
     * Výber fotiek z galérie - launcher.
     */
    private var resultGalleryLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult())
        { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data: Intent? = result.data
                val uri: Uri? = data?.data
                val contentResolver: ContentResolver? = context?.contentResolver
                if (uri != null && contentResolver != null) {
                    val bitmap = if (Build.VERSION.SDK_INT < 28) {
                        MediaStore.Images.Media.getBitmap(contentResolver, uri)
                    } else {
                        val source = ImageDecoder.createSource(contentResolver, uri)
                        ImageDecoder.decodeBitmap(source)
                    }

                    addBitmapToAdapter(bitmap)
                }
            }
        }

    private fun addBitmapToAdapter(bitmap: Bitmap) {
        val resizedBitmap = getResizedBitmap(bitmap, maxSizeOfPhoto)
        val item = PhotoItem(resizedBitmap)
        adapterOfPhotos.values.add(item)
        adapterOfPhotos.notifyItemInserted(adapterOfPhotos.values.size - 1)
        CoroutineScope(Dispatchers.Main).launch {
            addBitmapHex(resizedBitmap)
        }
    }

    private suspend fun addBitmapHex(bitmap: Bitmap) {
        val compressedByteArray = createCompressedByteArray(bitmap)
        val hexStringOfByteArray = createHexStringFromByteArray(compressedByteArray)
        adapterOfPhotos.addHexString(hexStringOfByteArray)
    }

    private suspend fun createCompressedByteArray(bitmap: Bitmap): ByteArray {
        val imageFile = createImageFile(bitmap)
        val compressedFile = createCompressedFile(imageFile)
        val byteArrayFromFile = createByteArrayFromFile(compressedFile)
        deleteFiles(imageFile, compressedFile)
        return byteArrayFromFile
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

    private fun deleteFiles(vararg files: File) {
        files.forEach { it.delete() }
    }

    private suspend fun createCompressedFile(imageFile: File): File {
        val compressedImageFileDeferred = CompletableDeferred<File>()
        CoroutineScope(Dispatchers.Main).launch {
            // https://github.com/zetbaitsu/Compressor
            val compressedImageFile: File = Compressor.compress(requireContext(), imageFile) {
                // resolution(1280, 720)
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

    private fun createImageFile(bitmap: Bitmap): File {
        val childName = "filename"
        val newImageFile = File(requireContext().cacheDir, childName)
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

    // https://stackoverflow.com/questions/16954109/reduce-the-size-of-a-bitmap-to-a-specified-size-in-android
    private fun getResizedBitmap(image: Bitmap, maxSize: Int): Bitmap {
        var width = image.width
        var height = image.height
        val bitmapRatio = width.toFloat() / height.toFloat()
        if (bitmapRatio > 1) {
            width = maxSize
            height = (width / bitmapRatio).toInt()
        } else {
            height = maxSize
            width = (height * bitmapRatio).toInt()
        }
        return Bitmap.createScaledBitmap(image, width, height, true)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Výber fotiek z galérie.
     */
    private fun choosePhoto() {

        val galleryIntent = Intent(
            Intent.ACTION_PICK,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        )
        resultGalleryLauncher.launch(galleryIntent)
    }

    /**
     * Použitie fotoaparátu.
     */
    private fun takePhoto() {
        val cameraIntent = Intent(
            ACTION_IMAGE_CAPTURE
        )
        resultTakePhotoLauncher.launch(cameraIntent)
    }


    companion object {
        const val ARG_PERIMETER_ID = "perimeter_id"
        const val ARG_AREA_ID = "area_id"
        const val ARG_DATA_ITEM_ID = "item_id"

    }

}
