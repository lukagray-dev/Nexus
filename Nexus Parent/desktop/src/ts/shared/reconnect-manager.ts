/**
 * @file reconnect-manager.ts
 * @description Automatic reconnection manager and backoff scheduler for Nexus Parent Desktop.
 *
 * Hey friend! Here is how our automated reconnection mechanism works in plain English:
 * 1. When a parent is actively connected to their child's phone and the connection is
 *    suddenly lost (e.g. WiFi hiccup, mobile network blip, app sleep), WebRTC drops.
 * 2. Instead of abandoning the parent on a blank screen, this manager steps in immediately!
 * 3. It tries up to 9 times to reconnect to the target child device.
 * 4. Each retry has an increased delay (10s, 30s, 60s, 90s, 120s, 150s, 180s, 210s, 240s).
 * 5. While waiting, a 1-second countdown ticks down (e.g. "reconnecting in 10 seconds"... 9s... 8s).
 * 6. The parent can see the attempt indicator right in the top button of the left sidebar.
 * 7. Clicking that button opens a popover showing the Device ID, attempt progress, and a Cancel button.
 * 8. Clicking "Cancel" stops all attempts immediately and returns the user to the pairing screen.
 * 9. If reconnection succeeds on any attempt, normal connected operation resumes automatically!
 */

import { showToast } from './toast.js';

/**
 * Maximum number of reconnection retries before giving up and returning to pairing screen.
 */
export const MAX_RECONNECT_ATTEMPTS = 9;

/**
 * Increased delay schedule in seconds for each of the 9 retry attempts.
 * Attempt 1: 10s delay
 * Attempt 2: 30s delay
 * Attempt 3: 60s delay
 * Attempt 4: 90s delay
 * Attempt 5: 120s delay
 * Attempt 6: 150s delay
 * Attempt 7: 180s delay
 * Attempt 8: 210s delay
 * Attempt 9: 240s delay
 */
export const DEFAULT_RECONNECT_DELAYS_SECONDS: readonly number[] = [
  10,  // Retry 1: 10 seconds
  30,  // Retry 2: 30 seconds
  60,  // Retry 3: 60 seconds
  90,  // Retry 4: 90 seconds
  120, // Retry 5: 120 seconds
  150, // Retry 6: 150 seconds
  180, // Retry 7: 180 seconds
  210, // Retry 8: 210 seconds
  240  // Retry 9: 240 seconds
];

/**
 * Target device connection metadata preserved during auto-reconnection attempts.
 */
export interface ReconnectTargetDevice {
  /** Raw unformatted 12-digit numeric identifier, e.g. "482194021104" */
  rawId: string;
  /** Visually chunked 12-digit identifier, e.g. "4821 9402 1104" */
  formattedId: string;
  /** Friendly device name, e.g. "Pixel 8 Pro" or "Lucas Galaxy S24" */
  name?: string;
  /** Hardware model identifier, e.g. "Google Pixel 8 Pro (Android 14)" */
  model?: string;
}

/**
 * Reconnection state machine lifecycle statuses:
 * - 'IDLE': No reconnection in progress.
 * - 'WAITING': In backoff delay period, counting down seconds to next attempt.
 * - 'CONNECTING': Handshake with child phone actively running.
 */
export type ReconnectStatus = 'IDLE' | 'WAITING' | 'CONNECTING';

/**
 * Full state snapshot of the Reconnection Manager.
 */
export interface ReconnectState {
  status: ReconnectStatus;
  isActive: boolean;
  currentAttempt: number;
  maxAttempts: number;
  secondsRemaining: number;
  totalDelaySeconds: number;
  targetDevice: ReconnectTargetDevice | null;
  lastError: string | null;
}

export type ReconnectListener = () => void;
export type ConnectHandler = (rawId: string, onProgress?: (statusText: string) => void) => Promise<boolean>;

/**
 * ReconnectManager coordinates the full lifecycle of automated reconnection attempts.
 */
