package com.ghadirb.yadavar.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.ghadirb.yadavar.R
import com.ghadirb.yadavar.assistant.AssistantFragment
import com.ghadirb.yadavar.databinding.ActivityMainBinding
import com.ghadirb.yadavar.dialogs.AddReminderDialog
import com.ghadirb.yadavar.ui.reminders.RemindersFragment
import com.ghadirb.yadavar.utils.QuietHoursManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        QuietHoursManager.schedule(this)

        if (savedInstanceState == null) {
            showTab(TAB_REMINDERS)
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_reminders -> showTab(TAB_REMINDERS)
                R.id.nav_assistant -> showTab(TAB_ASSISTANT)
            }
            true
        }

        if (intent?.getBooleanExtra(EXTRA_QUICK_ADD, false) == true) {
            binding.bottomNav.selectedItemId = R.id.nav_reminders
            AddReminderDialog().show(supportFragmentManager, "add_reminder")
        }
    }

    private fun showTab(tag: String) {
        val fm = supportFragmentManager
        val tx = fm.beginTransaction()
        listOf(TAB_REMINDERS, TAB_ASSISTANT).forEach { t ->
            fm.findFragmentByTag(t)?.let { tx.hide(it) }
        }
        val existing = fm.findFragmentByTag(tag)
        if (existing == null) {
            val fragment = if (tag == TAB_ASSISTANT) AssistantFragment() else RemindersFragment()
            tx.add(R.id.fragment_container, fragment, tag)
        } else {
            tx.show(existing)
        }
        tx.commit()
    }

    companion object {
        const val EXTRA_QUICK_ADD = "quick_add"
        const val ACTION_TOGGLE_FOCUS = "com.ghadirb.yadavar.TOGGLE_FOCUS"
        private const val TAB_REMINDERS = "reminders"
        private const val TAB_ASSISTANT = "assistant"
    }
}
