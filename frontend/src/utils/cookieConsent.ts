export const COOKIE_CONSENT_STORAGE_KEY = 'pravoos-cookie-consent'

export const COOKIE_INVENTORY_VERSION = 1

export const COOKIE_CONSENT_TTL_MS = 365 * 24 * 60 * 60 * 1000

export interface CookieConsent {
  version: number
  analytics: boolean
  decidedAt: string
  synced?: boolean
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
    return {
      version: parsed.version,
      analytics: parsed.analytics,
      decidedAt: parsed.decidedAt,
      synced: parsed.synced === true,
    }
  } catch {
    return null
  }
}

function write(consent: CookieConsent): CookieConsent {
  try {
    localStorage.setItem(COOKIE_CONSENT_STORAGE_KEY, JSON.stringify(consent))
  } catch {
    return consent
  }
  return consent
}

export function storeCookieConsent(analytics: boolean): CookieConsent {
  return write({
    version: COOKIE_INVENTORY_VERSION,
    analytics,
    decidedAt: new Date().toISOString(),
    synced: false,
  })
}

export function markCookieConsentSynced(): void {
  const consent = readCookieConsent()
  if (consent && !consent.synced) {
    write({ ...consent, synced: true })
  }
}

export function analyticsAllowed(): boolean {
  return readCookieConsent()?.analytics === true
}
