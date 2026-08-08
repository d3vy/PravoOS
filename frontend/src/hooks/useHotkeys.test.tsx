import { act, cleanup, renderHook } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ReactNode } from 'react'

const { startTimerMock, stopTimerMock } = vi.hoisted(() => ({
  startTimerMock: vi.fn(),
  stopTimerMock: vi.fn(),
}))

vi.mock('../api/time', () => ({
  timeApi: {
    startTimer: startTimerMock,
    stopTimer: stopTimerMock,
  },
}))

const navigateMock = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom')
  return { ...actual, useNavigate: () => navigateMock }
})

import { useHotkeys } from './useHotkeys'
import { useCommandPaletteStore } from '../store/commandPaletteStore'
import { useShortcutsDialogStore } from '../store/shortcutsDialogStore'

function dispatchKey(key: string, options: Partial<KeyboardEventInit> = {}): void {
  window.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true, ...options }))
}

function makeWrapper(initialPath = '/cases') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[initialPath]}>{children}</MemoryRouter>
    </QueryClientProvider>
  )
}

describe('useHotkeys', () => {
  beforeEach(() => {
    navigateMock.mockReset()
    startTimerMock.mockReset()
    stopTimerMock.mockReset()
    useCommandPaletteStore.setState({ open: false })
    useShortcutsDialogStore.setState({ open: false })
    vi.useFakeTimers()
  })

  afterEach(() => {
    cleanup()
    vi.useRealTimers()
  })

  it('navigates to /dashboard on "g" then "d"', () => {
    renderHook(() => useHotkeys(), { wrapper: makeWrapper() })
    act(() => {
      dispatchKey('g')
      dispatchKey('d')
    })
    expect(navigateMock).toHaveBeenCalledWith('/dashboard')
  })

  it('navigates to /cases on "g" then "c", and /clients on "g" then "k"', () => {
    renderHook(() => useHotkeys(), { wrapper: makeWrapper() })
    act(() => {
      dispatchKey('g')
      dispatchKey('c')
    })
    expect(navigateMock).toHaveBeenCalledWith('/cases')

    act(() => {
      dispatchKey('g')
      dispatchKey('k')
    })
    expect(navigateMock).toHaveBeenCalledWith('/clients')
  })

  it('clears the pending "g" prefix after the sequence timeout', () => {
    renderHook(() => useHotkeys(), { wrapper: makeWrapper() })
    act(() => {
      dispatchKey('g')
    })
    act(() => {
      vi.advanceTimersByTime(1000)
    })
    act(() => {
      dispatchKey('d')
    })
    expect(navigateMock).not.toHaveBeenCalled()
  })

  it('opens the command palette on "/"', () => {
    renderHook(() => useHotkeys(), { wrapper: makeWrapper() })
    act(() => {
      dispatchKey('/')
    })
    expect(useCommandPaletteStore.getState().open).toBe(true)
  })

  it('opens the shortcuts dialog on "?"', () => {
    renderHook(() => useHotkeys(), { wrapper: makeWrapper() })
    act(() => {
      dispatchKey('?')
    })
    expect(useShortcutsDialogStore.getState().open).toBe(true)
  })

  it('navigates to /cases?new=1 on "n" only while on the cases list route', () => {
    renderHook(() => useHotkeys(), { wrapper: makeWrapper('/cases') })
    act(() => {
      dispatchKey('n')
    })
    expect(navigateMock).toHaveBeenCalledWith('/cases?new=1')
  })

  it('ignores "n" when not on the cases list route', () => {
    renderHook(() => useHotkeys(), { wrapper: makeWrapper('/clients') })
    act(() => {
      dispatchKey('n')
    })
    expect(navigateMock).not.toHaveBeenCalled()
  })

  it('ignores keydowns while a modifier key is held', () => {
    renderHook(() => useHotkeys(), { wrapper: makeWrapper() })
    act(() => {
      dispatchKey('/', { metaKey: true })
    })
    expect(useCommandPaletteStore.getState().open).toBe(false)
  })

  it('ignores keydowns targeting an input element', () => {
    const input = document.createElement('input')
    document.body.appendChild(input)
    renderHook(() => useHotkeys(), { wrapper: makeWrapper() })
    act(() => {
      input.dispatchEvent(new KeyboardEvent('keydown', { key: '/', bubbles: true }))
    })
    expect(useCommandPaletteStore.getState().open).toBe(false)
    document.body.removeChild(input)
  })

  it('starts a timer on "t" when on a case page and no timer is running', async () => {
    startTimerMock.mockResolvedValue(undefined)
    renderHook(() => useHotkeys(), { wrapper: makeWrapper('/cases/case-1') })
    await act(async () => {
      dispatchKey('t')
      await Promise.resolve()
    })
    expect(startTimerMock).toHaveBeenCalledWith('case-1', expect.objectContaining({ billable: true }))
  })
})
