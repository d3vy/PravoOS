import { useAuthStore } from '../store/authStore'

const endpoint = import.meta.env.VITE_ERROR_REPORT_URL || ''
const MAX_REPORTS_PER_SESSION = 25
const DEDUPE_WINDOW_MS = 10_000

let reportCount = 0
const recentSignatures = new Map<string, number>()

interface ErrorContext {
  source: string
  componentStack?: string
}

interface ErrorPayload {
  message: string
  stack?: string
  source: string
  componentStack?: string
  url: string
  userAgent: string
  userId?: string
  timestamp: string
}

function shouldSend(signature: string): boolean {
  if (!endpoint || reportCount >= MAX_REPORTS_PER_SESSION) {
    return false
  }
  const now = Date.now()
  const lastSeen = recentSignatures.get(signature)
  if (lastSeen && now - lastSeen < DEDUPE_WINDOW_MS) {
    return false
  }
  recentSignatures.set(signature, now)
  return true
}

export function reportError(error: unknown, context: ErrorContext): void {
  const normalized = error instanceof Error ? error : new Error(String(error))
  const signature = `${context.source}:${normalized.message}`
  if (!shouldSend(signature)) {
    return
  }
  reportCount += 1

  const payload: ErrorPayload = {
    message: normalized.message,
    stack: normalized.stack,
    source: context.source,
    componentStack: context.componentStack,
    url: window.location.href,
    userAgent: navigator.userAgent,
    userId: useAuthStore.getState().user?.userId,
    timestamp: new Date().toISOString(),
  }

  try {
    const body = JSON.stringify(payload)
    if (navigator.sendBeacon) {
      navigator.sendBeacon(endpoint, new Blob([body], { type: 'application/json' }))
    } else {
      void fetch(endpoint, { method: 'POST', body, keepalive: true, headers: { 'Content-Type': 'application/json' } })
    }
  } catch {
    // reporting must never throw into the app
  }
}

export function installGlobalErrorReporting(): void {
  if (!endpoint) {
    return
  }
  window.addEventListener('error', (event) => {
    reportError(event.error ?? event.message, { source: 'window.onerror' })
  })
  window.addEventListener('unhandledrejection', (event) => {
    reportError(event.reason, { source: 'unhandledrejection' })
  })
}
