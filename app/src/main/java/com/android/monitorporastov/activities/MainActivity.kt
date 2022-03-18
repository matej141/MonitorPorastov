package com.android.monitorporastov.activities

import android.content.*
import android.location.LocationManager
import android.os.Bundle
import android.os.Message
import android.provider.Settings
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import androidx.lifecycle.Observer
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.*
import com.android.monitorporastov.ConnectionLiveData
import com.android.monitorporastov.DrawerLockInterface
import com.android.monitorporastov.MapSharedViewModel
import com.android.monitorporastov.R
import com.android.monitorporastov.databinding.ActivityMainBinding
import com.android.monitorporastov.fragments.DataListFragment
import com.google.android.material.navigation.NavigationView


class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener,
    DrawerLockInterface {

    private var _binding: ActivityMainBinding? = null
    private lateinit var appBarConfiguration: AppBarConfiguration

    private lateinit var drawerLayout: DrawerLayout

    private lateinit var navController: NavController
    private val viewModel: MapSharedViewModel by viewModels()

    private val binding get() = _binding!!
    private lateinit var connectionLiveData: ConnectionLiveData


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarMain.toolbar)

        drawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        navController = navHostFragment.navController
        appBarConfiguration = AppBarConfiguration(setOf(
            R.id.map_fragment, R.id.data_list_fragment), drawerLayout)
        // this.requestedOrientation= ActivityInfo.SCREEN_ORIENTATION_LOCKED
        setUpActionBarUnLocked()

        navView.setupWithNavController(navController)

        // týmto znemožníme viacnásobné načítavanie mapového fragmentu:
        navView.setNavigationItemSelectedListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            if (it.itemId == R.id.map_fragment) {
                navController.popBackStack(R.id.map_fragment, false)
                true
            } else {
                NavigationUI.onNavDestinationSelected(it, navController)

//                val fragmentManager: FragmentManager = supportFragmentManager
//                fragmentManager.beginTransaction().replace(R.id.map_fragment, DataListFragment())
//                    .addToBackStack(null).commit()
//                supportFragmentManager.commit {
//                    replace<DataListFragment>(R.id.nav_host_fragment_content_main)
//                    setReorderingAllowed(true)
//                    addToBackStack("name") // name can be null
//                }
                true
            }
        }

        setUpConnectionLiveData()
        observeNetwork()
        observeErrorMessage()
    }



    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        return true
    }

    override fun onStart() {
        checkForGPSProvider()
        super.onStart()
    }

    /**
     * Opýtame sa používateľa, či nechce zapnúť GPS.
     */
    private fun checkForGPSProvider() {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            AlertDialog.Builder(this)
                .setMessage(getString(R.string.gps_ad_title))
                .setPositiveButton(getString(R.string.button_positive_text)) { _, _ ->
                    startActivity(Intent(
                        Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
                .setNegativeButton(getString(R.string.button_negative_text)) { dialog, _ -> dialog.cancel() }
                .create()
                .show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    /**
     * Overide metódy lockDrawer z interfejsu DrawerLockInterface
     */
    override fun lockDrawer() {
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        setUpActionBarLocked()
    }

    /**
     * Overide metódy unlockDrawer z interfejsu DrawerLockInterface
     */
    override fun unlockDrawer() {
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
        setUpActionBarUnLocked()
    }

    /**
     * Nastavenie action baru, keď je uzamknutý drawer layut
     */
    private fun setUpActionBarLocked() {
        appBarConfiguration = AppBarConfiguration(setOf(
            R.id.map_fragment, R.id.data_list_fragment))
        setupActionBarWithNavController(navController, appBarConfiguration)
    }

    /**
     * Nastavenie action baru, keď je odomknutý drawer layut
     */
    private fun setUpActionBarUnLocked() {
        appBarConfiguration = AppBarConfiguration(setOf(
            R.id.map_fragment, R.id.data_list_fragment), drawerLayout)
        setupActionBarWithNavController(navController, appBarConfiguration)
    }

    private fun setUpConnectionLiveData() {
        connectionLiveData = ConnectionLiveData(this)
        setUpConnectionStatusReceiver()
    }

    private fun setUpConnectionStatusReceiver() {
        connectionLiveData.observe(this) {
            viewModel.isNetworkAvailable.value = it
        }
    }

    private fun observeNetwork() {
        viewModel.isNetworkAvailable.observe(this) { isAvailable ->
            if (!isAvailable) {
                AlertDialog.Builder(this)
                    .setTitle("Nemáte prístup na internet")
                    .setMessage("Skontrolujte, prosím, pripojenie do siete.")
                    .setNegativeButton("Ok") { dialog, _ -> dialog.cancel() }
                    .create()
                    .show()
                //viewModel.loaded.value = false
            }
            if (isAvailable && viewModel.loaded.value == false) {
//                Toast.makeText(context, "Znova načítavame dáta!", Toast.LENGTH_LONG).show()
//                viewModel.fetch()
//                viewModel.loaded.value = true
            }
        }
    }

    private fun observeErrorMessage() {
        viewModel.errorMessage.observe(this) { errorMessage ->
            val errorMessageTxt = createErrorMessageText(errorMessage)
            AlertDialog.Builder(this)
                .setTitle("Vyskytla sa chyba")
                .setMessage(errorMessageTxt)
                .setNegativeButton("Ok") { dialog, _ -> dialog.cancel() }
                .create()
                .show()

        }
    }

    private fun createErrorMessageText(errorMessage: String): String {
        val completeErrorMessage = "Ak problém pretrváva, kontaktuje podporu."
        if (errorMessage.isEmpty()) {
            return completeErrorMessage
        }
        return "Vyskytla sa chyba: $errorMessage\n$completeErrorMessage"
    }
}