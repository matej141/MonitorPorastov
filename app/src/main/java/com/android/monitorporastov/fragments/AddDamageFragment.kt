package com.android.monitorporastov.fragments

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentResolver
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.MediaStore.ACTION_IMAGE_CAPTURE
import android.util.Base64
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.android.monitorporastov.*
import com.android.monitorporastov.Utils.afterTextChanged
import com.android.monitorporastov.Utils.hideKeyboard
import com.android.monitorporastov.adapters.AddDamageFragmentPhotosRVAdapter
import com.android.monitorporastov.adapters.models.PhotoItem
import com.android.monitorporastov.databinding.FragmentAddDamageBinding
import com.android.monitorporastov.fragments.viewmodels.AddDamageFragmentViewModel
import com.android.monitorporastov.model.DamageData
import com.android.monitorporastov.viewmodels.MainSharedViewModelNew
import kotlinx.coroutines.*
import okhttp3.*
import java.util.*


/**
 * Fragment slúžiaci na zadanie údajov o poškodení (aj fotografií) a uloženie poškodenia.
 */
class AddDamageFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private var editData = false  // či pridávame nové poškodenie, alebo meníme existujúce
    private val sharedViewModel: MainSharedViewModelNew by activityViewModels()
    private val viewModel: AddDamageFragmentViewModel by viewModels()

    private var _binding: FragmentAddDamageBinding? = null

    private val binding get() = _binding!!
    private var adapterOfPhotos = AddDamageFragmentPhotosRVAdapter(mutableListOf())

    private lateinit var listOfDamageType: Array<String>

    private lateinit var damageDataItem: DamageData

    private var stringsOfPhotosList = listOf<String>()

//    private lateinit var callback: OnBackPressedCallback

//    private lateinit var userName: String   // dokoncit!!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
//        callback = object : OnBackPressedCallback(true) {
//            override fun handleOnBackPressed() {
//                if (editData) {
//                    checkIfBitmapsChanged()
//                    navigateToItemDetail()
//                }
//            }
//        }
    }

    private fun checkIfBitmapsChanged() {
        if (adapterOfPhotos.bitmaps.size != damageDataItem.bitmaps.size) {
            damageDataItem.bitmapsLoaded = false
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
        recyclerView = binding.recycleViewOfPhotos
        setUpListeners()
        setTextChangeListeners()
        listOfDamageType = resources.getStringArray(R.array.damages)
        observeDamageDataItemFromSharedViewModel()
        observeAdapterOfPhotos()
        observeIfEditing()
        observeUncompletedNameWarning()
        observeIfUpdateSucceeded()
        observeLoadingValue()
        viewModel.initViewModelMethods(sharedViewModel, viewLifecycleOwner)


    }

    private fun setTextChangeListeners() {
        binding.addDataName.editText?.apply {
            afterTextChanged {
                viewModel.setNameOfDamageDataRecord(it.toString())
            }
        }
        binding.addDataDamageTypeText.editText?.apply {
            afterTextChanged {
                viewModel.setDamageType(it.toString())
            }
        }
        binding.addDataDescription.editText?.apply {
            afterTextChanged {
                viewModel.setDescriptionOfDamageDataRecord(it.toString())
            }
        }
    }

    // https://stackoverflow.com/questions/56649766/trouble-with-navcontroller-inside-onbackpressedcallback
    override fun onStart() {
        super.onStart()
//        requireActivity().onBackPressedDispatcher.addCallback(callback)
    }

    override fun onStop() {
        super.onStop()
//        callback.remove()
    }

    /**
     * Ak chceme iba upraviť informácie, pomocou tejto metódy zobrazíme existujúce dáta.
     */
    private fun setUpExistingContent(damageDataItem: DamageData) {
        binding.addDataName.editText?.setText(damageDataItem.nazov)
        binding.addDataDamageType.setText(damageDataItem.typ_poskodenia)
        binding.addDataDescription.editText?.setText(damageDataItem.popis_poskodenia)

    }

    private fun setUpExistingBitmaps() {
        damageDataItem.bitmaps.forEach {
            adapterOfPhotos.photoItems.add((PhotoItem(it)))
            adapterOfPhotos.hexStrings.add("")
        }
        damageDataItem.indexesOfPhotos.forEach { adapterOfPhotos.indexesOfPhotos.add(it) }
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
            viewModel.saveData()
        }
    }

