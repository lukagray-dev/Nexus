# AI Assistant Sidebar

Comprehensive AI assistant component integrated with Groq AI API for intelligent Nexus assistance.

## Features

### Core Functionality
- **Chat Sessions**: Create, switch, and manage multiple chat sessions
- **Session History**: View and access previous conversations
- **Model Selection**: Choose from available Groq AI models
- **Context Tracking**: Visual indicator showing context usage (0-100%)
- **Markdown Support**: Rich text formatting in responses
- **Typing Indicators**: Visual feedback during AI responses

### UI Features
- **Resizable Panel**: Drag the left edge to resize (320px - 600px)
- **Font Size Control**: Use `Ctrl + +/-` to adjust font size (10px - 20px)
- **Keyboard Shortcuts**: 
  - `Ctrl + Shift + N`: Create new chat session
  - `Ctrl + +/-`: Adjust font size
  - `Enter`: Send message
  - `Shift + Enter`: New line in input

### Session Management
- **Auto-naming**: First message becomes session name (truncated to 30 chars)
- **Persistent Storage**: All sessions saved to localStorage
- **Delete Sessions**: Remove unwanted chat history
- **Active Session**: Visual indicator for current session

## Setup

### 1. Get Groq API Key
1. Visit [Groq](https://console.groq.com/keys)
2. Sign up or log in
3. Create a new API key
4. Copy the key (starts with `gsk_`)

### 2. Configure in App
1. Open AI sidebar
2. Click the model dropdown (bottom right)
3. Click "Configure API Key"
4. Paste your API key
5. Click "Save"

### 3. Select Model
1. Click the model dropdown
2. Choose from available models:
   - Llama models (fast, efficient)
   - Mixtral models (balanced)
   - Gemma models (specialized)

## Available Models

Groq provides several high-performance models:

- **llama-3.3-70b-versatile**: Best overall performance
- **llama-3.1-8b-instant**: Fastest responses
- **mixtral-8x7b-32768**: Large context window
- **gemma-7b-it**: Instruction-tuned

Model names are shortened in the UI for better display.

## Usage

### Starting a Conversation
1. Open the AI sidebar (click AI button in main UI)
2. Type your question in the input field
3. Press Enter or click Send button
4. Wait for AI response

### Managing Sessions
- **New Chat**: Click the + button in header
- **View History**: Click the clock icon in header
- **Switch Session**: Click any session in history dropdown
- **Delete Session**: Click trash icon next to session

### Context Indicator
The context indicator shows how much of the model's context window is used:
- **Green (0-60%)**: Plenty of space
- **Yellow (60-80%)**: Getting full
- **Red (80-100%)**: Near limit

When context is full, start a new session for best results.

## Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| `Ctrl + Shift + N` | New chat session |
| `Ctrl + +` | Increase font size |
| `Ctrl + -` | Decrease font size |
| `Enter` | Send message |
| `Shift + Enter` | New line in input |

## System Prompt

The AI is configured as "Nexus Assistant" with knowledge about:
- Nexus Parent and Child apps
- Feature capabilities (camera, location, SMS, etc.)
- Troubleshooting common issues
- Connection and setup procedures

The assistant provides:
- Concise, helpful responses
- Professional tone
- Markdown formatting
- Bullet points for clarity

## Technical Details

### Storage Keys
- `groq_api_key`: API key
- `ai_chat_sessions`: All chat sessions
- `ai_current_session`: Active session ID
- `ai_selected_model`: Current model
- `ai_chat_font_size`: Font size preference
- `ai_chat_panel_width`: Panel width

### API Integration
- **Endpoint**: `https://api.groq.com/openai/v1`
- **Max Context**: 8192 tokens (estimated)
- **Temperature**: 0.7
- **Max Tokens**: 1024 per response

### Session Structure
```javascript
{
  id: 'session_1234567890',
  name: 'Chat name...',
  messages: [
    { role: 'user', content: '...' },
    { role: 'assistant', content: '...' }
  ],
  createdAt: 1234567890,
  updatedAt: 1234567890
}
```

## Troubleshooting

### API Key Issues
- **Error**: "Invalid API key format"
  - Solution: Ensure key starts with `gsk_`
- **Error**: "API error: 401"
  - Solution: Check if API key is valid and active

### No Models Available
- Check API key is configured correctly
- Verify internet connection
- Try refreshing the page

### Messages Not Sending
- Ensure a model is selected
- Check API key is configured
- Verify internet connection
- Check browser console for errors

## Integration

The AI sidebar is automatically initialized on app load. To manually toggle:

```javascript
// Toggle sidebar
window.toggleAISidebar();

// Access sidebar instance
window.aiSidebar.open();
window.aiSidebar.close();
```

## Files

- `ai-sidebar.html`: UI structure
- `ai-sidebar.css`: Styling (dark theme with golden accents)
- `ai-sidebar.js`: Core functionality
- `README.md`: This documentation
