/**
 * @file firebase-config.ts
 * @description Centralized Firebase and WebRTC configuration for Nexus Parent Desktop.
 *
 * Hey friend! Just like in our Android child app, we keep all our cloud infrastructure,
 * Firebase credentials, and STUN server endpoints organized here in one clean module.
 *
 * Since we run inside Tauri's Chromium WebView, we can initialize the modular Firebase Web SDK
 * directly in JavaScript/TypeScript with full type safety and zero Rust IPC overhead.
 */

import { initializeApp, getApps, getApp, type FirebaseApp } from 'firebase/app';
import { getAuth, type Auth } from 'firebase/auth';
import { getDatabase, type Database } from 'firebase/database';
import { FIREBASE_ENV } from './firebase-env.js';

/**
 * Firebase project credentials loaded from the environment (.env).
 */
export const FIREBASE_CONFIG = FIREBASE_ENV;

/**
 * STUN servers for WebRTC ICE candidate discovery.
 * These allow the desktop parent to discover its public IP/port and establish
 * peer-to-peer connectivity even through symmetric/cone NATs and firewalls.
 */
export const STUN_SERVERS: RTCIceServer[] = [
  { urls: 'stun:stun.l.google.com:19302' },
  { urls: 'stun:stun1.l.google.com:19302' },
  { urls: 'stun:stun2.l.google.com:19302' },
  { urls: 'stun:stun3.l.google.com:19302' },
  { urls: 'stun:stun4.l.google.com:19302' }
];

/**
 * Label for the primary bidirectional control DataChannel.
 */
export const DATA_CHANNEL_LABEL = 'nexus-control';

/**
 * Lazily initializes and returns the singleton FirebaseApp instance.
 */
export function getFirebaseApp(): FirebaseApp {
  const existingApps = getApps();
  if (existingApps.length > 0) {
    return getApp();
  }
  return initializeApp(FIREBASE_CONFIG);
}

/**
 * Returns the Firebase Authentication service instance.
 */
export function getFirebaseAuth(): Auth {
  return getAuth(getFirebaseApp());
}

/**
 * Returns the Firebase Realtime Database service instance.
 */
export function getFirebaseDatabase(): Database {
  return getDatabase(getFirebaseApp());
}
