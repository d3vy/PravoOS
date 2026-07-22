import { create } from 'zustand'

interface ShortcutsDialogState {
  open: boolean
  setOpen: (open: boolean) => void
  toggle: () => void
}

export const useShortcutsDialogStore = create<ShortcutsDialogState>((set, get) => ({
  open: false,
  setOpen: (open) => set({ open }),
  toggle: () => set({ open: !get().open }),
}))
