/**
 * @file ai.ts
 * @description AI Apps Activity Monitor Panel component for Guardian G-Home Right Sidebar.
 *
 * Hey friend! This panel displays intercepted AI prompt queries and chatbot
 * interactions (ChatGPT, Claude, Gemini) submitted by the child.
 */

import type { AiPromptItem } from '../types.js';

const INITIAL_AI_PROMPTS: AiPromptItem[] = [
  { ai: 'ChatGPT', prompt: 'Explain the difference between mitosis and meiosis simply for 9th grade', time: '10:45 AM' },
  { ai: 'Claude', prompt: 'Write an outline for an essay on renewable energy adoption challenges', time: 'Yesterday' },
  { ai: 'Gemini', prompt: 'Solve this equation: 3x^2 - 12x + 9 = 0 step by step', time: 'Nov 09' }
];

/**
 * Creates and returns the AI Apps Activity panel element.
 */
export function renderAiPanel(): HTMLElement {
  const container = document.createElement('div');
  container.className = 'rs-body rs-ai-panel';

  container.innerHTML = `
    <!-- Header Subbar -->
    <div class="rs-subbar">
      <div class="ai-telemetry-text">
        AI PROMPTS & BOT ACTIVITY AUDIT
      </div>
    </div>

    <!-- Prompts List -->
    <div id="ai-list" class="rs-list-container">
      ${INITIAL_AI_PROMPTS.map(p => `
        <div class="rs-list-item ai-item">
          <div class="rs-item-top">
            <span class="rs-badge-small ai-model-badge">${p.ai}</span>
            <span class="rs-item-time">${p.time}</span>
          </div>
          <div class="rs-item-title ai-prompt-title">"${p.prompt}"</div>
          <div class="rs-item-meta">
            <span>Interactive Chat Session</span>
          </div>
        </div>
      `).join('')}
    </div>
  `;

  return container;
}


export const renderClaudePanel = renderAiPanel;

