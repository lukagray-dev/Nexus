/**
 * @file state.ts
 * @description State management for Nexus Parent Right Sidebar.
 *
 * Hey friend! This manages the active inspection and control panel open in the right sidebar.
 * It coordinates panel visibility, width resizing, monitoring lifecycle states
 * (IDLE -> STARTING -> LIVE -> STOPPING), and stores in-memory call logs and SMS text feeds!
 */

import type {
  RightSidebarPanelId,
  RightSidebarListener,
  MonitoringState,
  CallRecord,
  SmsRecord,
  NotificationRecord,
  WallpaperTarget,
  WallpaperDataState,
  WallpaperChunkPayload,
  VibrateFlashStatusState
} from './types.js';

class RightSidebarStateManager {
  private activePanelId: RightSidebarPanelId | null = null;
  private width: number = 380;
  private listeners: Set<RightSidebarListener> = new Set();

  // Telemetry Monitoring States
  private callLogMonitoringState: MonitoringState = 'IDLE';
  private smsMonitoringState: MonitoringState = 'IDLE';
  private notificationMonitoringState: MonitoringState = 'IDLE';
  private wallpaperMonitoringState: MonitoringState = 'IDLE';

  // In-Memory Telemetry Data Stores
  private callLogs: CallRecord[] = [];
  private smsMessages: SmsRecord[] = [];
  private notifications: NotificationRecord[] = [];

  // Wallpaper Data State
  private wallpaperData: WallpaperDataState = {
    homeUrl: null,
    lockUrl: null,
    isSame: true,
    currentTarget: 'home',
    isFetching: false,
    isPushing: false,
    pushProgress: 0
  };
  private incomingWallpaperChunks: Map<string, { totalChunks: number; chunks: Map<number, string>; isSame: boolean }> = new Map();

  // Vibrate & Flash Status State
  private vibrateFlashStatus: VibrateFlashStatusState = {
    lastAction: null,
    lastSuccess: null,
    statusMessage: null,
    timestamp: 0
  };

  /**
   * Returns whether the right sidebar is currently open.
   */
  public isOpen(): boolean {
    return this.activePanelId !== null;
  }

  /**
   * Returns the currently active panel identifier, or null if closed.
   */
  public getActivePanelId(): RightSidebarPanelId | null {
    return this.activePanelId;
  }

  /**
   * Opens the specified panel in the right sidebar.
   * If the panel is already open, clicking it again toggles it closed!
   */
  public openPanel(panelId: RightSidebarPanelId): void {
    if (this.activePanelId === panelId) {
      this.close();
      return;
    }
    this.activePanelId = panelId;
    this.notify();
  }

  /**
   * Closes the right sidebar.
   */
  public close(): void {
    if (this.activePanelId !== null) {
      this.activePanelId = null;
      this.notify();
    }
  }

  /**
   * Gets the width of the right sidebar.
   */
  public getWidth(): number {
    return this.width;
  }

  /**
   * Sets the right sidebar width when dragged.
   */
  public setWidth(newWidth: number): void {
    this.width = Math.max(300, Math.min(600, newWidth));
  }

  // ---------------------------------------------------------------------------
  // CALL LOG MONITORING & TELEMETRY ACCESSORS
  // ---------------------------------------------------------------------------

  public getCallLogMonitoringState(): MonitoringState {
    return this.callLogMonitoringState;
  }

  public setCallLogMonitoringState(state: MonitoringState): void {
    if (this.callLogMonitoringState !== state) {
      this.callLogMonitoringState = state;
      this.notify();
    }
  }

  public getCallLogs(): CallRecord[] {
    return [...this.callLogs];
  }

  public setCallLogs(records: CallRecord[]): void {
    // Sort descending by timestamp
    this.callLogs = [...records].sort((a, b) => b.timestamp - a.timestamp);
    this.notify();
  }

  public prependCallRecord(record: CallRecord): void {
    // Avoid duplicate insertions
    if (this.callLogs.some(r => r.id === record.id)) return;
    this.callLogs.unshift(record);
    this.notify();
  }

