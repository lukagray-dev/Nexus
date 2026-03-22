package nexus.android.parent.ai

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import nexus.android.parent.R
import java.util.UUID

/**
 * AI Chat Activity - Nexus AI Assistant
 * Full implementation with Groq API integration
 */
class AIChatActivity : AppCompatActivity() {

    private lateinit var messagesRecycler: RecyclerView
    private lateinit var aiInput: EditText
    private lateinit var sendBtn: MaterialCardView
    private lateinit var modelBtn: TextView
    private lateinit var closeBtn: ImageView
    private lateinit var newChatBtn: ImageView
    private lateinit var historyBtn: ImageView
    private lateinit var sessionNameText: TextView
    
    private val messages = mutableListOf<ChatMessage>()
    private lateinit var adapter: ChatAdapter
    
    private lateinit var groqClient: GroqApiClient
    private lateinit var aiPrefs: AIPreferences
    
    private var currentSession: AIPreferences.ChatSession? = null
    private var availableModels = listOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_chat)

        groqClient = GroqApiClient()
        aiPrefs = AIPreferences(this)
        
        initializeViews()
        setupRecyclerView()
        setupListeners()
        
        // Check if API key is configured
        if (!aiPrefs.hasApiKey()) {
            showApiKeyDialog()
        } else {
            loadModels()
            loadOrCreateSession()
        }
    }

    private fun initializeViews() {
        messagesRecycler = findViewById(R.id.ai_messages_recycler)
        aiInput = findViewById(R.id.ai_input)
        sendBtn = findViewById(R.id.ai_send_btn)
        modelBtn = findViewById(R.id.ai_model_btn)
        closeBtn = findViewById(R.id.ai_close_btn)
        newChatBtn = findViewById(R.id.ai_new_chat_btn)
        historyBtn = findViewById(R.id.ai_history_btn)
        sessionNameText = findViewById(R.id.ai_session_name)
    }

    private fun setupRecyclerView() {
        adapter = ChatAdapter(messages)
        messagesRecycler.layoutManager = LinearLayoutManager(this)
        messagesRecycler.adapter = adapter
    }

    private fun setupListeners() {
        // Close button
        closeBtn.setOnClickListener {
            finish()
        }

        // Send button
        sendBtn.setOnClickListener {
            sendMessage()
        }

        // Model selector
        modelBtn.setOnClickListener {
            showModelSelectionDialog()
        }
        
        // New chat button
        newChatBtn.setOnClickListener {
            createNewSession()
        }
        
        // History button
        historyBtn.setOnClickListener {
            showHistoryDialog()
        }
    }
    
    private fun loadOrCreateSession() {
        val sessions = aiPrefs.getSessions()
        val currentSessionId = aiPrefs.getCurrentSessionId()
        
        currentSession = if (currentSessionId != null) {
            sessions.find { it.id == currentSessionId }
        } else {
            null
        }
        
        if (currentSession == null) {
            createNewSession()
        } else {
            loadSession(currentSession!!)
        }
    }
    
    private fun createNewSession() {
        val sessionId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        
        currentSession = AIPreferences.ChatSession(
            id = sessionId,
            name = "New Chat",
            messages = mutableListOf(),
            createdAt = now,
            updatedAt = now
        )
        
        aiPrefs.saveCurrentSessionId(sessionId)
        sessionNameText.text = "New Chat"
        
        // Clear messages and show welcome
        messages.clear()
        adapter.notifyDataSetChanged()
        showWelcomeMessage()
        
        saveSession()
    }
    
    private fun loadSession(session: AIPreferences.ChatSession) {
        currentSession = session
        sessionNameText.text = session.name
        
        messages.clear()
        
        // Show welcome if no messages
        if (session.messages.isEmpty()) {
            showWelcomeMessage()
        } else {
            // Load messages
            for (msg in session.messages) {
                messages.add(ChatMessage(
                    type = if (msg.role == "user") MessageType.USER else MessageType.ASSISTANT,
                    text = msg.content
                ))
            }
            adapter.notifyDataSetChanged()
            messagesRecycler.scrollToPosition(messages.size - 1)
        }
    }
    
    private fun saveSession() {
        currentSession?.let { session ->
            val sessions = aiPrefs.getSessions().toMutableList()
            val existingIndex = sessions.indexOfFirst { it.id == session.id }
            
            if (existingIndex >= 0) {
                sessions[existingIndex] = session
            } else {
                sessions.add(session)
            }
            
            aiPrefs.saveSessions(sessions)
        }
    }

    private fun showWelcomeMessage() {
        messages.add(ChatMessage(MessageType.WELCOME, ""))
        adapter.notifyItemInserted(messages.size - 1)
    }

    private fun sendMessage() {
        val text = aiInput.text.toString().trim()
        if (text.isEmpty()) return
        
        // Check API key
        val apiKey = aiPrefs.getApiKey()
        if (apiKey.isNullOrEmpty()) {
            showToast("Please configure API key first")
            showApiKeyDialog()
            return
        }
        
        // Check model selection
        val model = aiPrefs.getSelectedModel()
        if (model.isNullOrEmpty()) {
            showToast("Please select a model first")
            showModelSelectionDialog()
            return
        }

        // Add user message
        messages.add(ChatMessage(MessageType.USER, text))
        adapter.notifyItemInserted(messages.size - 1)
        messagesRecycler.scrollToPosition(messages.size - 1)
        
        // Save to session
        currentSession?.messages?.add(AIPreferences.Message("user", text))
        currentSession?.updatedAt = System.currentTimeMillis()
        
        // Update session name from first message
        if (currentSession?.messages?.size == 1) {
            currentSession?.name = text.take(30) + if (text.length > 30) "..." else ""
            sessionNameText.text = currentSession?.name
        }
        
        saveSession()

        // Clear input
        aiInput.text.clear()

        // Call AI API
        callGroqAPI(apiKey, model, text)
    }
    
    private fun callGroqAPI(apiKey: String, model: String, userMessage: String) {
        // Show typing indicator
        messages.add(ChatMessage(MessageType.TYPING, ""))
        adapter.notifyItemInserted(messages.size - 1)
        messagesRecycler.scrollToPosition(messages.size - 1)
        
        lifecycleScope.launch {
            try {
                // Prepare messages for API
                val apiMessages = currentSession?.messages?.map {
                    GroqApiClient.ChatMessage(it.role, it.content)
                } ?: emptyList()
                
                // Call API
                val response = groqClient.sendChatCompletion(
                    apiKey = apiKey,
                    model = model,
                    messages = apiMessages
                )
                
                // Remove typing indicator
                val typingIndex = messages.indexOfLast { it.type == MessageType.TYPING }
                if (typingIndex >= 0) {
                    messages.removeAt(typingIndex)
                    adapter.notifyItemRemoved(typingIndex)
                }
                
                // Add AI response
                messages.add(ChatMessage(MessageType.ASSISTANT, response))
                adapter.notifyItemInserted(messages.size - 1)
                messagesRecycler.scrollToPosition(messages.size - 1)
                
                // Save to session
                currentSession?.messages?.add(AIPreferences.Message("assistant", response))
                currentSession?.updatedAt = System.currentTimeMillis()
                saveSession()
                
            } catch (e: Exception) {
                // Remove typing indicator
                val typingIndex = messages.indexOfLast { it.type == MessageType.TYPING }
                if (typingIndex >= 0) {
                    messages.removeAt(typingIndex)
                    adapter.notifyItemRemoved(typingIndex)
                }
                
                showToast("Error: ${e.message}")
            }
        }
    }
    
    private fun loadModels() {
        val apiKey = aiPrefs.getApiKey() ?: return
        
        lifecycleScope.launch {
            try {
                availableModels = groqClient.fetchModels(apiKey)
                
                // Set default model if none selected
                if (aiPrefs.getSelectedModel() == null && availableModels.isNotEmpty()) {
                    val defaultModel = availableModels.firstOrNull { it.contains("llama") } 
                        ?: availableModels.first()
                    aiPrefs.saveSelectedModel(defaultModel)
                    modelBtn.text = defaultModel
                } else {
                    modelBtn.text = aiPrefs.getSelectedModel() ?: "Select model"
                }
            } catch (e: Exception) {
                showToast("Failed to load models: ${e.message}")
            }
        }
    }

    private fun showToast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    /**
     * Chat message data class
     */
    data class ChatMessage(
        val type: MessageType,
        val text: String
    )

    /**
     * Message types
     */
    enum class MessageType {
        WELCOME,
        USER,
        ASSISTANT,
        TYPING
    }

    /**
     * RecyclerView adapter for chat messages
     */
    class ChatAdapter(private val messages: List<ChatMessage>) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        companion object {
            const val VIEW_TYPE_WELCOME = 0
            const val VIEW_TYPE_USER = 1
            const val VIEW_TYPE_ASSISTANT = 2
            const val VIEW_TYPE_TYPING = 3
        }

        override fun getItemViewType(position: Int): Int {
            return when (messages[position].type) {
                MessageType.WELCOME -> VIEW_TYPE_WELCOME
                MessageType.USER -> VIEW_TYPE_USER
                MessageType.ASSISTANT -> VIEW_TYPE_ASSISTANT
                MessageType.TYPING -> VIEW_TYPE_TYPING
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                VIEW_TYPE_WELCOME -> {
                    val view = inflater.inflate(R.layout.item_ai_welcome, parent, false)
                    WelcomeViewHolder(view)
                }
                VIEW_TYPE_USER -> {
                    val view = inflater.inflate(R.layout.item_ai_message_user, parent, false)
                    MessageViewHolder(view)
                }
                VIEW_TYPE_TYPING -> {
                    val view = inflater.inflate(R.layout.item_ai_message_assistant, parent, false)
                    TypingViewHolder(view)
                }
                else -> {
                    val view = inflater.inflate(R.layout.item_ai_message_assistant, parent, false)
                    MessageViewHolder(view)
                }
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val message = messages[position]
            when (holder) {
                is MessageViewHolder -> holder.bind(message)
                is TypingViewHolder -> holder.bind()
            }
        }

        override fun getItemCount() = messages.size

        class WelcomeViewHolder(view: View) : RecyclerView.ViewHolder(view)

        class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val messageText: TextView = view.findViewById(R.id.message_text)

            fun bind(message: ChatMessage) {
                messageText.text = message.text
            }
        }
        
        class TypingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val messageText: TextView = view.findViewById(R.id.message_text)

            fun bind() {
                messageText.text = "Typing..."
            }
        }
    }
    
    /**
     * Show API key configuration dialog
     */
    private fun showApiKeyDialog() {
        val input = EditText(this)
        input.hint = "Enter Groq API Key"
        input.setText(aiPrefs.getApiKey() ?: "")
        
        AlertDialog.Builder(this)
            .setTitle("Configure API Key")
            .setMessage("Enter your Groq API key to use the AI assistant")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val apiKey = input.text.toString().trim()
                if (apiKey.isNotEmpty()) {
                    aiPrefs.saveApiKey(apiKey)
                    loadModels()
                    if (currentSession == null) {
                        loadOrCreateSession()
                    }
                    showToast("API key saved")
                } else {
                    showToast("API key cannot be empty")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    /**
     * Show model selection dialog
     */
    private fun showModelSelectionDialog() {
        if (availableModels.isEmpty()) {
            showToast("Loading models...")
            loadModels()
            return
        }
        
        val currentModel = aiPrefs.getSelectedModel()
        val selectedIndex = availableModels.indexOf(currentModel).takeIf { it >= 0 } ?: 0
        
        AlertDialog.Builder(this)
            .setTitle("Select AI Model")
            .setSingleChoiceItems(availableModels.toTypedArray(), selectedIndex) { dialog, which ->
                val selectedModel = availableModels[which]
                aiPrefs.saveSelectedModel(selectedModel)
                modelBtn.text = selectedModel
                showToast("Model changed to $selectedModel")
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    /**
     * Show chat history dialog
     */
    private fun showHistoryDialog() {
        val sessions = aiPrefs.getSessions()
        
        if (sessions.isEmpty()) {
            showToast("No chat history")
            return
        }
        
        val sessionNames = sessions.map { session ->
            "${session.name} - ${formatDate(session.updatedAt)}"
        }.toTypedArray()
        
        AlertDialog.Builder(this)
            .setTitle("Chat History")
            .setItems(sessionNames) { _, which ->
                val selectedSession = sessions[which]
                aiPrefs.saveCurrentSessionId(selectedSession.id)
                loadSession(selectedSession)
                showToast("Loaded: ${selectedSession.name}")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    /**
     * Format timestamp to readable date
     */
    private fun formatDate(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }
}
