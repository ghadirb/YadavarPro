package com.ghadirb.yadavar.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.ghadirb.yadavar.R
import com.ghadirb.yadavar.dialogs.AddReminderDialog
import com.ghadirb.yadavar.ui.reminders.RemindersFragment
import com.ghadirb.yadavar.utils.QuietHoursManager

class MainActivity : AppCompatActivity() {

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        QuietHoursManager.schedule(this)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, RemindersFragment())
                .commit()
        }

        if (intent?.getBooleanExtra(EXTRA_QUICK_ADD, false) == true) {
            AddReminderDialog().show(supportFragmentManager, "add_reminder")
        }
    }

    companion object {
        const val EXTRA_QUICK_ADD = "quick_add"
        const val ACTION_TOGGLE_FOCUS = "com.ghadirb.yadavar.TOGGLE_FOCUS"
    }
}
