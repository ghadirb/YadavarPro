package com.ghadirb.yadavar.dialogs

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.ghadirb.yadavar.databinding.DialogAddReminderBinding
import com.ghadirb.yadavar.database.*
import com.ghadirb.yadavar.ui.reminders.RemindersViewModel
import java.util.Calendar

/**
 * Handles both "add" and "edit": pass an existing reminder via [newInstanceForEdit] and
 * the same layout doubles as the editor, matching the pattern Maliar-Pro used for its
 * Add/EditReminderDialog pair but collapsed into one dialog to keep the surface smaller.
 */
class AddReminderDialog : DialogFragment() {

    private var _binding: DialogAddReminderBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RemindersViewModel by activityViewModels()

    private var editingId: Long = 0
    private val calendar = Calendar.getInstance()

    companion object {
        private const val ARG_EDIT_ID = "edit_id"
        private const val ARG_EDIT_TITLE = "edit_title"
        private const val ARG_EDIT_TIME = "edit_time"

        fun newInstanceForEdit(reminder: ReminderEntity) = AddReminderDialog().apply {
            arguments = Bundle().apply {
                putLong(ARG_EDIT_ID, reminder.id)
                putString(ARG_EDIT_TITLE, reminder.title)
                putLong(ARG_EDIT_TIME, reminder.triggerTime)
            }
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

        arguments?.let {
            editingId = it.getLong(ARG_EDIT_ID)
            binding.editTitle.setText(it.getString(ARG_EDIT_TITLE))
            calendar.timeInMillis = it.getLong(ARG_EDIT_TIME, System.currentTimeMillis())
        }
        updateDateTimeLabel()

        binding.buttonPickDate.setOnClickListener { pickDate() }
        binding.buttonPickTime.setOnClickListener { pickTime() }
        binding.buttonSave.setOnClickListener { save() }
        binding.buttonCancel.setOnClickListener { dismiss() }
    }

    // NOTE: uses the platform Gregorian DatePickerDialog for now; swap in a Jalali-calendar
    // picker widget here once one is added to the UI layer - PersianCalendarHelper already
    // has the conversion math ready for it, updateDateTimeLabel() below already displays
    // the Jalali date.
    private fun pickDate() {
        DatePickerDialog(
            requireContext(),
            { _, y, m, d -> calendar.set(y, m, d); updateDateTimeLabel() },
            calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun pickTime() {
        TimePickerDialog(
            requireContext(),
            { _, h, min -> calendar.set(Calendar.HOUR_OF_DAY, h); calendar.set(Calendar.MINUTE, min); updateDateTimeLabel() },
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
            binding.editTitle.error = getString(com.ghadirb.yadavar.R.string.error_title_required)
            return
        }

        val reminder = ReminderEntity(
            id = editingId,
            title = title,
            description = binding.editDescription.text?.toString().orEmpty(),
            reminderType = binding.spinnerType.selectedItem as String,
            priority = binding.spinnerPriority.selectedItem as String,
            repeatPattern = binding.spinnerRepeat.selectedItem as String,
            triggerTime = calendar.timeInMillis,
            category = binding.editCategory.text?.toString().orEmpty(),
            soundUri = ReminderSound.builtIns[binding.spinnerSound.selectedItemPosition].value
        )
        viewModel.add(reminder)
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
