package com.skeagis.monitorporastov.activities

import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.*
import com.google.android.material.navigation.NavigationView
import com.skeagis.monitorporastov.*
import com.skeagis.monitorporastov.AppsEncryptedSharedPreferences.createEncryptedSharedPreferences
import com.skeagis.monitorporastov.AppsEncryptedSharedPreferences.getIfLoggedInValue
import com.skeagis.monitorporastov.AppsEncryptedSharedPreferences.getIfRememberCredentialsValue
import com.skeagis.monitorporastov.AppsEncryptedSharedPreferences.getSavedPasswordCharArray
import com.skeagis.monitorporastov.AppsEncryptedSharedPreferences.getSavedUsernameCharArray
import com.skeagis.monitorporastov.AppsEncryptedSharedPreferences.setLoggedInValue
import com.skeagis.monitorporastov.R
import com.skeagis.monitorporastov.Utils.createErrorMessageAD
import com.skeagis.monitorporastov.Utils.noNetworkAvailableAD
import com.skeagis.monitorporastov.apps_view_models.MainSharedViewModel
import com.skeagis.monitorporastov.connection.ConnectionLiveData
import com.skeagis.monitorporastov.databinding.ActivityMainBinding


class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener,
    DrawerLockInterface {

    private var _binding: ActivityMainBinding? = null
    private lateinit var appBarConfiguration: AppBarConfiguration

    private lateinit var drawerLayout: DrawerLayout

    private lateinit var navController: NavController
    private val sharedViewModel: MainSharedViewModel by viewModels()

    private val binding get() = _binding!!
    private lateinit var connectionLiveData: ConnectionLiveData
    private val sharedPreferences by lazy {
        createEncryptedSharedPreferences(applicationContext)
    }

    private lateinit var noNetworkAvailableAD: AlertDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setAllNecessaryForUI()
        callAllNecessaryFunctions()
    }

    private fun setAllNecessaryForUI() {
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
        setUpActionBarUnLocked()

        navView.setupWithNavController(navController)
        setMainNavigationBehaviour(navView)
        noNetworkAvailableAD = noNetworkAvailableAD(this)
    }

    private fun callAllNecessaryFunctions() {
        setUpConnectionLiveData()
        setUserCredentialsInSharedViewModel()
        setUpObservers()
    }


    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        return true
    }

    override fun onStart() {
        checkForGPSProvider()
        super.onStart()
    }

    private fun setMainNavigationBehaviour(navView: NavigationView) {
        navView.setNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.map_fragment -> {
                    // týmto znemožníme viacnásobné načítavanie mapového fragmentu:
                    navController.popBackStack(R.id.map_fragment, false)
                    drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }
                R.id.log_out -> {
                    askIfLogOut()
                    false
                }
                else -> {
                    NavigationUI.onNavDestinationSelected(it, navController)
                    drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }
            }
        }
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
            sharedViewModel.setNetworkAvailability(it)
        }
    }

    private fun observeNetwork() {
        sharedViewModel.isNetworkAvailable.observe(this) { isAvailable ->
            if (!isAvailable) {
                noNetworkAvailableAD.show()
            } else {
                noNetworkAvailableAD.dismiss()
            }
        }
    }

    private fun observeErrorMessage() {
        sharedViewModel.errorMessage.observe(this) { errorMessage ->
            errorMessage.getContentIfNotHandled()?.let { errorMessageString ->
                createErrorMessageAD(this, errorMessageString)
            }
        }
    }

    private fun observeUnauthorisedError() {
        sharedViewModel.unauthorisedErrorIsOccurred.observe(this) {
            createWrongSavedCredentialsWarningAD()
        }
    }

    private fun createWrongSavedCredentialsWarningAD() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.wrong_saved_credentials_ad_title))
            .setMessage(getString(R.string.wrong_saved_credentials_ad_message))
            .setPositiveButton(R.string.menu_log_out_txt) { _, _ ->
                logOut()
            }
            .setNeutralButton(getString(R.string.button_cancel_text)) { dialog, _ -> dialog.cancel() }
            .create()
            .show()
    }

    private fun askIfLogOut() {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.ask_if_log_out_title))
            .setPositiveButton(getString(R.string.button_positive_text)) { _, _ ->
                logOut()
            }
            .setNegativeButton(getString(R.string.button_negative_text)) { dialog, _ -> dialog.cancel() }
            .create()
            .show()
    }

    private fun logOut() {
        sharedPreferences.setLoggedInValue(false)
        wipeSharedPreferences()
        startLoginActivity()
    }

    private fun wipeSharedPreferences() {
        if (!sharedPreferences.getIfRememberCredentialsValue()
            && !sharedPreferences.getIfLoggedInValue()
        ) {
            sharedPreferences.edit().clear().apply()
        }
    }

    private fun startLoginActivity() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun setUserCredentialsInSharedViewModel() {
        val usernameCharArray = sharedPreferences.getSavedUsernameCharArray()
        val passwordCharArray = sharedPreferences.getSavedPasswordCharArray()
        if ((usernameCharArray == null || passwordCharArray == null) ||
            (usernameCharArray.isEmpty() || passwordCharArray.isEmpty())
        ) {
            unableToLoadUserCredentialsFromMemoryAD()
            return
        }
        sharedViewModel.setUsernameCharArray(usernameCharArray)
        sharedViewModel.setPasswordCharArray(passwordCharArray)
    }

    private fun unableToLoadUserCredentialsFromMemoryAD() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.unsuccessful_loading_of_credentials_ad_title))
            .setMessage(getString(R.string.unsuccessful_loading_of_credentials_ad_message))
            .setPositiveButton(getString(R.string.menu_log_out_txt)) { _, _ ->
                logOut()
            }
            .setNeutralButton(getString(R.string.button_cancel_text)) { dialog, _ ->
                dialog.cancel()
            }
            .create()
            .show()
    }

    private fun setUpObservers() {
        observeNetwork()
        observeErrorMessage()
        observeUnauthorisedError()
    }

    override fun onDestroy() {
        wipeSharedPreferences()
        super.onDestroy()
    }
}