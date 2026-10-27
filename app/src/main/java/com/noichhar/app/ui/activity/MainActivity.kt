package com.noichhar.app.ui.activity

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.noichhar.app.R
import com.noichhar.app.databinding.ActivityMainBinding
import com.noichhar.app.ui.fragment.AboutFragment
import com.noichhar.app.ui.fragment.ConfigureFragment
import com.noichhar.app.ui.fragment.HomeFragment
import com.noichhar.app.ui.fragment.RpnFragment
import com.noichhar.app.ui.fragment.StatsFragment
import com.noichhar.app.util.Constants

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var selectedTab = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        selectedTab = savedInstanceState?.getInt("selected_tab", 0) ?: 0
        binding.floatingNav.selectTab(selectedTab)
        binding.floatingNav.onTabSelected = { showTab(it) }
        if (savedInstanceState == null) showTab(selectedTab)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (selectedTab != 0) showTab(0) else {
                    isEnabled = false; onBackPressedDispatcher.onBackPressed()
                }
            }
        })
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                Constants.NOTIFICATION_PERMISSION_REQUEST_CODE)
        }
    }
    private fun showTab(id: Int) {
        selectedTab = id.coerceIn(0, 4)
        binding.floatingNav.selectTab(selectedTab)
        val fragment: Fragment = when (selectedTab) {
            1 -> StatsFragment(); 2 -> RpnFragment(); 3 -> ConfigureFragment(); 4 -> AboutFragment(); else -> HomeFragment()
        }
        // Performance: fragment switch uses replace with fade animation; fragments retain state via ViewModel and DiffUtil
        supportFragmentManager.beginTransaction().setCustomAnimations(R.anim.fade_slide_in, android.R.anim.fade_out)
            .replace(R.id.fragment_container, fragment).commit()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("selected_tab", selectedTab); super.onSaveInstanceState(outState)
    }
}
