package com.android.monitorporastov

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.MediaStore.ACTION_IMAGE_CAPTURE
import android.view.*
import android.widget.ImageButton
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.android.monitorporastov.databinding.FragmentAddDamageBinding
import com.android.monitorporastov.databinding.PhotoListItemBinding

import androidx.navigation.fragment.findNavController
import com.android.monitorporastov.placeholder.PlaceholderContent
import com.android.monitorporastov.placeholder.PlaceholderItem


class AddDamageFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private var dataItem: PlaceholderItem? = null
    private var editData = false


    private var _binding: FragmentAddDamageBinding? = null

    private val binding get() = _binding!!
    var adapterOfPhotos = PhotosRecyclerViewAdapter(mutableListOf())

    private val listOfDamageType = arrayOf("Požiar",
        "Sucho",
        "Povodeň",
        "Poškodenie diviačou zverou",
        "Poškodenie jeleňou zverou",
        "Poškodenie srnčou zverou",
        "Napadnutie škodcom")

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
    }

    private fun setUpExistingContent() {
        binding.addDataName.editText?.setText(dataItem?.name)
        binding.addDataDamageType.setText(dataItem?.damageType)
        binding.addDataDescription.editText?.setText(dataItem?.info)
        dataItem?.photos?.forEach { adapterOfPhotos.values.add(PhotoItem(it)) }
    }

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

    private fun saveData() {
        if (binding.addDataName.editText?.length() ?: 0 == 0) {
            warningAD()
            return
        }
        val item = createPlaceholderItem()

        if (editData) {
            if (item != null) {
                PlaceholderContent.changeItem(item, item.id)
            }
            navigateToItemDetail()

        } else {
            if (item != null) {
                PlaceholderContent.addItem(item)
            }
            navigateToMap()
        }
    }

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

    private fun navigateToMap() {
        val navController = findNavController()
        val b = true
        navController.previousBackStackEntry?.savedStateHandle?.set("key", b)
        navController.popBackStack()
        //Navigation.findNavController(v).navigate(R.id.action_addMeasureFragment_to_nav_map, bundle)
    }

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

    private fun warningAD() {
        AlertDialog.Builder(requireContext())
            .setTitle("Nekompletné údaje")
            .setMessage("Musíte zadať aspoň názov poškodenia")
            .setNegativeButton("Ok") { dialog, _ -> dialog.cancel() }
            .create()
            .show()
    }

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

    // https://stackoverflow.com/questions/62671106/onactivityresult-method-is-deprecated-what-is-the-alternative
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

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        menu.clear()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun choosePhoto() {
        // https://handyopinion.com/pick-image-from-gallery-in-kotlin-android/
        val galleryIntent = Intent(
            Intent.ACTION_PICK,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        )
        resultGalleryLauncher.launch(galleryIntent)
    }

    private fun takePhoto() {
        val cameraIntent = Intent(
            ACTION_IMAGE_CAPTURE
        )
        resultTakePhotoLauncher.launch(cameraIntent)
    }

    class PhotosRecyclerViewAdapter(
        var values: MutableList<PhotoItem>,
    ) :
        RecyclerView.Adapter<PhotosRecyclerViewAdapter.ViewHolder>() {

        val bitmaps = mutableListOf<Bitmap>()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding =
                PhotoListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = values[position]
            if (item.image is Uri) {
                holder.photoImage.setImageURI(item.image as Uri?)
                val bitmap = (holder.photoImage.drawable as BitmapDrawable).bitmap
                bitmaps.add(bitmap)
            }
            if (item.image is Bitmap) {
                holder.photoImage.setImageBitmap(item.image)
                bitmaps.add(item.image)
            }
            holder.deleteButton.setOnClickListener {
                values.removeAt(position)
                notifyItemRemoved(position)
                notifyItemRangeChanged(position, values.size)
            }
        }

        override fun getItemCount() = values.size

        inner class ViewHolder(binding: PhotoListItemBinding) :
            RecyclerView.ViewHolder(binding.root) {
            val photoImage: ImageView = binding.photoImage
            val deleteButton: ImageButton = binding.deleteButton
        }

    }

    data class PhotoItem(val image: Any?)

    companion object {
        const val ARG_PERIMETER_ID = "perimeter_id"
        const val ARG_AREA_ID = "area_id"

        const val ARG_DATA_ITEM_ID = "item_id"

    }

}
