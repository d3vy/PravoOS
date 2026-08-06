import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import i18n from '../../i18n'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { timeApi } from '../../api/time'
import { invoicesApi } from '../../api/invoices'
import type { CaseTimeSummary, TimeEntryResponse } from '../../types'
import { Button } from '../ui/Button'
import { SkeletonList } from '../ui/Skeleton'
import { formatDuration, formatMoney, parseHoursToMinutes } from '../../utils/billing'

interface Props {
  caseId: string
  clientId: string | null
}

const todayIso = (): string => new Date().toISOString().slice(0, 10)

export function CaseTimeSection({ caseId, clientId }: Props): JSX.Element {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  const [description, setDescription] = useState('')
  const [hours, setHours] = useState('')
  const [rate, setRate] = useState('')
  const [activityDate, setActivityDate] = useState(todayIso())
  const [billable, setBillable] = useState(true)
  const [vatRate, setVatRate] = useState('')
  const [error, setError] = useState<string | null>(null)

  const { data: summary, isLoading: summaryLoading } = useQuery<CaseTimeSummary>({
    queryKey: ['case-time', caseId],
    queryFn: () => timeApi.summary(caseId),
    enabled: caseId !== '',
  })

  const { data: activeTimer } = useQuery<TimeEntryResponse | null>({
    queryKey: ['active-timer'],
    queryFn: () => timeApi.activeTimer(),
    refetchInterval: 60_000,
  })

  const invalidate = (): void => {
    queryClient.invalidateQueries({ queryKey: ['case-time', caseId] })
    queryClient.invalidateQueries({ queryKey: ['active-timer'] })
  }

  const runningHere = activeTimer && activeTimer.caseId === caseId ? activeTimer : null
  const runningElsewhere = activeTimer && activeTimer.caseId !== caseId

  const startTimer = useMutation({
    mutationFn: () =>
      timeApi.startTimer(caseId, {
        description: description.trim() || t('timeTracking.defaultDescription'),
        hourlyRate: Number(rate.replace(',', '.')) || 0,
        billable,
      }),
    onMutate: async () => {
      await queryClient.cancelQueries({ queryKey: ['active-timer'] })
      const previous = queryClient.getQueryData<TimeEntryResponse | null>(['active-timer'])
      const optimisticEntry: TimeEntryResponse = {
        id: 'optimistic-timer',
        caseId,
        description: description.trim() || t('timeTracking.defaultDescription'),
        activityDate: todayIso(),
        minutes: 0,
        hourlyRate: Number(rate.replace(',', '.')) || 0,
        amount: 0,
        billable,
        running: true,
        startedAt: new Date().toISOString(),
        invoiced: false,
        createdAt: new Date().toISOString(),
      }
      queryClient.setQueryData<TimeEntryResponse | null>(['active-timer'], optimisticEntry)
      setError(null)
      return { previous }
    },
    onError: (_err, _vars, context) => {
      queryClient.setQueryData(['active-timer'], context?.previous ?? null)
      setError(t('timeTracking.startError'))
    },
    onSettled: invalidate,
  })

  const stopTimer = useMutation({
    mutationFn: () => timeApi.stopTimer(caseId),
    onMutate: async () => {
      await queryClient.cancelQueries({ queryKey: ['active-timer'] })
      const previous = queryClient.getQueryData<TimeEntryResponse | null>(['active-timer'])
      queryClient.setQueryData<TimeEntryResponse | null>(['active-timer'], null)
      setDescription('')
      return { previous }
    },
    onError: (_err, _vars, context) => {
      queryClient.setQueryData(['active-timer'], context?.previous ?? null)
    },
    onSettled: invalidate,
  })

  const createEntry = useMutation({
    mutationFn: () =>
      timeApi.create(caseId, {
        description: description.trim(),
        activityDate,
        minutes: parseHoursToMinutes(hours),
        hourlyRate: Number(rate.replace(',', '.')) || 0,
        billable,
      }),
    onSuccess: () => {
      invalidate()
      setDescription('')
      setHours('')
      setError(null)
    },
  })

  const deleteEntry = useMutation({
    mutationFn: (entryId: string) => timeApi.remove(caseId, entryId),
    onSuccess: invalidate,
  })

  const createInvoice = useMutation({
    mutationFn: () =>
      invoicesApi.create({
        clientId: clientId as string,
        caseId,
        vatRate: vatRate.trim() ? Number(vatRate.replace(',', '.')) : undefined,
      }),
    onSuccess: (invoice) => {
      invalidate()
      queryClient.invalidateQueries({ queryKey: ['invoices'] })
      navigate(`/invoices/${invoice.id}`)
    },
    onError: () => setError(t('timeTracking.noInvoiceHours')),
  })

  const handleManualAdd = (): void => {
    if (!description.trim() || parseHoursToMinutes(hours) === 0) {
      setError(t('timeTracking.manualError'))
      return
    }
    createEntry.mutate()
  }

  const entries = summary?.entries ?? []

  return (
    <section className="mb-10">
      <div className="flex items-center justify-between gap-3 mb-3">
        <h2 className="text-sm font-semibold text-fg">
          {t('timeTracking.title')}
          {summary && summary.uninvoicedBillableMinutes > 0 && (
            <span className="font-normal text-fg-muted">
              {t('timeTracking.toInvoice', {
                duration: formatDuration(summary.uninvoicedBillableMinutes),
                amount: formatMoney(summary.uninvoicedBillableAmount),
              })}
            </span>
          )}
        </h2>
        <div className="flex items-center gap-2">
        <input
          value={vatRate}
          onChange={(e) => setVatRate(e.target.value)}
          inputMode="decimal"
          placeholder={t('invoices.vatRateLabel')}
          aria-label={t('invoices.vatRateLabel')}
          className={fieldClass + ' w-44'}
        />
        <Button
          variant="primary"
          size="sm"
          disabled={!clientId || !summary || summary.uninvoicedBillableMinutes === 0}
          loading={createInvoice.isPending}
          title={!clientId ? t('timeTracking.noClientTooltip') : t('timeTracking.createInvoiceTooltip')}
          onClick={() => createInvoice.mutate()}
        >
          {t('timeTracking.issueInvoice')}
        </Button>
        </div>
      </div>

      {runningHere ? (
        <RunningTimer entry={runningHere} onStop={() => stopTimer.mutate()} stopping={stopTimer.isPending} />
      ) : (
        <div className="flex flex-col gap-2 p-4 mb-3 rounded-lg bg-surface border border-line">
          <div className="flex flex-col sm:flex-row gap-2">
            <input
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              maxLength={2000}
              placeholder={t('timeTracking.descPlaceholder')}
              className={fieldClass + ' flex-1'}
            />
            <input
              type="date"
              value={activityDate}
              onChange={(e) => setActivityDate(e.target.value)}
              className={fieldClass}
            />
          </div>
          <div className="flex flex-col sm:flex-row gap-2 items-stretch sm:items-center">
            <input
              value={hours}
              onChange={(e) => setHours(e.target.value)}
              inputMode="decimal"
              placeholder={t('timeTracking.hoursPlaceholder')}
              className={fieldClass + ' sm:w-32'}
            />
            <input
              value={rate}
              onChange={(e) => setRate(e.target.value)}
              inputMode="decimal"
              placeholder={t('timeTracking.ratePlaceholder')}
              className={fieldClass + ' sm:w-40'}
            />
            <label className="flex items-center gap-2 text-sm text-fg select-none">
              <input
                type="checkbox"
                checked={billable}
                onChange={(e) => setBillable(e.target.checked)}
                className="h-4 w-4 accent-accent"
              />
              {t('timeTracking.billable')}
            </label>
            <div className="flex gap-2 sm:ml-auto">
              <Button variant="secondary" loading={createEntry.isPending} onClick={handleManualAdd}>
                {t('common.add')}
              </Button>
              <Button
                variant="ghost"
                loading={startTimer.isPending}
                disabled={runningElsewhere}
                title={runningElsewhere ? t('timeTracking.timerElsewhereTooltip') : t('timeTracking.startTimerTooltip')}
                onClick={() => startTimer.mutate()}
              >
                {t('timeTracking.timerButton')}
              </Button>
            </div>
          </div>
        </div>
      )}

      {error && <p className="text-sm text-danger mb-3">{error}</p>}

      {summaryLoading ? (
        <SkeletonList count={3} />
      ) : entries.length === 0 ? (
        <p className="text-sm text-fg-muted">
          {t('timeTracking.emptyEntries')}
        </p>
      ) : (
        <div className="flex flex-col gap-2">
          {entries.map((entry) => (
            <div
              key={entry.id}
              className="flex items-center gap-3 p-3 rounded-lg bg-surface border border-line"
            >
              <div className="flex-1 min-w-0">
                <p className="text-sm text-fg truncate">
                  {entry.running ? '⏱ ' : ''}
                  {entry.description}
                </p>
                <span className="text-xs text-fg-muted">
                  {new Date(entry.activityDate).toLocaleDateString(i18n.language.startsWith('ru') ? 'ru-RU' : 'en-US')} · {formatDuration(entry.minutes)}
                  {entry.billable ? ` · ${formatMoney(entry.amount)}` : t('timeTracking.notBillable')}
                  {entry.invoiced ? t('timeTracking.invoiced') : ''}
                </span>
              </div>
              {!entry.invoiced && !entry.running && (
                <button
                  type="button"
                  title={t('timeTracking.deleteEntryTitle')}
                  disabled={deleteEntry.isPending}
                  onClick={() => deleteEntry.mutate(entry.id)}
                  className="shrink-0 text-fg-muted hover:text-red-600 dark:hover:text-red-400 transition-colors"
                >
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <polyline points="3 6 5 6 21 6" />
                    <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
                  </svg>
                </button>
              )}
            </div>
          ))}
        </div>
      )}
    </section>
  )
}