//    private fun setupRecycleView() {
//        recyclerView.adapter = adapterOfPhotos
//    }

    /**
     * Uloženie dát.
     */
    private fun saveData() {
        if (binding.addDataName.editText?.length() ?: 0 == 0) {
            warningAD()
            return
        }

        // ak používateľ iba upravoval dáta, upravené dáta uloží a naviguje ho naspäť
        // na fragment zobrazujúci detail o poškodení.
        if (editData) {
            updateDataInGeoserver()
            // ak používateľ pridával nové poškodenie, dáta uloží a naviguje ho naspäť
            // na mapový fragment.
        } else {
            saveDataToGeoserver()
        }
    }

    private fun createStringFromPoints(): String {
        val geoPoints = damageDataItem.coordinates
        var stringFromPoints = ""
        geoPoints.forEach { stringFromPoints += "${it.latitude},${it.longitude} " }
        // str += "${geoPoints[0].latitude},${geoPoints[0].longitude}"  // toto robilo chybu trebalo sa rozhodnut, ci toto robit v mape, alebo tu

        return stringFromPoints
    }

    private fun createInsertDataTransactionString(): String {
        val stringFromPoints = createStringFromPoints()

        return "<Transaction xmlns=\"http://www.opengis.net/wfs\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
                "xsi:schemaLocation=\"http://geoserver.org/geoserver_skeagis http://services.skeagis.sk:7492/geoserver/wfs?SERVICE=WFS&amp;REQUEST=DescribeFeatureType&amp;VERSION=1.0.0&amp;TYPENAME=geoserver_skeagis:porasty\" " +
                "xmlns:gml=\"http://www.opengis.net/gml\" version=\"1.0.0\" service=\"WFS\" " +
                "xmlns:geoserver_skeagis=\"http://geoserver.org/geoserver_skeagis\">" +
                "<Insert xmlns=\"http://www.opengis.net/wfs\">" +
                "<porasty xmlns=\"http://geoserver.org/geoserver_skeagis\">" +
                "<nazov xmlns=\"http://geoserver.org/geoserver_skeagis\">${damageDataItem.nazov}</nazov>" +
                "<pouzivatel xmlns=\"http://geoserver.org/geoserver_skeagis\">dano</pouzivatel>" +
                "<typ_poskodenia xmlns=\"http://geoserver.org/geoserver_skeagis\">${damageDataItem.typ_poskodenia}</typ_poskodenia>" +
                "<popis_poskodenia xmlns=\"http://geoserver.org/geoserver_skeagis\">${damageDataItem.popis_poskodenia}</popis_poskodenia>" +
                "<obvod xmlns=\"http://geoserver.org/geoserver_skeagis\">${damageDataItem.obvod}</obvod>" +
                "<obsah xmlns=\"http://geoserver.org/geoserver_skeagis\">${damageDataItem.obsah}</obsah>" +
                "<unique_id xmlns=\"http://geoserver.org/geoserver_skeagis\">${damageDataItem.unique_id}</unique_id>" +
                "<geom xmlns=\"http://geoserver.org/geoserver_skeagis\"><gml:Polygon srsName=\"urn:ogc:def:crs:EPSG::4326\">" +
                "<gml:outerBoundaryIs><gml:LinearRing><gml:coordinates cs=\",\" ts=\" \">" +
                stringFromPoints +
                "</gml:coordinates></gml:LinearRing></gml:outerBoundaryIs></gml:Polygon></geom></porasty></Insert></Transaction>"
    }

    private fun createRequestBody(requestBodyText: String): RequestBody {
        return RequestBody.create(MediaType.parse("text/xml"), requestBodyText)
    }

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
        val photoHexStrings = adapterOfPhotos.hexStrings.filter { it != "" }
        var photoStrings = ""
        photoHexStrings.forEach {
            val line =
                "   <fotografie xmlns=\"http://geoserver.org/geoserver_skeagis\">\n" +
                        "       <fotografia xmlns=\"http://geoserver.org/geoserver_skeagis\">$it</fotografia>\n " +
                        "<unique_id xmlns=\"http://geoserver.org/geoserver_skeagis\">${damageDataItem.unique_id}</unique_id>\n" +
                        "       </fotografie>\n"
            photoStrings += line
        }
        return photoStrings
    }

    private fun createInsertPhotosTransactionString(): String {
        val photoStrings = createPhotoStrings()
        return "<Transaction xmlns=\"http://www.opengis.net/wfs\" " +
                "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
                "xmlns:geoserver_skeagis=\"http://geoserver.org/geoserver_skeagis\" " +
                "xmlns:gml=\"http://www.opengis.net/gml\" service=\"WFS\" " +
                "xsi:schemaLocation=\"http://geoserver.org/geoserver_skeagis " +
                "http://services.skeagis.sk:7492/geoserver/wfs?SERVICE=WFS&amp;REQUEST=" +
                "DescribeFeatureType&amp;VERSION=1.0.0&amp;" +
                "TYPENAME=geoserver_skeagis:fotografie\" " +
                "version=\"1.0.0\">\n" +
                "    <Insert xmlns=\"http://www.opengis.net/wfs\">\n" +
                "        $photoStrings" +
                "    </Insert>\n" +
                "</Transaction>"
    }

    private fun createUniqueId(): String {
        val uuid = UUID.randomUUID().toString()
        return "dano:$uuid"
    }

    private fun updateDataInGeoserver() {
        CoroutineScope(Dispatchers.Main).launch {
            binding.progressBar.visibility = View.VISIBLE
            val list =
                listOf(
                    async { updateDamageInfoInGeoserver() },
                    async { updatePhotosInGeoserver() }
                )
            list.awaitAll()
            binding.progressBar.visibility = View.INVISIBLE
            Toast.makeText(context, "Záznam bol aktualizovaný",
                Toast.LENGTH_SHORT).show()
            updateDataInSharedViewModel()
            if (!damageDataItem.isUpdatingDirectlyFromMap) {
                navigateToDataDetailFragment()
            } else {
                navigateToMapFragment()
            }
        }
    }

    private fun updateDataInSharedViewModel() {
       // sharedViewModel.updateSelectedItems(damageDataItem)
    }

    private suspend fun updatePhotosInGeoserver(): Boolean {
        val updatePhotosTransactionString = createUpdatePhotosTransactionString()
        if (updatePhotosTransactionString.isEmpty()) {
            return false
        }
        // damageDataItem.bitmapsLoaded = false
        damageDataItem.bitmaps = adapterOfPhotos.bitmaps
        val requestBody = createRequestBody(updatePhotosTransactionString)
        return postToGeoserver(requestBody)
    }

    private fun createUpdatePhotosTransactionString(): String {
        val deletePhotosString = createDeletePhotosString()
        val insertPhotosString = createInsertPhotosString()
        var updatePhotosTransactionString = ""
        if (deletePhotosString.isEmpty() && insertPhotosString.isEmpty()) {
            return updatePhotosTransactionString
        }
        updatePhotosTransactionString += "<Transaction xmlns=\"http://www.opengis.net/wfs\" " +
                "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
                "xmlns:geoserver_skeagis=\"http://geoserver.org/geoserver_skeagis\" " +
                "xmlns:gml=\"http://www.opengis.net/gml\" service=\"WFS\" " +
                "xsi:schemaLocation=\"http://geoserver.org/geoserver_skeagis " +
                "http://services.skeagis.sk:7492/geoserver/wfs?SERVICE=WFS&amp;REQUEST=" +
                "DescribeFeatureType&amp;VERSION=1.0.0&amp;" +
                "TYPENAME=geoserver_skeagis:fotografie\" " +
                "version=\"1.0.0\">\n"
        if (deletePhotosString.isNotEmpty()) {
            updatePhotosTransactionString += deletePhotosString
        }
        if (insertPhotosString.isNotEmpty()) {
            updatePhotosTransactionString += insertPhotosString
        }
        updatePhotosTransactionString += "</Transaction>"
        return updatePhotosTransactionString
    }

    private fun createInsertPhotosString(): String {
        val photoStrings = createPhotoStrings()
        if (photoStrings.isEmpty()) {
            return ""
        }
        return "    <Insert xmlns=\"http://www.opengis.net/wfs\">\n" +
                "        $photoStrings" +
                "    </Insert>\n"
    }

    private fun createDeletePhotosString(): String {
        val deleteFilterString = createDeleteFilterString()
        if (deleteFilterString.isEmpty()) {
            return ""
        }
        return "<Delete xmlns=\"http://www.opengis.net/wfs\" typeName=\"geoserver_skeagis:fotografie\">\n" +
                "        <Filter xmlns=\"http://www.opengis.net/ogc\">\n" +
                "            <Or>\n" +
                "                $deleteFilterString" +
                "            </Or>\n" +
                "        </Filter>\n" +
                "    </Delete>"
    }

    private fun createDeleteFilterString(): String {
        val indexes = adapterOfPhotos.deletedIndexes
        var deleteFilterString = ""
        indexes.forEach {
            deleteFilterString +=
                "       <PropertyIsEqualTo>\n" +
                        "<PropertyName>id</PropertyName>\n" +
                        "<Literal>$it</Literal>\n" +
                        "</PropertyIsEqualTo>\n"
        }
        return deleteFilterString
    }

    private suspend fun updateDamageInfoInGeoserver(): Boolean {
        val updateDamageDataString = createUpdateDamageDataString()
        if (updateDamageDataString.isEmpty()) {
            return false
        }
        val requestBody = createRequestBody(updateDamageDataString)
        return postToGeoserver(requestBody)
    }

    private fun createUpdatePropertiesString(): String {
        val name = getDataName()
        val damageType = getDataDamageType()
        val description = getDataDescription()
        var updatePropertiesString = ""
        if (name != damageDataItem.nazov) {
            damageDataItem.nazov = name
            updatePropertiesString +=
                "<Property xmlns=\"http://www.opengis.net/wfs\">\n" +
                        "    <Name xmlns=\"http://www.opengis.net/wfs\">nazov</Name>\n" +
                        "    <Value xmlns=\"http://www.opengis.net/wfs\">\n" +
                        "        $name\n" +
                        "     </Value>\n" +
                        "</Property>\n"
        }
        if (damageType != damageDataItem.typ_poskodenia) {
            damageDataItem.typ_poskodenia = damageType
            updatePropertiesString +=
                "<Property xmlns=\"http://www.opengis.net/wfs\">\n" +
                        "    <Name xmlns=\"http://www.opengis.net/wfs\">typ_poskodenia</Name>\n" +
                        "    <Value xmlns=\"http://www.opengis.net/wfs\">\n" +
                        "        $damageType\n" +
                        "     </Value>\n" +
                        "</Property>\n"
        }
        if (description != damageDataItem.popis_poskodenia) {
            damageDataItem.popis_poskodenia = description
            updatePropertiesString +=
                "<Property xmlns=\"http://www.opengis.net/wfs\">\n" +
                        "    <Name xmlns=\"http://www.opengis.net/wfs\">popis_poskodenia</Name>\n" +
                        "    <Value xmlns=\"http://www.opengis.net/wfs\">\n" +
                        "        $description\n" +
                        "     </Value>\n" +
                        "</Property>\n"
        }
        if (damageDataItem.changedShapeOfPolygon) {
            updatePropertiesString +=
                "<Property xmlns=\"http://www.opengis.net/wfs\">\n" +
                        "    <Name xmlns=\"http://www.opengis.net/wfs\">obvod</Name>\n" +
                        "    <Value xmlns=\"http://www.opengis.net/wfs\">\n" +
                        "        ${damageDataItem.obvod}\n" +
                        "     </Value>\n" +
                        "</Property>\n" +
                        "<Property xmlns=\"http://www.opengis.net/wfs\">\n" +
                        "    <Name xmlns=\"http://www.opengis.net/wfs\">obsah</Name>\n" +
                        "    <Value xmlns=\"http://www.opengis.net/wfs\">\n" +
                        "        ${damageDataItem.obsah}\n" +
                        "     </Value>\n" +
                        "</Property>\n"
            updatePropertiesString += "<Property xmlns=\"http://www.opengis.net/wfs\">\n" +
                    "            <Name xmlns=\"http://www.opengis.net/wfs\">geom</Name>\n" +
                    "            <Value xmlns=\"http://www.opengis.net/wfs\">\n" +
                    "                <gml:Polygon srsName=\"urn:ogc:def:crs:EPSG::4326\">\n" +
                    "                    <gml:outerBoundaryIs>\n" +
                    "                        <gml:LinearRing>\n" +
                    "                            <gml:coordinates cs=\",\" ts=\" \">" +
                    createStringFromPoints() +
                    "                            </gml:coordinates>\n" +
                    "                        </gml:LinearRing>\n" +
                    "                    </gml:outerBoundaryIs>\n" +
                    "                </gml:Polygon>\n" +
                    "            </Value>\n" +
                    "        </Property>\n"
        }
        return updatePropertiesString
    }

    private fun createUpdateDamageDataString(): String {
        val updatePropertiesString = createUpdatePropertiesString()
        if (updatePropertiesString.isEmpty()) {
            return ""
        }
        return "<Transaction xmlns=\"http://www.opengis.net/wfs\" " +
                "xmlns:geoserver_skeagis=\"http://geoserver.org/geoserver_skeagis\" " +
                "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
                "xsi:schemaLocation=\"http://geoserver.org/geoserver_skeagis " +
                "http://212.5.204.126:7492/geoserver/wfs?SERVICE=WFS&amp;REQUEST=" +
                "DescribeFeatureType&amp;VERSION=1.0.0&amp;TYPENAME=geoserver_skeagis:porasty\" " +
                "version=\"1.0.0\" service=\"WFS\" xmlns:gml=\"http://www.opengis.net/gml\">\n" +
                "    <Update xmlns=\"http://www.opengis.net/wfs\" " +
                "typeName=\"geoserver_skeagis:porasty\">\n" +
                "        $updatePropertiesString" +
                "        <Filter xmlns=\"http://www.opengis.net/ogc\">\n" +
                "            <PropertyIsEqualTo>" +
                "<PropertyName>id</PropertyName>" +
                "<Literal>${damageDataItem.id}</Literal></PropertyIsEqualTo>\n" +
                "        </Filter>\n" +
                "    </Update>\n" +
                "</Transaction>"
    }

    private fun saveDataToGeoserver() {
        hideKeyboard()
        createDamageDataItem()
        val uniqueId = createUniqueId()
        damageDataItem.unique_id = uniqueId

        CoroutineScope(Dispatchers.Main).launch {
            binding.progressBar.visibility = View.VISIBLE
            val list =
                listOf(
                    async { sendDetailInfoToGeoserver() },
                    async {
                        if (adapterOfPhotos.bitmaps.isNotEmpty()) {
                            sendPhotosToGeoserver()
                        }
                    })
            list.awaitAll()
            binding.progressBar.visibility = View.INVISIBLE
            navigateToMapFragment()
        }
    }

    private suspend fun postToGeoserver(requestBody: RequestBody): Boolean {
        val deferredBoolean = CompletableDeferred<Boolean>()
        val service = RetroService.createServiceWithScalarsFactory(Utils.createOkHttpClient())
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
        val str = createInsertDataTransactionString()
        val requestBody = createRequestBody(createInsertDataTransactionString())
        return postToGeoserver(requestBody)
    }

    private suspend fun sendPhotosToGeoserver(): Boolean {
        val requestBody = createRequestBody(createInsertPhotosTransactionString())
        return postToGeoserver(requestBody)
    }

    private fun getDataName(): String {
        return binding.addDataName.editText?.text.toString()
    }

    private fun getDataDamageType(): String {
        return binding.addDataDamageType.text.toString()
    }

    private fun getDataDescription(): String {
        return binding.addDataDescription.editText?.text.toString()
    }

    private fun createDamageDataItem() {
        damageDataItem.nazov = getDataName()
        damageDataItem.typ_poskodenia = getDataDamageType()
        damageDataItem.popis_poskodenia = getDataDescription()
        damageDataItem.isInGeoserver = false
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
                viewModel.addBitmapToAdapter(bitmap, requireContext())
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

                    viewModel.addBitmapToAdapter(bitmap, requireContext())
                }
            }
        }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
