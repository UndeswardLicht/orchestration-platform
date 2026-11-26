import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

const controlUrl = process.env.LTP_CONTROL_URL ?? 'http://localhost:8080'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // Same paths Caddy proxies in production (deploy/Caddyfile)
    proxy: {
      '/api': controlUrl,
      '/v3/api-docs': controlUrl,
      '/swagger-ui': controlUrl,
      '/actuator/health': controlUrl,
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    css: true,
    // Page tests type, upload and wait for several renders; 5 s is tight on a busy CI runner
    testTimeout: 20000,
  },
})
