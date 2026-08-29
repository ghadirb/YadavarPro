package com.ghadirb.yadavar.dialogs

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ghadirb.yadavar.R
import com.ghadirb.yadavar.utils.ReminderSound

/**
 * Renders the built-in sound catalog inside a bounded, scrollable RecyclerView (instead of
 * inflating all rows into a plain LinearLayout, which used to make the Add Reminder dialog
 * grow to fit all 34 rows at once). Each row reflects two pieces of state: whether it's the
 * selected sound (radio button) and whether it's the one currently previewing (play/pause icon).
 */
class SoundPickerAdapter(
    private val items: List<ReminderSound.BuiltIn>,
    private var selectedValue: String,
    private val onSelect: (String) -> Unit,
    private val onTogglePlay: (String) -> Unit
) : RecyclerView.Adapter<SoundPickerAdapter.ViewHolder>() {

    private var playingValue: String? = null

    class ViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        val root: android.view.View = itemView.findViewById(R.id.sound_row_root)
        val label: TextView = itemView.findViewById(R.id.text_sound_label)
        val radio: RadioButton = itemView.findViewById(R.id.radio_sound)
        val play: ImageButton = itemView.findViewById(R.id.button_play)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sound_row, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val sound = items[position]
        holder.label.text = sound.label
        holder.radio.isChecked = sound.value == selectedValue
        val isPlaying = sound.value == playingValue
        holder.play.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
        holder.play.contentDescription = holder.itemView.context.getString(
            if (isPlaying) R.string.stop_sound else R.string.play_sound
        )
        val selectRow = {
            val previous = items.indexOfFirst { it.value == selectedValue }
            selectedValue = sound.value
            if (previous >= 0) notifyItemChanged(previous)
            notifyItemChanged(position)
            onSelect(sound.value)
        }
        holder.root.setOnClickListener { selectRow() }
        holder.radio.setOnClickListener { selectRow() }
        holder.play.setOnClickListener { onTogglePlay(sound.value) }
    }

    /** Called by the host after starting/stopping preview playback so exactly one row shows
     *  the "pause" icon at a time. Pass null when nothing is playing. */
    fun setPlaying(value: String?) {
        val previous = items.indexOfFirst { it.value == playingValue }
        playingValue = value
        if (previous >= 0) notifyItemChanged(previous)
        val current = items.indexOfFirst { it.value == value }
        if (current >= 0) notifyItemChanged(current)
    }
}
