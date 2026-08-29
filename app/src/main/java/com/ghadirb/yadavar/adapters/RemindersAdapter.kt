package com.ghadirb.yadavar.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ghadirb.yadavar.databinding.ItemReminderBinding
import com.ghadirb.yadavar.database.ReminderEntity
import java.text.SimpleDateFormat
import java.util.*

class RemindersAdapter(
    private val onComplete: (ReminderEntity) -> Unit,
    private val onDelete: (ReminderEntity) -> Unit,
    private val onClick: (ReminderEntity) -> Unit
) : ListAdapter<ReminderEntity, RemindersAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(val binding: ItemReminderBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemReminderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val fmt = SimpleDateFormat("HH:mm - yyyy/MM/dd", Locale.getDefault())
        holder.binding.apply {
            textTitle.text = item.title
            textTime.text = fmt.format(Date(item.triggerTime))
            textCategory.text = item.category
            try {
                categoryDot.setColorFilter(Color.parseColor(item.categoryColor))
            } catch (_: IllegalArgumentException) { /* ignore malformed color */ }

            root.setOnClickListener { onClick(item) }
            buttonDone.setOnClickListener { onComplete(item) }
            buttonDelete.setOnClickListener { onDelete(item) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<ReminderEntity>() {
            override fun areItemsTheSame(a: ReminderEntity, b: ReminderEntity) = a.id == b.id
            override fun areContentsTheSame(a: ReminderEntity, b: ReminderEntity) = a == b
        }
    }
}
