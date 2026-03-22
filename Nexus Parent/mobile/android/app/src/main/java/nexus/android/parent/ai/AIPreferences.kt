package nexus.android.parent.ai

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * AI Preferences Manager
 * Handles storage of API key, sessions, and settings
 */
class AIPreferences(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("ai_prefs", Context.MODE_PRIVATE)
    
    companion object {
        private const val KEY_API_KEY = "groq_api_key"
        private const val KEY_SELECTED_MODEL = "selected_model"
        private const val KEY_SESSIONS = "ai_sessions"
        private const val KEY_CURRENT_SESSION = "current_session_id"
    }
    
    // API Key
    fun saveApiKey(apiKey: String) {
        prefs.edit().putString(KEY_API_KEY, apiKey).apply()
    }
    
    fun getApiKey(): String? {
        return prefs.getString(KEY_API_KEY, null)
    }
    
    fun hasApiKey(): Boolean {
        return !getApiKey().isNullOrEmpty()
    }
    
    // Selected Model
    fun saveSelectedModel(model: String) {
        prefs.edit().putString(KEY_SELECTED_MODEL, model).apply()
    }
    
    fun getSelectedModel(): String? {
        return prefs.getString(KEY_SELECTED_MODEL, null)
    }
    
    // Sessions
    fun saveSessions(sessions: List<ChatSession>) {
        val jsonArray = JSONArray()
        for (session in sessions) {
            jsonArray.put(session.toJson())
        }
        prefs.edit().putString(KEY_SESSIONS, jsonArray.toString()).apply()
    }
    
    fun getSessions(): List<ChatSession> {
        val sessionsJson = prefs.getString(KEY_SESSIONS, null) ?: return emptyList()
        val sessions = mutableListOf<ChatSession>()
        
        try {
            val jsonArray = JSONArray(sessionsJson)
            for (i in 0 until jsonArray.length()) {
                val sessionJson = jsonArray.getJSONObject(i)
                sessions.add(ChatSession.fromJson(sessionJson))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return sessions
    }
    
    // Current Session ID
    fun saveCurrentSessionId(sessionId: String) {
        prefs.edit().putString(KEY_CURRENT_SESSION, sessionId).apply()
    }
    
    fun getCurrentSessionId(): String? {
        return prefs.getString(KEY_CURRENT_SESSION, null)
    }
    
    /**
     * Chat Session data class
     */
    data class ChatSession(
        val id: String,
        var name: String,
        val messages: MutableList<Message>,
        val createdAt: Long,
        var updatedAt: Long
    ) {
        fun toJson(): JSONObject {
            return JSONObject().apply {
                put("id", id)
                put("name", name)
                put("createdAt", createdAt)
                put("updatedAt", updatedAt)
                
                val messagesArray = JSONArray()
                for (message in messages) {
                    messagesArray.put(JSONObject().apply {
                        put("role", message.role)
                        put("content", message.content)
                    })
                }
                put("messages", messagesArray)
            }
        }
        
        companion object {
            fun fromJson(json: JSONObject): ChatSession {
                val messages = mutableListOf<Message>()
                val messagesArray = json.getJSONArray("messages")
                
                for (i in 0 until messagesArray.length()) {
                    val msgJson = messagesArray.getJSONObject(i)
                    messages.add(Message(
                        role = msgJson.getString("role"),
                        content = msgJson.getString("content")
                    ))
                }
                
                return ChatSession(
                    id = json.getString("id"),
                    name = json.getString("name"),
                    messages = messages,
                    createdAt = json.getLong("createdAt"),
                    updatedAt = json.getLong("updatedAt")
                )
            }
        }
    }
    
    data class Message(
        val role: String,
        val content: String
    )
}
