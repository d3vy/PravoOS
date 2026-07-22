import { create } from 'zustand'
import { persist } from 'zustand/middleware'

interface TimerReminderState {
  remindAfterMinutes: number
  lastReminderKey: string | null
  setRemindAfterMinutes: (minutes: number) => void
  markReminded: (key: string) => void
  wasReminded: (key: string) => boolean
}

export const useTimerReminderStore = create<TimerReminderState>()(
  persist(
    (set, get) => ({
      remindAfterMinutes: 180,
      lastReminderKey: null,
      setRemindAfterMinutes: (minutes) => set({ remindAfterMinutes: minutes }),
      markReminded: (key) => set({ lastReminderKey: key }),
      wasReminded: (key) => get().lastReminderKey === key,
    }),
    { name: 'pravoos-timer-reminder' }
  )
)
