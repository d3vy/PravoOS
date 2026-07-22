import { create } from 'zustand'

export type ToastVariant = 'success' | 'error' | 'info'

export interface ToastAction {
  label: string
  onClick: () => void
}

export interface ToastItem {
  id: string
  variant: ToastVariant
  message: string
  duration: number
  action?: ToastAction
}

export interface PushToastInput {
  variant: ToastVariant
  message: string
  duration?: number
  action?: ToastAction
}

interface ToastState {
  toasts: ToastItem[]
  push: (toast: PushToastInput) => string
  dismiss: (id: string) => void
}

const DEFAULT_DURATION = 4000

export const useToastStore = create<ToastState>((set) => ({
  toasts: [],
  push: (toast) => {
    const id = crypto.randomUUID()
    set((state) => ({
      toasts: [...state.toasts, { id, duration: DEFAULT_DURATION, ...toast }],
    }))
    return id
  },
  dismiss: (id) => set((state) => ({ toasts: state.toasts.filter((t) => t.id !== id) })),
}))
