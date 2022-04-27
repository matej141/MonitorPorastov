package com.skeagis.monitorporastov.fragments

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.skeagis.monitorporastov.Event
import com.skeagis.monitorporastov.R
import com.skeagis.monitorporastov.Utils.hideKeyboard
import com.skeagis.monitorporastov.adapters.DataDetailPhotosRVAdapter
import com.skeagis.monitorporastov.databinding.FragmentDataDetailBinding
import com.skeagis.monitorporastov.fragments.viewmodels.DataDetailFragmentViewModel
import com.skeagis.monitorporastov.model.DamageData
import com.skeagis.monitorporastov.apps_view_models.MainSharedViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * Fragment zobrazujúci detail poškodenia.
 */
class DataDetailFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView

    private var _binding: FragmentDataDetailBinding? = null

    private val binding get() = _binding!!

    private val sharedViewModel: MainSharedViewModel by activityViewModels()
    private val viewModel: DataDetailFragmentViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
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
        setUpObservers()
        setAdapterInViewModel()
        setUpBackStackCallback()
        hideKeyboard()
    }

    private fun setUpObservers() {
        observeDamageDataFromViewModel()
        observeBitmaps()
        observeIfNoPhotosToShow()
        observeLoadingValue()
        observeIfDeletingWasSuccessful()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.data_detail_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (viewModel.isNetworkAvailable.value == false) {
            return false
        }
        if (!checkIfPhotosHaveBeenLoaded(id)) {
            Toast.makeText(context, "Fotografie ešte neboli načítané, počkajte prosím",
                Toast.LENGTH_SHORT).show()
            return false
        }
        if (checkIfShouldNotClickOnButtons()) {
            return true
        }
        if (id == R.id.menu_edit_data) {
            viewModel.detailDamageDataItem.let {
                it.isInGeoserver = true
                it.isDirectlyFromMap = false
                findNavController().navigate(
                    R.id.action_data_detail_fragment_TO_add_or_update_fragment)
            }
        }
        if (id == R.id.menu_delete_data) {
            askIfDeleteDataAD()
        }

        if (id == R.id.menu_show_on_map_data) {
            handleToMapFragment()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setAdapterInViewModel() {
        val dataDetailPhotosRVAdapter =
            DataDetailPhotosRVAdapter(mutableListOf(), requireContext())
        viewModel.setAdapterOfDetailsOfPhotos(dataDetailPhotosRVAdapter)
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

    /**
     * Naplní tabuľku údajmi.
     */
    private fun setupContentOfDamageData(damageDataItem: DamageData) {
        damageDataItem.let {
            val txtPerimeter = "${
                it.obvod.toInt()
            } m"
            val txtArea = "${
                it.obsah.toInt()
            } m\u00B2"
            val defaultTxt = "-"
            binding.dataDetailName.text = it.nazov
            binding.dataDetailDamageType.text = it.typ_poskodenia.ifEmpty { defaultTxt }
            binding.dataDetailPerimeter.text = txtPerimeter
            binding.dataDetailArea.text = txtArea
            binding.dataDetailInfo.text = it.popis_poskodenia
            binding.dataDetailInfo.text = it.popis_poskodenia.ifEmpty { defaultTxt }
            binding.dataDetailDatetime.text =
                it.datetime?.let { datetimeStr -> createDateTimeString(datetimeStr) }

        }
    }

    private fun createDateTimeString(dateTimeString: String): String? {
        val sdf1 = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
        val date: Date? = sdf1.parse(dateTimeString)
        val sdf2 = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
        return date?.let { sdf2.format(it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun observeDamageDataFromViewModel() {
        if (sharedViewModel.selectedDamageDataItemFromMap.value == null) {
            viewModel.detailDamageDataItem = sharedViewModel.selectedDamageDataItem.value!!
            observeSelectedDamageDataFromViewModel()
            return
        }
        viewModel.detailDamageDataItem = sharedViewModel.selectedDamageDataItemFromMap.value!!
        observeSelectedItemFromMap()
    }


    private fun askIfDeleteDataAD() {
        AlertDialog.Builder(requireContext())  //
            .setTitle(R.string.if_delete_record_title)
            .setPositiveButton(R.string.button_positive_text) { _, _ ->
                binding.progressBar.visibility = View.VISIBLE
                viewModel.prepareToDelete(viewModel.detailDamageDataItem)
                viewModel.setImageInAdapterNonClickable()
            }
            .setNegativeButton(R.string.button_negative_text) { dialog, _ ->
                dialog.cancel()
            }
            .create()
            .show()
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
        setupContentOfDamageData(damageDataItem)
    }

    private fun observeLoadingValue() {
        viewModel.loading.observe(viewLifecycleOwner) { loadingValue ->
            binding.progressBar.visibility = if (loadingValue) View.VISIBLE else View.GONE
        }
    }

    private fun observeBitmaps() {
        viewModel.adapterOfDetailOfPhotos.observe(viewLifecycleOwner) {
            recyclerView.adapter = it
        }
    }

    private fun observeIfNoPhotosToShow() {
        viewModel.noPhotosToShow.observe(viewLifecycleOwner) { value ->
            binding.dataDetailPhotoNoPhotos.visibility = if (value) View.VISIBLE else View.GONE
        }
    }

    private fun observeIfDeletingWasSuccessful() {
        sharedViewModel.deletingWasSuccessful.observe(viewLifecycleOwner) { value ->
            value.getContentIfNotHandled()?.let { wasSuccessful ->
                if (wasSuccessful) {
                    onSuccessfulDelete(value)
                } else {
                    Toast.makeText(context, getString(R.string.unsuccessful_deleting),
                        Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun onSuccessfulDelete(value: Event<Boolean>) {
        if (checkIfPreviousFragmentIsMapFragment()) {
            value.setThatNotHandled()
        } else {
            Toast.makeText(context, getString(R.string.successful_deleting),
                Toast.LENGTH_LONG).show()
        }
        findNavController().navigateUp()
        viewModel.damageDataItem.value?.let {
            sharedViewModel.setUniqueIdOfDeletedDamageDataItem(it.unique_id)
        }
    }


    private fun checkIfPreviousFragmentIsMapFragment(): Boolean {
        val previousFragment = findNavController()
            .previousBackStackEntry?.destination?.id ?: return false
        if (previousFragment != R.id.map_fragment) {
            return false
        }
        return true
    }

    private fun checkIfShouldNotClickOnButtons(): Boolean {
        return viewModel.blockedClicking
    }

    private fun setUpBackStackCallback() {
        requireActivity().onBackPressedDispatcher.addCallback(this) {
            if (checkIfShouldNotClickOnButtons()) {
                return@addCallback
            }
            if (checkIfShouldCLearBackstack()
            ) {
                findNavController().navigate(R.id.action_data_detail_fragment_TO_map_fragment_PopUp)
            } else {
                findNavController().navigateUp()
            }

        }
    }

    private fun checkIfShouldCLearBackstack(): Boolean {
        return sharedViewModel.selectedDamageDataItemFromMap.value != null &&
                sharedViewModel.selectedDamageDataItem.value !=
                sharedViewModel.selectedDamageDataItemFromMap.value
    }
}