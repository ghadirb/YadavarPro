package com.ghadirb.yadavar.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.ghadirb.yadavar.R
import com.ghadirb.yadavar.databinding.FragmentSettingsBackupBinding
import com.ghadirb.yadavar.ui.reminders.RemindersViewModel
import com.ghadirb.yadavar.utils.QuietHoursManager
import java.nio.charset.Charset

class SettingsBackupFragment : Fragment() {

    private var _binding: FragmentSettingsBackupBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RemindersViewModel by activityViewModels()

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@registerForActivityResult
        viewModel.exportJson { json ->
            requireContext().contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charset.forName("UTF-8"))) }
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
        _binding = FragmentSettingsBackupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.buttonExport.setOnClickListener { exportLauncher.launch("yadavar-backup.json") }
        binding.buttonImport.setOnClickListener { importLauncher.launch(arrayOf("application/json", "text/*")) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
