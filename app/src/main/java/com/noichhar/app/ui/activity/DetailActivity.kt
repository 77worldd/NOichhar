package com.noichhar.app.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.noichhar.app.R
import com.noichhar.app.databinding.ActivityFragmentHostBinding
import com.noichhar.app.ui.fragment.DnsFragment
import com.noichhar.app.ui.fragment.FirewallFragment

/** Hosts the existing fully featured DNS and rule editors from the new tabs. */
class DetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityFragmentHostBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val dns = intent.getStringExtra(EXTRA_SCREEN) == SCREEN_DNS
        binding.toolbar.setTitle(if (dns) R.string.dns else R.string.firewall)
        binding.toolbar.setNavigationOnClickListener { finish() }
        if (savedInstanceState == null) supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, if (dns) DnsFragment() else FirewallFragment()).commit()
    }
    companion object {
        private const val EXTRA_SCREEN = "screen"
        const val SCREEN_DNS = "dns"
        const val SCREEN_RULES = "rules"
        fun intent(context: Context, screen: String): Intent = Intent(context, DetailActivity::class.java).putExtra(EXTRA_SCREEN, screen)
    }
}
