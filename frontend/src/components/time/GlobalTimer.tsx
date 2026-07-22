import { useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { timeApi } from '../../api/time'
import { casesApi } from '../../api/cases'
import type { CaseResponse, TimeEntryResponse } from '../../types'
import { useToast } from '../../hooks/useToast'
import { useTimerReminderStore } from '../../store/timerReminderStore'
import { Button } from '../ui/Button'

function useElapsed(startedAt: string | null): string {
  const [elapsed, setElapsed] = useState('00:00')

  useEffect(() => {
    if (!startedAt) {
      setElapsed('00:00')
      return
    }
    const started = new Date(startedAt).getTime()
    const tick = (): void => {
      const totalSeconds = Math.max(0, Math.floor((Date.now() - started) / 1000))
      const hh = Math.floor(totalSeconds / 3600)
      const mm = String(Math.floor((totalSeconds % 3600) / 60)).padStart(2, '0')
      const ss = String(totalSeconds % 60).padStart(2, '0')
      setElapsed(hh > 0 ? `${hh}:${mm}:${ss}` : `${mm}:${ss}`)
    }
    tick()
    const id = window.setInterval(tick, 1000)
    return () => window.clearInterval(id)
  }, [startedAt])

  return elapsed
}

export function GlobalTimer(): JSX.Element | null {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const toast = useToast()
  const [pickerOpen, setPickerOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)

  const { data: activeTimer } = useQuery<TimeEntryResponse | null>({
    queryKey: ['active-timer'],
    queryFn: () => timeApi.activeTimer(),
    refetchInterval: 30_000,
  })

  const { data: activeCase } = useQuery<CaseResponse>({
    queryKey: ['case', activeTimer?.caseId],
    queryFn: () => casesApi.get(activeTimer?.caseId as string),
    enabled: !!activeTimer?.caseId,
  })

  const invalidate = (caseId?: string): void => {
    queryClient.invalidateQueries({ queryKey: ['active-timer'] })
    if (caseId) queryClient.invalidateQueries({ queryKey: ['case-time', caseId] })
  }

  const stopTimer = useMutation({
    mutationFn: (caseId: string) => timeApi.stopTimer(caseId),
    onMutate: async () => {
      await queryClient.cancelQueries({ queryKey: ['active-timer'] })
      const previous = queryClient.getQueryData<TimeEntryResponse | null>(['active-timer'])
      queryClient.setQueryData<TimeEntryResponse | null>(['active-timer'], null)
      return { previous }
    },
    onError: (_err, _caseId, context) => {
      queryClient.setQueryData(['active-timer'], context?.previous ?? null)
      toast.error(t('globalTimer.stopError'))
    },
    onSettled: (_data, _err, caseId) => invalidate(caseId),
  })

  const elapsed = useElapsed(activeTimer?.startedAt ?? null)

  const remindAfterMinutes = useTimerReminderStore((state) => state.remindAfterMinutes)
  const wasReminded = useTimerReminderStore((state) => state.wasReminded)
  const markReminded = useTimerReminderStore((state) => state.markReminded)

  useEffect(() => {
    if (!activeTimer?.startedAt) return
    const reminderKey = `${activeTimer.id}:${activeTimer.startedAt}`
    const check = (): void => {
      const minutes = (Date.now() - new Date(activeTimer.startedAt as string).getTime()) / 60_000
      if (minutes < remindAfterMinutes || wasReminded(reminderKey)) return
      markReminded(reminderKey)
      toast.info(
        t('globalTimer.longRunningReminder', { hours: (remindAfterMinutes / 60).toFixed(1) }),
        {
          label: t('globalTimer.stop'),
          onClick: () => stopTimer.mutate(activeTimer.caseId),
        }
      )
    }
    const id = window.setInterval(check, 60_000)
    check()
    return () => window.clearInterval(id)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeTimer?.id, activeTimer?.startedAt, remindAfterMinutes])

  useEffect(() => {
    if (!pickerOpen) return
    const handleClickOutside = (event: MouseEvent): void => {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) setPickerOpen(false)
    }
    const handleEscape = (event: KeyboardEvent): void => {
      if (event.key === 'Escape') setPickerOpen(false)
    }
    document.addEventListener('mousedown', handleClickOutside)
    document.addEventListener('keydown', handleEscape)
    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
      document.removeEventListener('keydown', handleEscape)
    }
  }, [pickerOpen])

  if (activeTimer) {
    return (
      <div className="hidden md:flex items-center gap-2 pl-3 pr-1.5 py-1.5 rounded-lg border border-accent/40 bg-accent/5">
        <span className="w-2 h-2 rounded-full bg-accent animate-pulse" aria-hidden="true" />
        <span className="text-sm font-mono tabular-nums text-fg">{elapsed}</span>
        <button
          type="button"
          onClick={() => navigate(`/cases/${activeTimer.caseId}`)}
          title={t('globalTimer.openCase')}
          className="max-w-[10rem] truncate text-sm text-fg-muted hover:text-fg transition-colors"
        >
          {activeCase?.title ?? activeTimer.description}
        </button>
        <Button
          variant="ghost"
          size="sm"
          loading={stopTimer.isPending}
          onClick={() => stopTimer.mutate(activeTimer.caseId)}
          title={t('timeTracking.stop')}
        >
          {t('globalTimer.stop')}
        </Button>
      </div>
    )
  }

  return (
    <div ref={rootRef} className="relative hidden md:block">
      <button
        type="button"
        onClick={() => setPickerOpen((open) => !open)}
        aria-haspopup="menu"
        aria-expanded={pickerOpen}
        title={t('globalTimer.startTooltip')}
        className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-line text-fg-muted hover:text-fg hover:border-fg/30 transition-colors"
      >
        <ClockIcon />
        <span className="text-sm">{t('globalTimer.idle')}</span>
      </button>

      {pickerOpen && (
        <CasePicker
          onClose={() => setPickerOpen(false)}
          onPick={(caseId) => {
            timeApi
              .startTimer(caseId, { description: t('timeTracking.defaultDescription'), hourlyRate: 0, billable: true })
              .then(() => {
                invalidate(caseId)
                setPickerOpen(false)
              })
              .catch(() => toast.error(t('timeTracking.startError')))
          }}
        />
      )}
    </div>
  )
}

