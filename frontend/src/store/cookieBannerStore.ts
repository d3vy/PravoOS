import { create } from 'zustand'

interface CookieBannerState {
  reopened: boolean
  reopen: () => void
  close: () => void
}

export const useCookieBannerStore = create<CookieBannerState>((set) => ({
  reopened: false,
  reopen: () => set({ reopened: true }),
  close: () => set({ reopened: false }),
}))
