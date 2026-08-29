package com.ghadirb.yadavar.dialogs

import android.app.TimePickerDialog
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.ghadirb.yadavar.R
import com.ghadirb.yadavar.databinding.DialogAddReminderBinding
import com.ghadirb.yadavar.database.*
import com.ghadirb.yadavar.ui.reminders.RemindersViewModel
import com.ghadirb.yadavar.utils.EnumLabels
import com.ghadirb.yadavar.utils.LabeledOption
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
    private var customSoundUri: String? = null
    private var previewPlayer: MediaPlayer? = null

    private val pickAudio = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            requireContext().contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
        }
        customSoundUri = uri.toString()
        binding.textSoundName.text = getString(R.string.sound_from_device)
    }

    companion object {
        private const val ARG_EDIT_ID = "edit_id"
        fun newInstanceForEdit(id: Long) = AddReminderDialog().apply {
            arguments = Bundle().apply { putLong(ARG_EDIT_ID, id) }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogAddReminderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireContext()
        binding.spinnerType.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, EnumLabels.reminderTypes())
        binding.spinnerRepeat.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, EnumLabels.repeats())
        binding.spinnerPriority.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, EnumLabels.priorities())
        binding.spinnerAlert.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, EnumLabels.alertTypes())
        binding.spinnerCategory.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, CategoryCatalog.allNames())

        bindWeekdayChips()
        bindSoundList()

        binding.spinnerRepeat.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                val value = (binding.spinnerRepeat.selectedItem as LabeledOption).value
                binding.weekdayRow.visibility = if (value == RepeatPattern.CUSTOM.name) View.VISIBLE else View.GONE
                binding.intervalRow.visibility = if (value == RepeatPattern.CUSTOM_INTERVAL.name) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        editingId = arguments?.getLong(ARG_EDIT_ID) ?: 0L
        if (editingId != 0L) {
            viewModel.getById(editingId) { reminder ->
                if (reminder != null && _binding != null) applyReminder(reminder)
            }
        }
        updateDateTimeLabel()

        binding.buttonPickDate.setOnClickListener { pickJalaliDate() }
        binding.buttonPickTime.setOnClickListener { pickTime() }
        binding.buttonPickDeviceSound.setOnClickListener {
            pickAudio.launch(arrayOf("audio/*", "audio/mpeg", "audio/mp3", "audio/wav"))
        }
        binding.buttonSave.setOnClickListener { save() }
        binding.buttonCancel.setOnClickListener { dismiss() }
    }

    private fun bindWeekdayChips() {
        val group = binding.weekdayChips
        group.removeAllViews()
        EnumLabels.weekdays.forEach { day ->
            val chip = com.google.android.material.chip.Chip(requireContext()).apply {
                text = day.label
                isCheckable = true
                tag = day.value
            }
            group.addView(chip)
        }
    }

    private fun bindSoundList() {
        val container = binding.soundList
        container.removeAllViews()
        ReminderSound.builtIns.forEach { sound ->
            val row = layoutInflater.inflate(R.layout.item_sound_row, container, false)
            val name = row.findViewById<android.widget.TextView>(R.id.text_sound_label)
            val play = row.findViewById<android.widget.ImageButton>(R.id.button_play)
            val radio = row.findViewById<android.widget.RadioButton>(R.id.radio_sound)
            name.text = sound.label
            radio.tag = sound.value
            radio.setOnClickListener {
                customSoundUri = null
                clearSoundRadios(sound.value)
                binding.textSoundName.text = sound.label
            }
            play.setOnClickListener { preview(sound.value) }
            if (sound.value == ReminderSound.DEFAULT_ALARM) radio.isChecked = true
            container.addView(row)
        }
    }

    private fun clearSoundRadios(selected: String) {
        val container = binding.soundList
        for (i in 0 until container.childCount) {
            val radio = container.getChildAt(i).findViewById<android.widget.RadioButton>(R.id.radio_sound)
            radio.isChecked = radio.tag == selected
        }
    }

    private fun selectedSoundValue(): String {
        customSoundUri?.let { return it }
        val container = binding.soundList
        for (i in 0 until container.childCount) {
            val radio = container.getChildAt(i).findViewById<android.widget.RadioButton>(R.id.radio_sound)
            if (radio.isChecked) return radio.tag as String
        }
        return ReminderSound.DEFAULT_ALARM
    }

    private fun preview(value: String) {
        stopPreview()
        try {
            val uri = ReminderSound.toUri(requireContext(), value) ?: return
            previewPlayer = MediaPlayer().apply {
                setDataSource(requireContext(), uri)
                setOnCompletionListener { stopPreview() }
                prepare()
                start()
            }
        } catch (_: Exception) {
            Toast.makeText(requireContext(), R.string.sound_preview_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopPreview() {
        runCatching { previewPlayer?.stop(); previewPlayer?.release() }
        previewPlayer = null
    }

    private fun applyReminder(reminder: ReminderEntity) {
        loaded = reminder
        binding.editTitle.setText(reminder.title)
        binding.editDescription.setText(reminder.description)
        calendar.timeInMillis = reminder.triggerTime
        binding.checkBypassQuiet.isChecked = reminder.bypassQuietHours
        selectOption(binding.spinnerType, reminder.reminderType)
        selectOption(binding.spinnerRepeat, reminder.repeatPattern)
        selectOption(binding.spinnerPriority, reminder.priority)
        selectOption(binding.spinnerAlert, reminder.alertType)
        val catIndex = CategoryCatalog.allNames().indexOf(reminder.category)
        if (catIndex >= 0) binding.spinnerCategory.setSelection(catIndex)
        if (reminder.repeatIntervalDays > 0) binding.editInterval.setText(reminder.repeatIntervalDays.toString())
        val selectedDays = reminder.customRepeatDays.split(",").map { it.trim() }.toSet()
        for (i in 0 until binding.weekdayChips.childCount) {
            val chip = binding.weekdayChips.getChildAt(i) as com.google.android.material.chip.Chip
            chip.isChecked = chip.tag in selectedDays
        }
        if (reminder.soundUri.startsWith("content:") || reminder.soundUri.startsWith("file:")) {
            customSoundUri = reminder.soundUri
            binding.textSoundName.text = getString(R.string.sound_from_device)
        } else {
            clearSoundRadios(reminder.soundUri)
            binding.textSoundName.text = ReminderSound.labelFor(reminder.soundUri)
        }
        updateDateTimeLabel()
    }

    private fun selectOption(spinner: android.widget.Spinner, value: String) {
        val adapter = spinner.adapter ?: return
        for (i in 0 until adapter.count) {
            val item = adapter.getItem(i)
            if (item is LabeledOption && item.value == value) {
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

    private fun selectedWeekdays(): String {
        val days = mutableListOf<String>()
        for (i in 0 until binding.weekdayChips.childCount) {
            val chip = binding.weekdayChips.getChildAt(i) as com.google.android.material.chip.Chip
            if (chip.isChecked) days.add(chip.tag as String)
        }
        return days.joinToString(",")
    }

    private fun save() {
        val title = binding.editTitle.text?.toString()?.trim().orEmpty()
        if (title.isEmpty()) {
            binding.editTitle.error = getString(R.string.error_title_required)
            return
        }
        val category = binding.spinnerCategory.selectedItem as String
        val repeat = (binding.spinnerRepeat.selectedItem as LabeledOption).value
        val type = (binding.spinnerType.selectedItem as LabeledOption).value
        val priority = (binding.spinnerPriority.selectedItem as LabeledOption).value
        val alert = (binding.spinnerAlert.selectedItem as LabeledOption).value
        val interval = binding.editInterval.text?.toString()?.toIntOrNull() ?: 0
        val base = loaded
        val reminder = (base ?: ReminderEntity(title = title, triggerTime = calendar.timeInMillis)).copy(
            id = editingId,
            title = title,
            description = binding.editDescription.text?.toString().orEmpty(),
            reminderType = type,
            priority = priority,
            alertType = alert,
            repeatPattern = repeat,
            triggerTime = calendar.timeInMillis,
            category = category,
            categoryColor = CategoryCatalog.colorFor(category),
            soundUri = selectedSoundValue(),
            bypassQuietHours = binding.checkBypassQuiet.isChecked,
            customRepeatDays = selectedWeekdays(),
            repeatIntervalDays = interval
        )
        viewModel.save(reminder)
        dismiss()
    }

    override fun onDestroyView() {
        stopPreview()
        super.onDestroyView()
        _binding = null
    }
}
