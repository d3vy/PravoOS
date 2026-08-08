import { act, renderHook } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { useSwipeReveal } from './useSwipeReveal'
import type { TouchEvent } from 'react'

const REVEAL_WIDTH = 80

function touch(x: number, y: number): TouchEvent<HTMLElement> {
  return { touches: [{ clientX: x, clientY: y }] } as unknown as TouchEvent<HTMLElement>
}

describe('useSwipeReveal', () => {
  it('starts closed with zero offset', () => {
    const { result } = renderHook(() => useSwipeReveal(REVEAL_WIDTH))
    expect(result.current.offsetX).toBe(0)
    expect(result.current.dragging).toBe(false)
  })

  it('sets dragging on touch start', () => {
    const { result } = renderHook(() => useSwipeReveal(REVEAL_WIDTH))
    act(() => {
      result.current.onTouchStart(touch(100, 100))
    })
    expect(result.current.dragging).toBe(true)
  })

  it('tracks a horizontal drag and clamps it to -revealWidth', () => {
    const { result } = renderHook(() => useSwipeReveal(REVEAL_WIDTH))
    act(() => {
      result.current.onTouchStart(touch(100, 100))
    })
    act(() => {
      result.current.onTouchMove(touch(80, 100))
    })
    expect(result.current.offsetX).toBe(-20)

    act(() => {
      result.current.onTouchMove(touch(-50, 100))
    })
    expect(result.current.offsetX).toBe(-REVEAL_WIDTH)
  })

  it('never reveals past 0 when dragging right from closed', () => {
    const { result } = renderHook(() => useSwipeReveal(REVEAL_WIDTH))
    act(() => {
      result.current.onTouchStart(touch(100, 100))
    })
    act(() => {
      result.current.onTouchMove(touch(150, 100))
    })
    expect(result.current.offsetX).toBe(0)
  })

  it('ignores movement below the activation threshold', () => {
    const { result } = renderHook(() => useSwipeReveal(REVEAL_WIDTH))
    act(() => {
      result.current.onTouchStart(touch(100, 100))
    })
    act(() => {
      result.current.onTouchMove(touch(104, 100))
    })
    expect(result.current.offsetX).toBe(0)
  })

  it('locks to the vertical axis and ignores horizontal movement afterwards (scroll gesture)', () => {
    const { result } = renderHook(() => useSwipeReveal(REVEAL_WIDTH))
    act(() => {
      result.current.onTouchStart(touch(100, 100))
    })
    act(() => {
      result.current.onTouchMove(touch(100, 130))
    })
    act(() => {
      result.current.onTouchMove(touch(50, 130))
    })
    expect(result.current.offsetX).toBe(0)
  })

  it('opens on touch end when dragged past the halfway point, and clears dragging', () => {
    const { result } = renderHook(() => useSwipeReveal(REVEAL_WIDTH))
    act(() => {
      result.current.onTouchStart(touch(100, 100))
    })
    act(() => {
      result.current.onTouchMove(touch(100 - REVEAL_WIDTH * 0.6, 100))
    })
    act(() => {
      result.current.onTouchEnd()
    })
    expect(result.current.dragging).toBe(false)
    expect(result.current.offsetX).toBe(-REVEAL_WIDTH)
  })

  it('snaps back closed on touch end when dragged less than halfway', () => {
    const { result } = renderHook(() => useSwipeReveal(REVEAL_WIDTH))
    act(() => {
      result.current.onTouchStart(touch(100, 100))
    })
    act(() => {
      result.current.onTouchMove(touch(100 - REVEAL_WIDTH * 0.2, 100))
    })
    act(() => {
      result.current.onTouchEnd()
    })
    expect(result.current.offsetX).toBe(0)
  })

  it('closing further from an already-open state requires dragging further right', () => {
    const { result } = renderHook(() => useSwipeReveal(REVEAL_WIDTH))
    act(() => {
      result.current.onTouchStart(touch(100, 100))
    })
    act(() => {
      result.current.onTouchMove(touch(100 - REVEAL_WIDTH, 100))
    })
    act(() => {
      result.current.onTouchEnd()
    })
    expect(result.current.offsetX).toBe(-REVEAL_WIDTH)

    act(() => {
      result.current.onTouchStart(touch(20, 100))
    })
    act(() => {
      result.current.onTouchMove(touch(20 + 30, 100))
    })
    expect(result.current.offsetX).toBe(-REVEAL_WIDTH + 30)
  })

  it('reset closes and zeroes the offset', () => {
    const { result } = renderHook(() => useSwipeReveal(REVEAL_WIDTH))
    act(() => {
      result.current.onTouchStart(touch(100, 100))
    })
    act(() => {
      result.current.onTouchMove(touch(0, 100))
    })
    act(() => {
      result.current.onTouchEnd()
    })
    expect(result.current.offsetX).toBe(-REVEAL_WIDTH)

    act(() => {
      result.current.reset()
    })
    expect(result.current.offsetX).toBe(0)
  })
})
