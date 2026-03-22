/**
 * Production-Grade Chats Feature Module
 * Displays real-time chat monitoring with conversation threading and message history
 */

// Global storage for Chat data
if (!window.chatFeatureData) {
    window.chatFeatureData = {
        isActive: false,
        conversations: new Map(), // conversationId -> Conversation object
        conversationList: []      // Sorted list for display
    };
}

class ChatsFeature {
    constructor(container) {
        this.container = container;
        this.confirmationTimeout = null;
        this.data = window.chatFeatureData;
        this.selectedConversationId = null;
        
        this.init();
    }

    async init() {
        const html = await this.loadTemplate();
        this.container.innerHTML = html;

        await this.loadStyles();
        this.setupEventListeners();
        this.listenForChats();

        if (this.data.isActive) {
            this.updateButton(true);
        }
        
        if (this.data.conversationList.length > 0) {
            this.renderConversationList();
        }
    }

    async loadTemplate() {
        try {
            const response = await fetch('js/features/chats/chats.html');
            return await response.text();
        } catch (error) {
            console.error('Failed to load Chats template:', error);
            return '<div>Failed to load Chats feature</div>';
        }
    }

    async loadStyles() {
        try {
            const link = document.createElement('link');
            link.rel = 'stylesheet';
            link.href = 'js/features/chats/chats.css';
            document.head.appendChild(link);
        } catch (error) {
            console.error('Failed to load Chats styles:', error);
        }
    }

    setupEventListeners() {
        const searchInput = this.container.querySelector('#chats-search');
        const playPauseBtn = this.container.querySelector('#chats-play-pause-btn');
        const backBtn = this.container.querySelector('#chats-back-btn');

        if (searchInput) {
            searchInput.addEventListener('input', (e) => this.filterConversations(e.target.value));
        }

        if (playPauseBtn) {
            playPauseBtn.addEventListener('click', () => this.togglePlayPause());
        }
        
        if (backBtn) {
            backBtn.addEventListener('click', () => this.showConversationList());
        }
    }

    listenForChats() {
        window.addEventListener('chat-update', (event) => {
            const payload = event.detail;
            console.log('💬 Chat update received:', payload.type, payload.data);
            
            switch (payload.type) {
                case 'CONVERSATION_UPDATE':
                case 'APP_OPENED':
                    this.handleConversationUpdate(payload);
                    break;
                case 'TYPING_INDICATOR':
                    this.handleTypingIndicator(payload);
                    break;
            }
        });

        window.addEventListener('chat-confirmation', (event) => {
            const message = event.detail.message;
            console.log('💬 [Chat] Received confirmation:', message);
            
            if (message === 'CHAT_STARTED') {
                this.clearConfirmationTimeout();
                this.data.isActive = true;
                this.updateButton(true);
                this.hideLoading();
            } else if (message === 'CHAT_STOPPED') {
                this.clearConfirmationTimeout();
                this.data.isActive = false;
                this.updateButton(false);
                this.hideLoading();
            }
        });
    }

    handleConversationUpdate(payload) {
        const { data } = payload;
        const conversationId = data.conversationId;
        
        // Get or create conversation
        let conversation = this.data.conversations.get(conversationId);
        if (!conversation) {
            conversation = {
                id: conversationId,
                app: data.app,
                appName: data.appName,
                contactName: data.contactName || 'Unknown',
                messages: [],
                isTyping: false,
                lastActivityTs: data.conversationMeta?.lastActivityTs || Date.now(),
                unreadCount: 0
            };
            this.data.conversations.set(conversationId, conversation);
        }
        
        // Update conversation metadata
        conversation.contactName = data.contactName || conversation.contactName;
        conversation.lastActivityTs = data.conversationMeta?.lastActivityTs || Date.now();
        
        // Add new messages (deduplicate by msgId)
        if (data.messages && data.messages.length > 0) {
            const existingIds = new Set(conversation.messages.map(m => m.msgId));
            const newMessages = data.messages.filter(m => !existingIds.has(m.msgId));
            
            conversation.messages.push(...newMessages);
            
            // Sort by timestamp
            conversation.messages.sort((a, b) => a.ts - b.ts);
            
            // Limit to last 200 messages per conversation
            if (conversation.messages.length > 200) {
                conversation.messages = conversation.messages.slice(-200);
            }
            
            // Increment unread count if not viewing this conversation
            if (this.selectedConversationId !== conversationId) {
                conversation.unreadCount += newMessages.length;
            }
        }
        
        // Update conversation list (sorted by last activity)
        this.updateConversationList();
        
        // Render appropriate view
        if (this.selectedConversationId === conversationId) {
            this.renderConversationDetail(conversationId);
        } else {
            this.renderConversationList();
        }
    }

