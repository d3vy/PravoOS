import type { Breadcrumb, BrowserOptions, ErrorEvent } from '@sentry/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createSentrySink } from './sentrySink'

interface NamedIntegration {
  name: string
}

const init = vi.fn()

vi.mock('@sentry/react', () => ({
  init: (options: BrowserOptions) => init(options),
  withScope: vi.fn(),
  captureException: vi.fn(),
  breadcrumbsIntegration: (options: unknown) => ({ name: 'Breadcrumbs', options }),
}))

function initOptions(): BrowserOptions {
  createSentrySink({ dsn: 'https://public@sentry.example/1', environment: 'test' })
  return init.mock.calls[init.mock.calls.length - 1][0] as BrowserOptions
}

describe('sentry sink privacy configuration', () => {
  beforeEach(() => {
    init.mockClear()
  })

  it('drops every breadcrumb so only the declared error payload leaves the browser', () => {
    const breadcrumb: Breadcrumb = { category: 'fetch', data: { url: '/api/cases/uuid-1' } }
    expect(initOptions().beforeBreadcrumb?.(breadcrumb, undefined)).toBeNull()
  })

  it('registers no breadcrumb integration at all', () => {
    const resolve = initOptions().integrations as unknown as (
      defaults: NamedIntegration[]
    ) => NamedIntegration[]
    const resolved = resolve([{ name: 'Breadcrumbs' }, { name: 'Dedupe' }])
    expect(resolved.map((integration) => integration.name)).toEqual(['Dedupe'])
  })

  it('keeps tracing and default pii collection off', () => {
    const options = initOptions()
    expect(options.tracesSampleRate).toBe(0)
    expect(options.sendDefaultPii).toBe(false)
  })

  it('scrubs credentials and contacts out of the error payload', () => {
    const event = {
      type: undefined,
      message: 'failed for ivan@example.com',
      exception: { values: [{ value: 'Bearer abcdefgh12345678 rejected' }] },
      request: { url: 'https://app.test/cases?token=eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.c2ln' },
    } satisfies ErrorEvent

    const scrubbed = initOptions().beforeSend?.(event, { event_id: '1', originalException: null })

    expect(scrubbed).not.toBeNull()
    expect((scrubbed as ErrorEvent).message).toBe('failed for iv***@example.com')
    expect(event.exception.values[0].value).toContain('[redacted]')
    expect(event.request.url).toContain('[redacted]')
  })
})
