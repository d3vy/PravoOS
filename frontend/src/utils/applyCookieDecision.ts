import { privacyApi } from '../api/privacy'
import { refreshErrorReportingConsent } from '../lib/observability'
import { useAuthStore } from '../store/authStore'
import { markCookieConsentSynced, readCookieConsent, storeCookieConsent } from './cookieConsent'

function record(analytics: boolean): void {
  if (!useAuthStore.getState().user) {
    return
  }
  const request = analytics
    ? privacyApi.grantConsent('ANALYTICS_COOKIES')
    : privacyApi.revokeConsent('ANALYTICS_COOKIES')
  void request.then(markCookieConsentSynced).catch(() => undefined)
}

export function applyCookieDecision(analytics: boolean): void {
  storeCookieConsent(analytics)
  refreshErrorReportingConsent()
  record(analytics)
}

export function syncCookieDecision(): void {
  const consent = readCookieConsent()
  if (consent && !consent.synced) {
    record(consent.analytics)
  }
}
