/**
 * @file run-tests.js
 * @description Cross-platform Node.js test runner wrapper for Nexus Parent Desktop.
 *
 * Hey friend! Windows cmd.exe does not expand shell globs like `test/*.test.js`,
 * and older Node versions do not expand unquoted globs passed to `node --test`.
 * Resolving `.test.js` files explicitly guarantees `npm test` works everywhere.
 */

import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const testDir = path.resolve(__dirname, '../test');

const testFiles = fs
  .readdirSync(testDir)
  .filter((name) => name.endsWith('.test.js'))
  .sort()
  .map((name) => path.join('test', name));

if (testFiles.length === 0) {
  console.error(`[run-tests] No *.test.js files found in ${testDir}`);
  process.exit(1);
}

const result = spawnSync(process.execPath, ['--test', ...testFiles], {
  cwd: path.resolve(__dirname, '..'),
  stdio: 'inherit',
});

process.exit(result.status ?? 1);