export class ReconnectManager {
  private status: ReconnectStatus = 'IDLE';
  private isActive: boolean = false;
  private currentAttempt: number = 0;
  private maxAttempts: number = MAX_RECONNECT_ATTEMPTS;
  private delays: readonly number[] = DEFAULT_RECONNECT_DELAYS_SECONDS;
  private secondsRemaining: number = 0;
  private totalDelaySeconds: number = 0;
  private targetDevice: ReconnectTargetDevice | null = null;
  private lastError: string | null = null;

  // Active timers and cancellation flag
  private countdownTimer: ReturnType<typeof setInterval> | null = null;
  private isCancelled: boolean = false;

  // Connector delegate function injected by DeviceStateManager
  private connectHandler: ConnectHandler | null = null;

  // Event subscribers
  private listeners: Set<ReconnectListener> = new Set();
  private cancelCallbacks: Set<() => void> = new Set();
  private exhaustedCallbacks: Set<(target: ReconnectTargetDevice | null) => void> = new Set();

  /**
   * Constructs the ReconnectManager with optional custom configuration for unit testing.
   *
   * @param config Optional custom delays and max attempts override
   */
  constructor(config?: { delaysSeconds?: readonly number[]; maxAttempts?: number }) {
    if (config?.delaysSeconds) {
      this.delays = config.delaysSeconds;
    }
    if (config?.maxAttempts !== undefined) {
      this.maxAttempts = config.maxAttempts;
    }
  }

  /**
   * Injects the connection handler responsible for performing the real WebRTC connection.
   *
   * @param handler Connect delegate function
   */
  public setConnectHandler(handler: ConnectHandler): void {
    this.connectHandler = handler;
  }

  /**
   * Initiates the automatic reconnection sequence for a target child device.
   *
   * @param target The target child device metadata
   */
  public start(target: ReconnectTargetDevice): void {
    // Prevent duplicate concurrent reconnection sequences
    if (this.isActive && this.targetDevice?.rawId === target.rawId) {
      console.log('[ReconnectManager] Reconnection already in progress for device:', target.rawId);
      return;
    }

    // Clean up any existing timers
    this.clearTimer();

    console.log('[ReconnectManager] Starting reconnection sequence for device:', target.formattedId);
    this.isCancelled = false;
    this.isActive = true;
    this.targetDevice = target;
    this.currentAttempt = 1;
    this.totalDelaySeconds = this.delays[0] ?? 10;
    this.secondsRemaining = this.totalDelaySeconds;
    this.status = 'WAITING';
    this.lastError = null;

    // Start 1-second interval for countdown timer
    this.startCountdown();
    this.notify();
  }

  /**
   * Starts the 1-second interval timer that decrements secondsRemaining.
   */
  private startCountdown(): void {
    this.clearTimer();
    this.countdownTimer = setInterval(() => {
      this.tick();
    }, 1000);
  }

  /**
   * Ticks down the countdown timer by 1 second.
   * When secondsRemaining reaches 0, triggers the connection attempt!
   */
  public tick(): void {
    if (this.status !== 'WAITING' || !this.isActive) return;

    if (this.secondsRemaining > 1) {
      this.secondsRemaining--;
      this.notify();
    } else {
      // Countdown reached 0: time to trigger this retry attempt!
      this.secondsRemaining = 0;
      this.clearTimer();
      void this.executeAttempt();
    }
  }

