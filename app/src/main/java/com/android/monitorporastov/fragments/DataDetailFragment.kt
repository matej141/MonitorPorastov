package com.android.monitorporastov.fragments

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.android.monitorporastov.R
import com.android.monitorporastov.Utils.hideKeyboard
import com.android.monitorporastov.adapters.DataDetailPhotosRVAdapter
import com.android.monitorporastov.databinding.FragmentDataDetailBinding
import com.android.monitorporastov.fragments.viewmodels.DataDetailFragmentViewModel
import com.android.monitorporastov.model.DamageData
import com.android.monitorporastov.viewmodels.MainSharedViewModelNew
import kotlinx.coroutines.*
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.*

/**
 * Fragment zobrazujúci detail poškodenia.
 */
class DataDetailFragment : Fragment() {

    private var damageDataItem: DamageData? = null  // viewmodel
    private lateinit var recyclerView: RecyclerView

    private var _binding: FragmentDataDetailBinding? = null

    private val binding get() = _binding!!

    private val sharedViewModel: MainSharedViewModelNew by activityViewModels()
    private val viewModel: DataDetailFragmentViewModel by viewModels()
    private var stringsOfPhotosList = listOf<String>()  // viewmodel
    private var photosLoaded = false  // viewmodel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        // setUpBackStackCallback()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDataDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = binding.dataDetailPhotoRv
        observeDamageDataFromViewModel()
        observeBitmaps()
        observeIfNoPhotosToShow()
        observeLoadingValue()
        hideKeyboard()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.data_detail_menu, menu)
    }

    /**
     * Umelo vytvorene spravanie backpressed aktivity...
     * zabranuje tomu, aby sa cycklicky pri backpressed stlaceni buttonu opakovalo
     * map fragment -> data detail fragment a zaroven zabranuje tomu,
     * aby sa z backstacku vyhodil MapFragment
     */
    private fun setUpBackStackCallback() {
//        requireActivity().onBackPressedDispatcher.addCallback(this) {
//            navigateToDataListFragment()
//        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (!checkIfPhotosHaveBeenLoaded(id)) {
            Toast.makeText(context, "Fotografie ešte neboli načítané, počkajte prosím",
                Toast.LENGTH_SHORT).show()
            return false
        }
        if (id == R.id.menu_edit_data) {
            damageDataItem?.let {
                it.isInGeoserver = true
                it.isDirectlyFromMap = false
                //viewModel.saveNewItem(damageDataItem!!)
                findNavController().navigate(
                    R.id.action_data_detail_fragment_TO_add_measure_fragment)
            }
        }
        if (id == R.id.menu_delete_data) {
            CoroutineScope(Dispatchers.Main).launch {
                handleDeletingRecord()
            }
        }

        if (id == R.id.menu_show_on_map_data) {
            // damageDataItem?.showThisItemOnMap = true
            handleToMapFragment()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun checkIfPhotosHaveBeenLoaded(id: Int): Boolean {
        val loadedPhotos = viewModel.loadedPhotos.value
        if (loadedPhotos == false && checkIfMenuItemHasBeenSelected(id)) {
            return false
        }
        return true
    }

    private fun checkIfMenuItemHasBeenSelected(id: Int): Boolean {
        if (id == R.id.menu_edit_data || id == R.id.menu_delete_data
            || id == R.id.menu_show_on_map_data
        ) {
            return true
        }
        return false
    }

    private fun handleToMapFragment() {
        val damageDataItem = viewModel.damageDataItem.value
        if (damageDataItem == null) {
            Toast.makeText(context, "Dáta ešte neboli načítané, počkajte prosím",
                Toast.LENGTH_SHORT).show()
        } else {
            sharedViewModel.selectDamageDataToShowInMap(damageDataItem)
            navigateToMapFragment()
        }
    }

    private fun navigateToMapFragment() {
        findNavController().navigate(R.id.action_data_detail_fragment_TO_map_fragment)
    }

    private suspend fun handleDeletingRecord(): Boolean {
        val deferredBoolean = CompletableDeferred<Boolean>()
        val damageDataItem = viewModel.damageDataItem.value ?: return false
        AlertDialog.Builder(requireContext())  //
            .setTitle(R.string.if_delete_record_title)
            .setPositiveButton(R.string.button_positive_text) { _, _ ->
                CoroutineScope(Dispatchers.Main).launch {
                    binding.progressBar.visibility = View.VISIBLE
                    deferredBoolean.complete(sharedViewModel.deleteItem(damageDataItem))
                }
            }
            .setNegativeButton(R.string.button_negative_text) { dialog, _ ->
                deferredBoolean.complete(false)
                dialog.cancel()
            }
            .create()
            .show()

        if (deferredBoolean.await()) {

            Toast.makeText(context, "Dáta boli úspešne vymazané",
                Toast.LENGTH_SHORT).show()

            navigateToDataListFragment()
        }
        binding.progressBar.visibility = View.GONE
        return deferredBoolean.await()
    }

    private fun navigateToDataListFragment() {
        //findNavController().popBackStack()

//        findNavController().popBackStack(R.id.data_detail_fragment, true)
        findNavController().navigate(
            R.id.action_data_detail_fragment_TO_data_list_fragment)
    }

    /**
     * Naplní tabuľku údajmi.
     */
    private fun setupContent(damageDataItem: DamageData) {
        damageDataItem.let {
//            val txtPerimeter = "${
//                "%.${3}f".format(it.obvod)
//            } m"
//            val txtArea = "${
//                "%.${3}f".format(it.obsah)
//            } m\u00B2"

            val txtPerimeter = "${
                it.obvod.toInt()
            } m"
            val txtArea = "${
                it.obsah.toInt()
            } m\u00B2"
            binding.dataDetailName.text = it.nazov
            binding.dataDetailDamageType.text = it.typ_poskodenia
            binding.dataDetailPerimeter.text = txtPerimeter
            binding.dataDetailArea.text = txtArea
            binding.dataDetailInfo.text = it.popis_poskodenia
            binding.dataDetailInfo.text = it.popis_poskodenia
            binding.dataDetailDatetime.text =
                it.datetime?.let { datetimeStr -> createDateTime(datetimeStr) }

        }
    }

    private fun createDateTime(dateTimeString: String): String? {
        val sdf1 = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
        val date: Date? = sdf1.parse(dateTimeString)
        val timeStampDate = date?.let { Timestamp(it.time) }
        val sdf2 = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
        return date?.let { sdf2.format(it) }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        //sharedViewModel.clearStringsOfPhotosList()
        //activity?.viewModelStore?.clear()
    }

    private fun setUpPreviouslyLoadedPhotos() {
        binding.progressBar.visibility = View.GONE
        if (damageDataItem?.bitmaps?.isNotEmpty() == true) {
            recyclerView.adapter = DataDetailPhotosRVAdapter(damageDataItem!!.bitmaps)
        } else {
            binding.dataDetailPhotoNoPhotos.visibility = View.VISIBLE
        }
        photosLoaded = true
    }

    private fun setNewlyLoadedPhotos() {
        if (stringsOfPhotosList.isEmpty()) {
            binding.dataDetailPhotoNoPhotos.visibility = View.VISIBLE
            binding.progressBar.visibility = View.GONE
            photosLoaded = true
            return
        }
        binding.dataDetailPhotoNoPhotos.visibility = View.GONE
        photosLoaded = true
        val bitmaps = mutableListOf<Bitmap>()
        CoroutineScope(Dispatchers.Default).launch {
            stringsOfPhotosList.forEach {
                val imageBytes: ByteArray = Base64.decode(it, 0)
                val image = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                bitmaps.add(image)
                damageDataItem?.bitmaps?.add(image)
            }
            withContext(Dispatchers.Main) {
                recyclerView.adapter = DataDetailPhotosRVAdapter(bitmaps)
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun setUpPhotos() {
        if (damageDataItem != null && damageDataItem?.bitmapsLoaded == true) {
            setUpPreviouslyLoadedPhotos()
            return
        }
        setNewlyLoadedPhotos()
    }

    private fun observeDamageDataFromViewModel() {
        if (sharedViewModel.selectedDamageDataItemFromMap.value == null) {
            observeSelectedDamageDataFromViewModel()
            return
        }
        observeSelectedItemFromMap()
    }

    private fun setUpDamageData(damageData: DamageData) {
        damageDataItem = damageData
        setupContent(damageData)

    }

    private fun observeSelectedItemFromMap() {
        sharedViewModel.selectedDamageDataItemFromMap.observe(viewLifecycleOwner
        ) { selectedItemFromMap ->
            selectedItemFromMap?.let {
                setDataToViewModel(it)
            }
        }
    }

    private fun observeSelectedDamageDataFromViewModel() {
        sharedViewModel.selectedDamageDataItem.observe(viewLifecycleOwner) { selectedItem ->
            selectedItem?.let {
                setDataToViewModel(it)
            }
        }
    }

    private fun setDataToViewModel(damageDataItem: DamageData) {
        viewModel.setDamageDataItem(damageDataItem)
        viewModel.initViewModelMethods(sharedViewModel, viewLifecycleOwner)
        viewModel.prepareToLoadPhotos()
        setUpDamageData(damageDataItem)
    }

    private fun observeLoadingValue() {
        viewModel.loading.observe(viewLifecycleOwner) { loadingValue ->
            binding.progressBar.visibility = if (loadingValue) View.VISIBLE else View.GONE
        }
    }

    private fun observeBitmaps() {
        viewModel.bitmaps.observe(viewLifecycleOwner) { bitmaps ->
            recyclerView.adapter = DataDetailPhotosRVAdapter(bitmaps)
        }
    }

    private fun observeIfNoPhotosToShow() {
        viewModel.noPhotosToShow.observe(viewLifecycleOwner) { value ->
            binding.dataDetailPhotoNoPhotos.visibility = if (value) View.VISIBLE else View.GONE
            //binding.dataDetailPhotoNoPhotos.visibility = if (value) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        //viewModel.clearSelectedDamageDataItemFromMap()
    }

    companion object {
        const val ARG_DATA_ITEM_ID = "item_id"
    }

}