//        sharedViewModel.clearStringsOfPhotosList()
//        sharedViewModel.clearSelectedDamageDataItemFromMap()
    }

    override fun onDetach() {
        super.onDetach()
        hideKeyboard()
    }

    private fun observeDamageDataItemFromSharedViewModel() {
        if (sharedViewModel.selectedDamageDataItemFromMap.value == null) {
            observeSelectedDamageDataItem()
            return
        }
        observeSelectedItemFromMap()
    }

    private fun observeSelectedDamageDataItem() {
        sharedViewModel.selectedDamageDataItem.observe(viewLifecycleOwner) { damageDataItem ->
            damageDataItem?.let {
                viewModel.setExistingDamageData(it)
            }
        }
    }

    private fun observeSelectedItemFromMap() {
        sharedViewModel.selectedDamageDataItemFromMap.observe(viewLifecycleOwner) {
                selectedDamageDataItemFromMap ->
            selectedDamageDataItemFromMap?.let {
                viewModel.setDamageDataFromMap(it)
            }
        }
    }

//    private fun observePhotosFromViewModel() {
//        sharedViewModel.stringsOfPhotosList.observe(viewLifecycleOwner) { stringsOfPhotosList ->
//            stringsOfPhotosList?.let {
//                this.stringsOfPhotosList = it
//                binding.progressBarPhotos.visibility = View.VISIBLE
//                setUpPhotos()
//                observeIndexesOfPhotos()
//                damageDataItem.bitmapsLoaded = true
//                binding.progressBarPhotos.visibility = View.INVISIBLE
//            }
//        }
//    }

