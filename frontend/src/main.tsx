import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider, MutationCache } from '@tanstack/react-query'
import App from './App'
import { ErrorBoundary } from './components/ErrorBoundary'
import { ToastViewport } from './components/ui/Toast'
import { ConfirmDialogHost } from './components/ui/ConfirmDialog'
import { useToastStore } from './store/toastStore'
import { getErrorMessage } from './utils/errors'
import { installErrorReporting } from './lib/observability'
import { registerServiceWorker } from './pwa/registerServiceWorker'
import i18n from './i18n'
import { normalizeLanguage } from './i18n/config'
import './index.css'

installErrorReporting()
registerServiceWorker()

declare module '@tanstack/react-query' {
  interface Register {
    mutationMeta: {
      suppressErrorToast?: boolean
    }
  }
}

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: 1,
    },
  },
  mutationCache: new MutationCache({
    onError: (error, _variables, _context, mutation) => {
      if (mutation.meta?.suppressErrorToast) return
      useToastStore.getState().push({
        variant: 'error',
        message: getErrorMessage(error, i18n.t('common.genericError')),
      })
    },
  }),
})

// Apply persisted theme before first render to prevent flash
const storedTheme = localStorage.getItem('pravoos-theme')
if (storedTheme === 'dark' || (!storedTheme && window.matchMedia('(prefers-color-scheme: dark)').matches)) {
  document.documentElement.classList.add('dark')
}

document.documentElement.lang = normalizeLanguage(i18n.language)

const rootElement = document.getElementById('root')
if (!rootElement) throw new Error('Root element not found')

ReactDOM.createRoot(rootElement).render(
  <React.StrictMode>
    <ErrorBoundary>
      <BrowserRouter>
        <QueryClientProvider client={queryClient}>
          <App />
          <ToastViewport />
          <ConfirmDialogHost />
        </QueryClientProvider>
      </BrowserRouter>
    </ErrorBoundary>
  </React.StrictMode>
)
