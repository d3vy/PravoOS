import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

function setMatchMedia(prefersDark: boolean): void {
  window.matchMedia = vi.fn().mockReturnValue({
    matches: prefersDark,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  }) as unknown as typeof window.matchMedia
}

describe('useTheme', () => {
  beforeEach(() => {
    localStorage.clear()
    document.documentElement.classList.remove('dark')
    vi.resetModules()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('defaults to the system preference when nothing is stored (dark)', async () => {
    setMatchMedia(true)
    const { useTheme } = await import('./useTheme')
    const { result } = renderHook(() => useTheme())
    expect(result.current.theme).toBe('dark')
    expect(document.documentElement.classList.contains('dark')).toBe(true)
  })

  it('defaults to light when the system does not prefer dark', async () => {
    setMatchMedia(false)
    const { useTheme } = await import('./useTheme')
    const { result } = renderHook(() => useTheme())
    expect(result.current.theme).toBe('light')
    expect(document.documentElement.classList.contains('dark')).toBe(false)
  })

  it('prefers a stored theme over the system preference', async () => {
    setMatchMedia(true)
    localStorage.setItem('pravoos-theme', 'light')
    const { useTheme } = await import('./useTheme')
    const { result } = renderHook(() => useTheme())
    expect(result.current.theme).toBe('light')
  })

  it('toggleTheme flips the theme, updates the DOM class and persists it', async () => {
    setMatchMedia(false)
    const { useTheme } = await import('./useTheme')
    const { result } = renderHook(() => useTheme())

    act(() => {
      result.current.toggleTheme()
    })

    expect(result.current.theme).toBe('dark')
    expect(document.documentElement.classList.contains('dark')).toBe(true)
    expect(localStorage.getItem('pravoos-theme')).toBe('dark')

    act(() => {
      result.current.toggleTheme()
    })

    expect(result.current.theme).toBe('light')
    expect(document.documentElement.classList.contains('dark')).toBe(false)
    expect(localStorage.getItem('pravoos-theme')).toBe('light')
  })
})
