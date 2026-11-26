/**
 * @file about.ts
 * @description About Nexus Parent Settings category compositor.
 *
 * Hey friend! Combines all live Nexus Parent desktop metadata into one clear,
 * verified page: brand identity & updates, host runtime & paths, WebRTC/Firebase
 * transport & storage footprint, credits & links, and full workspace reset.
 */

import { createSettingsSectionHeader } from '../helpers.js';
import { renderParentIdentityGroup } from './parent-identity.js';
import { renderRuntimeEnvironmentGroup } from './runtime-environment.js';
import { renderTransportStorageGroup } from './transport-storage.js';
import { renderCreditsLinksGroup } from './credits-links.js';
import { renderWorkspaceResetGroup } from './workspace-reset.js';

/**
 * Renders the About Nexus Parent Settings main content section.
 */
export function renderAboutContent(): HTMLElement {
  const container = document.createElement('div');
  container.className = 'settings-content-section';

  container.appendChild(
    createSettingsSectionHeader(
      'About Nexus Parent',
      'Inspect live application version, runtime environment, transport telemetry, local paths, and licensing.'
    )
  );

  container.appendChild(renderParentIdentityGroup());
  container.appendChild(renderRuntimeEnvironmentGroup());
  container.appendChild(renderTransportStorageGroup());
  container.appendChild(renderCreditsLinksGroup());
  container.appendChild(renderWorkspaceResetGroup());

  return container;
}
