package com.ghadirb.yadavar.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.ghadirb.yadavar.R
import com.ghadirb.yadavar.databinding.FragmentSettingsAssistantBinding
import com.ghadirb.yadavar.ui.reminders.RemindersViewModel

class SettingsAssistantFragment : Fragment() {

    private var _binding: FragmentSettingsAssistantBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RemindersViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsAssistantBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val p = viewModel.prefs
        binding.inputApiKey.setText(p.aiApiKey)
        binding.inputApiUrl.setText(p.aiBaseUrl)
        binding.inputApiModel.setText(p.aiModel)

        binding.buttonSaveApi.setOnClickListener {
            viewModel.prefs.aiApiKey = binding.inputApiKey.text?.toString().orEmpty().trim()
            viewModel.prefs.aiBaseUrl = binding.inputApiUrl.text?.toString().orEmpty().trim()
            viewModel.prefs.aiModel = binding.inputApiModel.text?.toString().orEmpty().trim()
            Toast.makeText(requireContext(), R.string.assistant_api_saved, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
