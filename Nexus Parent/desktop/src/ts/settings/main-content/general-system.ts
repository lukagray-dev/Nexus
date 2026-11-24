/**
 * @file general-system.ts
 * @description General & System Settings main content view.
 *
 * Hey friend! This category manages:
 * 1. Default local storage path where downloaded media, screenshots, and DVR clips are saved.
 * 2. Launching minimized to the OS system tray on computer boot.
 * 3. Close window behavior (minimize to tray vs full process termination).
 * 4. Desktop OS system push notifications for urgent events.
 */

import { settingsState } from '../sidebar/state.js';
import { showToast } from '../../shared/toast.js';

export function renderGeneralSystemContent(): HTMLElement {
  const container = document.createElement('div');
  container.className = 'settings-content-section';

  const values = settingsState.getValues();

  container.innerHTML = `
    <div class="settings-content-header">
      <h2 class="settings-content-title">General & System</h2>
      <p class="settings-content-desc">Configure local export folders, native window close actions, system tray behavior, and desktop notifications.</p>
    </div>

    <div class="settings-group">
      <div class="settings-group-title">FILE STORAGE & EXPORT</div>

      <!-- 1. Downloads Directory -->
      <div class="settings-row-vertical">
        <div class="settings-row-label">
          <span class="settings-label-title">Downloads & Media Save Directory</span>
          <span class="settings-label-desc">Folder location on your PC where saved photos, recordings, and storage downloads are stored</span>
        </div>
        <div class="settings-file-picker-group">
          <input type="text" id="setting-downloads-dir" class="settings-input" value="${values.downloadsDirectory}" />
          <button type="button" id="btn-browse-downloads" class="settings-btn-inline">
            <img src="assets/icons/settings/folder.svg" alt="" style="width: 12px; height: 12px; filter: invert(1); vertical-align: middle; margin-right: 4px;" />
            Browse
          </button>
        </div>
      </div>
    </div>

    <div class="settings-group">
      <div class="settings-group-title">DESKTOP WINDOW & TRAY BEHAVIOR</div>

      <!-- 2. Launch Minimized to Tray -->
      <div class="settings-row">
        <div class="settings-row-label">
          <span class="settings-label-title">Launch Minimized on System Startup</span>
          <span class="settings-label-desc">Starts Nexus Parent in the background system tray when your PC boots</span>
        </div>
        <label class="settings-toggle">
          <input type="checkbox" id="setting-launch-minimized" ${values.launchMinimized ? 'checked' : ''} />
          <span class="settings-toggle-slider"></span>
        </label>
      </div>

      <!-- 3. Close Window Action -->
      <div class="settings-row">
        <div class="settings-row-label">
          <span class="settings-label-title">Close Button [X] Behavior</span>
          <span class="settings-label-desc">Keep active monitoring alive in background tray or terminate app</span>
        </div>
        <div class="settings-segmented-group" id="setting-close-action">
          <button type="button" class="settings-segmented-btn ${values.closeAction === 'tray' ? 'is-active' : ''}" data-value="tray">Minimize to Tray</button>
          <button type="button" class="settings-segmented-btn ${values.closeAction === 'exit' ? 'is-active' : ''}" data-value="exit">Exit Application</button>
        </div>
      </div>
    </div>

    <div class="settings-group">
      <div class="settings-group-title">DESKTOP ALERT NOTIFICATIONS</div>

      <!-- 4. Event Checkboxes -->
      <div class="settings-checkbox-list">
        <label class="settings-checkbox-row">
          <input type="checkbox" id="setting-alert-intruder" ${values.alertIntruderSelfie ? 'checked' : ''} />
          <span class="settings-checkbox-custom"></span>
          <div class="settings-checkbox-label">
            <span class="settings-label-title">Intruder Selfie Triggered</span>
            <span class="settings-label-desc">Desktop notification when child phone detects unauthorized PIN attempts</span>
          </div>
        </label>

        <label class="settings-checkbox-row">
          <input type="checkbox" id="setting-alert-low-battery" ${values.alertLowBattery ? 'checked' : ''} />
          <span class="settings-checkbox-custom"></span>
          <div class="settings-checkbox-label">
            <span class="settings-label-title">Child Device Low Battery</span>
            <span class="settings-label-desc">Alert when child battery drops below 15%</span>
          </div>
        </label>

        <label class="settings-checkbox-row">
          <input type="checkbox" id="setting-alert-disconnect" ${values.alertChildDisconnected ? 'checked' : ''} />
          <span class="settings-checkbox-custom"></span>
          <div class="settings-checkbox-label">
            <span class="settings-label-title">Child Device Disconnection</span>
            <span class="settings-label-desc">Alert when active P2P link drops or child enters offline zone</span>
          </div>
        </label>

        <label class="settings-checkbox-row">
          <input type="checkbox" id="setting-alert-sos" ${values.alertSos ? 'checked' : ''} />
          <span class="settings-checkbox-custom"></span>
          <div class="settings-checkbox-label">
            <span class="settings-label-title">Emergency SOS Alert</span>
            <span class="settings-label-desc">High-priority audio chime and desktop alert when child triggers emergency broadcast</span>
          </div>
        </label>
      </div>
    </div>
  `;

  // Bind reactive event listeners
  const dirInput = container.querySelector<HTMLInputElement>('#setting-downloads-dir');
  dirInput?.addEventListener('input', () => {
    settingsState.setValue('downloadsDirectory', dirInput.value.trim());
  });

  const browseBtn = container.querySelector<HTMLButtonElement>('#btn-browse-downloads');
  browseBtn?.addEventListener('click', () => {
    showToast('Folder dialog: ~/Downloads/Nexus selected');
  });

  const launchToggle = container.querySelector<HTMLInputElement>('#setting-launch-minimized');
  launchToggle?.addEventListener('change', () => {
    settingsState.setValue('launchMinimized', launchToggle.checked);
  });

  const closeActionButtons = container.querySelectorAll<HTMLButtonElement>('#setting-close-action .settings-segmented-btn');
  closeActionButtons.forEach((btn) => {
    btn.addEventListener('click', () => {
      closeActionButtons.forEach((b) => b.classList.remove('is-active'));
      btn.classList.add('is-active');
      settingsState.setValue('closeAction', btn.dataset.value as 'tray' | 'exit');
    });
  });

  const alertIntruder = container.querySelector<HTMLInputElement>('#setting-alert-intruder');
  alertIntruder?.addEventListener('change', () => {
    settingsState.setValue('alertIntruderSelfie', alertIntruder.checked);
  });

  const alertBattery = container.querySelector<HTMLInputElement>('#setting-alert-low-battery');
  alertBattery?.addEventListener('change', () => {
    settingsState.setValue('alertLowBattery', alertBattery.checked);
  });

  const alertDisconnect = container.querySelector<HTMLInputElement>('#setting-alert-disconnect');
  alertDisconnect?.addEventListener('change', () => {
    settingsState.setValue('alertChildDisconnected', alertDisconnect.checked);
  });

  const alertSos = container.querySelector<HTMLInputElement>('#setting-alert-sos');
  alertSos?.addEventListener('change', () => {
    settingsState.setValue('alertSos', alertSos.checked);
  });

  return container;
}
