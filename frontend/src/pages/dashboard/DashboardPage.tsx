import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { dashboardApi } from '../../api/dashboard'
import { Spinner } from '../../components/ui/Spinner'
import { Badge, CaseStatusBadge, CASE_STATUS_CONFIG, CASE_STATUS_ORDER } from '../../components/ui/Badge'
import type { CaseStatus, DashboardDeadline, DashboardResponse } from '../../types'

const STATUS_BAR_COLOR: Record<CaseStatus, string> = {
  INTAKE: 'bg-zinc-400 dark:bg-zinc-500',
  IN_PROGRESS: 'bg-blue-500',
  SUBMITTED: 'bg-amber-500',
  CLOSED_WON: 'bg-emerald-500',
  CLOSED_LOST: 'bg-red-500',
}

function formatDate(isoDate: string): string {
  const [year, month, day] = isoDate.split('-')
  return `${day}.${month}.${year}`
}

function daysLeftLabel(daysLeft: number): string {
  if (daysLeft <= 0) return 'сегодня'
  if (daysLeft === 1) return 'завтра'
  return `через ${daysLeft} дн.`
}

export default function DashboardPage(): JSX.Element {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['dashboard'],
    queryFn: dashboardApi.get,
  })

  return (
    <div className="bg-light-bg dark:bg-dark-bg">
      <div className="page-container py-8">
        <div className="mb-8">
          <p className="eyebrow mb-1">Рабочий стол</p>
          <h1 className="text-3xl font-semibold text-light-text dark:text-dark-text">Дашборд</h1>
        </div>

        {isLoading && (
          <div className="flex justify-center py-20">
            <Spinner />
          </div>
        )}

        {isError && (
          <div className="card-elevated p-6 text-light-secondary dark:text-dark-secondary">
            Не удалось загрузить данные дашборда. Попробуйте обновить страницу.
          </div>
        )}

        {data && <DashboardContent data={data} />}
      </div>
    </div>
  )
}

function DashboardContent({ data }: { data: DashboardResponse }): JSX.Element {
  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <StatCard label="Активные дела" value={data.activeCases} to="/cases" />
        <StatCard label="Незакрытые задачи" value={data.openTasks} />
        <StatCard label="Дедлайнов на неделе" value={data.upcomingDeadlines.length} />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <PipelineWidget data={data} />
        <DeadlinesWidget deadlines={data.upcomingDeadlines} />
      </div>

      <RecentCasesWidget cases={data.recentCases} />
    </div>
  )
}

function StatCard({ label, value, to }: { label: string; value: number; to?: string }): JSX.Element {
  const content = (
    <div className="card-elevated p-5 h-full">
      <p className="text-sm text-light-secondary dark:text-dark-secondary mb-1">{label}</p>
      <p className="text-3xl font-semibold text-light-text dark:text-dark-text">{value}</p>
    </div>
  )
  return to ? (
    <Link to={to} className="block hover:opacity-90 transition-opacity">
      {content}
    </Link>
  ) : (
    content
  )
}

function PipelineWidget({ data }: { data: DashboardResponse }): JSX.Element {
  const maxCount = Math.max(1, ...data.pipeline.map((item) => item.count))
  const total = data.pipeline.reduce((sum, item) => sum + item.count, 0)

  return (
    <div className="card-elevated p-5">
      <h2 className="text-lg font-semibold text-light-text dark:text-dark-text mb-4">Воронка дел</h2>
      {total === 0 ? (
        <p className="text-sm text-light-secondary dark:text-dark-secondary">Дел пока нет.</p>
      ) : (
        <div className="space-y-3">
          {CASE_STATUS_ORDER.map((status) => {
            const item = data.pipeline.find((entry) => entry.status === status)
            const count = item?.count ?? 0
            return (
              <div key={status}>
                <div className="flex items-center justify-between mb-1">
                  <span className="text-sm text-light-text dark:text-dark-text">
                    {CASE_STATUS_CONFIG[status].label}
                  </span>
                  <span className="text-sm font-medium text-light-secondary dark:text-dark-secondary">{count}</span>
                </div>
                <div className="h-2 rounded-full bg-light-bg dark:bg-dark-bg overflow-hidden">
                  <div
                    className={`h-full rounded-full ${STATUS_BAR_COLOR[status]}`}
                    style={{ width: `${(count / maxCount) * 100}%` }}
                  />
                </div>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}

function DeadlinesWidget({ deadlines }: { deadlines: DashboardDeadline[] }): JSX.Element {
  return (
    <div className="card-elevated p-5">
      <h2 className="text-lg font-semibold text-light-text dark:text-dark-text mb-4">Ближайшие дедлайны</h2>
      {deadlines.length === 0 ? (
        <p className="text-sm text-light-secondary dark:text-dark-secondary">Дедлайнов на ближайшие 7 дней нет.</p>
      ) : (
        <ul className="space-y-2">
          {deadlines.map((deadline, index) => (
            <li key={`${deadline.caseId}-${deadline.type}-${index}`}>
              <Link
                to={`/cases/${deadline.caseId}`}
                className="flex items-center justify-between gap-3 p-3 rounded-lg border border-light-border dark:border-dark-border hover:bg-light-bg dark:hover:bg-dark-bg transition-colors"
              >
                <div className="min-w-0">
                  <p className="text-sm font-medium text-light-text dark:text-dark-text truncate">
                    {deadline.caseTitle}
                  </p>
                  <p className="text-xs text-light-secondary dark:text-dark-secondary">
                    {deadline.typeName} · {formatDate(deadline.date)}
                  </p>
                </div>
                <Badge variant={deadline.daysLeft <= 3 ? 'danger' : 'warning'}>
                  {daysLeftLabel(deadline.daysLeft)}
                </Badge>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

function RecentCasesWidget({ cases }: { cases: DashboardResponse['recentCases'] }): JSX.Element {
  return (
    <div className="card-elevated p-5">
      <h2 className="text-lg font-semibold text-light-text dark:text-dark-text mb-4">Последние дела</h2>
      {cases.length === 0 ? (
        <p className="text-sm text-light-secondary dark:text-dark-secondary">Дел пока нет.</p>
      ) : (
        <ul className="divide-y divide-light-border dark:divide-dark-border">
          {cases.map((caseItem) => (
            <li key={caseItem.id}>
              <Link
                to={`/cases/${caseItem.id}`}
                className="flex items-center justify-between gap-3 py-3 hover:opacity-80 transition-opacity"
              >
                <span className="min-w-0 text-sm font-medium text-light-text dark:text-dark-text truncate">
                  {caseItem.title}
                </span>
                <CaseStatusBadge status={caseItem.status} />
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
