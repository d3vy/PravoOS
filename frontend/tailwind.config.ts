import type { Config } from 'tailwindcss'

const config: Config = {
  darkMode: 'class',
  content: [
    './index.html',
    './src/**/*.{js,ts,jsx,tsx}',
  ],
  theme: {
    extend: {
      colors: {
        light: {
          bg: '#ffffff',
          surface: '#fafafa',
          'surface-elevated': '#f4f4f5',
          border: '#e4e4e7',
          text: '#09090b',
          secondary: '#71717a',
          accent: '#09090b',
          'accent-hover': '#27272a',
        },
        dark: {
          bg: '#09090b',
          surface: '#111113',
          'surface-elevated': '#1c1c1f',
          border: '#27272a',
          text: '#fafafa',
          secondary: '#a1a1aa',
          accent: '#fafafa',
          'accent-hover': '#e4e4e7',
        },
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', '-apple-system', 'sans-serif'],
      },
      borderWidth: {
        '3': '3px',
      },
      boxShadow: {
        card: '0 1px 3px rgba(0,0,0,0.06), 0 4px 16px -4px rgba(0,0,0,0.08)',
        'card-dark': '0 1px 3px rgba(0,0,0,0.5), 0 8px 24px -8px rgba(0,0,0,0.7)',
        'sm-dark': '0 1px 2px rgba(0,0,0,0.4)',
      },
      animation: {
        'spin-slow': 'spin 3s linear infinite',
        'fade-in-up': 'fade-in-up 0.5s ease-out both',
      },
      keyframes: {
        'fade-in-up': {
          '0%': { opacity: '0', transform: 'translateY(24px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
      },
    },
  },
  plugins: [],
}

export default config
