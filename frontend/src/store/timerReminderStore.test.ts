import { beforeEach, describe, expect, it } from 'vitest'
import { useTimerReminderStore } from './timerReminderStore'

describe('useTimerReminderStore', () => {
  beforeEach(() => {
    useTimerReminderStore.setState({ remindAfterMinutes: 180, lastReminderKey: null })
  })

  it('defaults to a 180 minute reminder interval and no prior reminder', () => {
    const state = useTimerReminderStore.getState()
    expect(state.remindAfterMinutes).toBe(180)
    expect(state.wasReminded('any-key')).toBe(false)
  })

  it('setRemindAfterMinutes updates the interval', () => {
    useTimerReminderStore.getState().setRemindAfterMinutes(60)
    expect(useTimerReminderStore.getState().remindAfterMinutes).toBe(60)
  })

  it('markReminded records the key and wasReminded matches only that key', () => {
    useTimerReminderStore.getState().markReminded('case-1-timer')
    expect(useTimerReminderStore.getState().wasReminded('case-1-timer')).toBe(true)
    expect(useTimerReminderStore.getState().wasReminded('case-2-timer')).toBe(false)
  })

  it('markReminded with a new key replaces the previous one', () => {
    useTimerReminderStore.getState().markReminded('case-1-timer')
    useTimerReminderStore.getState().markReminded('case-2-timer')
    expect(useTimerReminderStore.getState().wasReminded('case-1-timer')).toBe(false)
    expect(useTimerReminderStore.getState().wasReminded('case-2-timer')).toBe(true)
  })
})
