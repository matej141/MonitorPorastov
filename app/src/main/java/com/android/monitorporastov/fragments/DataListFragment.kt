package com.android.monitorporastov.fragments

import android.os.Bundle
import android.view.*
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.android.monitorporastov.MapSharedViewModel
import com.android.monitorporastov.adapters.DataListItemRecyclerViewAdapter
import com.android.monitorporastov.R
import com.android.monitorporastov.databinding.FragmentDataListBinding
import com.android.monitorporastov.model.DamageData

/**
 * Fragment zobrazujúci zoznam poškodení.
 */
class DataListFragment : Fragment() {

    private var _binding: FragmentDataListBinding? = null

    private val binding get() = _binding!!

    private val viewModel: MapSharedViewModel by activityViewModels()

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: DataListItemRecyclerViewAdapter

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

        viewModel.fetchUserData()
        recyclerView = binding.dataItemList
        setUpAdapter()
        observeViewModel()
        setUpBackStackCallback()
    }

    private fun setUpBackStackCallback() {
//        requireActivity().onBackPressedDispatcher.addCallback(this) {
//            navigateToMapFragment()
//        }
    }

    private fun navigateToMapFragment() {
        findNavController().navigate(R.id.action_data_list_fragment_TO_map_fragment)
    }

    private fun setUpAdapter() {
        val onClickListener = createOnclickListener()
        adapter = DataListItemRecyclerViewAdapter(
            onClickListener
        )
        setupRecycleViewAdapter()
    }

    private fun setupRecycleViewAdapter() {
        recyclerView.adapter = adapter
    }

    private fun createOnclickListener(): View.OnClickListener {
        val onClickListener = View.OnClickListener { itemView ->
            val item = itemView.tag as DamageData

            viewModel.selectDamageData(item)

            itemView.findNavController().navigate(R.id.show_data_detail)
        }
        return onClickListener
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        menu.clear()
    }

    override fun onDestroyView() {
        super.onDestroyView()
       // viewModel.clearSelectedDamageDataItem()
        _binding = null
    }

    private fun observeViewModel() {
        viewModel.damageDataList.observe(viewLifecycleOwner, Observer { damageData ->
            adapter.setDataListItem(damageData)
            binding.progressBar.visibility = View.GONE
        })
    }

}