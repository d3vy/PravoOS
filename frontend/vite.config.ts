/// <reference types="vitest" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'

export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      strategies: 'injectManifest',
      srcDir: 'src',
      filename: 'sw.ts',
      registerType: 'autoUpdate',
      injectRegister: null,
      includeAssets: ['favicon.svg', 'icons/*.png'],
      manifest: {
        name: 'PravoOS — AI-ассистент для юристов',
        short_name: 'PravoOS',
        description: 'AI-платформа для юристов: дела, дедлайны, документы и переписка с клиентами.',
        lang: 'ru',
        start_url: '/dashboard',
        scope: '/',
        display: 'standalone',
        background_color: '#09090b',
        theme_color: '#1e3a5f',
        categories: ['business', 'productivity'],
        icons: [
          { src: '/icons/pwa-192x192.png', sizes: '192x192', type: 'image/png' },
          { src: '/icons/pwa-512x512.png', sizes: '512x512', type: 'image/png' },
          {
            src: '/icons/pwa-maskable-512x512.png',
            sizes: '512x512',
            type: 'image/png',
            purpose: 'maskable',
          },
        ],
      },
      injectManifest: {
        globPatterns: ['**/*.{js,css,html,svg,png,woff2}'],
        maximumFileSizeToCacheInBytes: 5 * 1024 * 1024,
      },
      devOptions: {
        enabled: false,
        type: 'module',
      },
    }),
  ],
  server: {
    port: 3000,
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: (id) => {
          if (!id.includes('node_modules')) return undefined
          if (
            /[\\/]node_modules[\\/](react|react-dom|scheduler|react-router|react-router-dom|@remix-run[\\/]router)[\\/]/.test(
              id
            )
          )
            return 'react'
          if (id.includes('/node_modules/@sentry')) return 'sentry'
          if (id.includes('/node_modules/framer-motion') || id.includes('/node_modules/motion'))
            return 'motion'
          if (id.includes('i18next')) return 'i18n'
          if (id.includes('/node_modules/@tanstack')) return 'query'
          return 'vendor'
        },
      },
    },
  },
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.ts', 'src/**/*.test.tsx'],
    setupFiles: ['src/test/setup.ts'],
    // v8 coverage instrumentation slows down slower async tests enough to trip the 5s default under --coverage
    testTimeout: 10000,
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'lcov'],
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/**/*.test.{ts,tsx}', 'src/test/**', 'src/main.tsx', 'src/sw.ts'],
      // Порог поднимать постепенно по мере роста покрытия (см. testing.md, раздел 8)
      thresholds: {
        lines: 1,
        statements: 1,
        functions: 1,
        branches: 1,
      },
    },
  },
})
