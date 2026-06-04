import { useQuery } from '@tanstack/react-query'
import { aiStatsApi } from '../../api/aiStats'
import type { AiResponseDto, AiStatsResponse } from '../../types'
import { Spinner } from '../../components/ui/Spinner'

export default function AiStatsPage(): JSX.Element {
  const { data: stats, isLoading } = useQuery<AiStatsResponse>({
    queryKey: ['ai-stats'],
    queryFn: aiStatsApi.getStats,
  })

  const { data: recent = [] } = useQuery<AiResponseDto[]>({
    queryKey: ['ai-recent-responses'],
    queryFn: aiStatsApi.getRecentResponses,
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
        <h1 className="text-3xl font-semibold text-light-text dark:text-dark-text mb-1">AI-метрики</h1>
        <p className="text-sm text-light-secondary dark:text-dark-secondary">
          Использование AI-анализа и оценки юристов по workflow
        </p>
      </div>

      <div className="grid gap-3 grid-cols-2 lg:grid-cols-4 mb-8">
        <StatCard label="Всего заключений" value={stats.totalResponses} />
        <StatCard label="Оценено" value={stats.ratedResponses} />
        <StatCard label="Полезных 👍" value={stats.positiveRatings} accent="positive" />
        <StatCard label="Бесполезных 👎" value={stats.negativeRatings} accent="negative" />
      </div>

      <h2 className="text-sm font-semibold text-light-text dark:text-dark-text mb-3">По типам анализа</h2>
      {stats.workflows.length === 0 ? (
        <p className="text-sm text-light-secondary dark:text-dark-secondary mb-8">Пока нет данных.</p>
      ) : (
        <div className="flex flex-col gap-2 mb-8">
          {stats.workflows.map((workflow) => (
            <div
              key={workflow.workflowId}
              className="flex items-center justify-between p-4 rounded-xl bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border"
            >
              <span className="text-sm text-light-text dark:text-dark-text">{workflow.workflowName}</span>
              <div className="flex items-center gap-6 text-sm">
                <span className="text-light-secondary dark:text-dark-secondary">{workflow.count} запусков</span>
                <span className="text-light-text dark:text-dark-text w-20 text-right">
                  {workflow.avgRating !== null ? `★ ${workflow.avgRating.toFixed(2)}` : '—'}
                </span>
              </div>
            </div>
          ))}
        </div>
      )}

      <h2 className="text-sm font-semibold text-light-text dark:text-dark-text mb-3">Последние заключения</h2>
      {recent.length === 0 ? (
        <p className="text-sm text-light-secondary dark:text-dark-secondary">Пока нет заключений.</p>
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
                  {ratingLabel(response.rating)} · {new Date(response.createdAt).toLocaleDateString('ru-RU')}
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
  return 'без оценки'
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
