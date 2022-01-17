package com.android.monitorporastov.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.android.monitorporastov.databinding.DataListItemBinding
import com.android.monitorporastov.placeholder.PlaceholderItem

/**
 * RecyclerViewAdapter určený na prácu s placeholder prvkami,
 */
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