const fieldClass =
  'px-3 py-2.5 rounded-lg border border-line bg-bg text-fg text-sm placeholder:text-fg-muted/60 focus:outline-none focus:ring-2 focus:ring-accent'

function RunningTimer({
  entry,
  onStop,
  stopping,
}: {
  entry: TimeEntryResponse
  onStop: () => void
  stopping: boolean
}): JSX.Element {
  const { t } = useTranslation()
  const [elapsed, setElapsed] = useState('00:00')

  useEffect(() => {
    if (!entry.startedAt) return
    const started = new Date(entry.startedAt).getTime()
    const tick = (): void => {
      const seconds = Math.max(0, Math.floor((Date.now() - started) / 1000))
      const mm = String(Math.floor(seconds / 60)).padStart(2, '0')
      const ss = String(seconds % 60).padStart(2, '0')
      setElapsed(`${mm}:${ss}`)
    }
    tick()
    const id = window.setInterval(tick, 1000)
    return () => window.clearInterval(id)
  }, [entry.startedAt])

  return (
    <div className="flex items-center gap-3 p-4 mb-3 rounded-lg border border-accent/40 bg-accent/5">
      <span className="text-lg font-mono tabular-nums text-fg">{elapsed}</span>
      <span className="flex-1 min-w-0 text-sm text-fg truncate">
        {entry.description}
      </span>
      <Button variant="danger" size="sm" loading={stopping} onClick={onStop}>
        {t('timeTracking.stop')}
      </Button>
    </div>
  )
}
