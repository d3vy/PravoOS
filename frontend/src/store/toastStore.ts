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
const MAX_VISIBLE_TOASTS = 3

export const useToastStore = create<ToastState>((set, get) => ({
  toasts: [],
  push: (toast) => {
    const duplicate = get().toasts.find(
      (existing) => existing.variant === toast.variant && existing.message === toast.message
    )
    if (duplicate) return duplicate.id

    const id = crypto.randomUUID()
    set((state) => ({
      toasts: [...state.toasts, { id, duration: DEFAULT_DURATION, ...toast }].slice(-MAX_VISIBLE_TOASTS),
    }))
    return id
  },
  dismiss: (id) => set((state) => ({ toasts: state.toasts.filter((t) => t.id !== id) })),
}))
