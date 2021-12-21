package com.android.monitorporastov

import android.Manifest
import android.R.id
import android.content.*
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.text.method.TextKeyListener.clear
import android.view.Menu
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import com.android.monitorporastov.databinding.ActivityMainBinding
import com.google.android.material.navigation.NavigationView
import android.widget.TextView

import android.view.ViewGroup

import android.widget.ArrayAdapter
import android.widget.ListAdapter
import android.view.MenuItem
import android.widget.Toast
import androidx.core.view.GravityCompat
import androidx.navigation.ui.*
import androidx.preference.PreferenceManager
import android.R.id.toggle
import androidx.navigation.NavController


class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener, DrawerLockInterface {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var drawerLayout: DrawerLayout
    private val neededPermission = listOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    private lateinit var navController : NavController
    private val requestCode = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarMain.toolbar)

        drawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        navController = navHostFragment.navController
        // val navController = findNavController(R.id.nav_host_fragment_content_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(setOf(
            R.id.nav_map, R.id.nav_my_data), drawerLayout)
        // this.requestedOrientation= ActivityInfo.SCREEN_ORIENTATION_LOCKED
        setUpActionBarUnLocked()


        navView.setupWithNavController(navController)
        navView.setNavigationItemSelectedListener{
            drawerLayout.closeDrawer(GravityCompat.START)
            if (it.itemId == R.id.nav_map) {
                navController.popBackStack(R.id.nav_map, false)
                true
            }
            else

                NavigationUI.onNavDestinationSelected(it , navController)
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        TODO("Not yet implemented")
    }

    override fun onStart() {
        super.onStart()
        // checkPermissions()
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            AlertDialog.Builder(this)
                .setMessage("Your GPS seems to be disabled, do you want to enable it?")
                .setPositiveButton("Yes") { _, _ -> startActivity(Intent(
                    Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                .setNegativeButton("No") { dialog, _ -> dialog.cancel() }
                .create()
                .show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    private fun checkPermissions() {
        val askedPermissions = mutableListOf<String>()
        val list: Array<String> = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        for (perm in neededPermission) {
            if (ActivityCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                askedPermissions.add(perm)
            }
        }
        if (askedPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, askedPermissions.toTypedArray(), requestCode)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        for (r in grantResults) {
            if (r != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }
    }

    // https://www.geeksforgeeks.org/how-to-get-current-location-in-android/
    private fun isLocationPermissionGranted(): Boolean {
        return if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                requestCode
            )
            false
        } else {
            true
        }
    }

    override fun onDestroy() {
        PreferenceManager.getDefaultSharedPreferences(baseContext).edit().clear().apply()

        super.onDestroy()
    }

    override fun lockDrawer() {
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        setUpActionBarLocked()
    }

    override fun unlockDrawer() {
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
        setUpActionBarUnLocked()
    }

    private fun setUpActionBarLocked() {
        appBarConfiguration = AppBarConfiguration(setOf(
            R.id.nav_map, R.id.nav_my_data))
        setupActionBarWithNavController(navController, appBarConfiguration)
    }

    private fun setUpActionBarUnLocked() {
        appBarConfiguration = AppBarConfiguration(setOf(
            R.id.nav_map, R.id.nav_my_data), drawerLayout)
        setupActionBarWithNavController(navController, appBarConfiguration)
    }


}