package com.ghadirb.yadavar.ui.reminders

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ghadirb.yadavar.adapters.RemindersAdapter
import com.ghadirb.yadavar.databinding.FragmentRemindersBinding
import com.ghadirb.yadavar.dialogs.AddReminderDialog
import kotlinx.coroutines.launch

class RemindersFragment : Fragment() {

    private var _binding: FragmentRemindersBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RemindersViewModel by viewModels()

    private val adapter = RemindersAdapter(
        onComplete = { viewModel.complete(it) },
        onDelete = { viewModel.delete(it) },
        onClick = { AddReminderDialog.newInstanceForEdit(it).show(childFragmentManager, "edit_reminder") }
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRemindersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerReminders.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerReminders.adapter = adapter

        binding.fabAdd.setOnClickListener {
            AddReminderDialog().show(childFragmentManager, "add_reminder")
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.activeReminders.collect { list ->
                adapter.submitList(list)
                binding.emptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                updatePeriodSummary(list)
            }
        }
    }

    // Overdue / today / this-week counts, ported from the reminder dashboard added on
    // Maliar-Pro's codex/reminder-finalize-v2 branch.
    private fun updatePeriodSummary(list: List<com.ghadirb.yadavar.database.ReminderEntity>) {
        val now = System.currentTimeMillis()
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }
        val startOfToday = cal.timeInMillis
        val endOfToday = startOfToday + 24 * 60 * 60 * 1000L
        val endOfWeek = startOfToday + 7 * 24 * 60 * 60 * 1000L

        val overdue = list.count { it.triggerTime < now }
        val today = list.count { it.triggerTime in startOfToday until endOfToday }
        val thisWeek = list.count { it.triggerTime in endOfToday until endOfWeek }
        binding.textPeriodSummary.text = "🔴 $overdue سررسیدشده   🟠 $today امروز   🔵 $thisWeek این هفته"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