    handleTypingIndicator(payload) {
        const { data } = payload;
        const conversationId = data.conversationId;
        
        const conversation = this.data.conversations.get(conversationId);
        if (conversation) {
            conversation.isTyping = data.isTyping || false;
            
            // Update UI if viewing this conversation
            if (this.selectedConversationId === conversationId) {
                this.updateTypingIndicator(conversation.isTyping);
            }
        }
    }

    updateConversationList() {
        this.data.conversationList = Array.from(this.data.conversations.values())
            .sort((a, b) => b.lastActivityTs - a.lastActivityTs);
    }

    filterConversations(query) {
        const lowerQuery = query.toLowerCase();
        const listContainer = this.container.querySelector('#chats-conversation-list');
        if (!listContainer) return;
        
        const items = listContainer.querySelectorAll('.conversation-item');
        items.forEach(item => {
            const contactName = item.dataset.contactName?.toLowerCase() || '';
            const appName = item.dataset.appName?.toLowerCase() || '';
            
            if (contactName.includes(lowerQuery) || appName.includes(lowerQuery)) {
                item.style.display = '';
            } else {
                item.style.display = 'none';
            }
        });
    }

    renderConversationList() {
        const listView = this.container.querySelector('#chats-list-view');
        const detailView = this.container.querySelector('#chats-detail-view');
        const listContainer = this.container.querySelector('#chats-conversation-list');
        
        if (!listView || !detailView || !listContainer) return;
        
        listView.style.display = 'flex';
        detailView.style.display = 'none';
        this.selectedConversationId = null;
        
        // Clear existing items
        listContainer.innerHTML = '';
        
        if (this.data.conversationList.length === 0) {
            listContainer.innerHTML = `
                <div class="list-placeholder">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"></path>
                    </svg>
                    <p>No conversations yet</p>
                    <p class="hint">Start monitoring to see chats</p>
                </div>
            `;
            return;
        }
        
        // Render conversation items
        this.data.conversationList.forEach(conversation => {
            const item = this.createConversationItem(conversation);
            listContainer.appendChild(item);
        });
    }

    createConversationItem(conversation) {
        const div = document.createElement('div');
        div.className = 'conversation-item';
        div.dataset.conversationId = conversation.id;
        div.dataset.contactName = conversation.contactName;
        div.dataset.appName = conversation.appName;
        
        const lastMessage = conversation.messages[conversation.messages.length - 1];
        const lastMessageText = lastMessage ? (lastMessage.text || '').substring(0, 60) : 'No messages';
        const time = this.formatTime(conversation.lastActivityTs);
        
        div.innerHTML = `
            <div class="conversation-header">
                <div class="conversation-app">${this.escapeHtml(conversation.appName)}</div>
                <div class="conversation-time">${time}</div>
            </div>
            <div class="conversation-contact">${this.escapeHtml(conversation.contactName)}</div>
            <div class="conversation-preview">
                ${lastMessage?.direction === 'OUT' ? '<span class="you-label">You: </span>' : ''}
                ${this.escapeHtml(lastMessageText)}
            </div>
            ${conversation.unreadCount > 0 ? `<div class="unread-badge">${conversation.unreadCount}</div>` : ''}
        `;
        
        div.addEventListener('click', () => this.showConversationDetail(conversation.id));
        
        return div;
    }

