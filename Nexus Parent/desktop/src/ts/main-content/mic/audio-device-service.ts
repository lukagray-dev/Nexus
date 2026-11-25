/**
 * @file audio-device-service.ts
 * @description Hardware audio device enumeration, permission unlocking, and output sink routing
 *              for Nexus Parent Desktop.
 *
 * Hey friend! WebView2 / Chromium exposes PC microphones and speakers through the standard
 * `navigator.mediaDevices` API and `setSinkId()` on HTMLAudioElement / AudioContext:
 * 1. Before microphone permission has been granted, `enumerateDevices()` often hides exact
 *    hardware names (returning empty labels). `hasMaskedLabels` lets the Settings UI show a
 *    non-intrusive "Detect Devices" button only when needed.
 * 2. Clicking "Detect Devices" briefly opens a mic stream via `getUserMedia({ audio: true })`
 *    and immediately stops every track so hardware names unlock without keeping the mic active.
 * 3. `applyAudioOutputDevice()` routes both the Mic monitor `<audio>` element and the Screen
 *    Audio `AudioContext` to the parent's chosen output speaker, falling back to system default
 *    gracefully if an external USB/Bluetooth device is unplugged.
 */

export const EVENT_AUDIO_OUTPUT_CHANGED = 'nexus-audio-output-changed';
export const EVENT_AUDIO_DEVICES_REFRESHED = 'nexus-audio-devices-refreshed';

export interface EnumeratedAudioDevice {
  deviceId: string;
  label: string;
  kind: 'audioinput' | 'audiooutput';
  groupId: string;
}

export interface AudioDeviceSnapshot {
  inputs: EnumeratedAudioDevice[];
  outputs: EnumeratedAudioDevice[];
  hasMaskedLabels: boolean;
  supported: boolean;
}

export interface AudioOutputSinkTarget {
  setSinkId?: (sinkId: string) => Promise<void>;
}

export interface AudioSinkApplyResult {
  appliedDeviceId: string;
  fellBackToDefault: boolean;
  supported: boolean;
}

/**
 * Normalizes a persisted output device setting into a Web Audio / HTMLMediaElement sinkId.
 * Both `'default'` and empty strings map to `''` (the browser's system default output).
 */
export function normalizeOutputSinkId(deviceId: string | null | undefined): string {
  const trimmed = (deviceId ?? '').trim();
  if (!trimmed || trimmed === 'default') {
    return '';
  }
  return trimmed;
}

/**
 * Enumerates available PC audio input (microphone) and output (speaker) devices.
 * Virtual `'default'` and empty-ID entries are excluded from the returned lists because
 * Settings always provides explicit `"System Default Input"` / `"System Default Output"` options.
 */
export async function enumerateAudioDevices(): Promise<AudioDeviceSnapshot> {
  if (
    typeof navigator === 'undefined' ||
    !navigator.mediaDevices ||
    typeof navigator.mediaDevices.enumerateDevices !== 'function'
  ) {
    return {
      inputs: [],
      outputs: [],
      hasMaskedLabels: false,
      supported: false,
    };
  }

  try {
    const rawDevices = await navigator.mediaDevices.enumerateDevices();
    const inputs: EnumeratedAudioDevice[] = [];
    const outputs: EnumeratedAudioDevice[] = [];
    const seenInputIds = new Set<string>();
    const seenOutputIds = new Set<string>();
    let hasMaskedLabels = false;

    for (const device of rawDevices) {
      if (device.kind !== 'audioinput' && device.kind !== 'audiooutput') {
        continue;
      }

      const trimmedLabel = (device.label || '').trim();
      if (!trimmedLabel) {
        // WebView2 masks hardware labels (and sometimes deviceIds) until mic permission is granted.
        hasMaskedLabels = true;
      }

      const deviceId = (device.deviceId || '').trim();
      // Skip empty placeholder entries and raw 'default' virtual alias to avoid duplicate rows.
      if (!deviceId || deviceId === 'default') {
        continue;
      }

      if (device.kind === 'audioinput') {
        if (seenInputIds.has(deviceId)) continue;
        seenInputIds.add(deviceId);
        inputs.push({
          deviceId,
          label: trimmedLabel || `Microphone ${inputs.length + 1}`,
          kind: 'audioinput',
          groupId: device.groupId || '',
        });
      } else {
        if (seenOutputIds.has(deviceId)) continue;
        seenOutputIds.add(deviceId);
        outputs.push({
          deviceId,
          label: trimmedLabel || `Speaker ${outputs.length + 1}`,
          kind: 'audiooutput',
          groupId: device.groupId || '',
        });
      }
    }

    return {
      inputs,
      outputs,
      hasMaskedLabels,
      supported: true,
    };
  } catch (err) {
    console.warn('[AudioDevices] Failed to enumerate media devices:', err);
    return {
      inputs: [],
      outputs: [],
      hasMaskedLabels: false,
      supported: false,
    };
  }
}

