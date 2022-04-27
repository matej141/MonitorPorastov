package com.skeagis.monitorporastov.fragments

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentResolver
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.MediaStore.ACTION_IMAGE_CAPTURE
import android.view.*
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.skeagis.monitorporastov.R
import com.skeagis.monitorporastov.Utils.afterTextChanged
import com.skeagis.monitorporastov.Utils.hideKeyboard
import com.skeagis.monitorporastov.adapters.AddOrUpdateRecordPhotosRVAdapter
import com.skeagis.monitorporastov.apps_view_models.MainSharedViewModel
import com.skeagis.monitorporastov.databinding.FragmentAddOrUpdateDamageBinding
import com.skeagis.monitorporastov.fragments.viewmodels.AddOrUpdateRecordFragmentViewModel
import com.skeagis.monitorporastov.model.DamageData


/**
 * Fragment slúžiaci na zadanie údajov o poškodení (aj fotografií) a uloženie poškodenia.
 */
class AddOrUpdateRecordFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private val sharedViewModel: MainSharedViewModel by activityViewModels()
    private val viewModel: AddOrUpdateRecordFragmentViewModel by viewModels()

    private var _binding: FragmentAddOrUpdateDamageBinding? = null

    private val binding get() = _binding!!
    private lateinit var listOfDamageType: Array<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAddOrUpdateDamageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = binding.recycleViewOfPhotos
        setUpListeners()
        setTextChangeListeners()
        listOfDamageType = resources.getStringArray(R.array.damages)
        setUpObservers()
        viewModel.initViewModelMethods(sharedViewModel, viewLifecycleOwner)
        setRecycleViewAdapterInViewModel()
        setUpBackStackCallback()
    }

    private fun setUpObservers() {
        observeDamageDataItemFromSharedViewModel()
        observeAdapterOfPhotos()
        observeIfEditing()
        observeUncompletedNameWarning()
        observeIfUpdateSucceeded()
        observeLoadingValue()
    }

    private fun setRecycleViewAdapterInViewModel() {
        val addDamageFragmentPhotosRVAdapter =
            AddOrUpdateRecordPhotosRVAdapter(mutableListOf(), requireContext())
        viewModel.setAdapterOfPhotos(addDamageFragmentPhotosRVAdapter)
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

    /**
     * Ak chceme iba upraviť informácie, pomocou tejto metódy zobrazíme existujúce dáta.
     */
    private fun setUpExistingContent(damageDataItem: DamageData) {
        binding.addDataName.editText?.setText(damageDataItem.nazov)
        binding.addDataDamageType.setText(damageDataItem.typ_poskodenia)
        binding.addDataDescription.editText?.setText(damageDataItem.popis_poskodenia)
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (checkIfClickingIsBlocked()) {
            return true
        }
        return super.onOptionsItemSelected(item)

    }

    private fun setUpBackStackCallback() {
        requireActivity().onBackPressedDispatcher.addCallback(this) {
            if (checkIfClickingIsBlocked()) {
                return@addCallback
            }
            findNavController().navigateUp()
        }
    }

    private fun blockEditTexts(view: View) {
            // view.isEnabled = false
            view.isClickable = false
            view.isFocusable = false

    }

    private fun saveData() {
        if (checkIfClickingIsBlocked()) {
            return
        }
        if (sharedViewModel.isNetworkAvailable.value == true) {
            // (activity as AppCompatActivity).supportActionBar?.setDisplayHomeAsUpEnabled(false)
            viewModel.saveData()

            blockEditTexts(binding.addDataNameText)
            blockEditTexts(binding.addDataDescriptionText)
        }
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
        if (checkIfClickingIsBlocked()) {
            return
        }
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
        sharedViewModel.selectedDamageDataItemFromMap.observe(viewLifecycleOwner) { selectedDamageDataItemFromMap ->
            selectedDamageDataItemFromMap?.let {
                viewModel.setDamageDataFromMap(it)
            }
        }
    }

    /**
     * Výber fotiek z galérie.
     */
    private fun choosePhoto() {
        if (checkIfClickingIsBlocked()) {
            return
        }
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
        if (checkIfClickingIsBlocked()) {
            return
        }
        val cameraIntent = Intent(
            ACTION_IMAGE_CAPTURE
        )
        resultTakePhotoLauncher.launch(cameraIntent)
    }

    private fun checkIfClickingIsBlocked(): Boolean {
        return viewModel.blockedClicking
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
        viewModel.adapterWasChanged.observe(viewLifecycleOwner) {
            val adapter: AddOrUpdateRecordPhotosRVAdapter? = viewModel.adapterOfPhotos.value
            if (adapter != null) {
                recyclerView.adapter = adapter
            }
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
                succeededToasts()
                observeWhereToNavigate()
            } else {
                nonSucceededToast()
            }
        }
    }

    private fun succeededToasts() {
        if (viewModel.isEditingData.value == true) {
            Toast.makeText(context, "Záznam bol úspešne aktualizovaný",
                Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Záznam bol úspešne uložený",
                Toast.LENGTH_SHORT).show()
        }
    }

    private fun nonSucceededToast() {
        if (viewModel.isEditingData.value == true) {
            Toast.makeText(context, "Záznam sa nepodarilo aktualizovať",
                Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Záznam sa nepodarilo uložiť",
                Toast.LENGTH_SHORT).show()
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