  /**
   * Executes an active reconnection attempt using the injected connectHandler.
   */
  public async executeAttempt(): Promise<boolean> {
    if (!this.isActive || this.isCancelled || !this.targetDevice) {
      return false;
    }

    this.status = 'CONNECTING';
    console.log(`[ReconnectManager] Executing reconnection attempt ${this.currentAttempt} of ${this.maxAttempts}...`);
    this.notify();

    if (!this.connectHandler) {
      console.warn('[ReconnectManager] No connectHandler registered. Simulating failure.');
      this.handleAttemptFailure('No connectHandler available');
      return false;
    }

    try {
      const succeeded = await this.connectHandler(this.targetDevice.rawId);

      // Check if user clicked cancel during the asynchronous handshake
      if (this.isCancelled) {
        console.log('[ReconnectManager] Attempt completed after cancellation. Ignoring result.');
        return false;
      }

      if (succeeded) {
        console.log(`[ReconnectManager] Reconnection SUCCEEDED on attempt ${this.currentAttempt}!`);
        this.handleSuccess();
        return true;
      } else {
        this.handleAttemptFailure('Connection attempt returned false');
        return false;
      }
    } catch (err: unknown) {
      if (this.isCancelled) return false;
      const errorMsg = err instanceof Error ? err.message : 'Connection failed';
      console.warn(`[ReconnectManager] Attempt ${this.currentAttempt} failed with error:`, errorMsg);
      this.handleAttemptFailure(errorMsg);
      return false;
    }
  }

  /**
   * Handles failure of a single reconnection attempt:
   * - If more attempts remain: schedules the next attempt with an increased delay.
   * - If all 9 attempts are exhausted: transitions to idle and notifies failure.
   */
  private handleAttemptFailure(errorMsg: string): void {
    this.lastError = errorMsg;

    if (this.currentAttempt < this.maxAttempts) {
      // Advance to next attempt
      this.currentAttempt++;
      const delayIndex = Math.min(this.currentAttempt - 1, this.delays.length - 1);
      this.totalDelaySeconds = this.delays[delayIndex];
      this.secondsRemaining = this.totalDelaySeconds;
      this.status = 'WAITING';

      console.log(`[ReconnectManager] Scheduling attempt ${this.currentAttempt} of ${this.maxAttempts} in ${this.totalDelaySeconds} seconds.`);
      this.startCountdown();
      this.notify();
    } else {
      // All 9 attempts exhausted!
      console.log(`[ReconnectManager] Exhausted all ${this.maxAttempts} reconnection attempts.`);
      const failedTarget = this.targetDevice;
      this.clearTimer();
      this.status = 'IDLE';
      this.isActive = false;
      this.currentAttempt = 0;
      this.secondsRemaining = 0;
      this.targetDevice = null;
      this.notify();

      // Trigger exhaustion callbacks
      for (const callback of this.exhaustedCallbacks) {
        try {
          callback(failedTarget);
        } catch (cbErr) {
          console.error('[ReconnectManager] Error in exhausted callback:', cbErr);
        }
      }

      showToast('Reconnection failed after 9 attempts. Please ensure child device is online.');
    }
  }

  /**
   * Immediately skips the remaining countdown timer and executes the retry right now!
   */
  public async retryNow(): Promise<boolean> {
    if (!this.isActive || this.status === 'CONNECTING' || !this.targetDevice) {
      return false;
    }
    console.log('[ReconnectManager] Immediate retry requested by user.');
    this.clearTimer();
    this.secondsRemaining = 0;
    return await this.executeAttempt();
  }

  /**
   * Cancels all reconnection attempts and resets to idle.
   * Hey friend! This is called when the parent clicks "Cancel Reconnection" from the
   * top button dropdown or the reconnecting view.
   */
  public cancel(): void {
    if (!this.isActive && this.status === 'IDLE') return;

    console.log('[ReconnectManager] Reconnection sequence cancelled by user.');
    this.isCancelled = true;
    this.clearTimer();
    this.status = 'IDLE';
    this.isActive = false;
    this.currentAttempt = 0;
    this.secondsRemaining = 0;
    this.targetDevice = null;
    this.lastError = null;

    // Trigger cancel callbacks so UI and deviceState can transition to empty state
    for (const callback of this.cancelCallbacks) {
      try {
        callback();
      } catch (cbErr) {
        console.error('[ReconnectManager] Error in cancel callback:', cbErr);
      }
    }

    this.notify();

    showToast('Reconnection cancelled.');
  }

