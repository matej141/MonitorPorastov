package com.android.monitorporastov

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.MediaStore.ACTION_IMAGE_CAPTURE
import android.view.*
import android.widget.ImageButton
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.android.monitorporastov.databinding.FragmentAddDamageBinding
import com.android.monitorporastov.databinding.PhotoListItemBinding
import android.graphics.BitmapFactory

import android.os.ParcelFileDescriptor
import android.text.Editable
import java.io.FileDescriptor


// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"


class AddDrawingFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView

    private var _binding: FragmentAddDamageBinding? = null

    private val binding get() = _binding!!
    var adapterOfPhotos = PhotosRecyclerViewAdapter(mutableListOf())

    val listOfDamageType = arrayOf("Požiar",
        "Sucho",
        "Povodeň",
        "Poškodenie diviačou zverou",
        "Poškodenie jeleňou zverou",
        "Poškodenie srnčou zverou",
        "Napadnutie škodcom")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentAddDamageBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = binding.itemList

        binding.galleryButton.setOnClickListener {
            choosePhoto()
        }
        binding.cameraButton.setOnClickListener {
            takePhoto()
        }
        recyclerView.adapter = adapterOfPhotos

        binding.addDataDamageType.setOnClickListener {
            choiceAD()
        }
//        binding.addDataDamageType.onFocusChangeListener =
//            View.OnFocusChangeListener { v, hasFocus ->
//                if (hasFocus) {
//                    choiceAD()
//                } else {
//                    // Hide your calender here
//                }
//            }
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
                val item = PlaceholderItem(data?.extras?.get("data") as Bitmap)
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
                // https://stackoverflow.com/questions/20782713/retrieve-bitmap-from-uri
//                val parcelFileDescriptor =
//                    uri?.let { requireContext().contentResolver.openFileDescriptor(it, "r") }
//                val fileDescriptor: FileDescriptor = parcelFileDescriptor!!.fileDescriptor
//                val image = BitmapFactory.decodeFileDescriptor(fileDescriptor)
//                parcelFileDescriptor.close()
                val item = uri?.let { PlaceholderItem(it) }
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
        val galleryIntent = Intent(
            Intent.ACTION_PICK,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        )
        resultGalleryLauncher.launch(galleryIntent)
    }

    private fun takePhoto() {
        val cameraIntent = Intent(
            MediaStore.ACTION_IMAGE_CAPTURE
        )
        resultTakePhotoLauncher.launch(cameraIntent)

    }

    class PhotosRecyclerViewAdapter(
        var values: MutableList<PlaceholderItem>,
    ) :
        RecyclerView.Adapter<PhotosRecyclerViewAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding =
                PhotoListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)

        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = values[position]
            if (item.image is Uri) {
                holder.photoImage.setImageURI(item.image as Uri?)
            }
            if (item.image is Bitmap) {
                holder.photoImage.setImageBitmap(item.image)
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

    data class PlaceholderItem(val image: Any?) {

    }

}