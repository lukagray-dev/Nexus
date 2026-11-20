/**
 * @file wellbeing.ts
 * @description Digital Wellbeing Panel component for Nexus Parent Right Sidebar.
 *
 * Hey friend! Digital Wellbeing app usage monitoring is currently under active development.
 * This component displays a clean, industrial "Coming Soon" placeholder view.
 */

/**
 * Creates and returns the Digital Wellbeing panel element.
 */
export function renderWellbeingPanel(): HTMLElement {
  const container = document.createElement('div');
  container.className = 'rs-body rs-empty-placeholder';

  container.innerHTML = `
    <div class="rs-empty-icon">
      <img src="assets/icons/right-sidebar/wellbeing/activity.svg" alt="" />
    </div>
    <div class="rs-empty-title">Coming Soon</div>
    <div class="rs-empty-desc">
      Digital Wellbeing screen time and usage monitoring is currently under active development and will be available in an upcoming update.
    </div>
  `;

  return container;
}
