/**
 * AI Assistant Sidebar Component
 * Integrates with Groq AI API for intelligent assistance
 * Features: Sessions, History, Resizable panel, Font size control, Context tracking
 */

const GROQ_API_URL = 'https://api.groq.com/openai/v1';
const STORAGE_KEY = 'groq_api_key';
const SESSIONS_KEY = 'ai_chat_sessions';
const CURRENT_SESSION_KEY = 'ai_current_session';
const FONT_SIZE_KEY = 'ai_chat_font_size';
const PANEL_WIDTH_KEY = 'ai_chat_panel_width';
const SELECTED_MODEL_KEY = 'ai_selected_model';
const MAX_CONTEXT_TOKENS = 8192;

const SYSTEM_PROMPT = `You are **Nexus Assistant**, the support AI for Nexus - a parental monitoring app (Parent Electron/Android app connecting to Child Android app).

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
- **Performance issues?** → Close unused windows, check network`;

class AISidebar {
    constructor(app = null) {
        this.app = app;
        this.container = null;
        this.messagesContainer = null;
        this.input = null;
        this.sendBtn = null;
        this.configModal = null;
        this.isOpen = false;
        this.isResizing = false;
        this.sessions = {};
        this.currentSessionId = null;
        this.currentModel = '';
        this.isStreaming = false;
        this.fontSize = 13;
        this.availableModels = [];
    }

    init() {
        console.log('Initializing AI sidebar...');
        this.loadSettings();
        this.cacheElements();
        this.bindEvents();
        this.loadSessions();
        this.loadModels();
        this.applyFontSize();
        console.log('✅ AI Sidebar initialized', { container: this.container, isOpen: this.isOpen });
    }

    loadSettings() {
        this.fontSize = parseInt(localStorage.getItem(FONT_SIZE_KEY)) || 13;
        const savedWidth = localStorage.getItem(PANEL_WIDTH_KEY);
        if (savedWidth && this.container) {
            this.container.style.width = savedWidth + 'px';
        }
    }

    cacheElements() {
        // Find the sidebar and modal that were injected by component loader
        this.container = document.querySelector('.ai-sidebar');
        this.configModal = document.querySelector('.ai-config-modal');
        
        console.log('Caching AI sidebar elements:', { 
            container: this.container, 
            configModal: this.configModal 
        });
        
        if (!this.container) {
            console.error('AI sidebar container not found in DOM');
            return;
        }
        
        this.messagesContainer = this.container.querySelector('.ai-messages');
        this.input = this.container.querySelector('.ai-input');
        this.sendBtn = this.container.querySelector('#ai-send');
        this.sessionNameEl = this.container.querySelector('.ai-session-name');
        this.contextIndicator = this.container.querySelector('.ai-context-percent');
        this.modelNameEl = this.container.querySelector('.ai-model-name');
        this.modelDropdown = this.container.querySelector('.ai-model-dropdown');
        this.historyDropdown = this.container.querySelector('.ai-history-dropdown');
        this.resizeHandle = this.container.querySelector('.ai-resize-handle');
        
        console.log('AI sidebar elements cached:', {
            messagesContainer: !!this.messagesContainer,
            input: !!this.input,
            sendBtn: !!this.sendBtn
        });
    }

    bindEvents() {
        if (!this.container) return;

        // Close button
        const closeBtn = this.container.querySelector('#ai-close');
        if (closeBtn) closeBtn.addEventListener('click', () => this.close());

        // Send button
        if (this.sendBtn) this.sendBtn.addEventListener('click', () => this.sendMessage());

        // Input events
        if (this.input) {
            this.input.addEventListener('keydown', (e) => {
                if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault();
                    this.sendMessage();
                }
            });

            this.input.addEventListener('input', () => {
                this.input.style.height = 'auto';
                this.input.style.height = Math.min(this.input.scrollHeight, 100) + 'px';
            });
        }

        // New chat button
        const newChatBtn = this.container.querySelector('#ai-new-chat');
        if (newChatBtn) newChatBtn.addEventListener('click', () => this.createNewSession());

