import { useEffect, useRef } from 'react'
import { createPortal } from 'react-dom'
import { motion, AnimatePresence } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import type { ReviewAnswerConfidence, TabularReviewCellDto } from '../../types'

interface CitationDrawerProps {
  cell: TabularReviewCellDto | null
  documentTitle: string
  question: string
  onClose: () => void
}

const CONFIDENCE_CLASSES: Record<ReviewAnswerConfidence, string> = {
  HIGH: 'bg-success-soft text-success',
  MEDIUM: 'bg-warning-soft text-warning',
  LOW: 'bg-warning-soft text-warning',
  NOT_FOUND: 'bg-surface-2 text-fg-muted',
}

export function CitationDrawer({
  cell,
  documentTitle,
  question,
  onClose,
}: CitationDrawerProps): JSX.Element | null {
  const { t } = useTranslation()
  const panelRef = useRef<HTMLDivElement>(null)
  const previouslyFocused = useRef<HTMLElement | null>(null)

  useEffect(() => {
    if (!cell) return undefined

    previouslyFocused.current = document.activeElement as HTMLElement | null
    const focusTimer = setTimeout(() => {
      panelRef.current?.querySelector<HTMLElement>('button')?.focus()
    }, 30)

    const handleKeyDown = (event: KeyboardEvent): void => {
      if (event.key === 'Escape') {
        event.preventDefault()
        onClose()
      }
    }
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      clearTimeout(focusTimer)
      document.removeEventListener('keydown', handleKeyDown)
      previouslyFocused.current?.focus()
    }
  }, [cell, onClose])

  if (typeof document === 'undefined') return null

  return createPortal(
    <AnimatePresence>
      {cell && (
        <motion.aside
          ref={panelRef}
          initial={{ x: '100%' }}
          animate={{ x: 0 }}
          exit={{ x: '100%' }}
          transition={{ duration: 0.18, ease: 'easeOut' }}
          role="dialog"
          aria-modal="false"
          aria-label={t('review.citationDrawerTitle')}
          className="fixed right-0 top-0 z-[140] flex h-full w-full max-w-md flex-col border-l border-line bg-overlay shadow-card"
        >
          <div className="flex items-start justify-between gap-4 border-b border-line px-5 py-4">
            <div className="min-w-0">
              <p className="text-xs uppercase tracking-wide text-fg-muted">{t('review.citationDrawerTitle')}</p>
              <p className="mt-1 text-sm font-medium text-fg [overflow-wrap:anywhere]">{documentTitle}</p>
            </div>
            <button
              type="button"
              onClick={onClose}
              aria-label={t('common.close')}
              className="shrink-0 text-fg-muted transition-colors hover:text-fg"
            >
              <svg className="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
                <line x1="18" y1="6" x2="6" y2="18" />
                <line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          </div>

          <div className="flex-1 overflow-y-auto px-5 py-4 scrollbar-thin">
            <p className="text-xs uppercase tracking-wide text-fg-muted">{t('review.questionLabel')}</p>
            <p className="mt-1 text-sm text-fg [overflow-wrap:anywhere]">{question}</p>

            <div className="mt-5 flex items-center gap-2">
              <p className="text-xs uppercase tracking-wide text-fg-muted">{t('review.answerLabel')}</p>
              <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${CONFIDENCE_CLASSES[cell.confidence]}`}>
                {t(`review.confidence.${cell.confidence}`)}
              </span>
            </div>
            <p className="mt-1 whitespace-pre-wrap text-sm text-fg [overflow-wrap:anywhere]">{cell.answer}</p>

            <p className="mt-6 text-xs uppercase tracking-wide text-fg-muted">{t('review.sourcesLabel')}</p>
            {cell.citations.length === 0 ? (
              <p className="mt-1 text-sm text-fg-muted">{t('review.noCitations')}</p>
            ) : (
              <ul className="mt-2 flex flex-col gap-3">
                {cell.citations.map((citation, index) => (
                  <li key={index} className="rounded-lg border border-line bg-surface p-3">
                    <p className="text-xs text-fg-muted">
                      {t('review.fragmentLabel', { index: citation.chunkIndex + 1 })}
                    </p>
                    <blockquote className="mt-1.5 border-l-2 border-accent pl-3 text-sm text-fg [overflow-wrap:anywhere]">
                      {citation.quote}
                    </blockquote>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </motion.aside>
      )}
    </AnimatePresence>,
    document.body
  )
}
