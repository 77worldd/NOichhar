package com.noichhar.app.ui.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.noichhar.app.R
import com.noichhar.app.databinding.ActivityFragmentHostBinding
import com.noichhar.app.ui.fragment.AppsFragment

class AppManagerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityFragmentHostBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setTitle(R.string.apps)
        binding.toolbar.setNavigationOnClickListener { finish() }
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, AppsFragment())
                .commit()
        }
    }
}
