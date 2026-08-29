package com.ghadirb.yadavar.dialogs

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.ghadirb.yadavar.R
import com.ghadirb.yadavar.databinding.DialogAddReminderBinding
import com.ghadirb.yadavar.database.*
import com.ghadirb.yadavar.ui.reminders.RemindersViewModel
import com.ghadirb.yadavar.utils.PersianCalendarHelper
import com.ghadirb.yadavar.utils.ReminderSound
import java.util.Calendar

class AddReminderDialog : DialogFragment() {

    private var _binding: DialogAddReminderBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RemindersViewModel by activityViewModels()

    private var editingId: Long = 0
    private var loaded: ReminderEntity? = null
    private val calendar = Calendar.getInstance()

    companion object {
        private const val ARG_EDIT_ID = "edit_id"

        fun newInstanceForEdit(id: Long) = AddReminderDialog().apply {
            arguments = Bundle().apply { putLong(ARG_EDIT_ID, id) }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = DialogAddReminderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.spinnerType.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item,
            ReminderType.entries.map { it.name }
        )
        binding.spinnerRepeat.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item,
            RepeatPattern.entries.map { it.name }
        )
        binding.spinnerPriority.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item,
            Priority.entries.map { it.name }
        )
        binding.spinnerSound.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item,
            ReminderSound.builtIns.map { it.label }
        )
        binding.spinnerCategory.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item,
            CategoryCatalog.allNames()
        )

        editingId = arguments?.getLong(ARG_EDIT_ID) ?: 0L
        if (editingId != 0L) {
            viewModel.getById(editingId) { reminder ->
                if (reminder != null && _binding != null) applyReminder(reminder)
            }
        }
        updateDateTimeLabel()

        binding.buttonPickDate.setOnClickListener { pickJalaliDate() }
        binding.buttonPickTime.setOnClickListener { pickTime() }
        binding.buttonSave.setOnClickListener { save() }
        binding.buttonCancel.setOnClickListener { dismiss() }
    }

    private fun applyReminder(reminder: ReminderEntity) {
        loaded = reminder
        binding.editTitle.setText(reminder.title)
        binding.editDescription.setText(reminder.description)
        calendar.timeInMillis = reminder.triggerTime
        binding.checkBypassQuiet.isChecked = reminder.bypassQuietHours
        selectSpinner(binding.spinnerType, reminder.reminderType)
        selectSpinner(binding.spinnerRepeat, reminder.repeatPattern)
        selectSpinner(binding.spinnerPriority, reminder.priority)
        val catIndex = CategoryCatalog.allNames().indexOf(reminder.category)
        if (catIndex >= 0) binding.spinnerCategory.setSelection(catIndex)
        val soundIndex = ReminderSound.builtIns.indexOfFirst { it.value == reminder.soundUri }
        if (soundIndex >= 0) binding.spinnerSound.setSelection(soundIndex)
        updateDateTimeLabel()
    }

    private fun selectSpinner(spinner: android.widget.Spinner, value: String) {
        val adapter = spinner.adapter ?: return
        for (i in 0 until adapter.count) {
            if (adapter.getItem(i) == value) {
                spinner.setSelection(i)
                return
            }
        }
    }

    private fun pickJalaliDate() {
        val j = PersianCalendarHelper.gregorianToJalali(
            calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH)
        )
        val dialog = JalaliDatePickerDialog.newInstance(j.year, j.month, j.day)
        dialog.onPicked = { y, m, d ->
            val (gy, gm, gd) = PersianCalendarHelper.jalaliToGregorian(y, m, d)
            calendar.set(Calendar.YEAR, gy)
            calendar.set(Calendar.MONTH, gm - 1)
            calendar.set(Calendar.DAY_OF_MONTH, gd)
            updateDateTimeLabel()
        }
        dialog.show(parentFragmentManager, "jalali_date")
    }

    private fun pickTime() {
        TimePickerDialog(
            requireContext(),
            { _, h, min ->
                calendar.set(Calendar.HOUR_OF_DAY, h)
                calendar.set(Calendar.MINUTE, min)
                updateDateTimeLabel()
            },
            calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true
        ).show()
    }

    private fun updateDateTimeLabel() {
        val jalali = PersianCalendarHelper.gregorianToJalali(
            calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH)
        )
        binding.textDateTime.text = "${PersianCalendarHelper.format(jalali)} - " +
            String.format("%02d:%02d", calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))
    }

    private fun save() {
        val title = binding.editTitle.text?.toString()?.trim().orEmpty()
        if (title.isEmpty()) {
            binding.editTitle.error = getString(R.string.error_title_required)
            return
        }
        val category = binding.spinnerCategory.selectedItem as String
        val base = loaded
        val reminder = (base ?: ReminderEntity(title = title, triggerTime = calendar.timeInMillis)).copy(
            id = editingId,
            title = title,
            description = binding.editDescription.text?.toString().orEmpty(),
            reminderType = binding.spinnerType.selectedItem as String,
            priority = binding.spinnerPriority.selectedItem as String,
            repeatPattern = binding.spinnerRepeat.selectedItem as String,
            triggerTime = calendar.timeInMillis,
            category = category,
            categoryColor = CategoryCatalog.colorFor(category),
            soundUri = ReminderSound.builtIns[binding.spinnerSound.selectedItemPosition].value,
            bypassQuietHours = binding.checkBypassQuiet.isChecked
        )
        viewModel.save(reminder)
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
