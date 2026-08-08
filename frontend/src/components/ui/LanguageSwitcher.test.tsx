import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import i18n from '../../i18n'
import { LanguageSwitcher } from './LanguageSwitcher'
import * as useLanguageModule from '../../hooks/useLanguage'

beforeEach(async () => {
  await i18n.changeLanguage('ru')
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

function mockUseLanguage(setLanguage = vi.fn()) {
  vi.spyOn(useLanguageModule, 'useLanguage').mockReturnValue({
    language: 'ru',
    supportedLanguages: ['ru', 'en'],
    setLanguage,
  })
  return setLanguage
}

describe('LanguageSwitcher', () => {
  it('menu is closed by default', () => {
    mockUseLanguage()
    render(<LanguageSwitcher />)
    expect(screen.queryByRole('menu')).toBeNull()
  })

  it('opens the menu on click and lists supported languages', async () => {
    mockUseLanguage()
    render(<LanguageSwitcher />)
    await userEvent.click(screen.getByRole('button'))
    expect(screen.getByRole('menu')).toBeInTheDocument()
    expect(screen.getAllByRole('menuitemradio')).toHaveLength(2)
  })

  it('marks the current language as checked', async () => {
    mockUseLanguage()
    render(<LanguageSwitcher />)
    await userEvent.click(screen.getByRole('button'))
    const items = screen.getAllByRole('menuitemradio')
    expect(items[0]).toHaveAttribute('aria-checked', 'true')
    expect(items[1]).toHaveAttribute('aria-checked', 'false')
  })

  it('selects a language, closes the menu and calls setLanguage', async () => {
    const setLanguage = mockUseLanguage()
    render(<LanguageSwitcher />)
    await userEvent.click(screen.getByRole('button'))
    await userEvent.click(screen.getAllByRole('menuitemradio')[1])
    expect(setLanguage).toHaveBeenCalledWith('en')
    expect(screen.queryByRole('menu')).toBeNull()
  })

  it('closes the menu when clicking outside', async () => {
    mockUseLanguage()
    render(
      <div>
        <LanguageSwitcher />
        <button type="button">outside</button>
      </div>
    )
    await userEvent.click(screen.getByRole('button', { name: /язык/i }))
    expect(screen.getByRole('menu')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'outside' }))
    await waitFor(() => expect(screen.queryByRole('menu')).toBeNull())
  })

  it('closes the menu on Escape', async () => {
    mockUseLanguage()
    render(<LanguageSwitcher />)
    await userEvent.click(screen.getByRole('button'))
    expect(screen.getByRole('menu')).toBeInTheDocument()
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await waitFor(() => expect(screen.queryByRole('menu')).toBeNull())
  })
})
