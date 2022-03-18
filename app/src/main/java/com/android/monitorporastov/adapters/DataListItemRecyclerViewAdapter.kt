package com.android.monitorporastov.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.android.monitorporastov.databinding.DataListItemBinding
import com.android.monitorporastov.model.DamageData

/**
 * RecyclerViewAdapter určený na prácu s placeholder prvkami,
 */
class DataListItemRecyclerViewAdapter(
    private val onClickListener: View.OnClickListener,
    ) :
    RecyclerView.Adapter<DataListItemRecyclerViewAdapter.ViewHolder>() {

    private var damageDataList =  listOf<DamageData>()

    fun setDataListItem(newDamageDataList: List<DamageData>) {
        this.damageDataList = newDamageDataList
        notifyItemRangeChanged(0, newDamageDataList.size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {

        val binding =
            DataListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)

    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = damageDataList[position]
        holder.bind(item)

        with(holder.itemView) {
            tag = item
            setOnClickListener(onClickListener)
        }
    }

    override fun getItemCount() = damageDataList.size

    inner class ViewHolder(binding: DataListItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        private val dataListItemName: TextView = binding.dataListItemName
        private val dataListItemDamageType: TextView = binding.dataListItemDamageType
        private val dataListItemInfo: TextView = binding.dataListItemInfo

        fun bind(item: DamageData) {
            val emptyTxt = "-"
            dataListItemName.text = item.nazov
            if (item.typ_poskodenia.isNullOrEmpty()) dataListItemDamageType.text =
                emptyTxt else dataListItemDamageType.text = item.typ_poskodenia
            if (item.popis_poskodenia.isNullOrEmpty()) dataListItemInfo.text =
                emptyTxt else dataListItemInfo.text = item.popis_poskodenia
        }
    }
}