  public clearCallLogs(): void {
    this.callLogs = [];
    this.notify();
  }

  // ---------------------------------------------------------------------------
  // SMS MONITORING & TELEMETRY ACCESSORS
  // ---------------------------------------------------------------------------

  public getSmsMonitoringState(): MonitoringState {
    return this.smsMonitoringState;
  }

  public setSmsMonitoringState(state: MonitoringState): void {
    if (this.smsMonitoringState !== state) {
      this.smsMonitoringState = state;
      this.notify();
    }
  }

  public getSmsMessages(): SmsRecord[] {
    return [...this.smsMessages];
  }

  public setSmsMessages(messages: SmsRecord[]): void {
    this.smsMessages = [...messages].sort((a, b) => b.timestamp - a.timestamp);
    this.notify();
  }

  public appendSmsChunk(messages: SmsRecord[]): void {
    const existingIds = new Set(this.smsMessages.map(m => m.id));
    const newItems = messages.filter(m => !existingIds.has(m.id));
    if (newItems.length > 0) {
      this.smsMessages = [...this.smsMessages, ...newItems].sort((a, b) => b.timestamp - a.timestamp);
      this.notify();
    }
  }

  public prependSmsRecord(record: SmsRecord): void {
    if (this.smsMessages.some(m => m.id === record.id)) return;
    this.smsMessages.unshift(record);
    this.notify();
  }

  public clearSmsMessages(): void {
    this.smsMessages = [];
    this.notify();
  }

  // ---------------------------------------------------------------------------
  // NOTIFICATION MONITORING & TELEMETRY ACCESSORS
  // ---------------------------------------------------------------------------

  public getNotificationMonitoringState(): MonitoringState {
    return this.notificationMonitoringState;
  }

  public setNotificationMonitoringState(state: MonitoringState): void {
    if (this.notificationMonitoringState !== state) {
      this.notificationMonitoringState = state;
      this.notify();
    }
  }

  public getNotifications(): NotificationRecord[] {
    return this.notifications;
  }

  public setNotificationSnapshot(items: NotificationRecord[]): void {
    this.notifications = [...items].sort((a, b) => b.postTime - a.postTime);
    this.notify();
  }

  public prependNotification(record: NotificationRecord): void {
    const filtered = this.notifications.filter(n => n.key !== record.key);
    this.notifications = [record, ...filtered];
    this.notify();
  }

  public removeNotification(key: string): void {
    const prevLen = this.notifications.length;
    this.notifications = this.notifications.filter(n => n.key !== key);
    if (this.notifications.length !== prevLen) {
      this.notify();
    }
  }

  public clearNotifications(): void {
    this.notifications = [];
    this.notify();
  }

  // ---------------------------------------------------------------------------
  // WALLPAPER MONITORING & TELEMETRY ACCESSORS
  // ---------------------------------------------------------------------------

  public getWallpaperMonitoringState(): MonitoringState {
    return this.wallpaperMonitoringState;
  }

  public setWallpaperMonitoringState(state: MonitoringState): void {
    if (this.wallpaperMonitoringState !== state) {
      this.wallpaperMonitoringState = state;
      this.notify();
    }
  }

  public getWallpaperData(): WallpaperDataState {
    return { ...this.wallpaperData };
  }

  public setWallpaperTarget(target: WallpaperTarget): void {
    if (this.wallpaperData.currentTarget !== target) {
      this.wallpaperData.currentTarget = target;
      this.notify();
    }
  }

  public setWallpaperFetching(isFetching: boolean): void {
    this.wallpaperData.isFetching = isFetching;
    this.notify();
  }

  public setWallpaperPushing(isPushing: boolean, progress: number): void {
    this.wallpaperData.isPushing = isPushing;
    this.wallpaperData.pushProgress = progress;
    this.notify();
  }

