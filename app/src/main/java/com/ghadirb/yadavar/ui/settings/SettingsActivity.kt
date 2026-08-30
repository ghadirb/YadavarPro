package com.ghadirb.yadavar.ui.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.ghadirb.yadavar.R
import com.ghadirb.yadavar.databinding.ActivitySettingsBinding
import com.google.android.material.tabs.TabLayoutMediator

/**
 * Hosts the settings screens as tabs (Alerts & quiet hours / Assistant / Backup / Subscription)
 * instead of one long scrolling page, so each section is quicker to find and the page never
 * feels overwhelming regardless of how many options a section grows to.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    private val tabTitles by lazy {
        listOf(
            getString(R.string.settings_tab_notifications),
            getString(R.string.settings_tab_assistant),
            getString(R.string.settings_tab_backup),
            getString(R.string.settings_tab_subscription)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.viewPager.adapter = SettingsPagerAdapter(this)
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = tabTitles[position]
        }.attach()
    }
}
