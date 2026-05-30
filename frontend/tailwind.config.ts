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
          bg: '#f8f9fc',
          surface: '#ffffff',
          border: '#dde1e9',
          text: '#0b0f1a',
          secondary: '#64748b',
          accent: '#1e3a5f',
          'accent-hover': '#162c4a',
        },
        dark: {
          bg: '#080c17',
          surface: '#0d1326',
          border: '#1a2540',
          text: '#e8edf8',
          secondary: '#8394ae',
          accent: '#3461c1',
          'accent-hover': '#4472d9',
        },
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', '-apple-system', 'sans-serif'],
      },
      animation: {
        'spin-slow': 'spin 3s linear infinite',
        'pulse-dot': 'pulse 1.4s ease-in-out infinite',
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
