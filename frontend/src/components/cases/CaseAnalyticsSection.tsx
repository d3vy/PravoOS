import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { casesApi } from '../../api/cases'
import type { CaseAnalyticsResponse, CaseTimelineStats, CourtStat } from '../../types'
import { Button } from '../ui/Button'
import { Spinner } from '../ui/Spinner'

function formatDate(value: string | null): string {
  return value ? new Date(value).toLocaleDateString('ru-RU') : '—'
}

function formatDateTime(value: string): string {
  return new Date(value).toLocaleString('ru-RU', { dateStyle: 'short', timeStyle: 'short' })
}

export function CaseAnalyticsSection({ caseId }: { caseId: string }): JSX.Element {
  const queryClient = useQueryClient()

  const { data, isLoading } = useQuery<CaseAnalyticsResponse>({
    queryKey: ['case-analytics', caseId],
    queryFn: () => casesApi.getAnalytics(caseId),
    enabled: caseId !== '',
  })

  const generateMutation = useMutation({
    mutationFn: () => casesApi.generateAnalytics(caseId),
    onSuccess: (result) => {
      queryClient.setQueryData(['case-analytics', caseId], result)
    },
  })

  return (
    <section className="mb-10 p-5 rounded-xl bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border">
      <h2 className="text-sm font-semibold text-light-text dark:text-dark-text mb-1">Судебная аналитика</h2>
      <p className="text-xs text-light-secondary dark:text-dark-secondary mb-4">
        Статистика по хронологии КАД.Арбитр и AI-справка: краткое содержание, анализ позиций и стратегия.
      </p>

      {isLoading ? (
        <Spinner size="md" />
      ) : !data ? (
        <p className="text-sm text-light-secondary dark:text-dark-secondary">Нет данных для аналитики.</p>
      ) : (
        <div className="flex flex-col gap-5">
          <TimelineStatsView timeline={data.timeline} />

          {data.courtStats.length > 0 && <CourtStatsView courtStats={data.courtStats} />}

          {generateMutation.isError && (
            <p className="text-sm text-red-600 dark:text-red-400">Не удалось сформировать AI-справку. Попробуйте снова.</p>
          )}

          <div>
            <Button
              variant="primary"
              loading={generateMutation.isPending}
              onClick={() => generateMutation.mutate()}
            >
              {data.aiAnalysis ? 'Обновить AI-справку' : 'Сформировать AI-справку'}
            </Button>
          </div>

          {data.aiAnalysis && (
            <div className="p-4 rounded-lg border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg">
              <p className="text-xs text-light-secondary dark:text-dark-secondary mb-2">
                AI-справка · {formatDateTime(data.aiAnalysis.generatedAt)}
              </p>
              <p className="text-sm text-light-text dark:text-dark-text whitespace-pre-wrap">
                {data.aiAnalysis.content}
              </p>
            </div>
          )}
        </div>
      )}
    </section>
  )
}

function TimelineStatsView({ timeline }: { timeline: CaseTimelineStats }): JSX.Element {
  const metrics: { label: string; value: string }[] = [
    { label: 'Событий', value: String(timeline.hearingCount) },
    { label: 'Первое событие', value: formatDate(timeline.firstEventDate) },
    { label: 'Последнее событие', value: formatDate(timeline.lastEventDate) },
    { label: 'Период', value: timeline.spanDays == null ? '—' : `${timeline.spanDays} дн.` },
    {
      label: 'Средний интервал',
      value: timeline.averageIntervalDays == null ? '—' : `${timeline.averageIntervalDays} дн.`,
    },
    {
      label: 'До заседания',
      value:
        timeline.daysToNextHearing == null
          ? '—'
          : timeline.daysToNextHearing < 0
            ? 'прошло'
            : `${timeline.daysToNextHearing} дн.`,
    },
  ]

  return (
    <div>
      <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
        {metrics.map((metric) => (
          <div
            key={metric.label}
            className="p-3 rounded-lg border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg"
          >
            <p className="text-xs text-light-secondary dark:text-dark-secondary">{metric.label}</p>
            <p className="text-sm font-semibold text-light-text dark:text-dark-text">{metric.value}</p>
          </div>
        ))}
      </div>

      {timeline.eventTypes.length > 0 && (
        <div className="mt-3 flex flex-wrap gap-1.5">
          {timeline.eventTypes.map((event) => (
            <span
              key={event.type}
              className="text-xs px-2 py-0.5 rounded-md border border-light-border dark:border-dark-border text-light-secondary dark:text-dark-secondary"
            >
              {event.type} · {event.count}
            </span>
          ))}
        </div>
      )}
    </div>
  )
}

function CourtStatsView({ courtStats }: { courtStats: CourtStat[] }): JSX.Element {
  return (
    <div>
      <h3 className="text-xs font-semibold text-light-secondary dark:text-dark-secondary uppercase tracking-wide mb-2">
        Статистика по судам (все ваши дела)
      </h3>
      <div className="flex flex-col gap-2">
        {courtStats.map((court) => (
          <div
            key={court.courtName}
            className="flex items-center justify-between gap-3 p-3 rounded-lg border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg"
          >
            <div className="min-w-0">
              <p className="text-sm text-light-text dark:text-dark-text [overflow-wrap:anywhere]">{court.courtName}</p>
              <p className="text-xs text-light-secondary dark:text-dark-secondary">
                дел: {court.totalCases} · выиграно {court.wonCases} · проиграно {court.lostCases}
              </p>
            </div>
            {court.winRatePercent != null && (
              <div className="text-right shrink-0">
                <span
                  className={`text-lg font-semibold ${
                    court.winRatePercent >= 50
                      ? 'text-emerald-600 dark:text-emerald-400'
                      : 'text-amber-600 dark:text-amber-400'
                  }`}
                >
                  {court.winRatePercent}%
                </span>
                <p className="text-xs text-light-secondary dark:text-dark-secondary">выигрышей</p>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}