        // History button
        const historyBtn = this.container.querySelector('#ai-history-btn');
        if (historyBtn) {
            historyBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                this.toggleHistoryDropdown();
            });
        }

        // Model button
        const modelBtn = this.container.querySelector('#ai-model-btn');
        if (modelBtn) {
            modelBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                this.toggleModelDropdown();
            });
        }

        // Configure button
        const configureBtn = this.container.querySelector('#ai-configure');
        if (configureBtn) {
            configureBtn.addEventListener('click', () => {
                this.closeModelDropdown();
                this.openConfigModal();
            });
        }

        // Config modal events
        if (this.configModal) {
            const closeBtn = this.configModal.querySelector('#ai-config-close');
            const cancelBtn = this.configModal.querySelector('#ai-config-cancel');
            const saveBtn = this.configModal.querySelector('#ai-config-save');

            if (closeBtn) closeBtn.addEventListener('click', () => this.closeConfigModal());
            if (cancelBtn) cancelBtn.addEventListener('click', () => this.closeConfigModal());
            if (saveBtn) saveBtn.addEventListener('click', () => this.saveApiKey());

            this.configModal.addEventListener('click', (e) => {
                if (e.target === this.configModal) this.closeConfigModal();
            });
        }

        // Close dropdowns on outside click
        document.addEventListener('click', () => {
            this.closeHistoryDropdown();
            this.closeModelDropdown();
        });

        // Resize handle
        this.setupResizeHandle();

        // Keyboard shortcuts
        document.addEventListener('keydown', (e) => {
            if (!this.isOpen) return;

            // Ctrl + +/- for font size
            if (e.ctrlKey && (e.key === '+' || e.key === '=' || e.key === '-')) {
                e.preventDefault();
                if (e.key === '+' || e.key === '=') {
                    this.changeFontSize(1);
                } else {
                    this.changeFontSize(-1);
                }
            }

            // Ctrl+Shift+N for new chat
            if (e.ctrlKey && e.shiftKey && e.key === 'N') {
                e.preventDefault();
                this.createNewSession();
            }
        });
    }

    setupResizeHandle() {
        if (!this.resizeHandle || !this.container) return;

        this.resizeHandle.addEventListener('mousedown', (e) => {
            e.preventDefault();
            this.isResizing = true;
            this.resizeHandle.classList.add('active');
            document.body.style.cursor = 'ew-resize';
            document.body.style.userSelect = 'none';

            const startX = e.clientX;
            const startWidth = this.container.offsetWidth;

            const onMouseMove = (e) => {
                if (!this.isResizing) return;
                const diff = startX - e.clientX;
                const newWidth = Math.min(Math.max(startWidth + diff, 320), 600);
                this.container.style.width = newWidth + 'px';
            };

            const onMouseUp = () => {
                this.isResizing = false;
                this.resizeHandle.classList.remove('active');
                document.body.style.cursor = '';
                document.body.style.userSelect = '';
                localStorage.setItem(PANEL_WIDTH_KEY, this.container.offsetWidth);
                document.removeEventListener('mousemove', onMouseMove);
                document.removeEventListener('mouseup', onMouseUp);
            };

            document.addEventListener('mousemove', onMouseMove);
            document.addEventListener('mouseup', onMouseUp);
        });
    }

    changeFontSize(delta) {
        this.fontSize = Math.min(Math.max(this.fontSize + delta, 10), 20);
        localStorage.setItem(FONT_SIZE_KEY, this.fontSize);
        this.applyFontSize();
        this.showFontSizeHint();
    }

    applyFontSize() {
        document.documentElement.style.setProperty('--ai-chat-font-size', this.fontSize + 'px');
    }

    showFontSizeHint() {
        const existing = document.querySelector('.font-size-hint');
        if (existing) existing.remove();

        const hint = document.createElement('div');
        hint.className = 'font-size-hint';
        hint.style.cssText = `
            position: fixed;
            top: 50%;
            left: 50%;
            transform: translate(-50%, -50%);
            background: rgba(251, 191, 36, 0.95);
            color: #000;
            padding: 12px 24px;
            border-radius: 8px;
            font-size: 14px;
            font-weight: 600;
            z-index: 100000;
            pointer-events: none;
        `;
        hint.textContent = `Font Size: ${this.fontSize}px`;
        document.body.appendChild(hint);

        setTimeout(() => hint.remove(), 1500);
    }

    open() {
        if (this.container) {
            console.log('Opening AI sidebar');
            this.container.classList.add('open');
            this.isOpen = true;
            if (this.input) this.input.focus();
        } else {
            console.error('AI sidebar container not found');
        }
    }

    close() {
        if (this.container) {
            console.log('Closing AI sidebar');
            this.container.classList.remove('open');
            this.isOpen = false;
        }
    }

    toggle() {
        console.log('Toggling AI sidebar, current state:', this.isOpen);
        this.isOpen ? this.close() : this.open();
    }

    // Session Management
    loadSessions() {
        try {
            const saved = localStorage.getItem(SESSIONS_KEY);
            this.sessions = saved ? JSON.parse(saved) : {};
            this.currentSessionId = localStorage.getItem(CURRENT_SESSION_KEY);

            if (!this.currentSessionId || !this.sessions[this.currentSessionId]) {
                this.createNewSession(false);
            } else {
                this.renderCurrentSession();
            }
            this.renderHistoryList();
        } catch (e) {
            console.error('Error loading sessions:', e);
            this.sessions = {};
            this.createNewSession(false);
        }
    }

    saveSessions() {
        try {
            localStorage.setItem(SESSIONS_KEY, JSON.stringify(this.sessions));
            localStorage.setItem(CURRENT_SESSION_KEY, this.currentSessionId);
        } catch (e) {
            console.error('Error saving sessions:', e);
        }
    }

    createNewSession(save = true) {
        const id = 'session_' + Date.now();
        const name = 'New Chat';

        this.sessions[id] = {
            id,
            name,
            messages: [],
            createdAt: Date.now(),
            updatedAt: Date.now()
        };

        this.currentSessionId = id;
        if (save) this.saveSessions();

        this.renderCurrentSession();
        this.renderHistoryList();
        this.updateContextIndicator();
    }

    switchSession(sessionId) {
        if (!this.sessions[sessionId]) return;
        this.currentSessionId = sessionId;
        this.saveSessions();
        this.renderCurrentSession();
        this.closeHistoryDropdown();
        this.updateContextIndicator();
    }

    deleteSession(sessionId, e) {
        e?.stopPropagation();
        delete this.sessions[sessionId];

        if (sessionId === this.currentSessionId) {
            const remaining = Object.keys(this.sessions);
            if (remaining.length > 0) {
                this.currentSessionId = remaining[0];
            } else {
                this.createNewSession(false);
            }
        }

        this.saveSessions();
        this.renderCurrentSession();
        this.renderHistoryList();
    }

    getCurrentSession() {
        return this.sessions[this.currentSessionId] || null;
    }

    updateSessionName(firstMessage) {
        const session = this.getCurrentSession();
        if (!session || session.name !== 'New Chat') return;

        session.name = firstMessage.slice(0, 30) + (firstMessage.length > 30 ? '...' : '');
        session.updatedAt = Date.now();
        this.saveSessions();
        this.updateSessionNameDisplay();
        this.renderHistoryList();
    }

    updateSessionNameDisplay() {
        const session = this.getCurrentSession();
        if (this.sessionNameEl && session) {
            this.sessionNameEl.textContent = session.name;
        }
    }

    renderCurrentSession() {
        const session = this.getCurrentSession();
        if (!session || !this.messagesContainer) return;

        this.updateSessionNameDisplay();

        if (session.messages.length === 0) {
            // Show welcome message (already in HTML)
            const welcome = this.messagesContainer.querySelector('.ai-welcome');
            if (welcome) welcome.style.display = 'flex';
            
            // Clear any other messages
            const messages = this.messagesContainer.querySelectorAll('.ai-message, .ai-error-message');
            messages.forEach(msg => msg.remove());
        } else {
            // Hide welcome message
            const welcome = this.messagesContainer.querySelector('.ai-welcome');
            if (welcome) welcome.style.display = 'none';
            
            // Clear and render messages
            const messages = this.messagesContainer.querySelectorAll('.ai-message, .ai-error-message');
            messages.forEach(msg => msg.remove());
            
            session.messages.forEach(msg => this.addMessageToDOM(msg.role, msg.content));
        }

        this.scrollToBottom();
    }

    // History Dropdown
    toggleHistoryDropdown() {
        this.historyDropdown?.classList.toggle('hidden');
        this.closeModelDropdown();
    }

    closeHistoryDropdown() {
        this.historyDropdown?.classList.add('hidden');
    }

    renderHistoryList() {
        const list = this.container?.querySelector('.ai-history-list');
        if (!list) return;

        const sortedSessions = Object.values(this.sessions).sort((a, b) => b.updatedAt - a.updatedAt);

        if (sortedSessions.length === 0) {
            list.innerHTML = '<div style="padding:12px;color:#71717a;text-align:center;">No chat history</div>';
            return;
        }

        list.innerHTML = sortedSessions.map(session => `
            <div class="ai-history-item ${session.id === this.currentSessionId ? 'active' : ''}" data-id="${session.id}">
                <div class="ai-history-item-content">
                    <div class="ai-history-item-name">${this.escapeHtml(session.name)}</div>
                    <div class="ai-history-item-date">${this.formatDate(session.updatedAt)}</div>
                </div>
                <button class="ai-history-item-delete" data-id="${session.id}" title="Delete">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <polyline points="3 6 5 6 21 6"></polyline>
                        <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
                    </svg>
                </button>
            </div>
        `).join('');

        // Bind events
        list.querySelectorAll('.ai-history-item').forEach(item => {
            item.addEventListener('click', (e) => {
                if (!e.target.closest('.ai-history-item-delete')) {
                    this.switchSession(item.dataset.id);
                }
            });
        });

        list.querySelectorAll('.ai-history-item-delete').forEach(btn => {
            btn.addEventListener('click', (e) => this.deleteSession(btn.dataset.id, e));
        });
    }

    formatDate(timestamp) {
        const date = new Date(timestamp);
        const now = new Date();
        const diff = now - date;

        if (diff < 60000) return 'Just now';
        if (diff < 3600000) return Math.floor(diff / 60000) + 'm ago';
        if (diff < 86400000) return Math.floor(diff / 3600000) + 'h ago';
        if (diff < 604800000) return Math.floor(diff / 86400000) + 'd ago';

        return date.toLocaleDateString();
    }

    // Model Management
    toggleModelDropdown() {
        this.modelDropdown?.classList.toggle('hidden');
        this.closeHistoryDropdown();
    }

    closeModelDropdown() {
        this.modelDropdown?.classList.add('hidden');
    }

    async loadModels() {
        const apiKey = localStorage.getItem(STORAGE_KEY);

        if (!apiKey) {
            this.updateModelUI([]);
            return;
        }

        try {
            const response = await fetch(`${GROQ_API_URL}/models`, {
                headers: { 'Authorization': `Bearer ${apiKey}` }
            });

            if (!response.ok) throw new Error('Failed to fetch models');

            const data = await response.json();
            this.availableModels = data.data
                .filter(m => m.id.includes('llama') || m.id.includes('mixtral') || m.id.includes('gemma'))
                .sort((a, b) => a.id.localeCompare(b.id));

            this.updateModelUI(this.availableModels);
        } catch (error) {
            console.error('Error loading models:', error);
            this.updateModelUI([]);
        }
    }

    updateModelUI(models) {
        const savedModel = localStorage.getItem(SELECTED_MODEL_KEY);
        const list = this.container?.querySelector('.ai-model-list');

        if (!list) return;

        if (models.length === 0) {
            list.innerHTML = '<div class="ai-dropdown-item" style="color:#71717a;cursor:default;">No models available</div>';
            if (this.modelNameEl) this.modelNameEl.textContent = 'Select model';
            return;
        }

        list.innerHTML = models.map(model => `
            <button class="ai-dropdown-item ${model.id === savedModel ? 'active' : ''}" data-model="${model.id}">
                ${this.getShortModelName(model.id)}
            </button>
        `).join('');

        // Set current model
        if (savedModel && models.some(m => m.id === savedModel)) {
            this.currentModel = savedModel;
        } else if (models.length > 0) {
            this.currentModel = models[0].id;
            localStorage.setItem(SELECTED_MODEL_KEY, this.currentModel);
        }

        if (this.modelNameEl) {
            this.modelNameEl.textContent = this.getShortModelName(this.currentModel);
        }

        // Bind events
        list.querySelectorAll('.ai-dropdown-item').forEach(btn => {
            btn.addEventListener('click', () => this.selectModel(btn.dataset.model));
        });
    }

    getShortModelName(modelId) {
        if (!modelId) return 'Select model';
        // Shorten model names for display
        return modelId.replace('llama-', 'L').replace('mixtral-', 'M').replace('gemma-', 'G')
            .replace('-8192', '').replace('-32768', '').replace('-it', '');
    }

    selectModel(modelId) {
        const previousModel = this.currentModel;
        this.currentModel = modelId;
        localStorage.setItem(SELECTED_MODEL_KEY, modelId);

        // Update UI
        if (this.modelNameEl) {
            this.modelNameEl.textContent = this.getShortModelName(modelId);
        }
        
        this.container?.querySelectorAll('.ai-model-list .ai-dropdown-item').forEach(btn => {
            btn.classList.toggle('active', btn.dataset.model === modelId);
        });

        this.closeModelDropdown();

        // Log model change
        const session = this.getCurrentSession();
        if (session && session.messages.length > 0 && previousModel !== modelId) {
            console.log(`Model switched from ${previousModel} to ${modelId}`);
        }
    }

    // Config Modal
    openConfigModal() {
        const apiKeyInput = document.querySelector('#ai-api-key-input');
        if (apiKeyInput) {
            apiKeyInput.value = localStorage.getItem(STORAGE_KEY) || '';
        }
        this.configModal?.classList.remove('hidden');
    }

    closeConfigModal() {
        this.configModal?.classList.add('hidden');
    }

    async saveApiKey() {
        const apiKeyInput = document.querySelector('#ai-api-key-input');
        const apiKey = apiKeyInput?.value?.trim();

        if (!apiKey) {
            this.showError('Please enter an API key');
            return;
        }

        if (!apiKey.startsWith('gsk_')) {
            this.showError('Invalid API key format. Groq keys start with "gsk_"');
            return;
        }

        localStorage.setItem(STORAGE_KEY, apiKey);
        this.closeConfigModal();
        await this.loadModels();
    }

    // Context Tracking
    updateContextIndicator() {
        const session = this.getCurrentSession();
        if (!session || !this.contextIndicator) return;

        // Estimate tokens (rough: 4 chars = 1 token)
        const totalChars = session.messages.reduce((sum, m) => sum + m.content.length, 0) + SYSTEM_PROMPT.length;
        const estimatedTokens = Math.ceil(totalChars / 4);
        const percentage = Math.min(Math.round((estimatedTokens / MAX_CONTEXT_TOKENS) * 100), 100);

        this.contextIndicator.textContent = percentage + '%';

        const indicator = this.contextIndicator.parentElement;
        if (indicator) {
            indicator.classList.remove('warning', 'danger');
            if (percentage > 80) {
                indicator.classList.add('danger');
            } else if (percentage > 60) {
                indicator.classList.add('warning');
            }
        }
    }

    // Messaging
    async sendMessage() {
        const message = this.input?.value?.trim();
        if (!message || this.isStreaming) return;

        const apiKey = localStorage.getItem(STORAGE_KEY);
        if (!apiKey) {
            this.openConfigModal();
            return;
        }

        if (!this.currentModel) {
            this.showError('Please select a model first');
            return;
        }

        const session = this.getCurrentSession();
        if (!session) return;

        // Clear input
        this.input.value = '';
        this.input.style.height = 'auto';

        // Hide welcome message
        const welcomeMsg = this.messagesContainer?.querySelector('.ai-welcome');
        if (welcomeMsg) welcomeMsg.style.display = 'none';

        // Update session name on first message
        if (session.messages.length === 0) {
            this.updateSessionName(message);
        }

        // Add user message
        this.addMessage('user', message);

        // Show typing indicator
        this.showTypingIndicator();
        this.isStreaming = true;
        if (this.sendBtn) this.sendBtn.disabled = true;

        try {
            const response = await this.callGroqAPI(apiKey, message);
            this.removeTypingIndicator();
            this.addMessage('assistant', response);
        } catch (error) {
            this.removeTypingIndicator();
            this.showError(error.message || 'Failed to get response');
        } finally {
            this.isStreaming = false;
            if (this.sendBtn) this.sendBtn.disabled = false;
        }
    }

    async callGroqAPI(apiKey, userMessage) {
        const session = this.getCurrentSession();
        const messages = [
            { role: 'system', content: SYSTEM_PROMPT },
            ...session.messages.slice(-10),
            { role: 'user', content: userMessage }
        ];

        const response = await fetch(`${GROQ_API_URL}/chat/completions`, {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${apiKey}`,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                model: this.currentModel,
                messages,
                temperature: 0.7,
                max_tokens: 1024
            })
        });

        if (!response.ok) {
            const error = await response.json().catch(() => ({}));
            throw new Error(error.error?.message || `API error: ${response.status}`);
        }

        const data = await response.json();
        return data.choices[0]?.message?.content || 'No response received';
    }

    addMessage(role, content) {
        const session = this.getCurrentSession();
        if (session) {
            session.messages.push({ role, content });
            session.updatedAt = Date.now();
            this.saveSessions();
        }

        this.addMessageToDOM(role, content);
        this.updateContextIndicator();
        this.scrollToBottom();
    }

    addMessageToDOM(role, content) {
        if (!this.messagesContainer) return;

        const messageDiv = document.createElement('div');
        messageDiv.className = `ai-message ${role}`;

        const avatarSvg = role === 'assistant'
            ? `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/>
                <path d="M8 14C8 14 9.5 16 12 16C14.5 16 16 14 16 14" stroke-linecap="round"/>
                <circle cx="9" cy="9" r="1" fill="currentColor"/>
                <circle cx="15" cy="9" r="1" fill="currentColor"/>
              </svg>`
            : `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
                <circle cx="12" cy="7" r="4"></circle>
              </svg>`;

        messageDiv.innerHTML = `
            <div class="ai-message-avatar">${avatarSvg}</div>
            <div class="ai-message-content">${this.parseMarkdown(content)}</div>
        `;

        this.messagesContainer.appendChild(messageDiv);
    }

    parseMarkdown(text) {
        let html = text
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/```(\w*)\n([\s\S]*?)```/g, '<pre><code>$2</code></pre>')
            .replace(/`([^`]+)`/g, '<code>$1</code>')
            .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
            .replace(/\*([^*]+)\*/g, '<em>$1</em>')
            .replace(/^### (.+)$/gm, '<h3>$1</h3>')
            .replace(/^## (.+)$/gm, '<h2>$1</h2>')
            .replace(/^# (.+)$/gm, '<h1>$1</h1>')
            .replace(/^> (.+)$/gm, '<blockquote>$1</blockquote>')
            .replace(/^- (.+)$/gm, '<li>$1</li>')
            .replace(/^\d+\. (.+)$/gm, '<li>$1</li>')
            .replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank">$1</a>')
            .replace(/\n/g, '<br>');

        html = html.replace(/(<li>.*?<\/li>)(<br>)?/g, '$1');
        html = html.replace(/(<li>[\s\S]*?<\/li>)+/g, '<ul>$&</ul>');

        return html;
    }

    showTypingIndicator() {
        if (!this.messagesContainer) return;

        const indicator = document.createElement('div');
        indicator.className = 'ai-typing-indicator';
        indicator.id = 'ai-typing';
        indicator.innerHTML = `
            <div class="ai-message-avatar">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <circle cx="12" cy="12" r="10"/>
                    <path d="M8 14C8 14 9.5 16 12 16C14.5 16 16 14 16 14" stroke-linecap="round"/>
                    <circle cx="9" cy="9" r="1" fill="currentColor"/>
                    <circle cx="15" cy="9" r="1" fill="currentColor"/>
                </svg>
            </div>
            <div class="ai-typing-dots">
                <span></span><span></span><span></span>
            </div>
        `;
        this.messagesContainer.appendChild(indicator);
        this.scrollToBottom();
    }

    removeTypingIndicator() {
        document.getElementById('ai-typing')?.remove();
    }

    showError(message) {
        if (!this.messagesContainer) return;

        const errorDiv = document.createElement('div');
        errorDiv.className = 'ai-error-message';
        errorDiv.innerHTML = `
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"></circle>
                <line x1="12" y1="8" x2="12" y2="12"></line>
                <line x1="12" y1="16" x2="12.01" y2="16"></line>
            </svg>
            <span>${this.escapeHtml(message)}</span>
        `;
        this.messagesContainer.appendChild(errorDiv);
        this.scrollToBottom();
        setTimeout(() => errorDiv.remove(), 5000);
    }

    scrollToBottom() {
        if (this.messagesContainer) {
            this.messagesContainer.scrollTop = this.messagesContainer.scrollHeight;
        }
    }

    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
}

// Export to window
window.AISidebar = AISidebar;
