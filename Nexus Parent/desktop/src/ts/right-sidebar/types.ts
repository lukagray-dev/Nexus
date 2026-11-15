/**
 * @file types.ts
 * @description Type definitions for Nexus Parent Right Sidebar.
 *
 * Hey friend! This centralizes all panel configurations and item schemas
 * for the right-hand inspection and hardware control panels.
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
 * Metadata configuration for each right sidebar panel.
 */
export interface PanelConfig {
  title: string;
  badge: string;
  render: () => HTMLElement;
}

export interface SmsItem {
  sender: string;
  text: string;
  time: string;
  unread: boolean;
}

export interface CallLogItem {
  name: string;
  number: string;
  type: 'incoming' | 'outgoing' | 'missed';
  duration: string;
  time: string;
}

export interface NotificationItem {
  app: string;
  title: string;
  desc: string;
  time: string;
  pkg: string;
}

export interface KeyloggerItem {
  app: string;
  query: string;
  time: string;
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

export interface AppLockItem {
  name: string;
  pkg: string;
  locked: boolean;
}
