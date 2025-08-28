package io.github.mycampusmaptst1

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import io.github.mycampusmaptst1.databinding.LocationsFragmentBinding
import io.github.mycampusmaptst1.overlays.SharedViewModel

class LocationsFragment : Fragment(R.layout.locations_fragment){
    private var _binding: LocationsFragmentBinding? = null
    private val binding get() = _binding!!
//  adapter for RecyclerView
    private lateinit var adapter: LocationAdapter
//  for communicating between fragments
    private val sharedViewModel: SharedViewModel by activityViewModels()
    private var selectedLocation: EachLocation? = null

//  create view for fragment
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = LocationsFragmentBinding.inflate(
            inflater,
            container,
            false
        )
        return binding.root
    }

//  set up UI
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupObservers()
        setupSearchView()

    }

    private fun setupRecyclerView() {
        adapter = LocationAdapter().apply {
            setOnItemClickListener {location ->
                selectedLocation = location
            }
            setOnGoButtonClickListener { selectedLocation ->
//              share location
                sharedViewModel.setSelectedLocations(selectedLocation)
//               notify activity to switch to map view
                (requireActivity() as MainActivity).navigateToMapFragment()
            }
        }
//      properties
        binding.recycleView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@LocationsFragment.adapter
//          divider btw items
            addItemDecoration(
                DividerItemDecoration(
                    requireContext(),
                    DividerItemDecoration.VERTICAL
                )
            )
        }
    }

    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            // called when user submits search query
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let { searchLocations(it) }
                return true
            }
            // called when search text changes
            override fun onQueryTextChange(newText: String?): Boolean {
                newText?.let { searchLocations(it) }
                return true
            }
        })
    }

    //  get locations based on search queue
    private fun searchLocations(query: String?) {
        if (query?.isEmpty() == true) {
            sharedViewModel.fetchLocationsFromDB()
        } else {
            sharedViewModel.fetchLocationsFromDB("%$query%")
        }
    }
    private fun setupObservers() {
        sharedViewModel.locations.observe(viewLifecycleOwner) { locations ->
            adapter.updateData(locations ?: emptyList())
        }
    }
    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

}
