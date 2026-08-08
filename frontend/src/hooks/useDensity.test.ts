import { act, renderHook } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import { useDensity } from './useDensity'

const STORAGE_KEY = 'pravoos.dashboard.density'

describe('useDensity', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('defaults to comfortable when nothing is stored', () => {
    const { result } = renderHook(() => useDensity())
    expect(result.current[0]).toBe('comfortable')
  })

  it('reads a previously stored compact preference', () => {
    localStorage.setItem(STORAGE_KEY, 'compact')
    const { result } = renderHook(() => useDensity())
    expect(result.current[0]).toBe('compact')
  })

  it('treats an unrecognized stored value as comfortable', () => {
    localStorage.setItem(STORAGE_KEY, 'ultra-compact')
    const { result } = renderHook(() => useDensity())
    expect(result.current[0]).toBe('comfortable')
  })

  it('toggle flips the density and persists it', () => {
    const { result } = renderHook(() => useDensity())

    act(() => {
      result.current[1]()
    })
    expect(result.current[0]).toBe('compact')
    expect(localStorage.getItem(STORAGE_KEY)).toBe('compact')

    act(() => {
      result.current[1]()
    })
    expect(result.current[0]).toBe('comfortable')
    expect(localStorage.getItem(STORAGE_KEY)).toBe('comfortable')
  })
})
