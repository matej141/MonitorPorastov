package com.android.monitorporastov.fragments

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.MediaStore.ACTION_IMAGE_CAPTURE
import android.view.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.android.monitorporastov.PhotoItem
import com.android.monitorporastov.PhotosRecyclerViewAdapter
import com.android.monitorporastov.R
import com.android.monitorporastov.databinding.FragmentAddDamageBinding
import com.android.monitorporastov.placeholder.PlaceholderContent
import com.android.monitorporastov.placeholder.PlaceholderItem


/**
 * Fragment slúžiaci na zadanie údajov o poškodení (aj fotografií) a uloženie poškodenia.
 */
class AddDamageFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private var dataItem: PlaceholderItem? = null
    private var editData = false  // či pridávame nové poškodenie, alebo meníme existujúce


    private var _binding: FragmentAddDamageBinding? = null

    private val binding get() = _binding!!
    private var adapterOfPhotos = PhotosRecyclerViewAdapter(mutableListOf())

    private lateinit var listOfDamageType: Array<String>

    private var perimeter: Double? = null
    private var area: Double? = null

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
                PlaceholderContent.addItem(item)
            }
            navigateToMap()
        }
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
                val item = PhotoItem(data?.extras?.get("data") as Bitmap)
                adapterOfPhotos.values.add(item)
                adapterOfPhotos.notifyItemInserted(adapterOfPhotos.values.size - 1)
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

                val item = uri?.let { PhotoItem(it) }
                if (item != null) {
                    adapterOfPhotos.values.add(item)
                }
                adapterOfPhotos.notifyItemInserted(adapterOfPhotos.values.size - 1)
            }
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