/**
 * Requests temporary microphone access so WebView2 unlocks real hardware device labels,
 * then stops all captured tracks immediately so the OS mic indicator turns off right away.
 */
export async function requestAudioDevicePermission(): Promise<boolean> {
  if (
    typeof navigator === 'undefined' ||
    !navigator.mediaDevices ||
    typeof navigator.mediaDevices.getUserMedia !== 'function'
  ) {
    return false;
  }

  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    stream.getTracks().forEach((track) => {
      try {
        track.stop();
      } catch {
        // Ignore track stop errors during permission probe cleanup
      }
    });

    if (typeof window !== 'undefined' && typeof window.dispatchEvent === 'function') {
      window.dispatchEvent(new CustomEvent(EVENT_AUDIO_DEVICES_REFRESHED));
    }
    return true;
  } catch (err) {
    console.warn('[AudioDevices] Microphone permission request declined or failed:', err);
    return false;
  }
}

/**
 * Subscribes to hardware plug/unplug (`devicechange`) events and manual permission refreshes.
 *
 * @returns Cleanup function that removes both listeners.
 */
export function subscribeAudioDeviceChanges(callback: () => void): () => void {
  const mediaDevices =
    typeof navigator !== 'undefined' ? navigator.mediaDevices : undefined;

  const onDeviceChange = () => {
    callback();
  };

  if (mediaDevices && typeof mediaDevices.addEventListener === 'function') {
    mediaDevices.addEventListener('devicechange', onDeviceChange);
  }

  if (typeof window !== 'undefined' && typeof window.addEventListener === 'function') {
    window.addEventListener(EVENT_AUDIO_DEVICES_REFRESHED, onDeviceChange);
  }

  return () => {
    if (mediaDevices && typeof mediaDevices.removeEventListener === 'function') {
      mediaDevices.removeEventListener('devicechange', onDeviceChange);
    }
    if (typeof window !== 'undefined' && typeof window.removeEventListener === 'function') {
      window.removeEventListener(EVENT_AUDIO_DEVICES_REFRESHED, onDeviceChange);
    }
  };
}

/**
 * Routes an HTMLMediaElement or AudioContext to the requested output device via `setSinkId()`.
 * Automatically falls back to the system default sink (`''`) if the chosen hardware device
 * is disconnected or no longer recognized by the OS.
 */
export async function applyAudioOutputDevice(
  target: AudioOutputSinkTarget | null | undefined,
  deviceId: string
): Promise<AudioSinkApplyResult> {
  const sinkId = normalizeOutputSinkId(deviceId);
  if (!target || typeof target.setSinkId !== 'function') {
    return {
      appliedDeviceId: sinkId,
      fellBackToDefault: false,
      supported: false,
    };
  }

  try {
    await target.setSinkId(sinkId);
    return {
      appliedDeviceId: sinkId,
      fellBackToDefault: false,
      supported: true,
    };
  } catch (err) {
    const errName = err instanceof Error ? err.name : '';
    const canFallback =
      sinkId !== '' && (errName === 'NotFoundError' || errName === 'OverconstrainedError' || errName === 'AbortError');

    if (canFallback) {
      console.warn(
        `[AudioDevices] Output device "${sinkId}" unavailable (${errName}); falling back to system default.`,
        err
      );
      try {
        await target.setSinkId('');
        return {
          appliedDeviceId: '',
          fellBackToDefault: true,
          supported: true,
        };
      } catch (fallbackErr) {
        console.warn('[AudioDevices] Failed to apply system default audio output fallback:', fallbackErr);
      }
    } else {
      console.warn('[AudioDevices] Failed to apply audio output sink:', err);
    }

    return {
      appliedDeviceId: '',
      fellBackToDefault: false,
      supported: true,
    };
  }
}
