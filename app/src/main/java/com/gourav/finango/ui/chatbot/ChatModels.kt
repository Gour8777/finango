package com.gourav.finango.ui.chatbot



data class ChatRequest(
    val prompt: String,
    val session_id: String
)

data class ChatResponse(
    val response: String
)

data class ChatMessage(
    val id: String = System.nanoTime().toString(),
    val role: Role,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    enum class Role { USER, BOT, TYPING }
}
