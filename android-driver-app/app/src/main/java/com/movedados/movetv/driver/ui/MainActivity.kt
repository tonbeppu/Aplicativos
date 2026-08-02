package com.movedados.movetv.driver.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.movedados.movetv.driver.R
import com.movedados.movetv.driver.services.LocationService
import com.movedados.movetv.driver.services.LocationGuardWorker
import com.movedados.movetv.driver.ui.home.HomeFragment
import com.movedados.movetv.driver.ui.monitoring.MonitoringFragment
import com.movedados.movetv.driver.ui.profile.ProfileFragment
import com.movedados.movetv.driver.utils.PreferenceManager

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: PreferenceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = PreferenceManager(this)

        if (!prefs.isLoggedIn()) {
            finish()
            return
        }

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { switchFragment(HomeFragment()); true }
                R.id.nav_monitoring -> { switchFragment(MonitoringFragment()); true }
                R.id.nav_profile -> { switchFragment(ProfileFragment()); true }
                else -> false
            }
        }

        if (savedInstanceState == null) {
            bottomNav.selectedItemId = R.id.nav_home
        }

        requestLocationPermission()
    }

    private fun switchFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    private fun requestLocationPermission() {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        ActivityCompat.requestPermissions(this, perms.toTypedArray(), 100)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != 100) return

        // Basta a localização estar concedida — negação de notificação não pode travar o GPS
        val locationGranted = permissions.indices.any { i ->
            (permissions[i] == Manifest.permission.ACCESS_FINE_LOCATION ||
             permissions[i] == Manifest.permission.ACCESS_COARSE_LOCATION) &&
                grantResults[i] == PackageManager.PERMISSION_GRANTED
        }

        if (locationGranted) {
            if (prefs.isGpsEnabled()) {
                ContextCompat.startForegroundService(this, Intent(this, LocationService::class.java))
                LocationGuardWorker.schedule(this)
            }
            requestIgnoreBatteryOptimizations()
        }
    }

    private fun requestIgnoreBatteryOptimizations() {
        val pm = getSystemService(PowerManager::class.java)
        if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (_: Exception) {
                // Alguns aparelhos não suportam o diálogo direto; segue sem travar o app
            }
        }
    }
}
