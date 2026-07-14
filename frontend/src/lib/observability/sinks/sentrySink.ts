import * as Sentry from '@sentry/react'
import { scrubPii } from '../scrub'
import type { ErrorReport, ErrorSink } from '../types'

interface SentrySinkOptions {
  dsn: string
  environment: string
  release?: string
}

export function createSentrySink({ dsn, environment, release }: SentrySinkOptions): ErrorSink {
  Sentry.init({
    dsn,
    environment,
    release: release || undefined,
    tracesSampleRate: 0,
    sendDefaultPii: false,
    beforeSend(event) {
      if (event.message) {
        event.message = scrubPii(event.message)
      }
      event.exception?.values?.forEach((value) => {
        value.value = scrubPii(value.value)
      })
      if (event.request?.url) {
        event.request.url = scrubPii(event.request.url)
      }
      return event
    },
  })

  return {
    name: 'sentry',
    send(report: ErrorReport, error: Error): void {
      Sentry.withScope((scope) => {
        scope.setUser(report.userId ? { id: report.userId } : null)
        scope.setTag('source', report.source)
        scope.setContext('ui', {
          url: report.url,
          componentStack: report.componentStack,
        })
        Sentry.captureException(error)
      })
    },
  }
}
