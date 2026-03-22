// Window Manager - Manages feature windows
console.log("✅ window-manager.js loaded");

class WindowManager {
  /**
   * Initializes a new instance of the WindowManager class.
   *
   * @constructor
   *
   * @property {Object} openWindows - An object containing feature names as keys and their corresponding window elements as values.
   * @property {Element} featureWindows - The element containing all feature windows.
   * @property {number} zIndexCounter - A counter used to keep track of the z-index for each feature window.
   */
  constructor() {
    this.openWindows = {};
    this.featureWindows = null;
    this.zIndexCounter = 200;
  }

  /**
   * Initializes the Window Manager with the given feature windows element.
   *
   * @param {Element} featureWindowsElement - The element containing all feature windows.
   * @returns {void}
   */
  init(featureWindowsElement) {
    this.featureWindows = featureWindowsElement;
  }

  /**
   * Creates a new feature window with the given feature name.
   *
   * @param {string} feature - The name of the feature to create a window for.
   * @returns {void}
   */
  createWindow(feature) {
    if (this.openWindows[feature]) {
      this.focusWindow(feature);
      return;
    }

    const win = document.createElement('div');
    win.className = 'feature-window flicker-in';
    
    // Center the window in the viewport
    const windowWidth = 600;
    const windowHeight = 400;
    const viewportWidth = window.innerWidth;
    const viewportHeight = window.innerHeight;
    
    // Calculate center position with slight offset for multiple windows
    const offset = Object.keys(this.openWindows).length * 30;
    const centerLeft = (viewportWidth - windowWidth) / 2 + offset;
    const centerTop = (viewportHeight - windowHeight) / 2 + offset;
    
    win.style.left = Math.max(0, centerLeft) + 'px';
    win.style.top = Math.max(0, centerTop) + 'px';
    win.style.width = windowWidth + 'px';
    win.style.height = windowHeight + 'px';
    win.style.zIndex = this.zIndexCounter++;
    win.setAttribute('data-feature', feature);
    
    // Create content container only (no window header)
    win.innerHTML = `
      <div class="feature-window-content" id="feature-content-${feature}">
        <!-- Feature-specific content will be injected here -->
      </div>
    `;

    // Focus on click
    win.addEventListener('mousedown', () => {
      this.focusWindow(feature);
    });

    // Observe window resize (no scrollable area update)
    const resizeObserver = new ResizeObserver(() => {
      // Just observe, don't update scrollable area
    });
    resizeObserver.observe(win);
    win._resizeObserver = resizeObserver;

    // Add to DOM and track
    this.featureWindows.appendChild(win);
    this.openWindows[feature] = win;

    // Inject feature content (async)
    const contentContainer = win.querySelector('.feature-window-content');
    this.injectFeatureContent(feature, contentContainer).then(() => {
      // Use setTimeout to ensure DOM is fully updated before setting up header
      setTimeout(() => {
        this.setupFeatureHeader(win, feature);
      }, 50);
    }).catch(error => {
      console.error(`Failed to load feature ${feature}:`, error);
      contentContainer.innerHTML = `<div style="text-align:center;padding:2rem;color:#888;">Failed to load feature</div>`;
    });
  }

  /**
   * Setup feature header for dragging and add close button
   *
   * @param {Element} windowEl - The window element
   * @param {string} feature - The feature name
   */
  setupFeatureHeader(windowEl, feature) {
    // Find the feature header using a more generic approach
    const contentContainer = windowEl.querySelector('.feature-window-content');
    if (!contentContainer) {
      console.warn(`No content container found for feature: ${feature}`);
      return;
    }

    // Try to find any element with a class ending in '-header'
    const allElements = contentContainer.querySelectorAll('[class*="-header"]');
    let headerEl = null;
    
    for (let el of allElements) {
      const classList = Array.from(el.classList);
      if (classList.some(cls => cls.endsWith('-header'))) {
        headerEl = el;
        break;
      }
    }
    
    if (!headerEl) {
      console.warn(`No header found for feature: ${feature}`);
      return;
    }

    console.log(`✓ Found header for ${feature}:`, headerEl.className);

    // Check if close button already exists (prevent duplicates)
    if (headerEl.querySelector('.feature-window-close')) {
      console.log(`Close button already exists for ${feature}`);
      return;
    }

    // Add close button to the header
    const closeBtn = document.createElement('button');
    closeBtn.className = 'feature-window-close';
    closeBtn.innerHTML = '×';
    closeBtn.title = 'Close';
    closeBtn.onclick = (e) => {
      e.stopPropagation();
      this.closeWindow(feature, windowEl);
    };
    
    // Insert close button at the end of the header
    headerEl.appendChild(closeBtn);
    console.log(`✓ Close button added to ${feature}`);

    // Make the header draggable (but not the close button)
    this.makeDraggable(windowEl, headerEl, closeBtn);
    
    // Add cursor style to indicate draggability
    headerEl.style.cursor = 'move';
    console.log(`✓ Dragging enabled for ${feature}`);
  }

  /**
   * Closes the feature window with the given feature name and removes it from the DOM.
   * Also removes the feature from the list of open features.
   *
   * @param {string} feature - The name of the feature to close the window for.
   * @param {Element} windowEl - The element of the feature window to close.
   * @returns {void}
   */
  closeWindow(feature, windowEl) {
    windowEl.classList.remove('flicker-in');
    windowEl.classList.add('flicker-out');
    
    // Clean up drag event listeners if they exist
    if (windowEl._dragCleanup) {
      windowEl._dragCleanup();
    }
    
    // Clean up resize observer if it exists
    if (windowEl._resizeObserver) {
      windowEl._resizeObserver.disconnect();
    }
    
    setTimeout(() => {
      if (windowEl.parentNode) {
        this.featureWindows.removeChild(windowEl);
      }
      delete this.openWindows[feature];
    }, 300);
  }

