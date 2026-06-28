import { Component, ErrorInfo, ReactNode } from 'react'
import { reportError } from '../lib/errorReporter'

interface ErrorBoundaryProps {
  children: ReactNode
}

interface ErrorBoundaryState {
  hasError: boolean
}

export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { hasError: false }

  static getDerivedStateFromError(): ErrorBoundaryState {
    return { hasError: true }
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo): void {
    console.error('Unhandled UI error', error, errorInfo)
    reportError(error, { source: 'ErrorBoundary', componentStack: errorInfo.componentStack ?? undefined })
  }

  private handleReload = (): void => {
    this.setState({ hasError: false })
    window.location.assign('/')
  }

  render(): ReactNode {
    if (!this.state.hasError) {
      return this.props.children
    }

    return (
      <div className="flex min-h-screen flex-col items-center justify-center gap-4 px-6 text-center">
        <h1 className="text-2xl font-semibold">Что-то пошло не так</h1>
        <p className="max-w-md text-sm text-gray-500 dark:text-gray-400">
          Произошла непредвиденная ошибка интерфейса. Мы уже знаем о ней — попробуйте обновить страницу.
        </p>
        <button
          onClick={this.handleReload}
          className="rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
        >
          На главную
        </button>
      </div>
    )
  }
}
