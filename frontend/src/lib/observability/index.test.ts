import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { COOKIE_CONSENT_STORAGE_KEY, storeCookieConsent } from '../../utils/cookieConsent'
import type { ErrorSink } from './types'

const dispose = vi.fn()
const send = vi.fn()

vi.mock('./sinks/sentrySink', () => ({
  createSentrySink: (): ErrorSink => ({ name: 'sentry', send, dispose }),
}))

describe('error reporting consent', () => {
  beforeEach(async () => {
    vi.stubEnv('VITE_SENTRY_DSN', 'https://public@sentry.example/1')
    localStorage.removeItem(COOKIE_CONSENT_STORAGE_KEY)
    vi.resetModules()
    dispose.mockClear()
    send.mockClear()
  })

  afterEach(() => {
    vi.unstubAllEnvs()
  })

  it('does not build a sink before consent is given', async () => {
    const { installErrorReporting, reportError } = await import('./index')
    installErrorReporting()
    reportError(new Error('boom'), { source: 'test' })
    expect(send).not.toHaveBeenCalled()
  })

  it('disposes the sink when consent is withdrawn so nothing is sent afterwards', async () => {
    const { installErrorReporting, refreshErrorReportingConsent, reportError } =
      await import('./index')
    storeCookieConsent(true)
    installErrorReporting()
    reportError(new Error('boom'), { source: 'test' })
    expect(send).toHaveBeenCalledTimes(1)

    storeCookieConsent(false)
    refreshErrorReportingConsent()
    expect(dispose).toHaveBeenCalledTimes(1)

    reportError(new Error('boom again'), { source: 'test' })
    expect(send).toHaveBeenCalledTimes(1)
  })
})
