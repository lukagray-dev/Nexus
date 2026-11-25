/**
 * @file helpers.ts
 * @description Shared DOM construction and reactive binding helpers for Settings sections.
 *
 * Hey friend! Every settings category shares the same tactical header, card group shell,
 * and segmented button patterns. Centralizing them here keeps each setting file small,
 * clean, and free of duplicated event-binding boilerplate.
 */

/**
 * Builds the top category header block (title + subtitle description).
 */
export function createSettingsSectionHeader(title: string, description: string): HTMLElement {
  const header = document.createElement('div');
  header.className = 'settings-content-header';
  header.innerHTML = `
    <h2 class="settings-content-title">${title}</h2>
    <p class="settings-content-desc">${description}</p>
  `;
  return header;
}

/**
 * Wraps one or more setting row elements inside a titled settings group container.
 */
export function createSettingsGroup(
  title: string,
  items: HTMLElement[],
  options?: { extraClass?: string; titleColor?: string; marginTop?: string }
): HTMLElement {
  const group = document.createElement('div');
  group.className = options?.extraClass ? `settings-group ${options.extraClass}` : 'settings-group';
  if (options?.marginTop) {
    group.style.marginTop = options.marginTop;
  }

  const titleEl = document.createElement('div');
  titleEl.className = 'settings-group-title';
  titleEl.textContent = title;
  if (options?.titleColor) {
    titleEl.style.color = options.titleColor;
  }
  group.appendChild(titleEl);

  items.forEach((item) => group.appendChild(item));
  return group;
}

/**
 * Attaches click handlers to a `.settings-segmented-group` so active styling and
 * callback invocation stay synchronized across every option button.
 */
export function bindSegmentedGroup<T extends string>(
  container: HTMLElement,
  groupSelector: string,
  onSelect: (value: T) => void | Promise<void>
): void {
  const buttons = container.querySelectorAll<HTMLButtonElement>(`${groupSelector} .settings-segmented-btn`);
  buttons.forEach((btn) => {
    btn.addEventListener('click', () => {
      const value = btn.dataset.value as T | undefined;
      if (!value) return;

      buttons.forEach((other) => other.classList.remove('is-active'));
      btn.classList.add('is-active');
      void onSelect(value);
    });
  });
}
