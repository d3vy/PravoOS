import type { Config } from 'tailwindcss'

function token(name: string): string {
  return `rgb(var(--${name}) / <alpha-value>)`
}

const config: Config = {
  darkMode: 'class',
  content: [
    './index.html',
    './src/**/*.{js,ts,jsx,tsx}',
  ],
  theme: {
    extend: {
      colors: {
        bg: token('bg'),
        surface: {
          DEFAULT: token('surface'),
          2: token('surface-2'),
        },
        overlay: token('overlay'),
        line: token('line'),
        fg: {
          DEFAULT: token('fg'),
          muted: token('fg-muted'),
        },
        accent: {
          DEFAULT: token('accent'),
          solid: token('accent-solid'),
          'solid-hover': token('accent-solid-hover'),
          fg: token('accent-fg'),
        },
        success: {
          DEFAULT: token('success'),
          soft: token('success-soft'),
        },
        warning: {
          DEFAULT: token('warning'),
          soft: token('warning-soft'),
        },
        danger: {
          DEFAULT: token('danger'),
          soft: token('danger-soft'),
        },
        info: {
          DEFAULT: token('info'),
          soft: token('info-soft'),
        },
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', '-apple-system', 'sans-serif'],
        display: ['Fraunces', 'ui-serif', 'Georgia', 'serif'],
        script: ['Caveat', 'cursive'],
      },
      borderWidth: {
        '3': '3px',
      },
      boxShadow: {
        card: 'var(--shadow-card)',
      },
      animation: {
        'spin-slow': 'spin 3s linear infinite',
        'fade-in-up': 'fade-in-up 0.5s ease-out both',
        'progress-bar': 'progress-bar 1.1s ease-in-out infinite',
      },
      keyframes: {
        'fade-in-up': {
          '0%': { opacity: '0', transform: 'translateY(24px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        'progress-bar': {
          '0%': { transform: 'translateX(-100%)' },
          '100%': { transform: 'translateX(300%)' },
        },
      },
    },
  },
  plugins: [],
}

export default config
