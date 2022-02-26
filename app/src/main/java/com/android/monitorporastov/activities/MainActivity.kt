package com.android.monitorporastov.activities

import android.content.*
import android.location.LocationManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import com.android.monitorporastov.databinding.ActivityMainBinding
import com.google.android.material.navigation.NavigationView

import android.view.MenuItem
import androidx.core.view.GravityCompat
import androidx.navigation.ui.*
import androidx.navigation.NavController
import com.android.monitorporastov.DrawerLockInterface
import com.android.monitorporastov.R


class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener,
    DrawerLockInterface {

    private var _binding: ActivityMainBinding? = null
    private lateinit var appBarConfiguration: AppBarConfiguration

    private lateinit var drawerLayout: DrawerLayout

    private lateinit var navController: NavController

    private val binding get() = _binding!!


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
            R.id.nav_map, R.id.data_list_fragment), drawerLayout)
        // this.requestedOrientation= ActivityInfo.SCREEN_ORIENTATION_LOCKED
        setUpActionBarUnLocked()

        navView.setupWithNavController(navController)

        // týmto znemožníme viacnásobné načítavanie mapového fragmentu:
        navView.setNavigationItemSelectedListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            if (it.itemId == R.id.nav_map) {
                navController.popBackStack(R.id.nav_map, false)
                true
            } else

                NavigationUI.onNavDestinationSelected(it, navController)
        }
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
            R.id.nav_map, R.id.data_list_fragment))
        setupActionBarWithNavController(navController, appBarConfiguration)
    }

    /**
     * Nastavenie action baru, keď je odomknutý drawer layut
     */
    private fun setUpActionBarUnLocked() {
        appBarConfiguration = AppBarConfiguration(setOf(
            R.id.nav_map, R.id.data_list_fragment), drawerLayout)
        setupActionBarWithNavController(navController, appBarConfiguration)
    }


}