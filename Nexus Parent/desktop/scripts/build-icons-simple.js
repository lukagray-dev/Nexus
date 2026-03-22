#!/usr/bin/env node

/**
 * Simple Icon Builder Script (Fallback)
 * Uses only sharp to generate PNG icons
 * electron-builder can work with PNG files if ICO/ICNS generation fails
 */

const fs = require('fs');
const path = require('path');

const SOURCE_ICON = path.join(__dirname, '../dist/Nexus_Parent_icon.png');
const BUILD_DIR = path.join(__dirname, '../build');
const ICONS_DIR = path.join(BUILD_DIR, 'icons');

console.log('🎨 Building application icons (simple mode)...\n');

// Check if source icon exists
if (!fs.existsSync(SOURCE_ICON)) {
  console.error('❌ Source icon not found:', SOURCE_ICON);
  console.error('Please ensure Nexus_Parent_icon.png exists in the dist/ directory');
  process.exit(1);
}

// Create directories
if (!fs.existsSync(BUILD_DIR)) {
  fs.mkdirSync(BUILD_DIR, { recursive: true });
}
if (!fs.existsSync(ICONS_DIR)) {
  fs.mkdirSync(ICONS_DIR, { recursive: true });
}

// Check for sharp
let sharp;
try {
  sharp = require('sharp');
} catch (error) {
  console.error('❌ sharp package not found.');
  console.error('Run: npm install --save-dev sharp\n');
  process.exit(1);
}

async function buildIcons() {
  try {
    const sourceBuffer = fs.readFileSync(SOURCE_ICON);
    
    if (!sourceBuffer || sourceBuffer.length === 0) {
      throw new Error('Source icon file is empty or invalid');
    }
    
    console.log(`✅ Source icon loaded (${(sourceBuffer.length / 1024).toFixed(2)} KB)\n`);
    
    // Generate 256x256 PNG for Windows (electron-builder can use this)
    console.log('📦 Generating Windows icon (256x256 PNG)...');
    const windowsIconPath = path.join(BUILD_DIR, 'icon.png');
    await sharp(sourceBuffer)
      .resize(256, 256, { 
        fit: 'contain', 
        background: { r: 0, g: 0, b: 0, alpha: 0 } 
      })
      .png()
      .toFile(windowsIconPath);
    
    const winStats = fs.statSync(windowsIconPath);
    console.log(`✅ icon.png created (${(winStats.size / 1024).toFixed(2)} KB)`);
    
    // Generate 512x512 PNG for macOS (electron-builder can use this)
    console.log('📦 Generating macOS icon (512x512 PNG)...');
    const macIconPath = path.join(BUILD_DIR, 'icon-mac.png');
    await sharp(sourceBuffer)
      .resize(512, 512, { 
        fit: 'contain', 
        background: { r: 0, g: 0, b: 0, alpha: 0 } 
      })
      .png()
      .toFile(macIconPath);
    
    const macStats = fs.statSync(macIconPath);
    console.log(`✅ icon-mac.png created (${(macStats.size / 1024).toFixed(2)} KB)`);
    
    // Generate Linux PNG set
    console.log('📦 Generating Linux PNG icons...');
    const linuxSizes = [16, 24, 32, 48, 64, 128, 256, 512];
    
    for (const size of linuxSizes) {
      const outputPath = path.join(ICONS_DIR, `${size}x${size}.png`);
      await sharp(sourceBuffer)
        .resize(size, size, { 
          fit: 'contain', 
          background: { r: 0, g: 0, b: 0, alpha: 0 } 
        })
        .png()
        .toFile(outputPath);
      
      const stats = fs.statSync(outputPath);
      console.log(`  ✓ ${size}x${size}.png (${(stats.size / 1024).toFixed(2)} KB)`);
    }
    
    console.log('✅ Linux PNG icons created');
    
    console.log('\n✨ Icon generation complete!');
    console.log(`📁 Output directory: ${BUILD_DIR}`);
    console.log('\n💡 Note: Using PNG format. For better results, install png2icons:');
    console.log('   npm install --save-dev png2icons');
    console.log('   Then run: node scripts/build-icons.js');
    
  } catch (error) {
    console.error('❌ Error generating icons:', error.message);
    console.error('\nStack trace:', error.stack);
    process.exit(1);
  }
}

buildIcons();
