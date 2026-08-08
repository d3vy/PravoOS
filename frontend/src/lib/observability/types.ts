export interface ErrorContext {
  source: string
  componentStack?: string
}

export interface ErrorReport {
  message: string
  stack?: string
  source: string
  componentStack?: string
  url: string
  userAgent: string
  userId?: string
  release?: string
  environment: string
  timestamp: string
}

export interface ErrorSink {
  readonly name: string
  send(report: ErrorReport, error: Error): void
  dispose?(): void
}
