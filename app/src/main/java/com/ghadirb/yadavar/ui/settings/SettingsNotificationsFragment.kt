package com.ghadirb.yadavar.ui.settings

import android.app.TimePickerDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.ghadirb.yadavar.databinding.FragmentSettingsNotificationsBinding
import com.ghadirb.yadavar.services.YadavarBackgroundService
import com.ghadirb.yadavar.ui.reminders.RemindersViewModel
import com.ghadirb.yadavar.utils.QuietHoursManager
import com.ghadirb.yadavar.utils.QuietMode

class SettingsNotificationsFragment : Fragment() {

    private var _binding: FragmentSettingsNotificationsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RemindersViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bind()

        binding.switchQuietHours.setOnCheckedChangeListener { _, checked ->
            viewModel.prefs.quietHoursEnabled = checked
            QuietHoursManager.schedule(requireContext())
            if (checked && viewModel.prefs.applySystemDnd && !QuietHoursManager.hasPolicyAccess(requireContext())) {
                requestPolicyAccess()
            }
        }
        binding.switchFocus.setOnCheckedChangeListener { _, checked ->
            viewModel.prefs.focusModeEnabled = checked
        }
        binding.switchGroup.setOnCheckedChangeListener { _, checked ->
            viewModel.prefs.groupByCategory = checked
        }
        binding.switchBackground.setOnCheckedChangeListener { _, checked ->
            viewModel.prefs.setBackgroundServiceEnabled(checked)
            if (checked) YadavarBackgroundService.start(requireContext()) else YadavarBackgroundService.stop(requireContext())
        }
        binding.radioNotifNone.setOnClickListener { viewModel.prefs.setNotificationMode("none") }
        binding.radioNotifSimple.setOnClickListener { viewModel.prefs.setNotificationMode("simple") }
        binding.radioNotifAction.setOnClickListener { viewModel.prefs.setNotificationMode("action") }
        binding.switchCritical.setOnCheckedChangeListener { _, checked ->
            viewModel.prefs.allowCriticalInQuiet = checked
        }
        binding.switchAnnounce.setOnCheckedChangeListener { _, checked ->
            viewModel.prefs.quietHoursAnnounce = checked
        }
        binding.switchSystemDnd.setOnCheckedChangeListener { _, checked ->
            viewModel.prefs.applySystemDnd = checked
            if (checked && !QuietHoursManager.hasPolicyAccess(requireContext())) requestPolicyAccess()
        }
        binding.buttonStart.setOnClickListener { pickTime(true) }
        binding.buttonEnd.setOnClickListener { pickTime(false) }
        binding.radioSilent.setOnClickListener { viewModel.prefs.quietMode = QuietMode.SILENT }
        binding.radioPriority.setOnClickListener { viewModel.prefs.quietMode = QuietMode.PRIORITY }
        binding.radioVibrate.setOnClickListener { viewModel.prefs.quietMode = QuietMode.VIBRATE }
        binding.buttonDndAccess.setOnClickListener { requestPolicyAccess() }
    }

    private fun bind() {
        val p = viewModel.prefs
        binding.switchQuietHours.isChecked = p.quietHoursEnabled
        binding.switchFocus.isChecked = p.focusModeEnabled
        binding.switchGroup.isChecked = p.groupByCategory
        binding.switchBackground.isChecked = p.isBackgroundServiceEnabled()
        when (p.getNotificationMode()) {
            "none" -> binding.radioNotifNone.isChecked = true
            "simple" -> binding.radioNotifSimple.isChecked = true
            else -> binding.radioNotifAction.isChecked = true
        }
        binding.switchCritical.isChecked = p.allowCriticalInQuiet
        binding.switchAnnounce.isChecked = p.quietHoursAnnounce
        binding.switchSystemDnd.isChecked = p.applySystemDnd
        binding.buttonStart.text = getString(com.ghadirb.yadavar.R.string.quiet_start, p.formatMinutes(p.quietStartMinutes))
        binding.buttonEnd.text = getString(com.ghadirb.yadavar.R.string.quiet_end, p.formatMinutes(p.quietEndMinutes))
        when (p.quietMode) {
            QuietMode.SILENT -> binding.radioSilent.isChecked = true
            QuietMode.PRIORITY -> binding.radioPriority.isChecked = true
            QuietMode.VIBRATE -> binding.radioVibrate.isChecked = true
        }
    }

    private fun pickTime(start: Boolean) {
        val current = if (start) viewModel.prefs.quietStartMinutes else viewModel.prefs.quietEndMinutes
        TimePickerDialog(requireContext(), { _, h, m ->
            val value = h * 60 + m
            if (start) viewModel.prefs.quietStartMinutes = value else viewModel.prefs.quietEndMinutes = value
            QuietHoursManager.schedule(requireContext())
            bind()
        }, current / 60, current % 60, true).show()
    }

    private fun requestPolicyAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
