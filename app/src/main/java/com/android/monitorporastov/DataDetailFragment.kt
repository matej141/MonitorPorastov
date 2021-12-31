package com.android.monitorporastov

import android.graphics.Bitmap
import android.os.Bundle
import android.view.*
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.android.monitorporastov.databinding.DataDetailPhotoListItemBinding
import com.android.monitorporastov.databinding.FragmentDataDetailBinding
import com.android.monitorporastov.placeholder.PlaceholderContent
import com.android.monitorporastov.placeholder.PlaceholderItem


class DataDetailFragment : Fragment() {

    private var item: PlaceholderItem? = null
    private lateinit var recyclerView: RecyclerView


    private var _binding: FragmentDataDetailBinding? = null

    private val binding get() = _binding!!


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        arguments?.let {
            if (it.containsKey(ARG_DATA_ITEM_ID)) {
                item = PlaceholderContent.ITEM_MAP[it.getInt(ARG_DATA_ITEM_ID)]
            }
        }

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDataDetailBinding.inflate(inflater, container, false)
        recyclerView = binding.dataDetailPhotoRv

        setupContent()
        return binding.root
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
                findNavController().navigate(R.id.action_data_detail_fragment_TO_add_measure_fragment,
                    bundle)
            }

        }
        return super.onOptionsItemSelected(item)
    }


    private fun setupContent() {
        item?.let {
            val txtPerimeter = "${
                "%.${3}f".format(it.perimeter)
            } m"
            val txtArea = "${
                "%.${3}f".format(it.area)
            } m\u00B2"
            binding.dataDetailName.text = it.name
            binding.dataDetailDamageType.text = it.damageType
            binding.dataDetailPerimeter.text = txtPerimeter
            binding.dataDetailArea.text = txtArea
            binding.dataDetailInfo.text = it.info
            val bitmaps = it.photos
            recyclerView.adapter = DataDetailPhotosRecyclerViewAdapter(bitmaps)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_DATA_ITEM_ID = "item_id"
    }

    class DataDetailPhotosRecyclerViewAdapter(
        var values: List<Bitmap>,
    ) :
        RecyclerView.Adapter<DataDetailPhotosRecyclerViewAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding =
                DataDetailPhotoListItemBinding.inflate(LayoutInflater.from(parent.context),
                    parent,
                    false)
            return ViewHolder(binding)

        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = values[position]
            holder.photoImage.setImageBitmap(item)
        }

        override fun getItemCount() = values.size

        inner class ViewHolder(binding: DataDetailPhotoListItemBinding) :
            RecyclerView.ViewHolder(binding.root) {
            val photoImage: ImageView = binding.photoImage
        }

    }
}