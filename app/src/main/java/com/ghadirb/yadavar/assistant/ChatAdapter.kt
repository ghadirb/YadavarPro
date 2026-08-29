package com.ghadirb.yadavar.assistant

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ghadirb.yadavar.databinding.ItemChatBinding

class ChatAdapter : ListAdapter<ChatMessage, ChatAdapter.Holder>(Diff) {

    object Diff : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(a: ChatMessage, b: ChatMessage) = a.id == b.id
        override fun areContentsTheSame(a: ChatMessage, b: ChatMessage) = a == b
    }

    class Holder(val binding: ItemChatBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val inflater = LayoutInflater.from(parent.context)
        return Holder(ItemChatBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = getItem(position)
        holder.binding.bubbleUser.visibility = if (item.fromUser) android.view.View.VISIBLE else android.view.View.GONE
        holder.binding.bubbleBot.visibility = if (item.fromUser) android.view.View.GONE else android.view.View.VISIBLE
        if (item.fromUser) holder.binding.bubbleUser.text = item.text
        else holder.binding.bubbleBot.text = item.text
    }
}
