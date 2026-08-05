export const COOKIE_CONSENT_STORAGE_KEY = 'pravoos-cookie-consent'

export interface CookieConsent {
  analytics: boolean
  decidedAt: string
}

export function readCookieConsent(): CookieConsent | null {
  try {
    const raw = localStorage.getItem(COOKIE_CONSENT_STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as Partial<CookieConsent>
    if (typeof parsed.analytics !== 'boolean' || typeof parsed.decidedAt !== 'string') {
      return null
    }
    return { analytics: parsed.analytics, decidedAt: parsed.decidedAt }
  } catch {
    return null
  }
}

export function storeCookieConsent(analytics: boolean): CookieConsent {
  const consent: CookieConsent = { analytics, decidedAt: new Date().toISOString() }
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
