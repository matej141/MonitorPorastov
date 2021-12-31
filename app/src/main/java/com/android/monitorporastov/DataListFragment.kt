package com.android.monitorporastov

import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.android.monitorporastov.databinding.DataListItemBinding
import com.android.monitorporastov.databinding.FragmentDataListBinding
import com.android.monitorporastov.placeholder.PlaceholderContent
import com.android.monitorporastov.placeholder.PlaceholderItem

class DataListFragment : Fragment() {

    private var _binding: FragmentDataListBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDataListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recyclerView: RecyclerView = binding.dataItemList
        val onClickListener = View.OnClickListener { itemView ->
            val item = itemView.tag as PlaceholderItem
            val bundle = Bundle()
            bundle.putInt(
                DataDetailFragment.ARG_DATA_ITEM_ID,
                item.id
            )
            itemView.findNavController().navigate(R.id.show_data_detail, bundle)
        }
        setupRecyclerView(recyclerView, onClickListener)
    }

    private fun setupRecyclerView(
        recyclerView: RecyclerView,
        onClickListener: View.OnClickListener,
    ) {
        recyclerView.adapter = PlaceholderItemRecyclerViewAdapter(
            PlaceholderContent.ITEMS,
            onClickListener
        )

    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        menu.clear()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    class PlaceholderItemRecyclerViewAdapter(
        private val values: List<PlaceholderItem>,
        private val onClickListener: View.OnClickListener,

        ) :
        RecyclerView.Adapter<PlaceholderItemRecyclerViewAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {

            val binding =
                DataListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)

        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = values[position]
            val emptyTxt = "-"
            holder.dataListItemName.text = item.name
            if (item.damageType.isEmpty()) holder.dataListItemDamageType.text =
                emptyTxt else holder.dataListItemDamageType.text = item.damageType
            if (item.info.isEmpty()) holder.dataListItemInfo.text =
                emptyTxt else holder.dataListItemInfo.text = item.info

            with(holder.itemView) {
                tag = item
                setOnClickListener(onClickListener)
            }
        }

        override fun getItemCount() = values.size

        inner class ViewHolder(binding: DataListItemBinding) :
            RecyclerView.ViewHolder(binding.root) {
            val dataListItemName: TextView = binding.dataListItemName
            val dataListItemDamageType: TextView = binding.dataListItemDamageType
            val dataListItemInfo: TextView = binding.dataListItemInfo
        }

    }

}