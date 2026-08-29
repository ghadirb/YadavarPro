package com.ghadirb.yadavar.assistant

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.ghadirb.yadavar.databinding.FragmentAssistantBinding
import kotlinx.coroutines.launch

class AssistantFragment : Fragment() {
    private var _binding: FragmentAssistantBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AssistantViewModel by viewModels()
    private val adapter = ChatAdapter()

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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
