import { beforeEach, describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import i18n from '../../i18n'
import { LegalLinks } from './LegalLinks'
import { useCookieBannerStore } from '../../store/cookieBannerStore'

describe('LegalLinks', () => {
  beforeEach(() => {
    useCookieBannerStore.setState({ reopened: false })
  })

  it('links to every published legal document', () => {
    render(
      <MemoryRouter>
        <LegalLinks />
      </MemoryRouter>
    )

    const hrefs = screen
      .getAllByRole('link')
      .map((link) => link.getAttribute('href'))
      .sort()

    expect(hrefs).toEqual([
      '/legal/consent',
      '/legal/cookies',
      '/legal/cross-border',
      '/legal/privacy',
    ])
  })

  it('reopens the cookie banner from anywhere it is mounted', async () => {
    render(
      <MemoryRouter>
        <LegalLinks />
      </MemoryRouter>
    )

    await userEvent.click(
      screen.getByRole('button', { name: i18n.t('legal.navCookieSettings') })
    )

    expect(useCookieBannerStore.getState().reopened).toBe(true)
  })
})
