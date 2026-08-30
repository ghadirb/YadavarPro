package com.ghadirb.yadavar.ui.settings

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class SettingsPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    private val fragments: List<() -> Fragment> = listOf(
        { SettingsNotificationsFragment() },
        { SettingsAssistantFragment() },
        { SettingsBackupFragment() },
        { SettingsSubscriptionFragment() }
    )

    override fun getItemCount(): Int = fragments.size

    override fun createFragment(position: Int): Fragment = fragments[position]()
}
