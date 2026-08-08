import { act, cleanup, renderHook } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ReactNode } from 'react'

const { timeCreateMock } = vi.hoisted(() => ({ timeCreateMock: vi.fn() }))

vi.mock('../api/time', () => ({
  timeApi: { create: timeCreateMock },
}))

const { toastInfoMock, toastSuccessMock, toastErrorMock } = vi.hoisted(() => ({
  toastInfoMock: vi.fn(),
  toastSuccessMock: vi.fn(),
  toastErrorMock: vi.fn(),
}))

vi.mock('./useToast', () => ({
  useToast: () => ({ info: toastInfoMock, success: toastSuccessMock, error: toastErrorMock }),
}))

import { usePassiveTimeCapture } from './usePassiveTimeCapture'
import { useActivityTimeStore } from '../store/activityTimeStore'

function setVisibility(state: DocumentVisibilityState): void {
  Object.defineProperty(document, 'visibilityState', { value: state, configurable: true })
}

function renderAt(path: string, queryClient = new QueryClient()) {
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[path]}>{children}</MemoryRouter>
    </QueryClientProvider>
  )
  return { ...renderHook(() => usePassiveTimeCapture(), { wrapper }), queryClient }
}

describe('usePassiveTimeCapture', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    useActivityTimeStore.setState({ accumulators: {} })
    timeCreateMock.mockReset()
    toastInfoMock.mockReset()
    toastSuccessMock.mockReset()
    toastErrorMock.mockReset()
    setVisibility('visible')
  })

  afterEach(() => {
    cleanup()
    vi.useRealTimers()
  })

  it('does not accumulate time outside a case route', () => {
    renderAt('/dashboard')
    act(() => {
      vi.advanceTimersByTime(3000)
    })
    expect(useActivityTimeStore.getState().accumulators).toEqual({})
  })

  it('does not accumulate for the "new case" placeholder route', () => {
    renderAt('/cases/new')
    act(() => {
      vi.advanceTimersByTime(3000)
    })
    expect(useActivityTimeStore.getState().accumulators).toEqual({})
  })

  it('accumulates one second per tick while on a case route and visible', () => {
    renderAt('/cases/case-1')
    act(() => {
      vi.advanceTimersByTime(3000)
    })
    expect(useActivityTimeStore.getState().accumulators['case-1'].seconds).toBe(3)
  })

  it('does not accumulate while the tab is hidden', () => {
    setVisibility('hidden')
    renderAt('/cases/case-1')
    act(() => {
      vi.advanceTimersByTime(3000)
    })
    expect(useActivityTimeStore.getState().accumulators).toEqual({})
  })

  it('does not accumulate while a timer is already running for that case', () => {
    const queryClient = new QueryClient()
    queryClient.setQueryData(['active-timer'], { caseId: 'case-1' })
    renderAt('/cases/case-1', queryClient)
    act(() => {
      vi.advanceTimersByTime(3000)
    })
    expect(useActivityTimeStore.getState().accumulators).toEqual({})
  })

  function advanceKeepingActive(totalMs: number): void {
    const STEP_MS = 60 * 1000
    let elapsed = 0
    while (elapsed < totalMs) {
      act(() => {
        vi.advanceTimersByTime(STEP_MS)
      })
      elapsed += STEP_MS
      act(() => {
        document.dispatchEvent(new Event('mousemove'))
      })
    }
  }

  it('suggests logging time once the threshold is reached, exactly once', () => {
    renderAt('/cases/case-1')
    advanceKeepingActive(15 * 60 * 1000 + 1000)
    expect(toastInfoMock).toHaveBeenCalledTimes(1)
    expect(useActivityTimeStore.getState().accumulators['case-1'].suggested).toBe(true)
  })

  it('logs the suggested time entry and resets the accumulator when the toast action runs', async () => {
    timeCreateMock.mockResolvedValue({})
    renderAt('/cases/case-1')
    advanceKeepingActive(15 * 60 * 1000 + 1000)

    const [, options] = toastInfoMock.mock.calls[0]
    await act(async () => {
      options.onClick()
      await Promise.resolve()
      await Promise.resolve()
    })

    expect(timeCreateMock).toHaveBeenCalledWith(
      'case-1',
      expect.objectContaining({ minutes: 15, billable: true })
    )
    expect(useActivityTimeStore.getState().accumulators['case-1']).toBeUndefined()
    expect(toastSuccessMock).toHaveBeenCalled()
  })
})
