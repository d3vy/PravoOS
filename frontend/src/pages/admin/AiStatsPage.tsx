import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import i18n from '../../i18n'
import { aiStatsApi } from '../../api/aiStats'
import { adminApi } from '../../api/admin'
import type { AiResponseDto, AiStatsResponse, ClientStatsResponse } from '../../types'
import { Spinner } from '../../components/ui/Spinner'

export default function AiStatsPage(): JSX.Element {
  const { t } = useTranslation()
  const dateLocale = i18n.language.startsWith('ru') ? 'ru-RU' : 'en-US'
  const { data: stats, isLoading } = useQuery<AiStatsResponse>({
    queryKey: ['ai-stats'],
    queryFn: aiStatsApi.getStats,
  })

  const { data: recent = [] } = useQuery<AiResponseDto[]>({
    queryKey: ['ai-recent-responses'],
    queryFn: aiStatsApi.getRecentResponses,
  })

  const { data: clientStats } = useQuery<ClientStatsResponse>({
    queryKey: ['client-stats'],
    queryFn: adminApi.getClientStats,
  })

  if (isLoading || !stats) {
    return (
      <div className="flex justify-center py-16">
        <Spinner size="lg" />
      </div>
    )
  }

  return (
    <div className="p-6 lg:p-8">
      <div className="mb-8">
        <h1 className="text-3xl font-semibold text-light-text dark:text-dark-text mb-1">{t('aiStats.title')}</h1>
        <p className="text-sm text-light-secondary dark:text-dark-secondary">
          {t('aiStats.subtitle')}
        </p>
      </div>

      {clientStats && (
        <div className="mb-8">
          <h2 className="text-sm font-semibold text-light-text dark:text-dark-text mb-3">{t('aiStats.clients')}</h2>
          <div className="p-5 rounded-xl bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border max-w-xs">
            <div className="flex items-center gap-2 text-sm text-light-secondary dark:text-dark-secondary mb-2">
              {t('aiStats.newClients')}
            </div>
            <div className="flex items-baseline gap-2">
              <span className="text-3xl font-semibold text-light-text dark:text-dark-text">
                +{clientStats.newThisWeek}
              </span>
              <span className="text-xs text-light-accent dark:text-dark-accent">{t('aiStats.perWeek')}</span>
            </div>
            <p className="text-xs text-light-secondary dark:text-dark-secondary mt-2">
              {t('aiStats.totalActive', { count: clientStats.totalActive })}
            </p>
          </div>
        </div>
      )}

      <h2 className="text-sm font-semibold text-light-text dark:text-dark-text mb-3">AI</h2>
      <div className="grid gap-3 grid-cols-2 lg:grid-cols-4 mb-8">
        <StatCard label={t('aiStats.totalResponses')} value={stats.totalResponses} />
        <StatCard label={t('aiStats.rated')} value={stats.ratedResponses} />
        <StatCard label={t('aiStats.positive')} value={stats.positiveRatings} accent="positive" />
        <StatCard label={t('aiStats.negative')} value={stats.negativeRatings} accent="negative" />
      </div>

      <h2 className="text-sm font-semibold text-light-text dark:text-dark-text mb-3">{t('aiStats.trustTitle')}</h2>
      <div className="grid gap-3 grid-cols-2 lg:grid-cols-3 mb-8">
        <ShareCard
          label={t('aiStats.verifiedCitations')}
          numerator={stats.citationsVerified}
          denominator={stats.citationsChecked}
          hint={t('aiStats.verifiedCitationsHint', { verified: stats.citationsVerified, checked: stats.citationsChecked })}
          accent="positive"
        />
        <ShareCard
          label={t('aiStats.positiveRatingsShare')}
          numerator={stats.positiveRatings}
          denominator={stats.positiveRatings + stats.negativeRatings}
          hint={t('aiStats.positiveRatingsHint', { positive: stats.positiveRatings, negative: stats.negativeRatings })}
        />
        <ShareCard
          label={t('aiStats.refusals')}
          numerator={stats.guardRefusals}
          denominator={stats.guardChecks}
          hint={t('aiStats.refusalsHint', { refusals: stats.guardRefusals, checks: stats.guardChecks })}
          accent="negative"
        />
      </div>

      <h2 className="text-sm font-semibold text-light-text dark:text-dark-text mb-3">{t('aiStats.byWorkflowTitle')}</h2>
      {stats.workflows.length === 0 ? (
        <p className="text-sm text-light-secondary dark:text-dark-secondary mb-8">{t('aiStats.noData')}</p>
      ) : (
        <div className="flex flex-col gap-2 mb-8">
          {stats.workflows.map((workflow) => (
            <div
              key={workflow.workflowId}
              className="flex items-center justify-between p-4 rounded-xl bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border"
            >
              <span className="text-sm text-light-text dark:text-dark-text">{workflow.workflowName}</span>
              <div className="flex items-center gap-6 text-sm">
                <span className="text-light-secondary dark:text-dark-secondary">{t('aiStats.runsCount', { count: workflow.count })}</span>
                <span className="text-light-text dark:text-dark-text w-20 text-right">
                  {workflow.avgRating !== null ? `★ ${workflow.avgRating.toFixed(2)}` : '—'}
                </span>
              </div>
            </div>
          ))}
        </div>
      )}

      <h2 className="text-sm font-semibold text-light-text dark:text-dark-text mb-3">{t('aiStats.recentTitle')}</h2>
      {recent.length === 0 ? (
        <p className="text-sm text-light-secondary dark:text-dark-secondary">{t('aiStats.noResponses')}</p>
      ) : (
        <div className="flex flex-col gap-2">
          {recent.map((response) => (
            <div
              key={response.id}
              className="p-4 rounded-xl bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border"
            >
              <div className="flex items-center justify-between mb-1.5">
                <span className="text-xs font-medium px-2 py-0.5 rounded-full bg-light-accent/10 dark:bg-dark-accent/10 text-light-accent dark:text-dark-accent">
                  {response.workflowName}
                </span>
                <span className="text-xs text-light-secondary dark:text-dark-secondary">
                  {ratingLabel(response.rating)} · {new Date(response.createdAt).toLocaleDateString(dateLocale)}
                </span>
              </div>
              <p className="text-sm text-light-text dark:text-dark-text line-clamp-2">{response.result}</p>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

function ratingLabel(rating: number | null): string {
  if (rating === 1) return '👍'
  if (rating === -1) return '👎'
  return i18n.t('aiStats.unrated')
}

interface ShareCardProps {
  label: string
  numerator: number
  denominator: number
  hint: string
  accent?: 'positive' | 'negative'
}

function ShareCard({ label, numerator, denominator, hint, accent }: ShareCardProps): JSX.Element {
  const share = denominator > 0 ? Math.round((numerator / denominator) * 100) : null
  const valueColor =
    accent === 'positive'
      ? 'text-emerald-600 dark:text-emerald-400'
      : accent === 'negative'
        ? 'text-red-600 dark:text-red-400'
        : 'text-light-text dark:text-dark-text'
  return (
    <div className="p-4 rounded-xl bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border">
      <p className={`text-2xl font-semibold ${valueColor}`}>{share === null ? '—' : `${share}%`}</p>
      <p className="text-xs text-light-secondary dark:text-dark-secondary mt-1">{label}</p>
      <p className="text-xs text-light-secondary dark:text-dark-secondary mt-2 opacity-70">{hint}</p>
    </div>
  )
}

interface StatCardProps {
  label: string
  value: number
  accent?: 'positive' | 'negative'
}

function StatCard({ label, value, accent }: StatCardProps): JSX.Element {
  const valueColor =
    accent === 'positive'
      ? 'text-emerald-600 dark:text-emerald-400'
      : accent === 'negative'
        ? 'text-red-600 dark:text-red-400'
        : 'text-light-text dark:text-dark-text'
  return (
    <div className="p-4 rounded-xl bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border">
      <p className={`text-2xl font-semibold ${valueColor}`}>{value}</p>
      <p className="text-xs text-light-secondary dark:text-dark-secondary mt-1">{label}</p>
    </div>
  )
}
