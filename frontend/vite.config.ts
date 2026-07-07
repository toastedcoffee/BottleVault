import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import path from 'path'

export default defineConfig({
  plugins: [react()],
  build: {
    // Vite 8 / Rolldown. Split the eager framework libs into their own chunk so
    // the entry chunk stays small and these rarely-changing deps cache well.
    // Explicit allowlist (Windows-safe [\\/] separators) — deliberately NOT a
    // catch-all /node_modules/, which would merge async-only recharts &
    // html5-qrcode into this eager chunk and undo the route-level lazy split.
    rolldownOptions: {
      output: {
        codeSplitting: {
          groups: [
            {
              name: 'react-vendor',
              test: /node_modules[\\/](react|react-dom|scheduler|react-router|react-router-dom|@tanstack[\\/]react-query|axios)[\\/]/,
              priority: 1,
            },
          ],
        },
      },
    },
  },
  test: {
    // globals so React Testing Library registers its automatic afterEach
    // cleanup and act() environment; tests still import describe/it/expect
    // explicitly.
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: `http://localhost:${process.env.API_PORT || '8085'}`,
        changeOrigin: true,
      },
    },
  },
})