    showConversationDetail(conversationId) {
        const conversation = this.data.conversations.get(conversationId);
        if (!conversation) return;
        
        this.selectedConversationId = conversationId;
        conversation.unreadCount = 0; // Clear unread count
        
        const listView = this.container.querySelector('#chats-list-view');
        const detailView = this.container.querySelector('#chats-detail-view');
        
        if (listView && detailView) {
            listView.style.display = 'none';
            detailView.style.display = 'flex';
        }
        
        this.renderConversationDetail(conversationId);
    }

    showConversationList() {
        this.renderConversationList();
    }

    renderConversationDetail(conversationId) {
        const conversation = this.data.conversations.get(conversationId);
        if (!conversation) return;
        
        const contactNameEl = this.container.querySelector('#chats-detail-contact-name');
        const appNameEl = this.container.querySelector('#chats-detail-app-name');
        const messagesContainer = this.container.querySelector('#chats-messages-container');
        
        if (contactNameEl) contactNameEl.textContent = conversation.contactName;
        if (appNameEl) appNameEl.textContent = conversation.appName;
        
        if (messagesContainer) {
            messagesContainer.innerHTML = '';
            
            if (conversation.messages.length === 0) {
                messagesContainer.innerHTML = `
                    <div class="messages-placeholder">
                        <p>No messages yet</p>
                    </div>
                `;
                return;
            }
            
            // Group messages by date
            const messagesByDate = this.groupMessagesByDate(conversation.messages);
            
            messagesByDate.forEach(({ date, messages }) => {
                // Date separator
                const dateSeparator = document.createElement('div');
                dateSeparator.className = 'date-separator';
                dateSeparator.textContent = date;
                messagesContainer.appendChild(dateSeparator);
                
                // Messages
                messages.forEach(message => {
                    const messageEl = this.createMessageElement(message);
                    messagesContainer.appendChild(messageEl);
                });
            });
            
            // Scroll to bottom
            messagesContainer.scrollTop = messagesContainer.scrollHeight;
        }
        
        this.updateTypingIndicator(conversation.isTyping);
    }

    groupMessagesByDate(messages) {
        const groups = [];
        let currentDate = null;
        let currentMessages = [];
        
        messages.forEach(message => {
            const date = this.formatDate(message.ts);
            
            if (date !== currentDate) {
                if (currentMessages.length > 0) {
                    groups.push({ date: currentDate, messages: currentMessages });
                }
                currentDate = date;
                currentMessages = [];
            }
            
            currentMessages.push(message);
        });
        
        if (currentMessages.length > 0) {
            groups.push({ date: currentDate, messages: currentMessages });
        }
        
        return groups;
    }

    createMessageElement(message) {
        const div = document.createElement('div');
        div.className = `message-bubble ${message.direction.toLowerCase()}`;
        
        const time = new Date(message.ts).toLocaleTimeString('en-US', { 
            hour: '2-digit', 
            minute: '2-digit' 
        });
        
        let content = `<div class="message-text">${this.escapeHtml(message.text || '')}</div>`;
        
        if (message.sender) {
            content = `<div class="message-sender">${this.escapeHtml(message.sender)}</div>` + content;
        }
        
        content += `<div class="message-time">${time}</div>`;
        
        div.innerHTML = content;
        
        return div;
    }

    updateTypingIndicator(isTyping) {
        const indicator = this.container.querySelector('#chats-typing-indicator');
        if (indicator) {
            indicator.style.display = isTyping ? 'flex' : 'none';
        }
    }

    togglePlayPause() {
        if (this.data.isActive) {
            this.stopMonitoring();
        } else {
            this.startMonitoring();
        }
    }

    startMonitoring() {
        console.log('💬 Starting chat monitoring');

        try {
            const app = window.nexusApp;
            if (!app || !app.connectionManager) {
                throw new Error('Connection manager not available');
            }

            this.clearConfirmationTimeout();
            this.showLoading();

            app.connectionManager.sendCommand('CHAT_ON');

            console.log('✅ Chat monitoring command sent');

            this.confirmationTimeout = setTimeout(() => {
                if (this.container.querySelector('.loading-spinner')) {
                    console.warn('⚠️ [Chat] Confirmation timeout');
                    this.hideLoading();
                    this.showError('Chat monitoring start timeout');
                }
            }, 30000);

        } catch (error) {
            console.error('❌ Failed to start chat monitoring:', error);
            this.hideLoading();
            this.showError('Failed to start: ' + error.message);
        }
    }

