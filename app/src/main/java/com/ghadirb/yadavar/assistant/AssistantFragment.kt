package com.ghadirb.yadavar.assistant

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.ghadirb.yadavar.R
import com.ghadirb.yadavar.databinding.FragmentAssistantBinding
import kotlinx.coroutines.launch

class AssistantFragment : Fragment() {
    private var _binding: FragmentAssistantBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AssistantViewModel by viewModels()
    private val adapter = ChatAdapter()

    private val voiceLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) viewModel.send(spoken)
    }

    private val audioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoice() else Toast.makeText(requireContext(), R.string.assistant_voice_denied, Toast.LENGTH_SHORT).show()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAssistantBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val layout = LinearLayoutManager(requireContext()).apply { stackFromEnd = true }
        binding.chatRecycler.layoutManager = layout
        binding.chatRecycler.adapter = adapter

        binding.sendButton.setOnClickListener {
            val text = binding.messageInput.text?.toString().orEmpty()
            viewModel.send(text)
            binding.messageInput.text = null
        }
        binding.voiceButton.setOnClickListener {
            audioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }

        listOf(
            binding.chipToday to "یادآوری‌های امروز",
            binding.chipTomorrow to "فردا ساعت ۹ یادآوری عمومی",
            binding.chipQuiet to "سکوت شبانه روشن",
            binding.chipHelp to "کمک"
        ).forEach { (chip, prompt) ->
            chip.setOnClickListener { viewModel.send(prompt) }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.messages.collect { list ->
                    adapter.submitList(list) {
                        if (list.isNotEmpty()) binding.chatRecycler.scrollToPosition(list.lastIndex)
                    }
                }
            }
        }
    }

    private fun startVoice() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.assistant_voice_prompt))
        }
        try {
            voiceLauncher.launch(intent)
        } catch (_: Exception) {
            Toast.makeText(requireContext(), R.string.assistant_voice_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
