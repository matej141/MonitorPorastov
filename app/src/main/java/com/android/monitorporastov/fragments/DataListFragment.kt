package com.android.monitorporastov.fragments

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.android.monitorporastov.adapters.PlaceholderItemRecyclerViewAdapter
import com.android.monitorporastov.R
import com.android.monitorporastov.databinding.FragmentDataListBinding
import com.android.monitorporastov.placeholder.PlaceholderContent
import com.android.monitorporastov.placeholder.PlaceholderItem

/**
 * Fragment zobrazujúci zoznam poškodení.
 */
class DataListFragment : Fragment() {

    private var _binding: FragmentDataListBinding? = null

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

}