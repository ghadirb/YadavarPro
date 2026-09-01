package com.ghadirb.yadavar.ui.profile

import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.ghadirb.yadavar.R
import com.ghadirb.yadavar.databinding.FragmentProfileBinding
import com.ghadirb.yadavar.services.YadavarBackgroundService
import com.ghadirb.yadavar.ui.reminders.RemindersViewModel
import com.ghadirb.yadavar.ui.settings.SettingsActivity
import com.ghadirb.yadavar.ui.subscription.SubscriptionActivity
import com.ghadirb.yadavar.utils.QuietHoursManager
import com.ghadirb.yadavar.utils.SubscriptionManager
import java.nio.charset.Charset

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RemindersViewModel by activityViewModels()

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@registerForActivityResult
        viewModel.exportJson { json ->
            requireContext().contentResolver.openOutputStream(uri)?.use {
                it.write(json.toByteArray(Charset.forName("UTF-8")))
            }
            Toast.makeText(requireContext(), R.string.backup_exported, Toast.LENGTH_SHORT).show()
        }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        val json = requireContext().contentResolver.openInputStream(uri)?.use {
            it.readBytes().toString(Charset.forName("UTF-8"))
        }.orEmpty()
        viewModel.importJson(json) { result ->
            result.onSuccess {
                QuietHoursManager.schedule(requireContext())
                Toast.makeText(requireContext(), getString(R.string.backup_imported, it), Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(requireContext(), R.string.backup_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindSubscription()
        bindBackground()
        bindBackup()
        bindQuietHours()
        binding.buttonMoreSettings.setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            bindSubscription()
            bindQuietHoursLabels()
            binding.switchBackground.isChecked = viewModel.prefs.isBackgroundServiceEnabled()
            binding.switchQuietHours.isChecked = viewModel.prefs.quietHoursEnabled
        }
    }

    private fun bindSubscription() {
        val status = SubscriptionManager.premiumExpiryLabel(requireContext())
            ?: if (SubscriptionManager.hasPersonalKey(requireContext())) {
                getString(R.string.subscription_personal_key)
            } else {
                getString(
                    R.string.subscription_free_left,
                    SubscriptionManager.remainingFreeLifetime(requireContext()),
                    SubscriptionManager.FREE_AI_LIFETIME_LIMIT
                )
            }
        binding.textSubscriptionStatus.text = status
        binding.buttonSubscription.setOnClickListener {
            startActivity(Intent(requireContext(), SubscriptionActivity::class.java))
        }
    }

    private fun bindBackground() {
        binding.switchBackground.isChecked = viewModel.prefs.isBackgroundServiceEnabled()
        binding.switchBackground.setOnCheckedChangeListener { _, checked ->
            viewModel.prefs.setBackgroundServiceEnabled(checked)
            if (checked) {
                YadavarBackgroundService.start(requireContext())
                Toast.makeText(requireContext(), R.string.bg_enabled_toast, Toast.LENGTH_SHORT).show()
            } else {
                YadavarBackgroundService.stop(requireContext())
                Toast.makeText(requireContext(), R.string.bg_disabled_toast, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun bindBackup() {
        binding.buttonExport.setOnClickListener { exportLauncher.launch("yadavar-backup.json") }
        binding.buttonImport.setOnClickListener { importLauncher.launch(arrayOf("application/json", "text/*")) }
    }

    private fun bindQuietHours() {
        binding.switchQuietHours.isChecked = viewModel.prefs.quietHoursEnabled
        bindQuietHoursLabels()
        binding.switchQuietHours.setOnCheckedChangeListener { _, checked ->
            viewModel.prefs.quietHoursEnabled = checked
            QuietHoursManager.schedule(requireContext())
        }
        binding.buttonQuietStart.setOnClickListener { pickQuietTime(true) }
        binding.buttonQuietEnd.setOnClickListener { pickQuietTime(false) }
    }

    private fun bindQuietHoursLabels() {
        val p = viewModel.prefs
        binding.buttonQuietStart.text = getString(R.string.quiet_from, p.formatMinutes(p.quietStartMinutes))
        binding.buttonQuietEnd.text = getString(R.string.quiet_until, p.formatMinutes(p.quietEndMinutes))
    }

    private fun pickQuietTime(start: Boolean) {
        val current = if (start) viewModel.prefs.quietStartMinutes else viewModel.prefs.quietEndMinutes
        TimePickerDialog(requireContext(), { _, hour, minute ->
            val value = hour * 60 + minute
            if (start) viewModel.prefs.quietStartMinutes = value else viewModel.prefs.quietEndMinutes = value
            QuietHoursManager.schedule(requireContext())
            bindQuietHoursLabels()
        }, current / 60, current % 60, true).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
