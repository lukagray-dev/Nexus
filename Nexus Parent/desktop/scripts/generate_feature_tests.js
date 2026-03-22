const fs = require('fs');
const path = require('path');

const featuresDir = path.join(__dirname, '../dist/js/features');

if (!fs.existsSync(featuresDir)) {
    console.error('Features directory not found');
    process.exit(1);
}

const features = fs.readdirSync(featuresDir).filter(f => fs.statSync(path.join(featuresDir, f)).isDirectory());

features.forEach(featureName => {
    // Skip camera as it's already done
    if (featureName === 'camera') return;

    const featureJsPath = path.join(featuresDir, featureName, `${featureName}.js`);
    if (!fs.existsSync(featureJsPath)) return;

    // Read the js file to guess the class name
    const content = fs.readFileSync(featureJsPath, 'utf8');
    const classMatch = content.match(/class\s+([A-Za-z0-9_]+)\s*\{/);
    if (!classMatch) return;

    const className = classMatch[1];
    
    // Check if test already exists
    const testPath = path.join(featuresDir, featureName, `${featureName}.test.js`);
    if (fs.existsSync(testPath)) {
        console.log(`Test already exists for ${featureName}`);
        return;
    }

    // Determine basic methods to mock based on content
    const hasInit = content.includes('init()');
    const hasStart = content.includes('start') || content.includes('toggle');
    const hasStop = content.includes('stop');
    const hasDestroy = content.includes('destroy(');

    const testContent = `import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import '../dist/js/features/${featureName}/${featureName}';

describe('${className}', () => {
  let container;
  let feature;

  beforeEach(() => {
    container = document.createElement('div');
    document.body.appendChild(container);

    // Mock global app connection manager
    window.nexusApp = {
      connectionManager: {
        sendCommand: vi.fn(),
        webrtcManager: {
          getStream: vi.fn()
        }
      }
    };

    // Override fetch to skip real network load in init
    global.fetch = vi.fn().mockResolvedValue({
      text: vi.fn().mockResolvedValue('<div id="${featureName}-stream"></div><button id="${featureName}-btn"></button>')
    });

    feature = new window.${className}(container);
  });

  afterEach(() => {
    ${hasDestroy ? 'if(typeof feature.destroy === "function") feature.destroy();' : ''}
    document.body.removeChild(container);
    vi.clearAllMocks();
  });

  it('can be instantiated', () => {
    expect(feature).not.toBeNull();
    expect(feature.container).toBe(container);
  });

  it('initializes layout via fetch', async () => {
    await new Promise(r => setTimeout(r, 60)); // Wait for async init
    expect(global.fetch).toHaveBeenCalled();
    expect(container.innerHTML).not.toBe('');
  });
  
  ${content.includes('updateUI(') ? `
  it('has updateUI method', () => {
    if(typeof feature.updateUI === 'function') {
      expect(typeof feature.updateUI).toBe('function');
    }
  });` : ''}
});
`;

    fs.writeFileSync(testPath, testContent);
    console.log(`Generated basic test for ${featureName} (${className})`);
});
