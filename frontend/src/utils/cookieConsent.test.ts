import { beforeEach, describe, expect, it } from 'vitest'
import {
  COOKIE_CONSENT_STORAGE_KEY,
  COOKIE_CONSENT_TTL_MS,
  COOKIE_INVENTORY_VERSION,
  analyticsAllowed,
  readCookieConsent,
  storeCookieConsent,
} from './cookieConsent'

describe('cookieConsent', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('stores the decision together with the inventory version', () => {
    const consent = storeCookieConsent(true)

    expect(consent.version).toBe(COOKIE_INVENTORY_VERSION)
    expect(readCookieConsent()).toEqual(consent)
    expect(analyticsAllowed()).toBe(true)
  })

  it('treats a decision taken for another inventory version as absent', () => {
    localStorage.setItem(
      COOKIE_CONSENT_STORAGE_KEY,
      JSON.stringify({
        version: COOKIE_INVENTORY_VERSION + 1,
        analytics: true,
        decidedAt: new Date().toISOString(),
      })
    )

    expect(readCookieConsent()).toBeNull()
    expect(analyticsAllowed()).toBe(false)
  })

  it('treats a decision older than the retention period as absent', () => {
    const decidedAt = new Date(Date.now() - COOKIE_CONSENT_TTL_MS - 1).toISOString()
    localStorage.setItem(
      COOKIE_CONSENT_STORAGE_KEY,
      JSON.stringify({ version: COOKIE_INVENTORY_VERSION, analytics: true, decidedAt })
    )

    expect(readCookieConsent()).toBeNull()
  })

  it('keeps a decision that is still within the retention period', () => {
    const decidedAt = new Date(Date.now() - COOKIE_CONSENT_TTL_MS + 60_000).toISOString()
    localStorage.setItem(
      COOKIE_CONSENT_STORAGE_KEY,
      JSON.stringify({ version: COOKIE_INVENTORY_VERSION, analytics: false, decidedAt })
    )

    expect(readCookieConsent()).toEqual({
      version: COOKIE_INVENTORY_VERSION,
      analytics: false,
      decidedAt,
    })
  })

  it('ignores malformed and unparsable payloads', () => {
    localStorage.setItem(COOKIE_CONSENT_STORAGE_KEY, 'not json')
    expect(readCookieConsent()).toBeNull()

    localStorage.setItem(
      COOKIE_CONSENT_STORAGE_KEY,
      JSON.stringify({ version: COOKIE_INVENTORY_VERSION, analytics: 'yes', decidedAt: 'now' })
    )
    expect(readCookieConsent()).toBeNull()
  })
})
