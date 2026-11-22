import { defineConfig, devices } from '@playwright/test'

/** NFR-08: every e2e test runs at 360, 768 and 1280 px. */
const viewports = [
  { name: 'mobile-360', width: 360, height: 740 },
  { name: 'tablet-768', width: 768, height: 1024 },
  { name: 'desktop-1280', width: 1280, height: 800 },
]

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['github'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env.LTP_WEB_URL ?? 'http://localhost:5173',
    trace: 'on-first-retry',
  },
  projects: viewports.map(({ name, width, height }) => ({
    name,
    use: { ...devices['Desktop Chrome'], viewport: { width, height } },
  })),
  webServer: process.env.LTP_WEB_URL
    ? undefined
    : { command: 'npm run dev', url: 'http://localhost:5173', reuseExistingServer: !process.env.CI },
})
