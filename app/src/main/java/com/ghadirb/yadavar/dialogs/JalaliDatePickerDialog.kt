package com.ghadirb.yadavar.dialogs

import android.app.Dialog
import android.os.Bundle
import android.widget.NumberPicker
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.ghadirb.yadavar.R
import com.ghadirb.yadavar.utils.PersianCalendarHelper

class JalaliDatePickerDialog : DialogFragment() {

    var onPicked: ((year: Int, month: Int, day: Int) -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = layoutInflater.inflate(R.layout.dialog_jalali_date, null)
        val yearPicker = view.findViewById<NumberPicker>(R.id.picker_year)
        val monthPicker = view.findViewById<NumberPicker>(R.id.picker_month)
        val dayPicker = view.findViewById<NumberPicker>(R.id.picker_day)

        val initial = arguments?.let {
            PersianCalendarHelper.JalaliDate(
                it.getInt(ARG_YEAR), it.getInt(ARG_MONTH), it.getInt(ARG_DAY)
            )
        } ?: PersianCalendarHelper.nowAsJalali()

        val months = arrayOf(
            "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
            "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
        )

        yearPicker.minValue = initial.year - 80
        yearPicker.maxValue = initial.year + 20
        yearPicker.value = initial.year
        yearPicker.wrapSelectorWheel = false

        monthPicker.minValue = 1
        monthPicker.maxValue = 12
        monthPicker.displayedValues = months
        monthPicker.value = initial.month

        fun refreshDays() {
            val max = PersianCalendarHelper.daysInJalaliMonth(yearPicker.value, monthPicker.value)
            dayPicker.minValue = 1
            if (dayPicker.value > max) dayPicker.value = max
            dayPicker.maxValue = max
        }
        dayPicker.minValue = 1
        dayPicker.maxValue = PersianCalendarHelper.daysInJalaliMonth(initial.year, initial.month)
        dayPicker.value = initial.day.coerceIn(1, dayPicker.maxValue)

        yearPicker.setOnValueChangedListener { _, _, _ -> refreshDays() }
        monthPicker.setOnValueChangedListener { _, _, _ -> refreshDays() }

        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.pick_jalali_date)
            .setView(view)
            .setPositiveButton(R.string.action_ok) { _, _ ->
                onPicked?.invoke(yearPicker.value, monthPicker.value, dayPicker.value)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .create()
    }

    companion object {
        private const val ARG_YEAR = "y"
        private const val ARG_MONTH = "m"
        private const val ARG_DAY = "d"

        fun newInstance(year: Int, month: Int, day: Int) = JalaliDatePickerDialog().apply {
            arguments = Bundle().apply {
                putInt(ARG_YEAR, year)
                putInt(ARG_MONTH, month)
                putInt(ARG_DAY, day)
            }
        }
    }
}