  /**
   * Called when a reconnection attempt succeeds or an external connection is established.
   */
  public handleSuccess(): void {
    if (!this.isActive && this.status === 'IDLE') return;

    console.log('[ReconnectManager] Reconnection successfully completed.');
    this.clearTimer();
    const connectedDevice = this.targetDevice;
    this.status = 'IDLE';
    this.isActive = false;
    this.currentAttempt = 0;
    this.secondsRemaining = 0;
    this.targetDevice = null;
    this.lastError = null;

    this.notify();

    if (connectedDevice) {
      const devName = connectedDevice.name || connectedDevice.formattedId;
      showToast(`Reconnected to ${devName} successfully.`);
    }
  }

  /**
   * Returns whether a reconnection sequence is actively ongoing.
   */
  public isReconnecting(): boolean {
    return this.isActive && (this.status === 'WAITING' || this.status === 'CONNECTING');
  }

  /**
   * Returns a snapshot of the current reconnection state.
   */
  public getState(): ReconnectState {
    return {
      status: this.status,
      isActive: this.isActive,
      currentAttempt: this.currentAttempt,
      maxAttempts: this.maxAttempts,
      secondsRemaining: this.secondsRemaining,
      totalDelaySeconds: this.totalDelaySeconds,
      targetDevice: this.targetDevice,
      lastError: this.lastError
    };
  }

  /**
   * Returns target device metadata if reconnecting, or null.
   */
  public getTargetDevice(): ReconnectTargetDevice | null {
    return this.targetDevice;
  }

  /**
   * Subscribes a listener to reconnection state updates (e.g. countdown ticks, status changes).
   *
   * @param listener Callback function
   * @returns Unsubscribe function
   */
  public subscribe(listener: ReconnectListener): () => void {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  }

  /**
   * Registers a callback triggered when reconnection is explicitly cancelled.
   */
  public onCancel(callback: () => void): () => void {
    this.cancelCallbacks.add(callback);
    return () => {
      this.cancelCallbacks.delete(callback);
    };
  }

  /**
   * Registers a callback triggered when all 9 reconnection attempts fail.
   */
  public onExhausted(callback: (target: ReconnectTargetDevice | null) => void): () => void {
    this.exhaustedCallbacks.add(callback);
    return () => {
      this.exhaustedCallbacks.delete(callback);
    };
  }

  /**
   * Cleans up the countdown interval timer safely.
   */
  private clearTimer(): void {
    if (this.countdownTimer) {
      clearInterval(this.countdownTimer);
      this.countdownTimer = null;
    }
  }

  /**
   * Notifies all subscribers of a state change.
   */
  private notify(): void {
    for (const listener of this.listeners) {
      try {
        listener();
      } catch (err) {
        console.error('[ReconnectManager] Error in state listener:', err);
      }
    }
  }

  /**
   * Configures custom delays and attempt limits (useful for testing).
   */
  public configure(config: { delaysSeconds?: readonly number[]; maxAttempts?: number }): void {
    if (config.delaysSeconds) this.delays = config.delaysSeconds;
    if (config.maxAttempts !== undefined) this.maxAttempts = config.maxAttempts;
  }

  /**
   * Resets all internal state and timers for fresh testing isolation.
   */
  public resetForTesting(): void {
    this.clearTimer();
    this.status = 'IDLE';
    this.isActive = false;
    this.currentAttempt = 0;
    this.secondsRemaining = 0;
    this.totalDelaySeconds = 0;
    this.targetDevice = null;
    this.lastError = null;
    this.isCancelled = false;
    this.delays = DEFAULT_RECONNECT_DELAYS_SECONDS;
    this.maxAttempts = MAX_RECONNECT_ATTEMPTS;
    this.listeners.clear();
    this.cancelCallbacks.clear();
    this.exhaustedCallbacks.clear();
  }
}

/**
 * Singleton instance of ReconnectManager shared across Nexus Parent.
 */
export const reconnectManager = new ReconnectManager();