function CasePicker({
  onClose,
  onPick,
}: {
  onClose: () => void
  onPick: (caseId: string) => void
}): JSX.Element {
  const { t } = useTranslation()
  const [query, setQuery] = useState('')
  const [debounced, setDebounced] = useState('')

  useEffect(() => {
    const id = window.setTimeout(() => setDebounced(query.trim()), 250)
    return () => window.clearTimeout(id)
  }, [query])

  const { data, isFetching } = useQuery({
    queryKey: ['timer-case-search', debounced],
    queryFn: () => casesApi.list(undefined, debounced, 0, 8),
    enabled: debounced.length > 0,
  })

  const results = useMemo(() => data?.items ?? [], [data])

  return (
    <div
      role="menu"
      className="absolute right-0 mt-2 w-72 rounded-xl border border-line bg-surface shadow-lg py-2 z-50"
    >
      <div className="px-2 pb-2">
        <input
          autoFocus
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder={t('globalTimer.searchPlaceholder')}
          className="w-full px-3 py-2 rounded-lg border border-line bg-bg text-fg text-sm placeholder:text-fg-muted/60 focus:outline-none focus:ring-2 focus:ring-accent"
        />
      </div>
      <div className="max-h-64 overflow-y-auto scrollbar-thin">
        {isFetching && <p className="px-4 py-2 text-sm text-fg-muted">{t('common.loading')}</p>}
        {!isFetching && debounced.length > 0 && results.length === 0 && (
          <p className="px-4 py-2 text-sm text-fg-muted">{t('globalTimer.noResults')}</p>
        )}
        {debounced.length === 0 && (
          <p className="px-4 py-2 text-sm text-fg-muted">{t('globalTimer.searchHint')}</p>
        )}
        {results.map((item) => (
          <button
            key={item.id}
            type="button"
            role="menuitem"
            onClick={() => onPick(item.id)}
            className="w-full text-left px-4 py-2.5 text-sm text-fg hover:bg-bg transition-colors truncate"
          >
            {item.title}
          </button>
        ))}
      </div>
      <button
        type="button"
        onClick={onClose}
        className="w-full text-left px-4 py-2 mt-1 border-t border-line text-xs text-fg-muted hover:text-fg transition-colors"
      >
        {t('common.cancel')}
      </button>
    </div>
  )
}

function ClockIcon(): JSX.Element {
  return (
    <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="9" />
      <polyline points="12 7 12 12 15 14" />
    </svg>
  )
}
