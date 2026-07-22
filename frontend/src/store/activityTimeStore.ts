import { create } from 'zustand'

interface CaseAccumulator {
  caseId: string
  seconds: number
  suggested: boolean
}

interface ActivityTimeState {
  accumulators: Record<string, CaseAccumulator>
  addSeconds: (caseId: string, seconds: number) => void
  markSuggested: (caseId: string) => void
  reset: (caseId: string) => void
}

export const ACTIVITY_SUGGEST_THRESHOLD_SECONDS = 15 * 60
export const ACTIVITY_IDLE_TIMEOUT_MS = 5 * 60 * 1000

export const useActivityTimeStore = create<ActivityTimeState>((set) => ({
  accumulators: {},
  addSeconds: (caseId, seconds) =>
    set((state) => {
      const current = state.accumulators[caseId] ?? { caseId, seconds: 0, suggested: false }
      return {
        accumulators: {
          ...state.accumulators,
          [caseId]: { ...current, seconds: current.seconds + seconds },
        },
      }
    }),
  markSuggested: (caseId) =>
    set((state) => {
      const current = state.accumulators[caseId]
      if (!current) return state
      return {
        accumulators: { ...state.accumulators, [caseId]: { ...current, suggested: true } },
      }
    }),
  reset: (caseId) =>
    set((state) => {
      const next = { ...state.accumulators }
      delete next[caseId]
      return { accumulators: next }
    }),
}))
