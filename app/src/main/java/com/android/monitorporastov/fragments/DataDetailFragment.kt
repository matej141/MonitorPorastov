package com.android.monitorporastov.fragments

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.*
import android.widget.Toast
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import org.osmdroid.tileprovider.modules.SqlTileWriter
import java.util.concurrent.TimeUnit

/**
 * Fragment zobrazujúci detail poškodenia.
 */
class DataDetailFragment : Fragment() {

    private var item: DamageData? = null
    private lateinit var recyclerView: RecyclerView

    private var _binding: FragmentDataDetailBinding? = null

    private val binding get() = _binding!!

    private val viewModel: ListViewModel by activityViewModels()
    private var stringsOfPhotosList = listOf<String>()

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
        observeDamageDataFromViewModel()
        hideKeyboard()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.data_detail_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == R.id.menu_edit_data) {
            this.item?.let {
                it.isNew = false
                it.isItemFromMap = false
                viewModel.saveItem(this.item!!)
                findNavController().navigate(
                    R.id.action_data_detail_fragment_TO_add_measure_fragment)
            }
        }
        if (id == R.id.menu_delete_data) {
            CoroutineScope(Dispatchers.Main).launch {
                handleDeletingRecord()
            }


        }
        return super.onOptionsItemSelected(item)
    }

    private suspend fun handleDeletingRecord() {
        if (item == null) {
            return
        }

        Utils.setSelectedItem(item!!)
        val resultOfOperation: Boolean =
            withContext(CoroutineScope(Dispatchers.Main).coroutineContext) {
                Utils.handleDeletingOfRecord(requireContext(), binding.progressBar)
            }
        if (resultOfOperation) {
            Toast.makeText(context, "Dáta boli úspešne vymazané",
                Toast.LENGTH_SHORT).show()
            navigateToDataListFragment()
        }
    }

    private fun navigateToDataListFragment() {
        findNavController().navigate(
            R.id.action_data_detail_fragment_TO_data_list_fragment)
    }

    /**
     * Naplní tabuľku údajmi.
     */
    private fun setupContent() {
        item?.let {
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
        //activity?.viewModelStore?.clear()
    }

    private fun setUpPreviouslyLoadedPhotos() {
        binding.progressBar.visibility = View.GONE
        if (item?.bitmaps?.isNotEmpty() == true) {
            recyclerView.adapter = DataDetailPhotosRVAdapter(item!!.bitmaps)
        } else {
            binding.dataDetailPhotoNoPhotos.visibility = View.VISIBLE
        }
    }

    private fun setNewlyLoadedPhotos() {
        if (stringsOfPhotosList.isEmpty()) {
            binding.dataDetailPhotoNoPhotos.visibility = View.VISIBLE
            binding.progressBar.visibility = View.GONE
            return
        }
        binding.dataDetailPhotoNoPhotos.visibility = View.GONE

        val bitmaps = mutableListOf<Bitmap>()
        CoroutineScope(Dispatchers.Default).launch {
            stringsOfPhotosList.forEach {
                val imageBytes: ByteArray = Base64.decode(it, 0)
                val image = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                bitmaps.add(image)
                item?.bitmaps?.add(image)
            }
            withContext(Dispatchers.Main) {
                recyclerView.adapter = DataDetailPhotosRVAdapter(bitmaps)
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun setUpPhotos() {
        if (item != null && item?.bitmapsLoaded == true) {
            setUpPreviouslyLoadedPhotos()
            return
        }
        setNewlyLoadedPhotos()
    }

    private fun observeDamageDataFromViewModel() {
        viewModel.selectedDamageDataItem.observe(viewLifecycleOwner, Observer { selectedItem ->
            selectedItem?.let {
                item = it
//                Toast.makeText(context, "${it.id}",
//                    Toast.LENGTH_SHORT).show()
//                 id 104
                setupContent()
                if (!it.bitmapsLoaded) {
                    item?.let { item -> viewModel.fetchPhotos(item) }
                    observePhotosFromViewModel()
                } else {
                    setUpPhotos()
                }

            }
        })
    }

    private fun observePhotosFromViewModel() {
        viewModel.stringsOfPhotosList.observe(viewLifecycleOwner) { stringsOfPhotosList ->
            stringsOfPhotosList?.let {
                this.stringsOfPhotosList = it
                setUpPhotos()
                observeIndexesOfPhotos()
                item?.bitmapsLoaded = true
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.clear()
    }

    private fun observeIndexesOfPhotos() {
        viewModel.indexesOfPhotosList.observe(viewLifecycleOwner) { indexesOfPhotosList ->
            indexesOfPhotosList?.let {
                item?.indexesOfPhotos = it
            }
        }
    }

    companion object {
        const val ARG_DATA_ITEM_ID = "item_id"
    }

}