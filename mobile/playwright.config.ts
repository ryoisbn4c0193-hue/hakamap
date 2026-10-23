import { defineConfig } from '@playwright/test';
const base = (process.env.HAKAMAP_BASE_PATH ?? '').replace(/\/$/, '') + '/';
export default defineConfig({
  testDir: './e2e',
  use: {
    isMobile: true,
    hasTouch: true,
    baseURL: 'http://127.0.0.1:4173' + base,
    viewport: { width: 390, height: 844 },
    launchOptions: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE
      ? {
          executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE,
        }
      : {},
  },
  webServer: {
    command: 'node scripts/serve-pwa.mjs',
    url: 'http://127.0.0.1:4173' + base,
    reuseExistingServer: false,
  },
});
