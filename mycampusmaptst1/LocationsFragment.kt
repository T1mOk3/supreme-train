package io.github.mycampusmaptst1

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import io.github.mycampusmaptst1.databinding.LocationsFragmentBinding
import io.github.mycampusmaptst1.overlays.SharedViewModel
import org.osmdroid.util.GeoPoint

class LocationsFragment : Fragment(R.layout.locations_fragment){
    private var _binding: LocationsFragmentBinding? = null
    private val binding get() = _binding!!
//  adapter for RecyclerView
    private lateinit var adapter: LocationAdapter
//  for communicating between fragments
    private val sharedViewModel: SharedViewModel by activityViewModels()
    private var selectedLocation: EachLocation? = null

    private var allLocations: List<EachLocation> = emptyList()
    private var currentFilter: String = "All Locations"


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

        setupCategorySpinner()
    }

    private fun setupCategorySpinner() {

        val categories = arrayOf(
            "All Locations",
            "Buildings",
            "Classrooms",
            "Food",
            "Shops"
        )
        val spinnerAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            categories
        )
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        // Set up spinner
        binding.spinnerCategory.adapter = spinnerAdapter
        // Handle spinner selection
        binding.spinnerCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedCategory = categories[position]
                currentFilter = selectedCategory
                filterLocationsByCategory(selectedCategory)
                Log.d("LocationsFragment", "Category selected: $selectedCategory")
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
            }
        }
    }

    private fun filterLocationsByCategory(category: String) {
        if (allLocations.isEmpty()) return
        val filteredLocations = when (category) {
            "All Locations" -> allLocations
            "Buildings" -> allLocations.filter { it.type.lowercase() in listOf("building", "med", "study", "library", "office") }
            "Classrooms" -> allLocations.filter { it.type.lowercase().contains("classroom") }
            "Food" -> allLocations.filter { it.type.lowercase() in listOf("restaurant", "cafe") }
            "Shops" -> allLocations.filter { it.type.lowercase().contains("shop") }
            else -> allLocations
        }
        adapter.updateData(filteredLocations)
        Log.d("LocationsFragment", "Filtered to ${filteredLocations.size} items in category: $category")
    }


    private fun setupRecyclerView() {
        adapter = LocationAdapter().apply {
            setOnItemClickListener {location ->
                selectedLocation = location
            }
            setOnGoButtonClickListener { selectedLocation ->
                val destination = GeoPoint(selectedLocation.latitude, selectedLocation.longitude)
                // share location
                Log.d("RecView", "Destination: $destination.latitude, $destination.longitude!")
                sharedViewModel.setSelectedLocation(selectedLocation)
                // Clear any search query
                binding.searchView.setQuery("", false)
                // switch to MapFragment
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
//            sharedViewModel.fetchLocationsFromDB()
            filterLocationsByCategory(currentFilter)
        } else {
//            sharedViewModel.fetchLocationsFromDB("%$query%")
            val baseList = if (currentFilter == "All Locations") {
                allLocations
            } else {
                // Get filtered list without updating adapter
                filterLocationsByCategory(currentFilter, false)
            }

            val searchResults = baseList.filter { location ->
                location.name.contains(query.toString(), true) ||
                        location.type.contains(query.toString(), true) ||
                        location.openHours.contains(query.toString(), true)
            }
            adapter.updateData(searchResults)
        }
    }

    private fun filterLocationsByCategory(category: String, updateAdapter: Boolean = true): List<EachLocation> {
        val filtered = when (category) {
            "All Locations" -> allLocations
            "Buildings" -> allLocations.filter { it.type.lowercase() in listOf("building", "med", "study", "library", "office") }
            "Classrooms" -> allLocations.filter { it.type.lowercase().contains("classroom") }
            "Food" -> allLocations.filter { it.type.lowercase() in listOf("restaurant", "cafe") }
            "Shops" -> allLocations.filter { it.type.lowercase().contains("shop") }
            else -> allLocations
        }

        if (updateAdapter) {
            adapter.updateData(filtered)
        }
        return filtered
    }

    private fun setupObservers() {
        sharedViewModel.locations.observe(viewLifecycleOwner) { locations ->
            adapter.updateData(locations ?: emptyList())
            allLocations = locations ?: emptyList()
            filterLocationsByCategory(currentFilter)
        }
    }
    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}