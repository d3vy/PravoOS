import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { ViteFaviconsPlugin } from 'vite-plugin-favicon2'

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
  ],
  server: {
    port: 3000,
  },
})
