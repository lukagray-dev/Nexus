package nexus.android.parent.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Groq API Client for AI chat functionality
 * Matches desktop implementation
 */
class GroqApiClient {
    
    companion object {
        private const val GROQ_API_URL = "https://api.groq.com/openai/v1"
        
        // System prompt matching desktop
        private const val SYSTEM_PROMPT = """You are **Nexus Assistant**, the support AI for Nexus - a parental monitoring app (Parent Electron/Android app connecting to Child Android app).

## Core Identity
- **Role**: Help parents use Nexus effectively
- **Tone**: Professional, helpful, concise. Clear and direct communication.
- **Format**: Concise. Short responses. Bullet points > paragraphs. Use markdown & emojis.
- **Scope**: Only Nexus assistance. Redirect if off-topic.

## What is Nexus?
**Two-part system**: Child app (stealth Android monitor) → Parent dashboard (Electron/Windows receiver)

### Child App Capabilities
- Stealth mode (hides from app drawer; disableable only from parent app)
- Customizable icon/name (pre-hide only)
- **Live streams**: camera, mic, location (GPS), SMS, calls, chat (WhatsApp/Telegram/Messenger), notifications, Gmail, file access
- Ghost audio player (forceful playback, ignores device mute)
- Self-restarts if killed; survives reboots
- All data syncs real-time to Parent app

### Parent App (Electron/Windows)
- **Sidebar**: Feature buttons (camera, location, SMS, calls, etc.)
- **Connection**: 12-digit Child ID required
- **Windows**: Draggable, resizable, closable; multiple open simultaneously
- **Settings**: Top-right profile icon; includes background video customization

## Quick Troubleshooting
- **App won't work?** → Child app needs ALL permissions enabled
- **Not syncing?** → Check Child ID (12 digits), connection status, permissions
- **Features not showing?** → Open feature windows from sidebar
- **Performance issues?** → Close unused windows, check network"""
    }
    
    /**
     * Fetch available models from Groq API
     */
    suspend fun fetchModels(apiKey: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$GROQ_API_URL/models")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json")
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = BufferedReader(InputStreamReader(connection.inputStream)).use {
                    it.readText()
                }
                
                val jsonResponse = JSONObject(response)
                val dataArray = jsonResponse.getJSONArray("data")
                val models = mutableListOf<String>()
                
                for (i in 0 until dataArray.length()) {
                    val model = dataArray.getJSONObject(i)
                    models.add(model.getString("id"))
                }
                
                connection.disconnect()
                models
            } else {
                connection.disconnect()
                throw Exception("Failed to fetch models: $responseCode")
            }
        } catch (e: Exception) {
            throw Exception("Error fetching models: ${e.message}")
        }
    }
    
    /**
     * Send chat completion request to Groq API
     */
    suspend fun sendChatCompletion(
        apiKey: String,
        model: String,
        messages: List<ChatMessage>,
        temperature: Float = 0.7f,
        maxTokens: Int = 1024
    ): String = withContext(Dispatchers.IO) {
        try {
            val url = URL("$GROQ_API_URL/chat/completions")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            
            // Build messages array
            val messagesArray = JSONArray()
            
            // Add system prompt
            messagesArray.put(JSONObject().apply {
                put("role", "system")
                put("content", SYSTEM_PROMPT)
            })
            
            // Add conversation history (last 10 messages)
            val recentMessages = messages.takeLast(10)
            for (message in recentMessages) {
                messagesArray.put(JSONObject().apply {
                    put("role", message.role)
                    put("content", message.content)
                })
            }
            
            // Build request body
            val requestBody = JSONObject().apply {
                put("model", model)
                put("messages", messagesArray)
                put("temperature", temperature)
                put("max_tokens", maxTokens)
            }
            
            // Send request
            OutputStreamWriter(connection.outputStream).use {
                it.write(requestBody.toString())
                it.flush()
            }
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = BufferedReader(InputStreamReader(connection.inputStream)).use {
                    it.readText()
                }
                
                val jsonResponse = JSONObject(response)
                val choices = jsonResponse.getJSONArray("choices")
                val firstChoice = choices.getJSONObject(0)
                val message = firstChoice.getJSONObject("message")
                val content = message.getString("content")
                
                connection.disconnect()
                content
            } else {
                val errorResponse = BufferedReader(InputStreamReader(connection.errorStream)).use {
                    it.readText()
                }
                connection.disconnect()
                
                val errorJson = JSONObject(errorResponse)
                val errorMessage = errorJson.optJSONObject("error")?.optString("message") 
                    ?: "API error: $responseCode"
                throw Exception(errorMessage)
            }
        } catch (e: Exception) {
            throw Exception("Error calling Groq API: ${e.message}")
        }
    }
    
    /**
     * Chat message data class for API
     */
    data class ChatMessage(
        val role: String,  // "user" or "assistant"
        val content: String
    )
}
