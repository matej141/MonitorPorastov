package com.android.monitorporastov

import android.content.ClipData
import android.content.ClipDescription
import android.os.Build
import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.fragment.app.Fragment
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
    private var p = true


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
        setupRecyclerView(recyclerView)
    }

    private fun setupRecyclerView(
        recyclerView: RecyclerView,
    ) {
        recyclerView.adapter = PlaceholderItemRecyclerViewAdapter(
            PlaceholderContent.ITEMS
        )
        p = false

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

    ) :
        RecyclerView.Adapter<PlaceholderItemRecyclerViewAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {

            val binding =
                DataListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)

        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = values[position]
            holder.dataListItemName.text = item.name
            holder.dataListItemDamageType.text = item.damageType
            holder.dataListItemInfo.text = item.info
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