    stopMonitoring() {
        console.log('💬 Stopping chat monitoring');

        try {
            const app = window.nexusApp;
            if (!app || !app.connectionManager) {
                throw new Error('Connection manager not available');
            }

            this.clearConfirmationTimeout();
            this.showLoading();

            app.connectionManager.sendCommand('CHAT_OFF');

            console.log('✅ Chat monitoring stop command sent');

            this.confirmationTimeout = setTimeout(() => {
                if (this.container.querySelector('.loading-spinner')) {
                    console.warn('⚠️ [Chat] Confirmation timeout');
                    this.hideLoading();
                    this.showError('Chat monitoring stop timeout');
                }
            }, 30000);

        } catch (error) {
            console.error('❌ Failed to stop chat monitoring:', error);
            this.hideLoading();
        }
    }

    formatTime(timestamp) {
        const date = new Date(timestamp);
        const now = new Date();
        const diff = now - date;

        if (diff < 60000) return 'Just now';
        if (diff < 3600000) return `${Math.floor(diff / 60000)}m ago`;
        if (diff < 86400000) return `${Math.floor(diff / 3600000)}h ago`;
        
        return date.toLocaleDateString();
    }

    formatDate(timestamp) {
        const date = new Date(timestamp);
        const today = new Date();
        const yesterday = new Date(today);
        yesterday.setDate(yesterday.getDate() - 1);
        
        if (date.toDateString() === today.toDateString()) {
            return 'Today';
        } else if (date.toDateString() === yesterday.toDateString()) {
            return 'Yesterday';
        } else {
            return date.toLocaleDateString('en-US', { 
                month: 'short', 
                day: 'numeric', 
                year: date.getFullYear() !== today.getFullYear() ? 'numeric' : undefined 
            });
        }
    }

    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    clearConfirmationTimeout() {
        if (this.confirmationTimeout) {
            clearTimeout(this.confirmationTimeout);
            this.confirmationTimeout = null;
        }
    }

    showLoading() {
        const playPauseBtn = this.container.querySelector('#chats-play-pause-btn');
        if (playPauseBtn) {
            playPauseBtn.innerHTML = `
                <svg class="loading-spinner" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <circle cx="12" cy="12" r="10" opacity="0.25"></circle>
                    <path d="M12 2 A10 10 0 0 1 22 12" opacity="0.75">
                        <animateTransform
                            attributeName="transform"
                            type="rotate"
                            from="0 12 12"
                            to="360 12 12"
                            dur="1s"
                            repeatCount="indefinite"/>
                    </path>
                </svg>
            `;
            playPauseBtn.disabled = true;
        }
    }

    hideLoading() {
        const playPauseBtn = this.container.querySelector('#chats-play-pause-btn');
        if (playPauseBtn) {
            playPauseBtn.disabled = false;
            
            if (this.data.isActive) {
                playPauseBtn.innerHTML = `
                    <svg class="pause-icon" viewBox="0 0 24 24" fill="currentColor">
                        <rect x="6" y="4" width="4" height="16"></rect>
                        <rect x="14" y="4" width="4" height="16"></rect>
                    </svg>
                `;
            } else {
                playPauseBtn.innerHTML = `
                    <svg class="play-icon" viewBox="0 0 24 24" fill="currentColor">
                        <polygon points="5 3 19 12 5 21 5 3"></polygon>
                    </svg>
                `;
            }
        }
    }

    updateButton(isActive) {
        this.hideLoading();
    }

    showError(message) {
        console.error('Chat error:', message);
        // TODO: Show toast notification
    }

    destroy() {
        if (this.data.isActive) {
            this.stopMonitoring();
        }
    }
}

window.ChatsFeature = ChatsFeature;
