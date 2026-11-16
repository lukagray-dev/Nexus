/**
 * @file load-env.js
 * @description Extracts Firebase credentials from the root .env file and generates
 * the TypeScript configuration file.
 *
 * Hey friend! To prevent committing sensitive production credentials into git,
 * this build script reads the local .env at the repository root and generates
 * `src/ts/webrtc/firebase-env.ts` during build time.
 */

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Path to the root .env file
const envPath = path.resolve(__dirname, '../../../.env');
const targetPath = path.resolve(__dirname, '../src/ts/webrtc/firebase-env.ts');

function parseEnv(content) {
  const config = {
    apiKey: '',
    authDomain: '',
    databaseURL: '',
    projectId: '',
    storageBucket: '',
    messagingSenderId: '',
    appId: '',
    measurementId: ''
  };

  const keys = Object.keys(config);

  for (const key of keys) {
    // Regex matching both JS object syntax (key: "value") and standard env syntax (KEY="value")
    const regex = new RegExp(`['"]?${key}['"]?\\s*[:=]\\s*['"]([^'"]+)['"]`, 'i');
    const match = content.match(regex);
    if (match && match[1]) {
      config[key] = match[1];
    }
  }

  return config;
}

try {
  let envContent = '';
  if (fs.existsSync(envPath)) {
    envContent = fs.readFileSync(envPath, 'utf8');
  } else {
    console.warn(`[load-env] Warning: .env file not found at ${envPath}. Using empty defaults.`);
  }

  const parsed = parseEnv(envContent);

  const fileOutput = `/**
 * AUTO-GENERATED from .env file by scripts/load-env.js.
 * DO NOT EDIT OR COMMIT THIS FILE DIRECTLY.
 */

export const FIREBASE_ENV = {
  apiKey: ${JSON.stringify(parsed.apiKey)},
  authDomain: ${JSON.stringify(parsed.authDomain)},
  databaseURL: ${JSON.stringify(parsed.databaseURL)},
  projectId: ${JSON.stringify(parsed.projectId)},
  storageBucket: ${JSON.stringify(parsed.storageBucket)},
  messagingSenderId: ${JSON.stringify(parsed.messagingSenderId)},
  appId: ${JSON.stringify(parsed.appId)},
  measurementId: ${JSON.stringify(parsed.measurementId)}
};
`;

  fs.writeFileSync(targetPath, fileOutput, 'utf8');
  console.log('[load-env] Successfully generated src/ts/webrtc/firebase-env.ts from .env');
} catch (error) {
  console.error('[load-env] Failed to generate firebase-env.ts:', error);
  process.exit(1);
}
