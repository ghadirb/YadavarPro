package com.ghadirb.yadavar.assistant

data class ChatMessage(
    val id: Long = System.currentTimeMillis(),
    val text: String,
    val fromUser: Boolean
)
