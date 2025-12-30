package com.gourav.finango.ui.chatbot

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.gourav.finango.R
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class Chatbot : Fragment() {

    // --- add: UI refs
    private lateinit var rv: RecyclerView
    private lateinit var input: EditText
    private lateinit var send: ImageButton
    private lateinit var topProgress: ProgressBar
    private lateinit var welcomeText: TextView
    private lateinit var inputLayout: View
    private val adapter = ChatAdapter { _ ->
        // simple retry: re-send last user message
        val lastUser = viewModel.ui.value.messages.lastOrNull { it.role == ChatMessage.Role.USER }
        lastUser?.let { viewModel.send(it.text) }
    }

    // --- add: token provider (use your JWT here)


    // --- add: VM wiring
    private val viewModel: ChatViewModel by viewModels(factoryProducer = {
        val api = ChatApi.create("https://finance-backend-lnok.onrender.com/")
        val repo = ChatRepository(api, tokenProvider)
        ChatVmFactory(repo)
    })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_chatbot, container, false)
    }

    // --- add: wire views + collectors
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rv = view.findViewById(R.id.chatList)
        input = view.findViewById(R.id.inputMessage)
        send = view.findViewById(R.id.btnSend)
        topProgress = view.findViewById(R.id.topProgress)
        welcomeText = view.findViewById(R.id.welcomeText)
        inputLayout = view.findViewById(R.id.inputLayout)

        rv.layoutManager = LinearLayoutManager(requireContext()).apply { stackFromEnd = true }
        rv.adapter = adapter
        val hasChats = viewModel.ui.value.messages.isNotEmpty()
        welcomeText.visibility = if (hasChats) View.GONE else View.VISIBLE
        inputLayout.visibility = if (hasChats) View.VISIBLE else View.GONE

        inputLayout.visibility = View.GONE
        send.setOnClickListener { submit() }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { submit(); true } else false
        }
        welcomeText.setOnClickListener {
            if (viewModel.ui.value.messages.isEmpty()) {
                showDefaultBotMessage()
            } else {
                // If chats already exist, just switch visibility
                welcomeText.visibility = View.GONE
                inputLayout.visibility = View.VISIBLE
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.ui.collectLatest { s ->
                adapter.submitList(s.messages) {
                    if (adapter.itemCount > 0)
                        rv.scrollToPosition(adapter.itemCount - 1)
                }

                topProgress.visibility = if (s.loading) View.VISIBLE else View.GONE
                send.isEnabled = !s.loading
                input.isEnabled = !s.loading

                // ✅ Always show input if there are chats
                val empty = s.messages.isEmpty()
                welcomeText.visibility = if (empty) View.VISIBLE else View.GONE
                inputLayout.visibility = if (empty) View.GONE else View.VISIBLE
            }
        }
    }
    private fun showDefaultBotMessage() {
        if (viewModel.ui.value.messages.isNotEmpty()) return  // prevent duplicates

        lifecycleScope.launch {
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
            val userDoc = FirebaseFirestore.getInstance().collection("users").document(userId).get().await()
            val name = userDoc.getString("name") ?: "there"
            val botGreeting = "Hii, $name! How may I assist you today?"

            viewModel.addMessageDirect("Hii! FinAssist", ChatMessage.Role.USER)
            viewModel.addMessageDirect(botGreeting, ChatMessage.Role.BOT)

            inputLayout.visibility = View.VISIBLE
            welcomeText.visibility = View.GONE
        }
    }






    private fun submit() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        viewModel.send(text)
        input.setText("")
    }
    private val tokenProvider: suspend () -> String = {
        val user = FirebaseAuth.getInstance().currentUser
            ?: throw IllegalStateException("User not signed in")
        val tokenResult = user.getIdToken(true).await()
        tokenResult.token ?: throw IllegalStateException("Token is null")
    }

}