//    private fun observeIndexesOfPhotos() {
//        sharedViewModel.indexesOfPhotosList.observe(viewLifecycleOwner) { indexesOfPhotosList ->
//            indexesOfPhotosList?.let {
//                damageDataItem.indexesOfPhotos = it
//            }
//        }
//    }

    private fun setUpPhotos() {
        if (stringsOfPhotosList.isEmpty()) {
            binding.progressBarPhotos.visibility = View.INVISIBLE
            return
        }

        val bitmaps = mutableListOf<Bitmap>()
        CoroutineScope(Dispatchers.Default).launch {
            stringsOfPhotosList.forEach {
                val imageBytes: ByteArray = Base64.decode(it, 0)
                val image = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                bitmaps.add(image)
                damageDataItem.bitmaps.add(image)
            }
            withContext(Dispatchers.Main) {
                // recyclerView.adapter = DataDetailPhotosRVAdapter(bitmaps)
                setUpExistingBitmaps()
                //setupRecycleView()
                binding.progressBarPhotos.visibility = View.INVISIBLE
            }
        }
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


    private fun observeIfEditing() {
        viewModel.isEditingData.observe(viewLifecycleOwner) {
            if (it) {
                observeDamageDataItem()
            }
        }
    }

    private fun observeDamageDataItem() {
        viewModel.damageDataItem.observe(viewLifecycleOwner) {
            setUpExistingContent(it)
        }
    }

    private fun observeAdapterOfPhotos() {
        viewModel.adapterOfPhotos.observe(viewLifecycleOwner) { adapterOfPhotos ->
            recyclerView.adapter = adapterOfPhotos
        }
    }

    private fun observeUncompletedNameWarning() {
        viewModel.uncompletedNameWarning.observe(viewLifecycleOwner) {
            warningAD()
        }
    }

    private fun observeIfUpdateSucceeded() {
        viewModel.updateSucceeded.observe(viewLifecycleOwner) { updateSucceeded ->
            if (updateSucceeded) {
                Toast.makeText(context, "Záznam bol úspešne aktualizovaný",
                    Toast.LENGTH_SHORT).show()
                observeWhereToNavigate()
            }
            else {
                Toast.makeText(context, "Pri aktualizovaní záznamu nastala chyba",
                    Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeWhereToNavigate() {
        viewModel.navigateToMapFragment.observe(viewLifecycleOwner) { navigateToMapFragment ->
            if (navigateToMapFragment) {
                navigateToMapFragment()
            } else {
                navigateToDataDetailFragment()
            }
        }
    }

    private fun observeLoadingValue() {
        viewModel.loading.observe(viewLifecycleOwner) { loadingValue ->
            binding.progressBar.visibility = if (loadingValue) View.VISIBLE else View.GONE
        }
    }

    /**
     * Naviguje používateľa na fragment zobrazujúci detail o poškodení.
     */
    private fun navigateToDataDetailFragment() {
        val navController = findNavController()
        navController.navigate(R.id.action_add_damage_fragment_TO_data_detail_fragment)
        //navController.navigateUp()
    }

    /**
     * Naviguje používateľa na naspäť na mapový fragment.
     */
    private fun navigateToMapFragment() {
        val navController = findNavController()
        navController.previousBackStackEntry?.savedStateHandle?.set("key", true)
        navController.popBackStack()
    }


}