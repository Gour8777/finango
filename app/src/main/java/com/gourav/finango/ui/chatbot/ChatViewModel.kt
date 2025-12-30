package com.gourav.finango.ui.chatbot



import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.plus

class ChatViewModel(
    private val repo: ChatRepository,
    val sessionId: String = "demo-session-1"
) : ViewModel() {

    private val _ui = MutableStateFlow(ChatUiState())
    val ui = _ui.asStateFlow()

    fun send(prompt: String) {
        if (prompt.isBlank()) return

        val userMsg = ChatMessage(role = ChatMessage.Role.USER, text = prompt)
        val typing = ChatMessage(role = ChatMessage.Role.TYPING, text = "…")

        _ui.value = _ui.value.copy(
            messages = _ui.value.messages + userMsg + typing,
            loading = true
        )

        viewModelScope.launch {
            val result = repo.sendMessage(prompt, sessionId)
            val withoutTyping = _ui.value.messages.filter { it.role != ChatMessage.Role.TYPING }
            if (result.isSuccess) {
                val botMsg = ChatMessage(role = ChatMessage.Role.BOT, text = result.getOrThrow())
                _ui.value = _ui.value.copy(messages = withoutTyping + botMsg, loading = false, error = null)
            } else {
                val err = result.exceptionOrNull()?.localizedMessage ?: "Network error"
                val botMsg = ChatMessage(role = ChatMessage.Role.BOT, text = "⚠️ $err. Tap to retry.")
                _ui.value = _ui.value.copy(messages = withoutTyping + botMsg, loading = false, error = err)
            }
        }
    }
    fun addMessageDirect(text: String, role: ChatMessage.Role) {
        val msg = ChatMessage(
            role = role,
            text = text,
            id = System.currentTimeMillis().toString()
        )
        _ui.value = _ui.value.copy(
            messages = _ui.value.messages + msg
        )
    }


}

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

class ChatVmFactory(private val repo: ChatRepository) :
    androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(repo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

