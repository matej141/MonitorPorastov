package com.android.monitorporastov.fragments

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.*
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.android.monitorporastov.*
import com.android.monitorporastov.Utils.hideKeyboard
import com.android.monitorporastov.adapters.DataDetailPhotosRVAdapter
import com.android.monitorporastov.databinding.FragmentDataDetailBinding
import com.android.monitorporastov.model.DamageData
import kotlinx.coroutines.*

/**
 * Fragment zobrazujúci detail poškodenia.
 */
class DataDetailFragment : Fragment() {

    private var damageDataItem: DamageData? = null
    private lateinit var recyclerView: RecyclerView

    private var _binding: FragmentDataDetailBinding? = null

    private val binding get() = _binding!!

    private val viewModel: MapSharedViewModel by activityViewModels()
    private var stringsOfPhotosList = listOf<String>()
    private var photosLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        setUpBackStackCallback()
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
        if (!photosLoaded) {
            Toast.makeText(context, "Fotografie ešte neboli načítané, počkajte prosím",
                Toast.LENGTH_SHORT).show()
            return false
        }
        if (id == R.id.menu_edit_data) {
            damageDataItem?.let {
                it.isInGeoserver = true
                it.isUpdatingDirectlyFromMap = false
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

    private fun handleToMapFragment() {
        if (damageDataItem == null) {
            Toast.makeText(context, "Dáta ešte neboli načítané, počkajte prosím",
                Toast.LENGTH_SHORT).show()
        } else {
            viewModel.selectDamageDataToShowInMap(damageDataItem!!)
            navigateToMapFragment()

        }
    }

    private fun navigateToMapFragment() {
        findNavController().navigate(R.id.action_data_detail_fragment_TO_map_fragment)
    }

    private suspend fun handleDeletingRecord(): Boolean {
        val deferredBoolean = CompletableDeferred<Boolean>()
        if (damageDataItem == null) {
            return false
        }
        AlertDialog.Builder(requireContext())  //
            .setTitle(R.string.if_delete_record_title)
            .setPositiveButton(R.string.button_positive_text) { _, _ ->
                CoroutineScope(Dispatchers.Main).launch {
                    binding.progressBar.visibility = View.VISIBLE
                    deferredBoolean.complete(viewModel.deleteItem(damageDataItem!!))
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
            Utils.navigateToPreviousFragment(findNavController())
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
    private fun setupContent() {
        damageDataItem?.let {
            val txtPerimeter = "${
                "%.${3}f".format(it.obvod)
            } m"
            val txtArea = "${
                "%.${3}f".format(it.obsah)
            } m\u00B2"
            binding.dataDetailName.text = it.nazov
            binding.dataDetailDamageType.text = it.typ_poskodenia
            binding.dataDetailPerimeter.text = txtPerimeter
            binding.dataDetailArea.text = txtArea
            binding.dataDetailInfo.text = it.popis_poskodenia
//            val bitmaps = it.photos
//            recyclerView.adapter = DataDetailPhotosRVAdapter(bitmaps)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        viewModel.clearStringsOfPhotosList()
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
        if (viewModel.selectedDamageDataItemFromMap.value == null) {
            observeSelectedDamageDataFromViewModel()
            return
        }
        observeSelectedItemFromMap()
    }

    private fun observeSelectedItemFromMap() {
        viewModel.selectedDamageDataItemFromMap.observe(viewLifecycleOwner,
            Observer { selectedItemFromMap ->
                selectedItemFromMap?.let {
                    setUpDamageData(it)
                }
            })
    }

    private fun setUpDamageData(damageData: DamageData) {
        damageDataItem = damageData
        setupContent()
        if (!damageDataItem?.bitmapsLoaded!!) {
            damageDataItem?.let { item -> viewModel.fetchPhotos(item) }
            observePhotosFromViewModel()
        } else {
            setUpPhotos()
        }
    }

    private fun observeSelectedDamageDataFromViewModel() {
        viewModel.selectedDamageDataItem.observe(viewLifecycleOwner, Observer { selectedItem ->
            selectedItem?.let {
                setUpDamageData(it)
            }
        })
    }

    private fun observePhotosFromViewModel() {
        viewModel.stringsOfPhotosList.observe(viewLifecycleOwner) { stringsOfPhotosList ->
            stringsOfPhotosList?.let {
                this.stringsOfPhotosList = it
                setUpPhotos()
                observeIndexesOfPhotos()
                damageDataItem?.bitmapsLoaded = true
            }
        }
    }

    private fun observeIndexesOfPhotos() {
        viewModel.indexesOfPhotosList.observe(viewLifecycleOwner) { indexesOfPhotosList ->
            indexesOfPhotosList?.let {
                damageDataItem?.indexesOfPhotos = it
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.clearStringsOfPhotosList()
        //viewModel.clearSelectedDamageDataItemFromMap()
    }

    companion object {
        const val ARG_DATA_ITEM_ID = "item_id"
    }

}