package com.ghadirb.yadavar.dialogs

import android.media.MediaPlayer
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.ghadirb.yadavar.R
import com.ghadirb.yadavar.utils.ReminderSound
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Replaces the old inline "all 34 sounds inflated into one LinearLayout" picker. This shows
 * the same catalog in a bounded, independently-scrolling RecyclerView inside a bottom sheet,
 * so the Add Reminder dialog itself always stays a fixed, reasonable size no matter how many
 * built-in sounds exist. Tapping a row's play button previews it and toggles to a "stop" icon;
 * tapping it again, tapping a different row, finishing playback, or closing the sheet all stop it.
 */
class SoundPickerBottomSheet(
    private val currentValue: String,
    private val onSoundChosen: (String) -> Unit
) : BottomSheetDialogFragment() {

    private var player: MediaPlayer? = null
    private var playingValue: String? = null
    private var adapter: SoundPickerAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottomsheet_sound_picker, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recycler = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler_sounds)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        val newAdapter = SoundPickerAdapter(
            items = ReminderSound.builtIns,
            selectedValue = currentValue,
            onSelect = { value ->
                onSoundChosen(value)
                dismiss()
            },
            onTogglePlay = { value -> togglePlay(value) }
        )
        adapter = newAdapter
        recycler.adapter = newAdapter
    }

    private fun togglePlay(value: String) {
        if (playingValue == value) {
            stopPlayback()
            return
        }
        stopPlayback()
        try {
            val uri = ReminderSound.toUri(requireContext(), value) ?: return
            player = MediaPlayer().apply {
                setDataSource(requireContext(), uri)
                setOnCompletionListener { stopPlayback() }
                prepare()
                start()
            }
            playingValue = value
            adapter?.setPlaying(value)
        } catch (_: Exception) {
            Toast.makeText(requireContext(), R.string.sound_preview_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopPlayback() {
        runCatching { player?.apply { if (isPlaying) stop(); release() } }
        player = null
        playingValue = null
        adapter?.setPlaying(null)
    }

    override fun onDestroyView() {
        stopPlayback()
        super.onDestroyView()
    }
}
