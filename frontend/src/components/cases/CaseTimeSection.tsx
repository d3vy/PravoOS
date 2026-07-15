import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { timeApi } from '../../api/time'
import { invoicesApi } from '../../api/invoices'
import type { CaseTimeSummary, TimeEntryResponse } from '../../types'
import { Button } from '../ui/Button'
import { formatDuration, formatMoney, parseHoursToMinutes } from '../../utils/billing'

interface Props {
  caseId: string
  clientId: string | null
}

const todayIso = (): string => new Date().toISOString().slice(0, 10)

export function CaseTimeSection({ caseId, clientId }: Props): JSX.Element {
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  const [description, setDescription] = useState('')
  const [hours, setHours] = useState('')
  const [rate, setRate] = useState('')
  const [activityDate, setActivityDate] = useState(todayIso())
  const [billable, setBillable] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const { data: summary } = useQuery<CaseTimeSummary>({
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
        description: description.trim() || 'Работа по делу',
        hourlyRate: Number(rate.replace(',', '.')) || 0,
        billable,
      }),
    onSuccess: () => {
      invalidate()
      setError(null)
    },
    onError: () => setError('Не удалось запустить таймер. Возможно, таймер уже идёт по другому делу.'),
  })

  const stopTimer = useMutation({
    mutationFn: () => timeApi.stopTimer(caseId),
    onSuccess: () => {
      invalidate()
      setDescription('')
    },
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
    mutationFn: () => invoicesApi.create({ clientId: clientId as string, caseId }),
    onSuccess: (invoice) => {
      invalidate()
      queryClient.invalidateQueries({ queryKey: ['invoices'] })
      navigate(`/invoices/${invoice.id}`)
    },
    onError: () => setError('Нет несписанных оплачиваемых часов для счёта.'),
  })

  const handleManualAdd = (): void => {
    if (!description.trim() || parseHoursToMinutes(hours) === 0) {
      setError('Укажите описание и длительность в часах (например, 1,5).')
      return
    }
    createEntry.mutate()
  }

  const entries = summary?.entries ?? []

  return (
    <section className="mb-10">
      <div className="flex items-center justify-between gap-3 mb-3">
        <h2 className="text-sm font-semibold text-light-text dark:text-dark-text">
          Учёт времени
          {summary && summary.uninvoicedBillableMinutes > 0 && (
            <span className="font-normal text-light-secondary dark:text-dark-secondary">
              {' '}
              · к счёту {formatDuration(summary.uninvoicedBillableMinutes)} (
              {formatMoney(summary.uninvoicedBillableAmount)})
            </span>
          )}
        </h2>
        <Button
          variant="primary"
          size="sm"
          disabled={!clientId || !summary || summary.uninvoicedBillableMinutes === 0}
          loading={createInvoice.isPending}
          title={
            !clientId
              ? 'Привяжите клиента к делу, чтобы выставить счёт'
              : 'Создать счёт по несписанным оплачиваемым часам'
          }
          onClick={() => createInvoice.mutate()}
        >
          Выставить счёт
        </Button>
      </div>

      {runningHere ? (
        <RunningTimer entry={runningHere} onStop={() => stopTimer.mutate()} stopping={stopTimer.isPending} />
      ) : (
        <div className="flex flex-col gap-2 p-4 mb-3 rounded-lg bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border">
          <div className="flex flex-col sm:flex-row gap-2">
            <input
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              maxLength={2000}
              placeholder="Что делали (напр., подготовка иска)"
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
              placeholder="Часы (1,5)"
              className={fieldClass + ' sm:w-32'}
            />
            <input
              value={rate}
              onChange={(e) => setRate(e.target.value)}
              inputMode="decimal"
              placeholder="Ставка ₽/час"
              className={fieldClass + ' sm:w-40'}
            />
            <label className="flex items-center gap-2 text-sm text-light-text dark:text-dark-text select-none">
              <input
                type="checkbox"
                checked={billable}
                onChange={(e) => setBillable(e.target.checked)}
                className="h-4 w-4 accent-light-accent dark:accent-dark-accent"
              />
              К оплате
            </label>
            <div className="flex gap-2 sm:ml-auto">
              <Button variant="secondary" loading={createEntry.isPending} onClick={handleManualAdd}>
                Добавить
              </Button>
              <Button
                variant="ghost"
                loading={startTimer.isPending}
                disabled={runningElsewhere}
                title={runningElsewhere ? 'Таймер уже идёт по другому делу' : 'Запустить таймер'}
                onClick={() => startTimer.mutate()}
              >
                ▶ Таймер
              </Button>
            </div>
          </div>
        </div>
      )}

      {error && <p className="text-sm text-red-600 dark:text-red-400 mb-3">{error}</p>}

      {entries.length === 0 ? (
        <p className="text-sm text-light-secondary dark:text-dark-secondary">
          Записей учёта времени пока нет.
        </p>
      ) : (
        <div className="flex flex-col gap-2">
          {entries.map((entry) => (
            <div
              key={entry.id}
              className="flex items-center gap-3 p-3 rounded-lg bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border"
            >
              <div className="flex-1 min-w-0">
                <p className="text-sm text-light-text dark:text-dark-text truncate">
                  {entry.running ? '⏱ ' : ''}
                  {entry.description}
                </p>
                <span className="text-xs text-light-secondary dark:text-dark-secondary">
                  {new Date(entry.activityDate).toLocaleDateString('ru-RU')} · {formatDuration(entry.minutes)}
                  {entry.billable ? ` · ${formatMoney(entry.amount)}` : ' · не к оплате'}
                  {entry.invoiced ? ' · в счёте' : ''}
                </span>
              </div>
              {!entry.invoiced && !entry.running && (
                <button
                  type="button"
                  title="Удалить запись"
                  disabled={deleteEntry.isPending}
                  onClick={() => deleteEntry.mutate(entry.id)}
                  className="shrink-0 text-light-secondary dark:text-dark-secondary hover:text-red-600 dark:hover:text-red-400 transition-colors"
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
  'px-3 py-2.5 rounded-lg border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg text-light-text dark:text-dark-text text-sm placeholder:text-light-secondary/60 dark:placeholder:text-dark-secondary/60 focus:outline-none focus:ring-2 focus:ring-light-accent dark:focus:ring-dark-accent'

function RunningTimer({
  entry,
  onStop,
  stopping,
}: {
  entry: TimeEntryResponse
  onStop: () => void
  stopping: boolean
}): JSX.Element {
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
    <div className="flex items-center gap-3 p-4 mb-3 rounded-lg border border-light-accent/40 dark:border-dark-accent/40 bg-light-accent/5 dark:bg-dark-accent/10">
      <span className="text-lg font-mono tabular-nums text-light-text dark:text-dark-text">{elapsed}</span>
      <span className="flex-1 min-w-0 text-sm text-light-text dark:text-dark-text truncate">
        {entry.description}
      </span>
      <Button variant="danger" size="sm" loading={stopping} onClick={onStop}>
        ■ Стоп
      </Button>
    </div>
  )
}
