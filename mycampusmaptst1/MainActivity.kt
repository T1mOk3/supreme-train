package io.github.mycampusmaptst1

import android.app.AlertDialog
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import io.github.mycampusmaptst1.databinding.ActivityMainBinding
import io.github.mycampusmaptst1.overlays.SharedViewModel
import org.osmdroid.config.Configuration

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val sharedViewModel: SharedViewModel by viewModels()

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(this, getSharedPreferences("osmprefs", MODE_PRIVATE))
//      changing status bar color to black
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
//      hide action bar
        supportActionBar?.hide()
//      set map fragment as starting
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        replaceFragment(MapFragment())
//      load db
        sharedViewModel.initDatabaseHelper(applicationContext)
//      navigation logic
        binding.bottomNavigationView.setOnItemSelectedListener {
            when(it.itemId){
                R.id.map -> {
                    replaceFragment(MapFragment())
                    true
                }
                R.id.locations -> {
                    replaceFragment(LocationsFragment())
                    true
                }
                R.id.most_visited -> {
                    replaceFragment(MostVisitedFragment())
                    true
                }
                R.id.me -> {
                    replaceFragment(MeFragment())
                    true
                }
                else ->{
                    false
                }
            }
        }

        if (!wereInstructionsSeen()) {
            Handler(Looper.getMainLooper()).postDelayed({
                showCustomInstructions()
            }, 1000)
        }

        // show info btn


    }
    private fun replaceFragment(fragment: Fragment) {
        val fragmentTag = fragment.javaClass.simpleName // Use unique tag
        val existingFragment = supportFragmentManager.findFragmentByTag(fragmentTag)

        if (existingFragment == null) {
//             only create new fragment if it doesn't exist
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer1, fragment, fragmentTag)
                .addToBackStack(null)
                .commit()
        } else {
//             reuse existing fragment
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer1, existingFragment)
                .addToBackStack(null)
                .commit()
        }
    }

    fun navigateToMapFragment() {
        binding.bottomNavigationView.selectedItemId = R.id.map
        replaceFragment(MapFragment())
    }

    fun showInstructions()
    {

    }

    private fun showCustomInstructions() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_instructions, null)

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Got it!\nLet's start") { dialog, which -> }
            .setCancelable(true)
            .show()
    }

    private fun wereInstructionsSeen(): Boolean {
        val sharedPref = getSharedPreferences("app_prefs", MODE_PRIVATE)
        return sharedPref.getBoolean("instructions_seen", false)
    }

}

