import type { ErrorReport, ErrorSink } from '../types'

export function createBeaconSink(endpoint: string): ErrorSink {
  return {
    name: 'beacon',
    send(report: ErrorReport): void {
      try {
        const body = JSON.stringify(report)
        if (navigator.sendBeacon) {
          navigator.sendBeacon(endpoint, new Blob([body], { type: 'application/json' }))
        } else {
          void fetch(endpoint, {
            method: 'POST',
            body,
            keepalive: true,
            headers: { 'Content-Type': 'application/json' },
          })
        }
      } catch {
        // reporting must never throw into the app
      }
    },
  }
}
