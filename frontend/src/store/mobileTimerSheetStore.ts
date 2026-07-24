import { create } from 'zustand'

interface MobileTimerSheetState {
  open: boolean
  toggle: () => void
  close: () => void
}

export const useMobileTimerSheetStore = create<MobileTimerSheetState>((set) => ({
  open: false,
  toggle: () => set((state) => ({ open: !state.open })),
  close: () => set({ open: false }),
}))
