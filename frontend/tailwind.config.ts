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
          bg: '#f7f8fb',
          surface: '#ffffff',
          'surface-elevated': '#fbfcfe',
          border: '#e3e7ef',
          text: '#0b1220',
          secondary: '#5b6678',
          accent: '#1e3a5f',
          'accent-hover': '#15293f',
          gold: '#9c7c46',
          'gold-soft': '#b08d57',
        },
        dark: {
          bg: '#070b15',
          surface: '#0d1326',
          'surface-elevated': '#121a31',
          border: '#1d2842',
          text: '#e9eef8',
          secondary: '#8b9bb6',
          accent: '#3461c1',
          'accent-hover': '#4773da',
          gold: '#c9a86a',
          'gold-soft': '#d8bd86',
        },
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', '-apple-system', 'sans-serif'],
        display: ['Lora', 'Georgia', 'serif'],
      },
      borderWidth: {
        '3': '3px',
      },
      boxShadow: {
        card: '0 1px 2px rgba(11, 18, 32, 0.04), 0 8px 24px -12px rgba(11, 18, 32, 0.10)',
        'card-dark': '0 1px 2px rgba(0, 0, 0, 0.4), 0 12px 32px -16px rgba(0, 0, 0, 0.6)',
      },
      letterSpacing: {
        widest: '0.18em',
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
