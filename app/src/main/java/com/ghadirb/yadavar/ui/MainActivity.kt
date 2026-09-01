package com.ghadirb.yadavar.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.ghadirb.yadavar.R
import com.ghadirb.yadavar.assistant.AssistantFragment
import com.ghadirb.yadavar.databinding.ActivityMainBinding
import com.ghadirb.yadavar.dialogs.AddReminderDialog
import com.ghadirb.yadavar.ui.profile.ProfileFragment
import com.ghadirb.yadavar.ui.reminders.RemindersFragment
import com.ghadirb.yadavar.utils.PreferencesManager
import com.ghadirb.yadavar.utils.QuietHoursManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private fun ensureReliableAlarmDelivery() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!com.ghadirb.yadavar.utils.ReliabilityHelper.canScheduleExactAlarms(this)) {
                // Without this OS-level permission, alarms silently fall back to an inexact
                // AlarmManager.set(), which the system is free to delay - this is almost
                // certainly why reminders were firing up to a minute late. Asking once, up
                // front, is much better than a reminders app quietly being unreliable.
                AlertDialog.Builder(this)
                    .setTitle(R.string.exact_alarm_title)
                    .setMessage(R.string.exact_alarm_message)
                    .setPositiveButton(R.string.open_settings) { _, _ ->
                        com.ghadirb.yadavar.utils.ReliabilityHelper.openExactAlarmSettings(this)
                    }
                    .setNegativeButton(R.string.action_later, null)
                    .show()
                return
            }
        }
        maybeAskBatteryOptimization()
    }

    private fun maybeAskBatteryOptimization() {
        val prefs = PreferencesManager(this)
        if (prefs.hasAskedBatteryOptimization()) return
        if (com.ghadirb.yadavar.utils.ReliabilityHelper.isIgnoringBatteryOptimizations(this)) return
        prefs.setAskedBatteryOptimization()
        // Many OEM skins (MIUI, EMUI, ColorOS, ...) kill or heavily delay background alarm
        // delivery for apps under battery optimization, even with an exact alarm scheduled.
        // Asked once, ever - declining doesn't nag again, but the option stays reachable
        // from Settings -> اعلان و سکوت if they change their mind later.
        AlertDialog.Builder(this)
            .setTitle(R.string.battery_opt_title)
            .setMessage(R.string.battery_opt_message)
            .setPositiveButton(R.string.open_settings) { _, _ ->
                com.ghadirb.yadavar.utils.ReliabilityHelper.openBatteryOptimizationSettings(this)
            }
            .setNegativeButton(R.string.action_later, null)
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        QuietHoursManager.schedule(this)
        ensureReliableAlarmDelivery()

        if (savedInstanceState == null) {
            showTab(TAB_REMINDERS)
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_reminders -> showTab(TAB_REMINDERS)
                R.id.nav_assistant -> showTab(TAB_ASSISTANT)
                R.id.nav_profile -> showTab(TAB_PROFILE)
            }
            true
        }

        handleOpenIntent(intent, applyExtras = savedInstanceState == null)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpenIntent(intent, applyExtras = true)
    }

    private fun handleOpenIntent(intent: Intent?, applyExtras: Boolean) {
        if (intent == null || !applyExtras) return
        val openReminder = intent.getLongExtra(EXTRA_OPEN_REMINDER_ID, -1L)
        if (openReminder >= 0L || intent.getBooleanExtra(EXTRA_FROM_NOTIFICATION, false)) {
            binding.bottomNav.selectedItemId = R.id.nav_reminders
        }
        if (intent.getBooleanExtra(EXTRA_QUICK_ADD, false)) {
            binding.bottomNav.selectedItemId = R.id.nav_reminders
            AddReminderDialog().show(supportFragmentManager, "add_reminder")
        }
    }

    private fun showTab(tag: String) {
        val fm = supportFragmentManager
        val tx = fm.beginTransaction()
        listOf(TAB_REMINDERS, TAB_ASSISTANT, TAB_PROFILE).forEach { t ->
            fm.findFragmentByTag(t)?.let { tx.hide(it) }
        }
        val existing = fm.findFragmentByTag(tag)
        if (existing == null) {
            val fragment = when (tag) {
                TAB_ASSISTANT -> AssistantFragment()
                TAB_PROFILE -> ProfileFragment()
                else -> RemindersFragment()
            }
            tx.add(R.id.fragment_container, fragment, tag)
        } else {
            tx.show(existing)
        }
        tx.commit()
    }

    companion object {
        const val EXTRA_QUICK_ADD = "quick_add"
        const val EXTRA_OPEN_REMINDER_ID = "open_reminder_id"
        const val EXTRA_FROM_NOTIFICATION = "from_notification"
        const val ACTION_TOGGLE_FOCUS = "com.ghadirb.yadavar.TOGGLE_FOCUS"
        private const val TAB_REMINDERS = "reminders"
        private const val TAB_ASSISTANT = "assistant"
        private const val TAB_PROFILE = "profile"
    }
}
