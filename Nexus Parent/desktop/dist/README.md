# Nexus Parent Desktop UI - Current Status

## Overview

The Nexus Parent desktop UI is a modular, maintainable application built with vanilla JavaScript, HTML, and CSS. It provides a dashboard for monitoring child devices with features like camera streaming, screen monitoring, location tracking, and more.

**Current Status**: Core UI Framework Complete - Features Ready for Implementation

## Recent Updates

### ✓ Completed

- **Dust Animation Background**: Ethereal particle system with Simplex noise-based fluid motion
  - 18,000 particles with depth-based parallax
  - Smooth flow field animation with gravity and mouse repulsion
  - Visible across entire dashboard with proper z-index layering
  - Configurable particle size and noise scale

- **Modular Architecture**: Refactored into maintainable components
  - Separated concerns: managers, components, features, loaders
  - Dynamic component and feature loading system
  - Window management for draggable/resizable feature panels

- **Connection System**: WebRTC-based device connection
  - Device ID validation (12-digit format)
  - Connection state management
  - Automatic reconnection support
  - Keep-alive mechanism to prevent NAT timeout

- **UI Components**:
  - Main sidebar with collapsible state and resize handle
  - AI assistant sidebar (right-sliding panel)
  - Settings sidebar with account management
  - Profile dropdown with disconnect/delete options
  - Subscription details dropdown

- **Feature System**: All 14 features with placeholder implementations
  - Camera, Screen, Location, SMS, Call Log, Mic
  - Notifications, Chats, Gmail, Files
  - Digital Wellbeing, App Lock, Wallpaper, Vibrate & Flash
  - Each feature has HTML template, CSS styles, and JavaScript class
  - Dynamic loading on-demand with error handling

- **Dialog System**: Confirmation dialogs
  - Disconnect confirmation
  - Two-step account deletion with password verification
  - Modal overlay with animations

## Directory Structure

```markdown
dist/  
├── index.html                     # Main HTML file  
├── styles/                        # Global styles  
│   ├── base.css                   # Base styles and utilities  
│   ├── components.css             # Component styles
│   ├── dashboard.css              # Dashboard layout and z-index management
│   └── animations.css             # Animation definitions
├── js/                            # JavaScript source files
│   ├── app.js                     # Main application orchestrator
│   ├── animations/                # Animation modules
│   │   ├── dust-animation.js      # Ethereal dust particle system
│   │   └── particles.js           # Connection screen particles
│   ├── managers/                  # State and interaction managers
│   │   ├── connection-manager.js  # WebRTC device connection
│   │   ├── dialog-manager.js      # Dialog state and interactions
│   │   ├── ui-manager.js          # UI state and transitions
│   │   └── window-manager.js      # Window management (drag, resize, focus)
│   ├── loaders/                   # Dynamic loaders
│   │   ├── component-loader.js    # Dynamic component loader with caching
│   │   └── feature-loader.js      # Dynamic feature loader with error handling
│   ├── components/                # UI components
│   │   ├── sidebar/               # Main sidebar (collapsible, resizable)
│   │   ├── ai-sidebar/            # AI assistant panel
│   │   └── settings/              # Settings and account management
│   └── features/                  # 14 feature modules
│       ├── camera/                # Camera monitoring
│       ├── screen/                # Screen streaming
│       ├── location/              # Location tracking
│       ├── sms/                   # SMS monitoring
│       ├── calllog/               # Call log monitoring
│       ├── mic/                   # Microphone monitoring
│       ├── notifications/         # Notification monitoring
│       ├── chats/                 # Chat message monitoring
│       ├── gmail/                 # Gmail monitoring
│       ├── files/                 # File monitoring
│       ├── wellbeing/             # Digital wellbeing stats
│       ├── applock/               # App lock management
│       ├── wallpaper/             # Wallpaper management
│       └── vibrateflash/          # Vibrate and flash control
└── Nexus_Parent_icon.png          # Application icon
```

## Architecture Components

### 1. Main Application (`js/app.js`)

