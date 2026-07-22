import { useCallback } from 'react'
import { useToastStore, type ToastAction } from '../store/toastStore'

export interface UndoToastOptions {
  duration?: number
}

export function useToast() {
  const push = useToastStore((state) => state.push)

  const success = useCallback((message: string, action?: ToastAction) => push({ variant: 'success', message, action }), [push])
  const error = useCallback((message: string, action?: ToastAction) => push({ variant: 'error', message, action }), [push])
  const info = useCallback((message: string, action?: ToastAction) => push({ variant: 'info', message, action }), [push])
  const undo = useCallback(
    (message: string, onUndo: () => void, undoLabel: string, options?: UndoToastOptions) =>
      push({
        variant: 'success',
        message,
        duration: options?.duration ?? 5000,
        action: { label: undoLabel, onClick: onUndo },
      }),
    [push]
  )

  return { success, error, info, undo }
}
