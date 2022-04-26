package com.skeagis.monitorporastov.fragments

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.skeagis.monitorporastov.CoroutineScopeDelegate
import com.skeagis.monitorporastov.CoroutineScopeInterface
import com.skeagis.monitorporastov.R
import com.skeagis.monitorporastov.adapters.DataListItemRecyclerViewAdapter
import com.skeagis.monitorporastov.databinding.FragmentDataListBinding
import com.skeagis.monitorporastov.fragments.viewmodels.DataListFragmentViewModel
import com.skeagis.monitorporastov.model.DamageData
import com.skeagis.monitorporastov.apps_view_models.MainSharedViewModel

/**
 * Fragment zobrazujúci zoznam poškodení.
 */
class DataListFragment : Fragment(), CoroutineScopeInterface by CoroutineScopeDelegate() {

    private var _binding: FragmentDataListBinding? = null

    private val binding get() = _binding!!

    private val sharedViewModel: MainSharedViewModel by activityViewModels()

    private val viewModel: DataListFragmentViewModel by activityViewModels()

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
        recyclerView = binding.dataItemList
        setUpAdapter()
        observeDamageDataList()
        observeIfDataLoaded()
        sharedViewModel.clearSelectedDamageDataItemFromMap()
        viewModel.initViewModelMethods(sharedViewModel, viewLifecycleOwner)
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

            sharedViewModel.selectDamageData(item)

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
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    private fun observeDamageDataList() {
        viewModel.damageDataList.observe(viewLifecycleOwner) { damageData ->
            adapter.setDataList(damageData)
            binding.progressBar.visibility = View.GONE
        }
    }

    private fun observeIfDataLoaded() {
        viewModel.loadedUserData.observe(viewLifecycleOwner) { loaded ->
            if (loaded) {
                binding.progressBar.visibility = View.GONE
            }
            else {
                binding.progressBar.visibility = View.VISIBLE
                Toast.makeText(context, "Načítavam dáta",
                    Toast.LENGTH_SHORT).show()
            }

        }
    }

}
