import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import i18n from '../../i18n'
import { casesApi } from '../../api/cases'
import type { CaseAnalyticsResponse, CaseTimelineStats, OutcomeStat } from '../../types'
import { Button } from '../ui/Button'
import { Spinner } from '../ui/Spinner'

function locale(): string {
  return i18n.language.startsWith('ru') ? 'ru-RU' : 'en-US'
}

function formatDate(value: string | null): string {
  return value ? new Date(value).toLocaleDateString(locale()) : '—'
}

function formatDateTime(value: string): string {
  return new Date(value).toLocaleString(locale(), { dateStyle: 'short', timeStyle: 'short' })
}

export function CaseAnalyticsSection({ caseId }: { caseId: string }): JSX.Element {
  const { t } = useTranslation()
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
      <h2 className="text-sm font-semibold text-light-text dark:text-dark-text mb-1">{t('analytics.title')}</h2>
      <p className="text-xs text-light-secondary dark:text-dark-secondary mb-4">
        {t('analytics.hint')}
      </p>

      {isLoading ? (
        <Spinner size="md" />
      ) : !data ? (
        <p className="text-sm text-light-secondary dark:text-dark-secondary">{t('analytics.noData')}</p>
      ) : (
        <div className="flex flex-col gap-5">
          <TimelineStatsView timeline={data.timeline} />

          {data.courtStats.length > 0 && (
            <OutcomeStatsView title={t('analytics.courtStats')} stats={data.courtStats} />
          )}
          {data.judgeStats.length > 0 && (
            <OutcomeStatsView title={t('analytics.judgeStats')} stats={data.judgeStats} />
          )}
          {data.partyStats.length > 0 && (
            <OutcomeStatsView title={t('analytics.partyStats')} stats={data.partyStats} />
          )}

          {generateMutation.isError && (
            <p className="text-sm text-red-600 dark:text-red-400">{t('analytics.generateError')}</p>
          )}

          <div>
            <Button
              variant="primary"
              loading={generateMutation.isPending}
              onClick={() => generateMutation.mutate()}
            >
              {data.aiAnalysis ? t('analytics.updateAiReport') : t('analytics.generateAiReport')}
            </Button>
          </div>

          {data.aiAnalysis && (
            <div className="p-4 rounded-lg border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg">
              <p className="text-xs text-light-secondary dark:text-dark-secondary mb-2">
                {t('analytics.aiReport')} · {formatDateTime(data.aiAnalysis.generatedAt)}
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
  const { t } = useTranslation()
  const metrics: { label: string; value: string }[] = [
    { label: t('analytics.metricEvents'), value: String(timeline.hearingCount) },
    { label: t('analytics.metricFirstEvent'), value: formatDate(timeline.firstEventDate) },
    { label: t('analytics.metricLastEvent'), value: formatDate(timeline.lastEventDate) },
    {
      label: t('analytics.metricSpan'),
      value: timeline.spanDays == null ? '—' : t('analytics.days', { count: timeline.spanDays }),
    },
    {
      label: t('analytics.metricAverageInterval'),
      value:
        timeline.averageIntervalDays == null
          ? '—'
          : t('analytics.days', { count: timeline.averageIntervalDays }),
    },
    {
      label: t('analytics.metricToNextHearing'),
      value:
        timeline.daysToNextHearing == null
          ? '—'
          : timeline.daysToNextHearing < 0
            ? t('analytics.passed')
            : t('analytics.days', { count: timeline.daysToNextHearing }),
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

      {timeline.judge && (
        <p className="mt-3 text-sm text-light-text dark:text-dark-text">
          <span className="text-light-secondary dark:text-dark-secondary">{t('analytics.judge')}</span>
          {timeline.judge}
        </p>
      )}

      {timeline.parties.length > 0 && (
        <div className="mt-2">
          <p className="text-xs text-light-secondary dark:text-dark-secondary mb-1">{t('analytics.parties')}</p>
          <div className="flex flex-wrap gap-1.5">
            {timeline.parties.map((party) => (
              <span
                key={`${party.name}-${party.role ?? ''}`}
                className="text-xs px-2 py-0.5 rounded-md border border-light-border dark:border-dark-border text-light-secondary dark:text-dark-secondary"
              >
                {party.name}
                {party.role && <span className="opacity-70"> · {party.role}</span>}
              </span>
            ))}
          </div>
        </div>
      )}

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

function OutcomeStatsView({ title, stats }: { title: string; stats: OutcomeStat[] }): JSX.Element {
  const { t } = useTranslation()
  return (
    <div>
      <h3 className="text-xs font-semibold text-light-secondary dark:text-dark-secondary uppercase tracking-wide mb-2">
        {title}
      </h3>
      <div className="flex flex-col gap-2">
        {stats.map((stat) => (
          <div
            key={stat.name}
            className="flex items-center justify-between gap-3 p-3 rounded-lg border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg"
          >
            <div className="min-w-0">
              <p className="text-sm text-light-text dark:text-dark-text [overflow-wrap:anywhere]">{stat.name}</p>
              <p className="text-xs text-light-secondary dark:text-dark-secondary">
                {t('analytics.casesLabel', { total: stat.totalCases, won: stat.wonCases, lost: stat.lostCases })}
              </p>
            </div>
            {stat.winRatePercent != null && (
              <div className="text-right shrink-0">
                <span
                  className={`text-lg font-semibold ${
                    stat.winRatePercent >= 50
                      ? 'text-emerald-600 dark:text-emerald-400'
                      : 'text-amber-600 dark:text-amber-400'
                  }`}
                >
                  {stat.winRatePercent}%
                </span>
                <p className="text-xs text-light-secondary dark:text-dark-secondary">{t('analytics.winRate')}</p>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}
