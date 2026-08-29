package com.ghadirb.yadavar.ui.settings

import android.app.TimePickerDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.ghadirb.yadavar.R
import com.ghadirb.yadavar.databinding.ActivitySettingsBinding
import com.ghadirb.yadavar.ui.reminders.RemindersViewModel
import com.ghadirb.yadavar.utils.QuietHoursManager
import com.ghadirb.yadavar.utils.QuietMode
import java.nio.charset.Charset

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val viewModel: RemindersViewModel by viewModels()

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@registerForActivityResult
        viewModel.exportJson { json ->
            contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charset.forName("UTF-8"))) }
            Toast.makeText(this, R.string.backup_exported, Toast.LENGTH_SHORT).show()
        }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        val json = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charset.forName("UTF-8")) }.orEmpty()
        viewModel.importJson(json) { result ->
            result.onSuccess {
                QuietHoursManager.schedule(this)
                Toast.makeText(this, getString(R.string.backup_imported, it), Toast.LENGTH_SHORT).show()
                bind()
            }.onFailure {
                Toast.makeText(this, R.string.backup_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }
        bind()

        binding.switchQuietHours.setOnCheckedChangeListener { _, checked ->
            viewModel.prefs.quietHoursEnabled = checked
            QuietHoursManager.schedule(this)
            if (checked && viewModel.prefs.applySystemDnd && !QuietHoursManager.hasPolicyAccess(this)) {
                requestPolicyAccess()
            }
        }
        binding.switchFocus.setOnCheckedChangeListener { _, checked ->
            viewModel.prefs.focusModeEnabled = checked
        }
        binding.switchGroup.setOnCheckedChangeListener { _, checked ->
            viewModel.prefs.groupByCategory = checked
        }
        binding.switchCritical.setOnCheckedChangeListener { _, checked ->
            viewModel.prefs.allowCriticalInQuiet = checked
        }
        binding.switchAnnounce.setOnCheckedChangeListener { _, checked ->
            viewModel.prefs.quietHoursAnnounce = checked
        }
        binding.switchSystemDnd.setOnCheckedChangeListener { _, checked ->
            viewModel.prefs.applySystemDnd = checked
            if (checked && !QuietHoursManager.hasPolicyAccess(this)) requestPolicyAccess()
        }
        binding.buttonStart.setOnClickListener { pickTime(true) }
        binding.buttonEnd.setOnClickListener { pickTime(false) }
        binding.radioSilent.setOnClickListener { viewModel.prefs.quietMode = QuietMode.SILENT }
        binding.radioPriority.setOnClickListener { viewModel.prefs.quietMode = QuietMode.PRIORITY }
        binding.radioVibrate.setOnClickListener { viewModel.prefs.quietMode = QuietMode.VIBRATE }
        binding.buttonExport.setOnClickListener { exportLauncher.launch("yadavar-backup.json") }
        binding.buttonImport.setOnClickListener { importLauncher.launch(arrayOf("application/json", "text/*")) }
        binding.buttonDndAccess.setOnClickListener { requestPolicyAccess() }
    }

    private fun bind() {
        val p = viewModel.prefs
        binding.switchQuietHours.isChecked = p.quietHoursEnabled
        binding.switchFocus.isChecked = p.focusModeEnabled
        binding.switchGroup.isChecked = p.groupByCategory
        binding.switchCritical.isChecked = p.allowCriticalInQuiet
        binding.switchAnnounce.isChecked = p.quietHoursAnnounce
        binding.switchSystemDnd.isChecked = p.applySystemDnd
        binding.buttonStart.text = getString(R.string.quiet_start, p.formatMinutes(p.quietStartMinutes))
        binding.buttonEnd.text = getString(R.string.quiet_end, p.formatMinutes(p.quietEndMinutes))
        when (p.quietMode) {
            QuietMode.SILENT -> binding.radioSilent.isChecked = true
            QuietMode.PRIORITY -> binding.radioPriority.isChecked = true
            QuietMode.VIBRATE -> binding.radioVibrate.isChecked = true
        }
    }

    private fun pickTime(start: Boolean) {
        val current = if (start) viewModel.prefs.quietStartMinutes else viewModel.prefs.quietEndMinutes
        TimePickerDialog(this, { _, h, m ->
            val value = h * 60 + m
            if (start) viewModel.prefs.quietStartMinutes = value else viewModel.prefs.quietEndMinutes = value
            QuietHoursManager.schedule(this)
            bind()
        }, current / 60, current % 60, true).show()
    }

    private fun requestPolicyAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
    }
}
