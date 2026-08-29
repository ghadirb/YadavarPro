package com.ghadirb.yadavar.ui.reminders

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ghadirb.yadavar.R
import com.ghadirb.yadavar.adapters.ReminderListItem
import com.ghadirb.yadavar.adapters.RemindersAdapter
import com.ghadirb.yadavar.database.CategoryCatalog
import com.ghadirb.yadavar.database.ReminderEntity
import com.ghadirb.yadavar.databinding.FragmentRemindersBinding
import com.ghadirb.yadavar.dialogs.AddReminderDialog
import com.ghadirb.yadavar.ui.settings.SettingsActivity
import com.ghadirb.yadavar.utils.QuietHoursManager
import com.ghadirb.yadavar.utils.ReminderTemplates
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch

class RemindersFragment : Fragment() {

    private var _binding: FragmentRemindersBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RemindersViewModel by activityViewModels()

    private val adapter = RemindersAdapter(
        onComplete = { viewModel.complete(it) },
        onDelete = { viewModel.delete(it) },
        onClick = { AddReminderDialog.newInstanceForEdit(it.id).show(childFragmentManager, "edit_reminder") }
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
        binding.buttonSettings.setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }
        binding.chipActive.setOnClickListener { viewModel.showCompleted.value = false }
        binding.chipDone.setOnClickListener { viewModel.showCompleted.value = true }
        binding.switchFocusQuick.setOnCheckedChangeListener { _, checked ->
            viewModel.prefs.focusModeEnabled = checked
            refreshQuietBanner()
        }

        bindTemplates()

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.categories.collect { rebuildCategoryChips(it) }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.visibleReminders.collect { list ->
                adapter.submitList(toListItems(list))
                binding.emptyState.isVisible = list.isEmpty()
                updatePeriodSummary(list)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.showCompleted.collect { done ->
                binding.chipActive.isChecked = !done
                binding.chipDone.isChecked = done
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedCategory.collect { rebuildCategoryChips(viewModel.categories.value) }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshQuietBanner()
        binding.switchFocusQuick.isChecked = viewModel.prefs.focusModeEnabled
    }

    private fun refreshQuietBanner() {
        val prefs = viewModel.prefs
        val quiet = QuietHoursManager.isQuietNow(prefs)
        binding.bannerQuiet.isVisible = quiet
        binding.bannerQuiet.text = when {
            prefs.focusModeEnabled -> getString(R.string.focus_on_banner)
            else -> getString(
                R.string.quiet_hours_banner,
                prefs.formatMinutes(prefs.quietStartMinutes),
                prefs.formatMinutes(prefs.quietEndMinutes)
            )
        }
    }

    private fun bindTemplates() {
        binding.templatesStrip.removeAllViews()
        ReminderTemplates.all.forEach { template ->
            val chip = Chip(requireContext()).apply {
                text = template.title
                isCheckable = false
                setOnClickListener {
                    viewModel.save(ReminderTemplates.toReminder(template))
                    Toast.makeText(requireContext(), R.string.template_added, Toast.LENGTH_SHORT).show()
                }
            }
            binding.templatesStrip.addView(chip)
        }
    }

    private fun rebuildCategoryChips(fromDb: List<String>) {
        val selected = viewModel.selectedCategory.value
        val names = (CategoryCatalog.allNames() + fromDb).distinct()
        binding.categoryChips.removeAllViews()
        fun addChip(label: String, value: String?) {
            val chip = Chip(requireContext()).apply {
                text = label
                isCheckable = true
                isChecked = selected == value
                setOnClickListener { viewModel.selectedCategory.value = value }
            }
            binding.categoryChips.addView(chip)
        }
        addChip(getString(R.string.category_all), null)
        names.forEach { addChip(it, it) }
    }

    private fun toListItems(list: List<ReminderEntity>): List<ReminderListItem> {
        if (!viewModel.prefs.groupByCategory || viewModel.selectedCategory.value != null) {
            return list.map { ReminderListItem.Row(it) }
        }
        val grouped = list.groupBy { it.category.ifBlank { getString(R.string.category_none) } }
        val items = mutableListOf<ReminderListItem>()
        grouped.toSortedMap().forEach { (cat, rows) ->
            val color = rows.firstOrNull()?.categoryColor ?: CategoryCatalog.colorFor(cat)
            items += ReminderListItem.Header(cat, rows.size, color)
            rows.forEach { items += ReminderListItem.Row(it) }
        }
        return items
    }

    private fun updatePeriodSummary(list: List<ReminderEntity>) {
        val now = System.currentTimeMillis()
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }
        val startOfToday = cal.timeInMillis
        val endOfToday = startOfToday + 24 * 60 * 60 * 1000L
        val endOfWeek = startOfToday + 7 * 24 * 60 * 60 * 1000L
        val overdue = list.count { !it.isCompleted && it.triggerTime < now }
        val today = list.count { it.triggerTime in startOfToday until endOfToday }
        val thisWeek = list.count { it.triggerTime in endOfToday until endOfWeek }
        binding.textPeriodSummary.text = getString(R.string.period_summary, overdue, today, thisWeek)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