  /**
   * Sets the z-index of the feature window with the given feature name to the highest value,
   * effectively bringing the window to the front.
   *
   * @param {string} feature - The name of the feature to focus the window for.
   */
  focusWindow(feature) {
    const win = this.openWindows[feature];
    if (win) {
      win.style.zIndex = this.zIndexCounter++;
    }
  }

  /**
   * Makes the given feature window draggable by setting up event listeners on the given header element.
   * The window will be moved to follow the mouse when the header element is dragged.
   * Windows are constrained to stay within the visible viewport and below the top info area.
   *
   * @param {Element} windowEl - The element of the feature window to make draggable.
   * @param {Element} headerEl - The element of the header of the feature window that the user will drag to move the window.
   * @param {Element} closeBtn - The close button element to exclude from dragging.
   * @returns {void}
   */
  makeDraggable(windowEl, headerEl, closeBtn) {
    let isDragging = false;
    let offsetX = 0;
    let offsetY = 0;

    const onMouseDown = (e) => {
      // Don't start dragging if clicking on the close button or any interactive element
      if (e.target === closeBtn || 
          e.target.closest('button') || 
          e.target.closest('input') || 
          e.target.closest('select') ||
          e.target.closest('textarea')) {
        return;
      }
      
      isDragging = true;
      offsetX = e.clientX - windowEl.offsetLeft;
      offsetY = e.clientY - windowEl.offsetTop;
      
      // Prevent text selection while dragging
      e.preventDefault();
    };

    const onMouseMove = (e) => {
      if (!isDragging) return;

      // Calculate new position
      let newLeft = e.clientX - offsetX;
      let newTop = e.clientY - offsetY;

      // Get viewport dimensions
      const viewportWidth = window.innerWidth;
      const viewportHeight = window.innerHeight;
      
      // Get window dimensions
      const windowWidth = windowEl.offsetWidth;
      const windowHeight = windowEl.offsetHeight;

      // Get sidebar width to prevent windows from going over it
      const sidebar = document.getElementById('sidebar');
      const sidebarWidth = sidebar && !sidebar.classList.contains('collapsed') 
        ? sidebar.offsetWidth 
        : 0;

      // Get top-info height to prevent windows from going under it
      const topInfo = document.querySelector('.top-info');
      const minTop = topInfo ? topInfo.offsetHeight : 80;

      // Account for title bar height (32px)
      const titleBarHeight = 32;
      const maxBottom = viewportHeight - titleBarHeight;

      // Constrain to viewport - prevent going off-screen
      if (newLeft < sidebarWidth) newLeft = sidebarWidth; // Don't go over sidebar
      if (newTop < minTop) newTop = minTop; // Keep below top-info area
      if (newLeft + windowWidth > viewportWidth) newLeft = viewportWidth - windowWidth;
      if (newTop + windowHeight > maxBottom) newTop = maxBottom - windowHeight; // Account for title bar

      windowEl.style.left = newLeft + 'px';
      windowEl.style.top = newTop + 'px';
    };

    const onMouseUp = () => {
      if (isDragging) {
        isDragging = false;
      }
    };

    // Attach event listeners
    headerEl.addEventListener('mousedown', onMouseDown);
    document.addEventListener('mousemove', onMouseMove);
    document.addEventListener('mouseup', onMouseUp);

    // Store cleanup function for later removal if needed
    windowEl._dragCleanup = () => {
      headerEl.removeEventListener('mousedown', onMouseDown);
      document.removeEventListener('mousemove', onMouseMove);
      document.removeEventListener('mouseup', onMouseUp);
    };
  }

/**
 * Injects feature content by dynamically loading the feature module.
 * Uses the FeatureLoader to load HTML, CSS, and JavaScript for each feature.
 *
 * @param {string} feature - The name of the feature to inject content for.
 * @param {Element} container - The element to inject the content into.
 */
  async injectFeatureContent(feature, container) {
    // Use the global feature loader to load the feature module
    if (window.featureLoader) {
      await window.featureLoader.loadFeature(feature, container);
    } else {
      console.error('FeatureLoader not available');
      container.innerHTML = '<div style="text-align:center;padding:2rem;color:#888;">Feature loader not initialized</div>';
    }
  }

/**
 * Returns a human-readable title for the given feature.
 *
 * @param {string} feature - The name of the feature to get a title for.
 * @returns {string} The title of the feature, or the feature name if no title is found.
 */
  getFeatureTitle(feature) {
    const titles = {
      'camera': 'Monitor Camera',
      'screen': 'Monitor Screen',
      'location': 'Monitor Location',
      'sms': 'Monitor SMS',
      'mic': 'Monitor Mic',
      'calllog': 'Monitor Call Log',
      'notifications': 'Monitor Notifications',
      'chats': 'Monitor Chats',
      'gmail': 'Monitor Gmail',
      'files': 'Monitor Files',
      'wellbeing': 'Digital Wellbeing',
      'applock': 'Monitor App Lock',
      'wallpaper': 'Change Wallpaper',
      'vibrateflash': 'Vibrate and Flash'
    };
    return titles[feature] || feature;
  }

/**
 * Closes all open feature windows and removes them from the DOM.
 * Also resets the list of open features to an empty object.
 * @returns {void}
 */
  closeAll() {
    Object.keys(this.openWindows).forEach(feature => {
      const win = this.openWindows[feature];
      if (win && win.parentNode) {
        win.parentNode.removeChild(win);
      }
    });
    this.openWindows = {};
  }
}

window.WindowManager = WindowManager;
