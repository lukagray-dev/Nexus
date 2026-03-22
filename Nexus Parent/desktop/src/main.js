// Electron Main Process
// Nexus Parent Desktop Application

const { app, BrowserWindow, Menu, ipcMain, dialog } = require('electron');
const path = require('path');

// Check if we're in development mode
const isDev = process.env.NODE_ENV === 'development' || process.argv.includes('--dev');

let mainWindow;
let deeplinkingUrl;

// Register custom protocol for deep linking
if (process.defaultApp) {
  if (process.argv.length >= 2) {
    app.setAsDefaultProtocolClient('nexus', process.execPath, [path.resolve(process.argv[1])]);
  }
} else {
  app.setAsDefaultProtocolClient('nexus');
}

// Handle deep link on macOS
app.on('open-url', (event, url) => {
  event.preventDefault();
  deeplinkingUrl = url;
  handleDeepLink(url);
});

// Handle deep link on Windows
const gotTheLock = app.requestSingleInstanceLock();

if (!gotTheLock) {
  app.quit();
} else {
  app.on('second-instance', (event, commandLine, workingDirectory) => {
    // Someone tried to run a second instance, focus our window instead
    if (mainWindow) {
      if (mainWindow.isMinimized()) mainWindow.restore();
      mainWindow.focus();
    }

    // Handle deep link from second instance (Windows)
    const url = commandLine.find(arg => arg.startsWith('nexus://'));
    if (url) {
      handleDeepLink(url);
    }
  });
}

/**
 * Handle deep link URL
 */
function handleDeepLink(url) {
  console.log('🔗 Deep link received:', url);
  
  if (mainWindow && url.includes('nexus://auth/')) {
    try {
      // Manual parsing for custom protocol URLs with hash
      let accessToken, refreshToken, type, token;
      
      // Check if URL has hash parameters
      if (url.includes('#')) {
        const hashPart = url.split('#')[1];
        const hashParams = new URLSearchParams(hashPart);
        accessToken = hashParams.get('access_token');
        refreshToken = hashParams.get('refresh_token');
        type = hashParams.get('type');
        token = hashParams.get('token');
        
        console.log('🔗 Parsed from hash - type:', type, 'has access_token:', !!accessToken, 'has refresh_token:', !!refreshToken);
      } else if (url.includes('?')) {
        // Parse query parameters
        const queryPart = url.split('?')[1];
        const queryParams = new URLSearchParams(queryPart);
        token = queryParams.get('token');
        type = queryParams.get('type');
        accessToken = queryParams.get('access_token');
        refreshToken = queryParams.get('refresh_token');
        
        console.log('🔗 Parsed from query - type:', type, 'token:', token);
      }
      
      // Determine the callback type
      if (url.includes('reset-password') || type === 'recovery') {
        // Password reset callback
        console.log('🔗 Triggering password-reset-callback');
        mainWindow.webContents.send('password-reset-callback', { 
          token: token || accessToken, 
          type: type || 'recovery',
          accessToken,
          refreshToken
        });
      } else if (url.includes('callback') || type === 'signup') {
        // Email verification callback
        console.log('🔗 Triggering auth-callback');
        mainWindow.webContents.send('auth-callback', { token, type });
      }
      
      // Focus window
      if (mainWindow.isMinimized()) mainWindow.restore();
      mainWindow.focus();
    } catch (error) {
      console.error('❌ Error parsing deep link:', error);
    }
  }
}

/**
 * Create the main application window
 */
function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1200,
    height: 800,
    minWidth: 1000,
    minHeight: 600,
    frame: false, // Frameless for custom window controls
    show: false, // Don't show until ready
    backgroundColor: '#1a1a2e',
    icon: path.join(__dirname, '../dist/Nexus_Parent_icon.png'),
    webPreferences: {
      nodeIntegration: true,
      contextIsolation: false,
      enableRemoteModule: true,
      webSecurity: !isDev // Disable in dev for localhost
    }
  });

  // Load the app
  // Always load from dist folder (email service runs on port 3000)
  mainWindow.loadFile(path.join(__dirname, '../dist/index.html'));

  // Remove default menu
  mainWindow.setMenuBarVisibility(false);
  Menu.setApplicationMenu(null);

  // Show window when ready
  mainWindow.once('ready-to-show', () => {
    mainWindow.show();
    console.log('✅ Main window ready');
  });

  // Open DevTools in development
  if (isDev) {
    mainWindow.webContents.openDevTools();
  }

  // Keyboard shortcuts
  mainWindow.webContents.on('before-input-event', (event, input) => {
    // F12: Toggle DevTools
    if (input.key === 'F12') {
      mainWindow.webContents.toggleDevTools();
    }
    // Ctrl+R: Reload
    if (input.key.toLowerCase() === 'r' && input.control) {
      mainWindow.reload();
      event.preventDefault();
    }
  });

  // Handle window close
  mainWindow.on('closed', () => {
    mainWindow = null;
  });
}

// --- IPC Handlers ---

/**
 * Window Controls
 */
ipcMain.on('window-minimize', () => {
  if (mainWindow) mainWindow.minimize();
});

ipcMain.on('window-maximize', () => {
  if (mainWindow) {
    if (mainWindow.isMaximized()) {
      mainWindow.unmaximize();
    } else {
      mainWindow.maximize();
    }
  }
});

ipcMain.on('window-close', () => {
  if (mainWindow) mainWindow.close();
});

/**
 * Get window maximized state
 */
ipcMain.handle('window-is-maximized', () => {
  return mainWindow ? mainWindow.isMaximized() : false;
});

/**
 * Dialog: Save File
 */
ipcMain.handle('show-save-dialog', async (event, options) => {
  try {
    const result = await dialog.showSaveDialog(mainWindow, options || {});
    return result;
  } catch (error) {
    console.error('Error showing save dialog:', error);
    return { canceled: true, error: error.message };
  }
});

/**
 * Dialog: Open Directory
 */
ipcMain.handle('show-open-directory-dialog', async (event, options) => {
  try {
    const result = await dialog.showOpenDialog(mainWindow, {
      properties: ['openDirectory', 'createDirectory'],
      ...options
    });
    return result;
  } catch (error) {
    console.error('Error showing open dialog:', error);
    return { canceled: true, error: error.message };
  }
});

// --- App Lifecycle ---

/**
 * App ready - create window
 */
app.whenReady().then(() => {
  console.log('🚀 Nexus Parent Desktop starting...');
  createWindow();

  // Handle deep link if app was opened with one
  if (deeplinkingUrl) {
    handleDeepLink(deeplinkingUrl);
  }

  // macOS: Re-create window when dock icon clicked
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow();
    }
  });
});

/**
 * Quit when all windows closed (except macOS)
 */
app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit();
  }
});

/**
 * Cleanup before quit
 */
app.on('before-quit', () => {
  console.log('🧹 Cleaning up...');
  // Add any cleanup logic here
});

/**
 * Handle uncaught exceptions
 */
process.on('uncaughtException', (error) => {
  console.error('❌ Uncaught exception:', error);
});

process.on('unhandledRejection', (reason, promise) => {
  console.error('❌ Unhandled rejection at:', promise, 'reason:', reason);
});

console.log('✅ Electron main process initialized');
