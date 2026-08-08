export const COOKIE_CONSENT_STORAGE_KEY = 'pravoos-cookie-consent'

export const COOKIE_INVENTORY_VERSION = 1

export const COOKIE_CONSENT_TTL_MS = 365 * 24 * 60 * 60 * 1000

export interface CookieConsent {
  version: number
  analytics: boolean
  decidedAt: string
}

function isExpired(decidedAt: string, now: number): boolean {
  const decided = Date.parse(decidedAt)
  return Number.isNaN(decided) || now - decided >= COOKIE_CONSENT_TTL_MS
}

export function readCookieConsent(now: number = Date.now()): CookieConsent | null {
  try {
    const raw = localStorage.getItem(COOKIE_CONSENT_STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as Partial<CookieConsent>
    if (typeof parsed.analytics !== 'boolean' || typeof parsed.decidedAt !== 'string') {
      return null
    }
    if (parsed.version !== COOKIE_INVENTORY_VERSION || isExpired(parsed.decidedAt, now)) {
      return null
    }
    return { version: parsed.version, analytics: parsed.analytics, decidedAt: parsed.decidedAt }
  } catch {
    return null
  }
}

export function storeCookieConsent(analytics: boolean): CookieConsent {
  const consent: CookieConsent = {
    version: COOKIE_INVENTORY_VERSION,
    analytics,
    decidedAt: new Date().toISOString(),
  }
  try {
    localStorage.setItem(COOKIE_CONSENT_STORAGE_KEY, JSON.stringify(consent))
  } catch {
    return consent
  }
  return consent
}

export function analyticsAllowed(): boolean {
  return readCookieConsent()?.analytics === true
}
