package com.ghadirb.yadavar.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ghadirb.yadavar.databinding.ItemReminderBinding
import com.ghadirb.yadavar.databinding.ItemSectionHeaderBinding
import com.ghadirb.yadavar.database.ReminderEntity
import com.ghadirb.yadavar.utils.EnumLabels
import com.ghadirb.yadavar.utils.PersianCalendarHelper
import java.util.Calendar

sealed class ReminderListItem {
    data class Header(val title: String, val count: Int, val color: String) : ReminderListItem()
    data class Row(val reminder: ReminderEntity) : ReminderListItem()
}

class RemindersAdapter(
    private val onComplete: (ReminderEntity) -> Unit,
    private val onDelete: (ReminderEntity) -> Unit,
    private val onClick: (ReminderEntity) -> Unit
) : ListAdapter<ReminderListItem, RecyclerView.ViewHolder>(DIFF) {

    override fun getItemViewType(position: Int) = when (getItem(position)) {
        is ReminderListItem.Header -> 0
        is ReminderListItem.Row -> 1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == 0) {
            HeaderHolder(ItemSectionHeaderBinding.inflate(inflater, parent, false))
        } else {
            RowHolder(ItemReminderBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ReminderListItem.Header -> (holder as HeaderHolder).bind(item)
            is ReminderListItem.Row -> (holder as RowHolder).bind(item.reminder)
        }
    }

    inner class HeaderHolder(val binding: ItemSectionHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ReminderListItem.Header) {
            binding.textHeader.text = "${item.title}  (${item.count})"
            runCatching { binding.headerDot.setColorFilter(Color.parseColor(item.color)) }
        }
    }

    inner class RowHolder(val binding: ItemReminderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ReminderEntity) {
            val cal = Calendar.getInstance().apply { timeInMillis = item.triggerTime }
            val jalali = PersianCalendarHelper.gregorianToJalali(
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
            )
            binding.textTitle.text = item.title
            binding.textTime.text = "${PersianCalendarHelper.format(jalali)}  " +
                String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
            binding.textCategory.text = buildString {
                append(item.category.ifBlank { "—" })
                append("  ·  ")
                append(EnumLabels.alertType(item.alertType))
                append("  ·  ")
                append(EnumLabels.repeat(item.repeatPattern))
                if (item.bypassQuietHours) append("  ·  عبور از سکوت")
            }
            try {
                binding.categoryDot.setColorFilter(Color.parseColor(item.categoryColor))
            } catch (_: IllegalArgumentException) { }
            binding.root.setOnClickListener { onClick(item) }
            binding.buttonDone.setOnClickListener { onComplete(item) }
            binding.buttonDelete.setOnClickListener { onDelete(item) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<ReminderListItem>() {
            override fun areItemsTheSame(a: ReminderListItem, b: ReminderListItem) = when {
                a is ReminderListItem.Header && b is ReminderListItem.Header -> a.title == b.title
                a is ReminderListItem.Row && b is ReminderListItem.Row -> a.reminder.id == b.reminder.id
                else -> false
            }
            override fun areContentsTheSame(a: ReminderListItem, b: ReminderListItem) = a == b
        }
    }
}
