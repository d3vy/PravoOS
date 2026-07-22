import { useEffect, useRef } from 'react'
import { createPortal } from 'react-dom'
import { motion, AnimatePresence } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import { useToastStore, type ToastItem, type ToastVariant } from '../../store/toastStore'

const VARIANT_STYLES: Record<ToastVariant, string> = {
  success: 'border-success/30 bg-success-soft text-success',
  error: 'border-danger/30 bg-danger-soft text-danger',
  info: 'border-line bg-overlay text-fg',
}

export function ToastViewport(): JSX.Element | null {
  const toasts = useToastStore((state) => state.toasts)

  if (typeof document === 'undefined') return null

  return createPortal(
    <div
      className="fixed inset-x-0 bottom-0 z-[200] flex flex-col items-end gap-2 p-4 pointer-events-none sm:inset-x-auto sm:right-0"
      aria-live="polite"
      aria-atomic="false"
    >
      <AnimatePresence>
        {toasts.map((toast) => (
          <ToastCard key={toast.id} toast={toast} />
        ))}
      </AnimatePresence>
    </div>,
    document.body
  )
}

function ToastCard({ toast }: { toast: ToastItem }): JSX.Element {
  const { t } = useTranslation()
  const dismiss = useToastStore((state) => state.dismiss)
  const timerRef = useRef<ReturnType<typeof setTimeout>>()

  useEffect(() => {
    timerRef.current = setTimeout(() => dismiss(toast.id), toast.duration)
    return () => clearTimeout(timerRef.current)
  }, [toast.id, toast.duration, dismiss])

  return (
    <motion.div
      role={toast.variant === 'error' ? 'alert' : 'status'}
      layout
      initial={{ opacity: 0, y: 12, scale: 0.96 }}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      exit={{ opacity: 0, x: 24, transition: { duration: 0.15 } }}
      transition={{ duration: 0.18, ease: 'easeOut' }}
      className={`pointer-events-auto flex w-full max-w-sm items-start gap-3 rounded-xl border px-4 py-3 shadow-card ${VARIANT_STYLES[toast.variant]}`}
    >
      <p className="flex-1 min-w-0 text-sm leading-snug [overflow-wrap:anywhere]">{toast.message}</p>
      <div className="flex shrink-0 items-center gap-3">
        {toast.action && (
          <button
            type="button"
            onClick={() => {
              toast.action?.onClick()
              dismiss(toast.id)
            }}
            className="text-sm font-medium underline underline-offset-2 hover:opacity-80"
          >
            {toast.action.label}
          </button>
        )}
        <button
          type="button"
          onClick={() => dismiss(toast.id)}
          aria-label={t('common.close')}
          className="text-current/60 hover:text-current transition-colors"
        >
          <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
            <line x1="18" y1="6" x2="6" y2="18" />
            <line x1="6" y1="6" x2="18" y2="18" />
          </svg>
        </button>
      </div>
    </motion.div>
  )
}
