import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { timeApi } from '../../api/time'
import { casesApi } from '../../api/cases'
import type { CaseResponse, TimeEntryResponse } from '../../types'
import { useToast } from '../../hooks/useToast'
import { useMobileTimerSheetStore } from '../../store/mobileTimerSheetStore'
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

export function MobileTimerSheet(): JSX.Element | null {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const toast = useToast()
  const open = useMobileTimerSheetStore((state) => state.open)
  const close = useMobileTimerSheetStore((state) => state.close)
  const [query, setQuery] = useState('')
  const [debounced, setDebounced] = useState('')

  const { data: activeTimer } = useQuery<TimeEntryResponse | null>({
    queryKey: ['active-timer'],
    queryFn: () => timeApi.activeTimer(),
    refetchInterval: 30_000,
    enabled: open,
  })

  const { data: activeCase } = useQuery<CaseResponse>({
    queryKey: ['case', activeTimer?.caseId],
    queryFn: () => casesApi.get(activeTimer?.caseId as string),
    enabled: open && !!activeTimer?.caseId,
  })

  useEffect(() => {
    const id = window.setTimeout(() => setDebounced(query.trim()), 250)
    return () => window.clearTimeout(id)
  }, [query])

  const { data: searchData, isFetching } = useQuery({
    queryKey: ['timer-case-search', debounced],
    queryFn: () => casesApi.list(undefined, debounced, 0, 8),
    enabled: open && !activeTimer && debounced.length > 0,
  })
  const results = useMemo(() => searchData?.items ?? [], [searchData])

  const elapsed = useElapsed(activeTimer?.startedAt ?? null)

  const invalidate = (caseId?: string): void => {
    queryClient.invalidateQueries({ queryKey: ['active-timer'] })
    if (caseId) queryClient.invalidateQueries({ queryKey: ['case-time', caseId] })
  }

  const startTimer = useMutation({
    mutationFn: (caseId: string) =>
      timeApi.startTimer(caseId, { description: t('timeTracking.defaultDescription'), hourlyRate: 0, billable: true }),
    onSuccess: (_data, caseId) => {
      invalidate(caseId)
      setQuery('')
      close()
    },
    onError: () => toast.error(t('timeTracking.startError')),
  })

  const stopTimer = useMutation({
    mutationFn: (caseId: string) => timeApi.stopTimer(caseId),
    onSuccess: (_data, caseId) => {
      invalidate(caseId)
      close()
    },
    onError: () => toast.error(t('globalTimer.stopError')),
  })

  useEffect(() => {
    if (!open) return
    document.body.style.overflow = 'hidden'
    return () => {
      document.body.style.overflow = ''
    }
  }, [open])

  if (!open) return null

  return (
    <div className="md:hidden fixed inset-0 z-50 flex flex-col justify-end">
      <button type="button" aria-label={t('common.close')} onClick={close} className="absolute inset-0 bg-black/40" />
      <div
        role="dialog"
        aria-modal="true"
        aria-label={t('globalTimer.idle')}
        className="relative bg-surface rounded-t-2xl border-t border-line max-h-[80vh] overflow-y-auto scrollbar-thin p-4"
        style={{ paddingBottom: 'max(1.25rem, env(safe-area-inset-bottom))' }}
      >
        <div className="w-10 h-1 rounded-full bg-line mx-auto mb-4" aria-hidden="true" />

        {activeTimer ? (
          <div className="flex flex-col gap-4">
            <div className="flex items-center gap-3">
              <span className="w-2.5 h-2.5 rounded-full bg-accent animate-pulse" aria-hidden="true" />
              <span className="text-2xl font-mono tabular-nums text-fg">{elapsed}</span>
            </div>
            <button
              type="button"
              onClick={() => {
                close()
                navigate(`/cases/${activeTimer.caseId}`)
              }}
              className="text-left text-sm text-fg-muted hover:text-fg transition-colors truncate"
            >
              {activeCase?.title ?? activeTimer.description}
            </button>
            <Button
              variant="secondary"
              loading={stopTimer.isPending}
              onClick={() => stopTimer.mutate(activeTimer.caseId)}
              className="min-h-11"
            >
              {t('globalTimer.stop')}
            </Button>
          </div>
        ) : (
          <div className="flex flex-col gap-3">
            <input
              autoFocus
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder={t('globalTimer.searchPlaceholder')}
              className="w-full px-3 min-h-11 rounded-lg border border-line bg-bg text-fg text-sm placeholder:text-fg-muted/60 focus:outline-none focus:ring-2 focus:ring-accent"
            />
            <div className="flex flex-col gap-1 max-h-72 overflow-y-auto scrollbar-thin">
              {isFetching && <p className="px-1 py-2 text-sm text-fg-muted">{t('common.loading')}</p>}
              {!isFetching && debounced.length > 0 && results.length === 0 && (
                <p className="px-1 py-2 text-sm text-fg-muted">{t('globalTimer.noResults')}</p>
              )}
              {debounced.length === 0 && (
                <p className="px-1 py-2 text-sm text-fg-muted">{t('globalTimer.searchHint')}</p>
              )}
              {results.map((item) => (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => startTimer.mutate(item.id)}
                  disabled={startTimer.isPending}
                  className="w-full text-left px-3 min-h-11 flex items-center rounded-lg text-sm text-fg hover:bg-bg transition-colors truncate disabled:opacity-60"
                >
                  {item.title}
                </button>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
