import { beforeEach, describe, expect, it } from 'vitest'
import { useActivityTimeStore } from './activityTimeStore'

describe('useActivityTimeStore', () => {
  beforeEach(() => {
    useActivityTimeStore.setState({ accumulators: {} })
  })

  it('creates a new accumulator on first addSeconds call', () => {
    useActivityTimeStore.getState().addSeconds('case-1', 30)
    expect(useActivityTimeStore.getState().accumulators['case-1']).toEqual({
      caseId: 'case-1',
      seconds: 30,
      suggested: false,
    })
  })

  it('accumulates seconds across multiple calls', () => {
    useActivityTimeStore.getState().addSeconds('case-1', 30)
    useActivityTimeStore.getState().addSeconds('case-1', 15)
    expect(useActivityTimeStore.getState().accumulators['case-1'].seconds).toBe(45)
  })

  it('tracks accumulators for different cases independently', () => {
    useActivityTimeStore.getState().addSeconds('case-1', 30)
    useActivityTimeStore.getState().addSeconds('case-2', 10)
    const { accumulators } = useActivityTimeStore.getState()
    expect(accumulators['case-1'].seconds).toBe(30)
    expect(accumulators['case-2'].seconds).toBe(10)
  })

  it('markSuggested flags an existing accumulator', () => {
    useActivityTimeStore.getState().addSeconds('case-1', 30)
    useActivityTimeStore.getState().markSuggested('case-1')
    expect(useActivityTimeStore.getState().accumulators['case-1'].suggested).toBe(true)
  })

  it('markSuggested is a no-op when there is no accumulator', () => {
    useActivityTimeStore.getState().markSuggested('unknown-case')
    expect(useActivityTimeStore.getState().accumulators).toEqual({})
  })

  it('reset removes the accumulator for a case', () => {
    useActivityTimeStore.getState().addSeconds('case-1', 30)
    useActivityTimeStore.getState().addSeconds('case-2', 10)
    useActivityTimeStore.getState().reset('case-1')
    const { accumulators } = useActivityTimeStore.getState()
    expect(accumulators['case-1']).toBeUndefined()
    expect(accumulators['case-2'].seconds).toBe(10)
  })

  it('reset on an unknown case is a no-op', () => {
    useActivityTimeStore.getState().addSeconds('case-1', 30)
    useActivityTimeStore.getState().reset('unknown-case')
    expect(useActivityTimeStore.getState().accumulators['case-1'].seconds).toBe(30)
  })
})
