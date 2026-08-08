import * as Sentry from '@sentry/react'
import { scrubPii } from '../scrub'
import type { ErrorReport, ErrorSink } from '../types'

interface SentrySinkOptions {
  dsn: string
  environment: string
  release?: string
}

const SELF_MANAGED_INTEGRATIONS = new Set(['GlobalHandlers', 'BrowserApiErrors', 'Breadcrumbs'])

export function createSentrySink({ dsn, environment, release }: SentrySinkOptions): ErrorSink {
  const client = Sentry.init({
    dsn,
    environment,
    release: release || undefined,
    tracesSampleRate: 0,
    sendDefaultPii: false,
    autoSessionTracking: false,
    integrations: (defaults) => [
      ...defaults.filter((integration) => !SELF_MANAGED_INTEGRATIONS.has(integration.name)),
      Sentry.breadcrumbsIntegration({ dom: false, console: false }),
    ],
    beforeBreadcrumb(breadcrumb) {
      breadcrumb.message = scrubPii(breadcrumb.message)
      if (typeof breadcrumb.data?.url === 'string') {
        breadcrumb.data.url = scrubPii(breadcrumb.data.url)
      }
      return breadcrumb
    },
    beforeSend(event) {
      event.message = scrubPii(event.message)
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
    dispose(): void {
      void client?.close()
    },
  }
}
