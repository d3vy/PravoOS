import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import i18n from '../../i18n'
import { aiStatsApi } from '../../api/aiStats'
import { adminApi } from '../../api/admin'
import type { AiResponseDto, AiStatsResponse, ClientStatsResponse } from '../../types'
import { Spinner } from '../../components/ui/Spinner'
import { PageHeader } from '../../components/ui/PageHeader'

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
      <PageHeader title={t('aiStats.title')} description={t('aiStats.subtitle')} />

      {clientStats && (
        <div className="mb-8">
          <h2 className="text-sm font-semibold text-fg mb-3">{t('aiStats.clients')}</h2>
          <div className="p-5 rounded-xl bg-surface border border-line max-w-xs">
            <div className="flex items-center gap-2 text-sm text-fg-muted mb-2">
              {t('aiStats.newClients')}
            </div>
            <div className="flex items-baseline gap-2">
              <span className="text-3xl font-semibold text-fg">
                +{clientStats.newThisWeek}
              </span>
              <span className="text-xs text-accent">{t('aiStats.perWeek')}</span>
            </div>
            <p className="text-xs text-fg-muted mt-2">
              {t('aiStats.totalActive', { count: clientStats.totalActive })}
            </p>
          </div>
        </div>
      )}

      <h2 className="text-sm font-semibold text-fg mb-3">AI</h2>
      <div className="grid gap-3 grid-cols-2 lg:grid-cols-4 mb-8">
        <StatCard label={t('aiStats.totalResponses')} value={stats.totalResponses} />
        <StatCard label={t('aiStats.rated')} value={stats.ratedResponses} />
        <StatCard label={t('aiStats.positive')} value={stats.positiveRatings} accent="positive" />
        <StatCard label={t('aiStats.negative')} value={stats.negativeRatings} accent="negative" />
      </div>

      <h2 className="text-sm font-semibold text-fg mb-3">{t('aiStats.trustTitle')}</h2>
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

      <h2 className="text-sm font-semibold text-fg mb-3">{t('aiStats.byWorkflowTitle')}</h2>
      {stats.workflows.length === 0 ? (
        <p className="text-sm text-fg-muted mb-8">{t('aiStats.noData')}</p>
      ) : (
        <div className="flex flex-col gap-2 mb-8">
          {stats.workflows.map((workflow) => (
            <div
              key={workflow.workflowId}
              className="flex items-center justify-between p-4 rounded-xl bg-surface border border-line"
            >
              <span className="text-sm text-fg">{workflow.workflowName}</span>
              <div className="flex items-center gap-6 text-sm">
                <span className="text-fg-muted">{t('aiStats.runsCount', { count: workflow.count })}</span>
                <span className="text-fg w-20 text-right">
                  {workflow.avgRating !== null ? `★ ${workflow.avgRating.toFixed(2)}` : '—'}
                </span>
              </div>
            </div>
          ))}
        </div>
      )}

      <h2 className="text-sm font-semibold text-fg mb-3">{t('aiStats.agentTitle')}</h2>
      {stats.agentTools.length === 0 ? (
        <p className="text-sm text-fg-muted mb-8">{t('aiStats.agentNoData')}</p>
      ) : (
        <div className="flex flex-col gap-2 mb-8">
          {stats.agentTools.map((tool) => {
            const decided = tool.approved + tool.rejected + tool.expired + tool.failed
            const approvalRate = decided > 0 ? Math.round((tool.approved / decided) * 100) : null
            return (
              <div
                key={tool.toolName}
                className="flex items-center justify-between p-4 rounded-xl bg-surface border border-line"
              >
                <span className="text-sm text-fg">
                  {t(`chat.toolStep.${tool.toolName}`, { defaultValue: tool.toolName })}
                </span>
                <div className="flex items-center gap-4 text-xs text-fg-muted">
                  <span>{t('aiStats.agentCreated')}: {tool.created}</span>
                  <span>{t('aiStats.agentApproved')}: {tool.approved}</span>
                  <span>{t('aiStats.agentRejected')}: {tool.rejected}</span>
                  <span>{t('aiStats.agentExpired')}: {tool.expired}</span>
                  <span>{t('aiStats.agentFailed')}: {tool.failed}</span>
                  <span className="text-fg w-24 text-right">
                    {approvalRate === null ? '—' : `${approvalRate}%`}
                  </span>
                </div>
              </div>
            )
          })}
        </div>
      )}

      <h2 className="text-sm font-semibold text-fg mb-3">{t('aiStats.recentTitle')}</h2>
      {recent.length === 0 ? (
        <p className="text-sm text-fg-muted">{t('aiStats.noResponses')}</p>
      ) : (
        <div className="flex flex-col gap-2">
          {recent.map((response) => (
            <div
              key={response.id}
              className="p-4 rounded-xl bg-surface border border-line"
            >
              <div className="flex items-center justify-between mb-1.5">
                <span className="text-xs font-medium px-2 py-0.5 rounded-full bg-accent/10 text-accent">
                  {response.workflowName}
                </span>
                <span className="text-xs text-fg-muted">
                  {ratingLabel(response.rating)} · {new Date(response.createdAt).toLocaleDateString(dateLocale)}
                </span>
              </div>
              <p className="text-sm text-fg line-clamp-2">{response.result}</p>
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
      ? 'text-success'
      : accent === 'negative'
        ? 'text-danger'
        : 'text-fg'
  return (
    <div className="p-4 rounded-xl bg-surface border border-line">
      <p className={`text-2xl font-semibold ${valueColor}`}>{share === null ? '—' : `${share}%`}</p>
      <p className="text-xs text-fg-muted mt-1">{label}</p>
      <p className="text-xs text-fg-muted mt-2 opacity-70">{hint}</p>
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
      ? 'text-success'
      : accent === 'negative'
        ? 'text-danger'
        : 'text-fg'
  return (
    <div className="p-4 rounded-xl bg-surface border border-line">
      <p className={`text-2xl font-semibold ${valueColor}`}>{value}</p>
      <p className="text-xs text-fg-muted mt-1">{label}</p>
    </div>
  )
}
