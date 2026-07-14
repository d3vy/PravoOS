import { useAuthStore } from '../../store/authStore'
import { scrubPii } from './scrub'
import { createBeaconSink } from './sinks/beaconSink'
import { createSentrySink } from './sinks/sentrySink'
import { ReportThrottle } from './throttle'
import type { ErrorContext, ErrorReport, ErrorSink } from './types'

const MAX_REPORTS_PER_SESSION = 25
const DEDUPE_WINDOW_MS = 10_000

const environment = import.meta.env.VITE_SENTRY_ENVIRONMENT || 'local'
const release = import.meta.env.VITE_APP_RELEASE || undefined

const throttle = new ReportThrottle({
  maxReports: MAX_REPORTS_PER_SESSION,
  dedupeWindowMs: DEDUPE_WINDOW_MS,
})

let sinks: ErrorSink[] = []

function buildSinks(): ErrorSink[] {
  const configured: ErrorSink[] = []
  const sentryDsn = import.meta.env.VITE_SENTRY_DSN
  if (sentryDsn) {
    configured.push(createSentrySink({ dsn: sentryDsn, environment, release }))
  }
  const beaconEndpoint = import.meta.env.VITE_ERROR_REPORT_URL
  if (beaconEndpoint) {
    configured.push(createBeaconSink(beaconEndpoint))
  }
  return configured
}

function toReport(error: Error, context: ErrorContext): ErrorReport {
  return {
    message: scrubPii(error.message),
    stack: scrubPii(error.stack),
    source: context.source,
    componentStack: context.componentStack,
    url: scrubPii(window.location.href),
    userAgent: navigator.userAgent,
    userId: useAuthStore.getState().user?.userId,
    release,
    environment,
    timestamp: new Date().toISOString(),
  }
}

export function reportError(error: unknown, context: ErrorContext): void {
  if (sinks.length === 0) {
    return
  }
  const normalized = error instanceof Error ? error : new Error(String(error))
  if (!throttle.allow(`${context.source}:${normalized.message}`)) {
    return
  }
  const report = toReport(normalized, context)
  sinks.forEach((sink) => {
    try {
      sink.send(report, normalized)
    } catch {
      // reporting must never throw into the app
    }
  })
}

export function installErrorReporting(): void {
  sinks = buildSinks()
  if (sinks.length === 0) {
    return
  }
  window.addEventListener('error', (event) => {
    reportError(event.error ?? event.message, { source: 'window.onerror' })
  })
  window.addEventListener('unhandledrejection', (event) => {
    reportError(event.reason, { source: 'unhandledrejection' })
  })
}
