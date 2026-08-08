import { beforeEach, describe, expect, it, vi } from 'vitest'
import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import i18n from '../i18n'
import { CookieBanner } from './CookieBanner'
import { useCookieBannerStore } from '../store/cookieBannerStore'
import { COOKIE_CONSENT_STORAGE_KEY, readCookieConsent } from '../utils/cookieConsent'

vi.mock('../api/privacy', () => ({
  privacyApi: { grantConsent: vi.fn(), revokeConsent: vi.fn() },
}))

function renderBanner(): void {
  render(
    <MemoryRouter>
      <CookieBanner />
    </MemoryRouter>
  )
}

describe('CookieBanner', () => {
  beforeEach(() => {
    localStorage.clear()
    useCookieBannerStore.setState({ reopened: false })
  })

  it('offers accepting and declining with equally weighted buttons', () => {
    renderBanner()

    const accept = screen.getByRole('button', { name: i18n.t('cookieBanner.accept') })
    const decline = screen.getByRole('button', { name: i18n.t('cookieBanner.decline') })

    expect(accept.className).toBe(decline.className)
  })

  it('stores a refusal and hides itself', async () => {
    renderBanner()

    await userEvent.click(screen.getByRole('button', { name: i18n.t('cookieBanner.decline') }))

    expect(readCookieConsent()?.analytics).toBe(false)
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
  })

  it('stays hidden once a decision exists but comes back when settings are reopened', () => {
    localStorage.setItem(
      COOKIE_CONSENT_STORAGE_KEY,
      JSON.stringify({ version: 1, analytics: true, decidedAt: new Date().toISOString() })
    )
    renderBanner()
    expect(screen.queryByRole('dialog')).toBeNull()

    act(() => useCookieBannerStore.getState().reopen())

    expect(screen.getByRole('dialog')).toBeInTheDocument()
  })
})