  public handleIncomingWallpaperChunk(payload: WallpaperChunkPayload): void {
    const { screen, chunkIndex, totalChunks, data, isSame } = payload;
    let screenEntry = this.incomingWallpaperChunks.get(screen);
    if (!screenEntry || screenEntry.totalChunks !== totalChunks) {
      screenEntry = { totalChunks, chunks: new Map(), isSame };
      this.incomingWallpaperChunks.set(screen, screenEntry);
    }

    screenEntry.chunks.set(chunkIndex, data);
    screenEntry.isSame = isSame;

    // Check if all chunks have arrived
    if (screenEntry.chunks.size >= totalChunks && totalChunks > 0) {
      const sortedSlices: string[] = [];
      for (let i = 0; i < totalChunks; i++) {
        sortedSlices.push(screenEntry.chunks.get(i) || '');
      }
      const fullBase64 = sortedSlices.join('');
      const dataUrl = `data:image/jpeg;base64,${fullBase64}`;

      if (screen === 'home') {
        this.wallpaperData.homeUrl = dataUrl;
        if (isSame) {
          this.wallpaperData.lockUrl = dataUrl;
        }
      } else if (screen === 'lock') {
        this.wallpaperData.lockUrl = dataUrl;
      }
      this.wallpaperData.isSame = isSame;
      this.wallpaperData.isFetching = false;
      this.incomingWallpaperChunks.delete(screen);
      this.notify();
    }
  }

  public setPushedWallpaperSuccess(target: WallpaperTarget, previewUrl?: string): void {
    this.wallpaperData.isPushing = false;
    this.wallpaperData.pushProgress = 100;
    if (previewUrl) {
      if (target === 'both') {
        this.wallpaperData.homeUrl = previewUrl;
        this.wallpaperData.lockUrl = previewUrl;
        this.wallpaperData.isSame = true;
      } else if (target === 'home') {
        this.wallpaperData.homeUrl = previewUrl;
      } else if (target === 'lock') {
        this.wallpaperData.lockUrl = previewUrl;
      }
    }
    this.notify();
  }

  // ---------------------------------------------------------------------------
  // VIBRATE & FLASH ACCESSORS
  // ---------------------------------------------------------------------------

  public getVibrateFlashStatus(): VibrateFlashStatusState {
    return { ...this.vibrateFlashStatus };
  }

  public setVibrateFlashStatus(action: string, success: boolean | null, message: string): void {
    this.vibrateFlashStatus = {
      lastAction: action,
      lastSuccess: success,
      statusMessage: message,
      timestamp: Date.now()
    };
    this.notify();
  }

  /**
   * Resets all monitoring states to IDLE when peer connection drops.
   */
  public resetMonitoringStates(): void {
    let changed = false;
    if (this.callLogMonitoringState !== 'IDLE') {
      this.callLogMonitoringState = 'IDLE';
      changed = true;
    }
    if (this.smsMonitoringState !== 'IDLE') {
      this.smsMonitoringState = 'IDLE';
      changed = true;
    }
    if (this.notificationMonitoringState !== 'IDLE') {
      this.notificationMonitoringState = 'IDLE';
      changed = true;
    }
    if (this.wallpaperMonitoringState !== 'IDLE') {
      this.wallpaperMonitoringState = 'IDLE';
      changed = true;
    }
    if (this.wallpaperData.isFetching || this.wallpaperData.isPushing) {
      this.wallpaperData.isFetching = false;
      this.wallpaperData.isPushing = false;
      changed = true;
    }
    this.incomingWallpaperChunks.clear();
    if (changed) {
      this.notify();
    }
  }

  /**
   * Subscribes a callback to state changes.
   */
  public subscribe(listener: RightSidebarListener): () => void {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  }

  private notify(): void {
    for (const listener of this.listeners) {
      try {
        listener();
      } catch (err) {
        console.error('[RightSidebarState] Error in listener callback:', err);
      }
    }
  }
}

export const rightSidebarState = new RightSidebarStateManager();
export type { RightSidebarPanelId };
