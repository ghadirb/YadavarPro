package com.ghadirb.yadavar.models

import com.google.gson.annotations.SerializedName

data class APIKey(
    @SerializedName("provider")
    val provider: AIProvider,
    @SerializedName("key")
    val key: String,
    @SerializedName("baseUrl")
    val baseUrl: String? = null,
    @SerializedName("isActive")
    val isActive: Boolean = false,
    @SerializedName("isAutoProvisioned")
    val isAutoProvisioned: Boolean = false
)

enum class AIProvider {
    OPENAI, ANTHROPIC, OPENROUTER, AIML, GLADIA, LIARA, GAPGPT, CUSTOM, IVIRA, AVALAI
}
