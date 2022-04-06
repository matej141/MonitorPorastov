package com.android.monitorporastov.fragments

import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.android.monitorporastov.*
import com.android.monitorporastov.adapters.DataListItemRecyclerViewAdapter
import com.android.monitorporastov.databinding.FragmentDataListBinding
import com.android.monitorporastov.fragments.viewmodels.DataListFragmentViewModel
import com.android.monitorporastov.model.DamageData
import com.android.monitorporastov.viewmodels.MainSharedViewModelNew
import kotlinx.coroutines.launch

/**
 * Fragment zobrazujúci zoznam poškodení.
 */
class DataListFragment : Fragment(), CoroutineScopeInterface by CoroutineScopeDelegate() {

    private var _binding: FragmentDataListBinding? = null

    private val binding get() = _binding!!

    private val sharedViewModel: MainSharedViewModelNew by activityViewModels()

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
        launch {
            // sharedViewModel.fetchUserData()
        }
        recyclerView = binding.dataItemList
        setUpAdapter()
        observeDamageDataList()
        observeIfDataLoaded()
        // setCredentials()
        sharedViewModel.clearSelectedDamageDataItemFromMap()
        viewModel.initViewModelMethods(sharedViewModel, viewLifecycleOwner)
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
        // viewModel.clearSelectedDamageDataItem()
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    private fun observeDamageDataList() {
        viewModel.damageDataList.observe(viewLifecycleOwner) { damageData ->

            adapter.setDataList(damageData)
            Log.d("HOVNOOOO", "sucks")
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

//    private fun observeIfDataLoaded() {
//        sharedViewModel.loadedUserData.observe(viewLifecycleOwner) { loadedUserData ->
//            viewModel.
//        }
//    }


}
