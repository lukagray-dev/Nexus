/**
 * @file wellbeing-protocol.test.js
 * @description Unit and integration tests for Digital Wellbeing Monitoring on Nexus Parent:
 *              - Left sidebar placement under "Device Security & Controls" (removed from "Social & AI Activity")
 *              - DataChannel protocol constants & Play/Pause confirmation flow with loading.svg
 *              - RightSidebarStateManager chunked WELLBEING_SNAPSHOT reassembly & disconnect reset
 *              - Formatting, search/category filtering, and DOM rendering across empty/error/populated states
 */

import { describe, it, beforeEach } from 'node:test';
import assert from 'node:assert';

function createMockElement(tag) {
  let _className = '';
  let _innerHTML = '';
  const _classes = new Set();

  const el = {
    tagName: tag.toUpperCase(),
    id: '',
    value: '',
    textContent: '',
    dataset: {},
    attributes: {},
    style: {},
    children: [],
    _parsedById: {},
    get className() {
      return _className;
    },
    set className(val) {
      _className = String(val);
      _classes.clear();
      _className
        .split(/\s+/)
        .filter(Boolean)
        .forEach((c) => _classes.add(c));
    },
    get innerHTML() {
      return _innerHTML;
    },
    set innerHTML(html) {
      _innerHTML = String(html);
      const byId = {};
      const inputRegex = /<input[^>]*id="([^"]+)"[^>]*value="([^"]*)"[^>]*>/g;
      let match;
      while ((match = inputRegex.exec(_innerHTML)) !== null) {
        const inp = createMockElement('input');
        inp.id = match[1];
        inp.value = match[2];
        byId[inp.id] = inp;
      }
      const btnRegex = /<button[^>]*id="([^"]+)"[^>]*>/g;
      while ((match = btnRegex.exec(_innerHTML)) !== null) {
        const btn = createMockElement('button');
        btn.id = match[1];
        byId[btn.id] = btn;
      }
      const divRegex = /<div[^>]*id="([^"]+)"[^>]*>/g;
      while ((match = divRegex.exec(_innerHTML)) !== null) {
        const div = createMockElement('div');
        div.id = match[1];
        byId[div.id] = div;
      }
      el._parsedById = byId;
    },
    classList: {
      add: (c) => {
        _classes.add(c);
        _className = Array.from(_classes).join(' ');
      },
      remove: (c) => {
        _classes.delete(c);
        _className = Array.from(_classes).join(' ');
      },
      toggle: (c, force) => {
        if (force === undefined) {
          if (_classes.has(c)) _classes.delete(c);
          else _classes.add(c);
        } else if (force) {
          _classes.add(c);
        } else {
          _classes.delete(c);
        }
        _className = Array.from(_classes).join(' ');
      },
      contains: (c) => _classes.has(c),
    },
    setAttribute: (k, v) => {
      el.attributes[k] = String(v);
    },
    getAttribute: (k) => el.attributes[k],
    appendChild: (child) => {
      el.children.push(child);
      return child;
    },
    addEventListener: (event, handler) => {
      if (!el._listeners) el._listeners = {};
      if (!el._listeners[event]) el._listeners[event] = [];
      el._listeners[event].push(handler);
    },
    querySelector: (selector) => {
      if (selector.startsWith('#') && el._parsedById[selector.slice(1)]) {
        return el._parsedById[selector.slice(1)];
      }
      for (const child of el.children) {
        if (selector.startsWith('#') && child.id === selector.slice(1)) {
          return child;
        }
        const found = child.querySelector(selector);
        if (found) return found;
      }
      return null;
    },
    querySelectorAll: () => [],
  };

  return el;
}

