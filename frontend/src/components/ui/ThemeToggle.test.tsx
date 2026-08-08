import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import '../../i18n'
import { ThemeToggle } from './ThemeToggle'
import * as useThemeModule from '../../hooks/useTheme'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('ThemeToggle', () => {
  it('calls toggleTheme when clicked', async () => {
    const toggleTheme = vi.fn()
    vi.spyOn(useThemeModule, 'useTheme').mockReturnValue({ theme: 'light', toggleTheme })
    render(<ThemeToggle />)
    await userEvent.click(screen.getByRole('button'))
    expect(toggleTheme).toHaveBeenCalledTimes(1)
  })

  it('labels the button to switch to dark mode while currently light', () => {
    vi.spyOn(useThemeModule, 'useTheme').mockReturnValue({ theme: 'light', toggleTheme: vi.fn() })
    render(<ThemeToggle />)
    expect(screen.getByRole('button')).toHaveAccessibleName(/тёмн|dark/i)
  })

  it('labels the button to switch to light mode while currently dark', () => {
    vi.spyOn(useThemeModule, 'useTheme').mockReturnValue({ theme: 'dark', toggleTheme: vi.fn() })
    render(<ThemeToggle />)
    expect(screen.getByRole('button')).toHaveAccessibleName(/свет|light/i)
  })
})
