import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./test/setup.js'],
    include: [
      'dist/js/managers/connection-manager.test.js',
      'dist/js/webrtc/webrtc-manager.test.js'
    ],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'json'],
      include: ['src/**/*.js', 'dist/js/**/*.js'],
      exclude: [
        '**/*.test.js', 
        'node_modules/**', 
        'dist/js/libs/**', 
        'test/**'
      ]
    },
    environmentMatchGlobs: []
  }
});
