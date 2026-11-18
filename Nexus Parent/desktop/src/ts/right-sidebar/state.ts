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
  SmsRecord
} from './types.js';

class RightSidebarStateManager {
  private activePanelId: RightSidebarPanelId | null = null;
  private width: number = 380;
  private listeners: Set<RightSidebarListener> = new Set();

  // Telemetry Monitoring States
  private callLogMonitoringState: MonitoringState = 'IDLE';
  private smsMonitoringState: MonitoringState = 'IDLE';

  // In-Memory Telemetry Data Stores
  private callLogs: CallRecord[] = [];
  private smsMessages: SmsRecord[] = [];

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
