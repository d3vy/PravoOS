/// <reference types="vitest" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { ViteFaviconsPlugin } from 'vite-plugin-favicon2'
import { VitePWA } from 'vite-plugin-pwa'

export default defineConfig({
  plugins: [
    react(),
    ViteFaviconsPlugin({
      logo: './public/favicon.svg',
      inject: true,
      favicons: {
        appName: 'PravoOS',
        appDescription: 'AI-платформа для юристов',
        background: '#1e3a5f',
        theme_color: '#1e3a5f',
        icons: {
          coast: false,
          yandex: false,
          windows: false,
        },
      },
    }),
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
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts'],
  },
})
