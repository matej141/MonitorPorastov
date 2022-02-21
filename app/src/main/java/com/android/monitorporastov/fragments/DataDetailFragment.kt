package com.android.monitorporastov.fragments

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.android.monitorporastov.ListViewModel
import com.android.monitorporastov.R
import com.android.monitorporastov.adapters.DataDetailPhotosRVAdapter
import com.android.monitorporastov.databinding.FragmentDataDetailBinding
import com.android.monitorporastov.model.DamageData
import com.android.monitorporastov.model.UsersData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_edit_data, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == R.id.menu_edit_data) {
            val bundle = Bundle()
            this.item?.let {
                bundle.putInt(
                    AddDamageFragment.ARG_DATA_ITEM_ID,
                    it.id
                )
                findNavController().navigate(
                    R.id.action_data_detail_fragment_TO_add_measure_fragment,
                    bundle)
            }

        }
        return super.onOptionsItemSelected(item)
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
        viewModel.deletePhotos()
    }

    private fun observeDamageDataFromViewModel() {
        viewModel.selectedDamageDataItem.observe(viewLifecycleOwner, Observer { selectedItem ->
            selectedItem?.let {
                item = it
                setupContent()
                item?.let { viewModel.fetchPhotos(it) }
                observePhotosFromViewModel()
            }
        })
    }

    private fun setUpPhotos() {
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
            }
            withContext(Dispatchers.Main) {
                recyclerView.adapter = DataDetailPhotosRVAdapter(bitmaps)
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun observePhotosFromViewModel() {
        viewModel.stringsOfPhotosList.observe(viewLifecycleOwner, Observer { stringsOfPhotosList ->
            stringsOfPhotosList?.let {
                this.stringsOfPhotosList = it
                setUpPhotos()
            }
        })
    }

    companion object {
        const val ARG_DATA_ITEM_ID = "item_id"
    }

}