The `NexusApp` class orchestrates the entire application:

**Responsibilities:**

- Component loading and injection
- Manager initialization
- Window manager setup
- Connection/disconnection flow
- Screen transitions (connect ↔ dashboard)

**Key Methods:**

- `init()` - Initializes all systems
- `handleConnect()` - Connects to child device
- `handleDisconnect()` - Disconnects from device

### 2. Managers (`js/managers/`)

#### Connection Manager

Handles device connection lifecycle:

- Device ID validation (12 digits, no spaces)
- Connection establishment via Firebase
- Connection polling with timeout
- Disconnection and cleanup

#### Dialog Manager

Manages all dialog interactions:

- Disconnect confirmation
- Two-step account deletion
- Modal state management

#### UI Manager

Handles UI state and visual feedback:

- Screen transitions
- Error display with shake animation
- Dust animation initialization
- Event listener setup
- Dropdown management

### 3. Loaders

#### Component Loader (`loaders/component-loader.js`)

- Dynamically loads sidebar, AI sidebar, settings
- Caches loaded HTML to prevent duplicate fetches
- Injects components into DOM containers

#### Feature Loader (`loaders/feature-loader.js`)

- Dynamically loads feature HTML, CSS, and JavaScript
- Instantiates feature classes
- Handles load errors gracefully
- Caches loaded features

### 4. Window Manager (`managers/window-manager.js`)

Manages feature windows:

- Creates draggable, resizable windows
- Centers windows with cascading offset
- Drag-and-drop functionality
- Focus management (z-index)
- Feature content injection

### 5. UI Components (`js/components/`)

#### Main Sidebar

- Collapsible with toggle button
- Resizable width (120px - 400px)
- Feature buttons with data attributes
- Logo button for expand when collapsed
- Stealth mode button

#### AI Sidebar

- Right-sliding panel
- Placeholder for AI chat functionality
- Close button with animations

#### Settings Sidebar

- Right-sliding panel
- Account information
- Theme preferences
- Auto-save functionality

### 6. Feature Modules (`js/features/`)

All 14 features follow the same pattern:

**File Structure:**

```markdown
{feature}/
├── {feature}.html    # UI template
├── {feature}.css     # Styles
└── {feature}.js      # Class implementation
```

**Class Pattern:**

```javascript
class {Feature}Feature {
    constructor(container) {
        this.container = container;
        this.setupEventListeners();
    }

    setupEventListeners() {
        // Setup event listeners
    }

    destroy() {
        // Cleanup resources
    }
}

// Export to window for dynamic loading
window.{Feature}Feature = {Feature}Feature;
```

### 7. Animations

#### Dust Animation (`js/animations/dust-animation.js`)

- 18,000 particles with Simplex noise flow field
- Depth-based parallax effect
- Gravity toward bottom-right corner
- Mouse repulsion effect
- Particle size: 0.02-0.2px (very fine)
- Noise scale: 0.015 (smooth, broad waves)
- Visible across entire dashboard

#### Particles Animation (`js/animations/particles.js`)

- Connection screen background animation
- Floating particles with random motion

## Current Features Status

| Feature | Status | Implementation |
| - | - | - |
| Camera | ✅ Loaded | Placeholder - awaiting WebRTC integration |
| Screen | ✅ Loaded | Placeholder - awaiting WebRTC integration |
| Location | ✅ Loaded | Placeholder - awaiting map integration |
| SMS | ✅ Loaded | Placeholder - awaiting data integration |
| Call Log | ✅ Loaded | Placeholder - awaiting data integration |
| Mic | ✅ Loaded | Placeholder - awaiting audio integration |
| Notifications | ✅ Loaded | Placeholder - awaiting data integration |
| Chats | ✅ Loaded | Placeholder - awaiting data integration |
| Gmail | ✅ Loaded | Placeholder - awaiting API integration |
| Files | ✅ Loaded | Placeholder - awaiting file system integration |
| Wellbeing | ✅ Loaded | Placeholder - awaiting stats integration |
| App Lock | ✅ Loaded | Placeholder - awaiting app management |
| Wallpaper | ✅ Loaded | Placeholder - awaiting image upload |
| Vibrate & Flash | ✅ Loaded | Placeholder - awaiting device control |

