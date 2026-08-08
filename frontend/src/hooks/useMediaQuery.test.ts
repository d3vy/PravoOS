import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Mock } from 'vitest'
import { useMediaQuery } from './useMediaQuery'

class FakeMediaQueryList {
  matches: boolean
  private listeners = new Set<(event: MediaQueryListEvent) => void>()

  constructor(matches: boolean) {
    this.matches = matches
  }

  addEventListener(_type: string, listener: (event: MediaQueryListEvent) => void): void {
    this.listeners.add(listener)
  }

  removeEventListener(_type: string, listener: (event: MediaQueryListEvent) => void): void {
    this.listeners.delete(listener)
  }

  emit(matches: boolean): void {
    this.matches = matches
    this.listeners.forEach((listener) => listener({ matches } as MediaQueryListEvent))
  }

  get listenerCount(): number {
    return this.listeners.size
  }
}

describe('useMediaQuery', () => {
  let mediaQueryList: FakeMediaQueryList
  let matchMediaSpy: Mock<[], FakeMediaQueryList>

  beforeEach(() => {
    mediaQueryList = new FakeMediaQueryList(false)
    matchMediaSpy = vi.fn(() => mediaQueryList)
    window.matchMedia = matchMediaSpy as unknown as typeof window.matchMedia
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('returns the initial match state', () => {
    mediaQueryList = new FakeMediaQueryList(true)
    matchMediaSpy.mockReturnValue(mediaQueryList)
    const { result } = renderHook(() => useMediaQuery('(min-width: 768px)'))
    expect(result.current).toBe(true)
  })

  it('updates when the media query change event fires', () => {
    const { result } = renderHook(() => useMediaQuery('(min-width: 768px)'))
    expect(result.current).toBe(false)

    act(() => {
      mediaQueryList.emit(true)
    })

    expect(result.current).toBe(true)
  })

  it('removes its listener on unmount', () => {
    const { unmount } = renderHook(() => useMediaQuery('(min-width: 768px)'))
    expect(mediaQueryList.listenerCount).toBe(1)
    unmount()
    expect(mediaQueryList.listenerCount).toBe(0)
  })

  it('re-subscribes when the query string changes', () => {
    const { rerender } = renderHook(({ query }) => useMediaQuery(query), {
      initialProps: { query: '(min-width: 768px)' },
    })
    expect(matchMediaSpy).toHaveBeenCalledWith('(min-width: 768px)')

    rerender({ query: '(min-width: 1024px)' })
    expect(matchMediaSpy).toHaveBeenCalledWith('(min-width: 1024px)')
  })
})
