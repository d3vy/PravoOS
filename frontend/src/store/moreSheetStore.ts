import { create } from 'zustand'

interface MoreSheetState {
  open: boolean
  toggle: () => void
  close: () => void
}

export const useMoreSheetStore = create<MoreSheetState>((set) => ({
  open: false,
  toggle: () => set((state) => ({ open: !state.open })),
  close: () => set({ open: false }),
}))