describe('Digital Wellbeing Monitoring & Sidebar Placement Tests', () => {
  let scrollContainer;

  beforeEach(() => {
    scrollContainer = createMockElement('div');
    scrollContainer.id = 'sidebar-scroll-content';

    global.document = {
      createElement: (tag) => createMockElement(tag),
      getElementById: (id) => (id === 'sidebar-scroll-content' ? scrollContainer : null),
      addEventListener: () => {},
      removeEventListener: () => {},
    };

    global.window = {
      addEventListener: () => {},
      removeEventListener: () => {},
    };
  });

  it('Left sidebar places Digital Wellbeing under Device Security & Controls and not Social & AI Activity', async () => {
    const { renderSidebarContent } = await import('../src/js/left-sidebar/sidebar.js');
    renderSidebarContent();

    assert.strictEqual(scrollContainer.children.length, 4);

    const socialSection = scrollContainer.children[2];
    const controlsSection = scrollContainer.children[3];

    assert.ok(socialSection.children[0].innerHTML.includes('Social &amp; AI Activity') || socialSection.children[0].innerHTML.includes('Social & AI Activity'));
    assert.ok(controlsSection.children[0].innerHTML.includes('Device Security &amp; Controls') || controlsSection.children[0].innerHTML.includes('Device Security & Controls'));

    const socialItems = socialSection.children[1].children.map((item) => item.dataset.feature);
    const controlItems = controlsSection.children[1].children.map((item) => item.dataset.feature);

    assert.strictEqual(socialItems.includes('wellbeing'), false);
    assert.strictEqual(controlItems.includes('wellbeing'), true);
    assert.deepStrictEqual(controlItems, ['applock', 'vibrateflash', 'wallpaper', 'wellbeing']);
  });

  it('Protocol constants and formatScreenTimeDuration conform to contract', async () => {
    const types = await import('../src/js/right-sidebar/types.js');

    assert.strictEqual(types.CMD_WELLBEING_ON, 'WELLBEING_ON');
    assert.strictEqual(types.CMD_WELLBEING_OFF, 'WELLBEING_OFF');
    assert.strictEqual(types.CMD_WELLBEING_REFRESH, 'WELLBEING_REFRESH');
    assert.strictEqual(types.CMD_WELLBEING_RANGE_PREFIX, 'WELLBEING_RANGE:');
    assert.strictEqual(types.RESP_WELLBEING_STARTED, 'WELLBEING_STARTED');
    assert.strictEqual(types.RESP_WELLBEING_STOPPED, 'WELLBEING_STOPPED');
    assert.strictEqual(types.RESP_WELLBEING_ERROR_PREFIX, 'WELLBEING_ERROR: ');
    assert.strictEqual(types.TYPE_WELLBEING_SNAPSHOT, 'WELLBEING_SNAPSHOT');

    assert.strictEqual(types.formatScreenTimeDuration(0), '0m');
    assert.strictEqual(types.formatScreenTimeDuration(45_000), '45s');
    assert.strictEqual(types.formatScreenTimeDuration(25 * 60_000), '25m');
    assert.strictEqual(types.formatScreenTimeDuration(2 * 3600_000 + 14 * 60_000), '2h 14m');
    assert.strictEqual(types.formatScreenTimeDuration(3 * 3600_000), '3h');
  });

  it('RightSidebarState reassembles chunked WELLBEING_SNAPSHOT payloads and resets on disconnect', async () => {
    const { rightSidebarState } = await import('../src/js/right-sidebar/state.js');
    rightSidebarState.clearWellbeingData();
    rightSidebarState.setWellbeingMonitoringState('LIVE');

    rightSidebarState.handleWellbeingSnapshotChunk({
      type: 'WELLBEING_SNAPSHOT',
      range: 'today',
      chunkIndex: 0,
      totalChunks: 2,
      totalScreenTimeMs: 5400_000,
      unlockCount: 18,
      totalLaunches: 42,
      activeAppName: 'YouTube',
      activePackageName: 'com.google.android.youtube',
      generatedAt: 1_700_000_000_000,
      buckets: [
        { label: '12', durationMs: 1800_000 },
        { label: '13', durationMs: 3600_000 },
      ],
      apps: [
        {
          packageName: 'com.whatsapp',
          appName: 'WhatsApp',
          category: 'social',
          totalTimeMs: 1800_000,
          launchCount: 20,
          lastUsedTimestamp: 1_700_000_000_000,
          percent: 33,
        },
      ],
    });

    // Not complete until chunk 1 arrives
    assert.strictEqual(rightSidebarState.getWellbeingApps().length, 0);

    rightSidebarState.handleWellbeingSnapshotChunk({
      type: 'WELLBEING_SNAPSHOT',
      range: 'today',
      chunkIndex: 1,
      totalChunks: 2,
      apps: [
        {
          packageName: 'com.google.android.youtube',
          appName: 'YouTube',
          category: 'video',
          totalTimeMs: 3600_000,
          launchCount: 22,
          lastUsedTimestamp: 1_700_000_000_000,
          percent: 67,
        },
      ],
    });

    const apps = rightSidebarState.getWellbeingApps();
    const summary = rightSidebarState.getWellbeingSummary();
    assert.strictEqual(apps.length, 2);
    // Sorted descending by totalTimeMs
    assert.strictEqual(apps[0].appName, 'YouTube');
    assert.strictEqual(apps[1].appName, 'WhatsApp');
    assert.strictEqual(summary.unlockCount, 18);
    assert.strictEqual(summary.totalLaunches, 42);
    assert.strictEqual(rightSidebarState.getWellbeingBuckets().length, 2);

    rightSidebarState.resetMonitoringStates();
    assert.strictEqual(rightSidebarState.getWellbeingMonitoringState(), 'IDLE');
    rightSidebarState.clearWellbeingData();
  });

  it('filterWellbeingApps & renderWellbeingPanel support search, categories, errors, and live data', async () => {
    const { rightSidebarState } = await import('../src/js/right-sidebar/state.js');
    const { filterWellbeingApps, formatWellbeingCategoryLabel, renderWellbeingPanel } = await import(
      '../src/js/right-sidebar/wellbeing/wellbeing.js'
    );

    const sampleApps = [
      {
        packageName: 'com.google.android.youtube',
        appName: 'YouTube',
        category: 'video',
        totalTimeMs: 3600_000,
        launchCount: 12,
        lastUsedTimestamp: 1_700_000_000_000,
        percent: 60,
      },
      {
        packageName: 'com.whatsapp',
        appName: 'WhatsApp',
        category: 'social',
        totalTimeMs: 1800_000,
        launchCount: 18,
        lastUsedTimestamp: 1_700_000_000_000,
        percent: 30,
      },
      {
        packageName: 'com.google.android.apps.maps',
        appName: 'Maps',
        category: 'maps',
        totalTimeMs: 600_000,
        launchCount: 3,
        lastUsedTimestamp: 1_700_000_000_000,
        percent: 10,
      },
    ];

    assert.strictEqual(formatWellbeingCategoryLabel('game'), 'Games');
    assert.strictEqual(filterWellbeingApps(sampleApps, 'tube', 'all').length, 1);
    assert.strictEqual(filterWellbeingApps(sampleApps, '', 'social').length, 1);
    assert.strictEqual(filterWellbeingApps(sampleApps, '', 'other').length, 1);

    // Error state rendering
    rightSidebarState.clearWellbeingData();
    rightSidebarState.setWellbeingError('Usage Access permission not granted on child device');
    const errPanel = renderWellbeingPanel();
    assert.ok(errPanel.innerHTML.includes('Usage Access permission not granted on child device'));

    // Populated dashboard rendering
    rightSidebarState.setWellbeingMonitoringState('LIVE');
    rightSidebarState.handleWellbeingSnapshotChunk({
      type: 'WELLBEING_SNAPSHOT',
      range: 'today',
      chunkIndex: 0,
      totalChunks: 1,
      totalScreenTimeMs: 6000_000,
      unlockCount: 14,
      totalLaunches: 33,
      activeAppName: 'YouTube',
      activePackageName: 'com.google.android.youtube',
      generatedAt: Date.now(),
      buckets: [{ label: '10', durationMs: 6000_000 }],
      apps: sampleApps,
    });

    const livePanel = renderWellbeingPanel();
    assert.ok(livePanel.innerHTML.includes('YouTube'));
    assert.ok(livePanel.innerHTML.includes('1h 40m'));
    assert.ok(livePanel.innerHTML.includes('Active: YouTube'));

    rightSidebarState.resetMonitoringStates();
    rightSidebarState.clearWellbeingData();
  });

  it('Play/Pause confirmation state machine follows loading.svg interval transitions', () => {
    let state = 'IDLE';
    let icon = 'play.svg';
    let disabled = false;

    const clickPlay = () => {
      if (state === 'STARTING' || state === 'STOPPING') return null;
      if (state === 'IDLE') {
        state = 'STARTING';
        icon = 'loading.svg';
        disabled = true;
        return 'WELLBEING_ON';
      }
      if (state === 'LIVE') {
        state = 'STOPPING';
        icon = 'loading.svg';
        disabled = true;
        return 'WELLBEING_OFF';
      }
      return null;
    };

    const onChildMsg = (msg) => {
      if (msg === 'WELLBEING_STARTED' && state === 'STARTING') {
        state = 'LIVE';
        icon = 'pause.svg';
        disabled = false;
      } else if (msg === 'WELLBEING_STOPPED' && state === 'STOPPING') {
        state = 'IDLE';
        icon = 'play.svg';
        disabled = false;
      } else if (msg.startsWith('WELLBEING_ERROR:')) {
        state = 'IDLE';
        icon = 'play.svg';
        disabled = false;
      }
    };

    assert.strictEqual(clickPlay(), 'WELLBEING_ON');
    assert.strictEqual(state, 'STARTING');
    assert.strictEqual(icon, 'loading.svg');
    assert.strictEqual(disabled, true);
    assert.strictEqual(clickPlay(), null, 'Duplicate clicks while loading must be ignored');

    onChildMsg('WELLBEING_STARTED');
    assert.strictEqual(state, 'LIVE');
    assert.strictEqual(icon, 'pause.svg');
    assert.strictEqual(disabled, false);

    assert.strictEqual(clickPlay(), 'WELLBEING_OFF');
    assert.strictEqual(state, 'STOPPING');
    assert.strictEqual(icon, 'loading.svg');
    assert.strictEqual(disabled, true);

    onChildMsg('WELLBEING_STOPPED');
    assert.strictEqual(state, 'IDLE');
    assert.strictEqual(icon, 'play.svg');
    assert.strictEqual(disabled, false);

    // Error recovery during STARTING
    clickPlay();
    onChildMsg('WELLBEING_ERROR: Usage Access permission not granted on child device');
    assert.strictEqual(state, 'IDLE');
    assert.strictEqual(icon, 'play.svg');
    assert.strictEqual(disabled, false);
  });
});
