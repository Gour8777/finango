package com.gourav.finango.ui.chatbot



class ChatRepository(
    private val api: ChatApi,
    private val authTokenProvider: suspend () -> String
) {
    suspend fun sendMessage(prompt: String, sessionId: String): Result<String> = try {
        val token = authTokenProvider()
        val res = api.send(bearer = "Bearer $token", body = ChatRequest(prompt, sessionId))
        val text = res.response.trim()
        if (text.isEmpty()) Result.failure(IllegalStateException("Empty response"))
        else Result.success(text)
    } catch (t: Throwable) {
        Result.failure(t)
    }
}