## Communication Flow

```mermaidflowchart TD
User Action (click button)
    ↓
Sidebar Event Listener
    ↓
WindowManager.createWindow()
    ↓
FeatureLoader.loadFeature()
    ↓
Load HTML + CSS + JS
    ↓
Instantiate Feature Class
    ↓
Inject into Window Container
    ↓
Feature Ready for Interaction
```

## Styling

**Color Scheme:**

- Primary Accent: `#fbbf24` (yellow)
- Background: `#000` (black)
- Text: `rgba(255, 255, 255, 0.8)` (light gray)
- Borders: `rgba(63, 63, 70, 0.3)` (subtle gray)
- Sidebar: `rgba(15, 15, 15, 0.2)` (semi-transparent)

**Design System:**

- Glassmorphism with backdrop blur
- Rounded corners: 6-8px
- Smooth transitions: 0.2s
- Z-index layering:
  - Canvas: 0
  - Main area: 10
  - Feature windows: 50-200
  - Sidebar: 100
  - Top info: 100
  - Modals: 3000

## Performance Optimizations

1. **Lazy Loading**: Features only load when opened
2. **CSS Caching**: Stylesheets cached after first load
3. **Module Caching**: Feature instances reused
4. **Cleanup**: Features implement `destroy()` for resource cleanup
5. **Dust Animation**: Optimized WebGL rendering with 18,000 particles

## Browser Support

- Chrome/Edge 90+
- Firefox 88+
- Safari 14+

## Known Issues & TODOs

### Immediate TODOs

- [ ] Implement actual feature functionality (camera, screen, location, etc.)
- [ ] Connect features to Rust backend via Tauri
- [ ] Implement data streaming for camera/screen/mic
- [ ] Add real data sources for SMS, calls, notifications, etc.
- [ ] Implement file upload for wallpaper
- [ ] Add app management for app lock feature

### Future Enhancements

- [ ] Feature search/filtering
- [ ] Custom feature ordering
- [ ] Feature favorites/pinning
- [ ] Keyboard shortcuts
- [ ] Feature preview on hover
- [ ] Drag-and-drop feature reordering
- [ ] Feature notifications/badges
- [ ] Dark/light theme toggle
- [ ] Responsive mobile layout

## Adding a New Feature

1. Create folder: `js/features/{feature-name}/`
2. Create `{feature-name}.html` with UI template
3. Create `{feature-name}.css` with styles
4. Create `{feature-name}.js` with class:

   ```javascript
   class {Feature}Feature {
       constructor(container) {
           this.container = container;
           this.setupEventListeners();
       }
       setupEventListeners() { }
       destroy() { }
   }
   window.{Feature}Feature = {Feature}Feature;
   ```

5. Add button to sidebar in `index.html`:

   ```html
   <button class="sidebar-btn" data-feature="{feature-name}">
       Feature Name
   </button>
   ```

6. Update `WindowManager.getFeatureTitle()` if needed

## Troubleshooting

**Feature Not Loading:**

- Check browser console for errors
- Verify files exist in `js/features/{feature}/`
- Ensure class name matches `{Feature}Feature` pattern
- Verify window export: `window.{Feature}Feature = {Feature}Feature;`

**Dust Animation Not Visible:**

- Check z-index layering in dashboard.css
- Verify canvas element has `z-index: 0`
- Ensure main-area has `pointer-events: none` with children having `pointer-events: auto`

**Feature Window Not Dragging:**

- Check WindowManager.makeDraggable() is called
- Verify header element has correct class
- Check for event listener conflicts

## Development Notes

- Keep feature files small and focused
- Use consistent naming conventions
- Document complex logic with comments
- Test features in isolation before integration
- All features must export to window object for dynamic loading
- Features should implement cleanup in `destroy()` method
