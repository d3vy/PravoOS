import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Trans, useTranslation } from 'react-i18next'
import { dashboardApi } from '../../api/dashboard'
import { Button } from '../../components/ui/Button'
import { Spinner } from '../../components/ui/Spinner'
import { Badge, CaseStatusBadge, caseStatusLabel, CASE_STATUS_ORDER } from '../../components/ui/Badge'
import type { CaseStatus, DashboardDeadline, DashboardResponse } from '../../types'
import type { TFunction } from 'i18next'

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

function daysLeftLabel(daysLeft: number, t: TFunction): string {
  if (daysLeft <= 0) return t('dashboard.today')
  if (daysLeft === 1) return t('dashboard.tomorrow')
  return t('dashboard.inDays', { count: daysLeft })
}

function greeting(hour: number, t: TFunction): string {
  if (hour >= 5 && hour < 12) return t('dashboard.greetingMorning')
  if (hour >= 12 && hour < 18) return t('dashboard.greetingDay')
  if (hour >= 18 && hour < 23) return t('dashboard.greetingEvening')
  return t('dashboard.greetingNight')
}

export default function DashboardPage(): JSX.Element {
  const { t } = useTranslation()
  const { data, isLoading, isError } = useQuery({
    queryKey: ['dashboard'],
    queryFn: dashboardApi.get,
  })

  return (
    <div className="bg-bg">
      <div className="page-container py-8">
        <div className="mb-8">
          <p className="eyebrow mb-1">{t('dashboard.eyebrow')}</p>
          <h1 className="text-3xl font-semibold text-fg">{t('dashboard.title')}</h1>
        </div>

        {isLoading && (
          <div className="flex justify-center py-20">
            <Spinner />
          </div>
        )}

        {isError && (
          <div className="card-elevated p-6 text-fg-muted">
            {t('dashboard.loadError')}
          </div>
        )}

        {data && <DashboardContent data={data} />}
      </div>
    </div>
  )
}

function DashboardContent({ data }: { data: DashboardResponse }): JSX.Element {
  const { t } = useTranslation()
  return (
    <div className="space-y-6">
      <DigestBanner data={data} />

      {data.activeCases === 0 && data.recentCases.length === 0 && <QuickStartCard />}

      <QuickAskWidget />

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <StatCard label={t('dashboard.statActiveCases')} value={data.activeCases} to="/cases" />
        <StatCard label={t('dashboard.statOpenTasks')} value={data.openTasks} />
        <StatCard label={t('dashboard.statWeekDeadlines')} value={data.upcomingDeadlines.length} />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <PipelineWidget data={data} />
        <DeadlinesWidget deadlines={data.upcomingDeadlines} />
      </div>

      <RecentCasesWidget cases={data.recentCases} />
    </div>
  )
}

const ONBOARDING_DISMISSED_KEY = 'pravoos.onboarding.dismissed'

function QuickStartCard(): JSX.Element | null {
  const { t } = useTranslation()
  const [dismissed, setDismissed] = useState(() => localStorage.getItem(ONBOARDING_DISMISSED_KEY) === 'true')

  if (dismissed) return null

  const steps = [
    { to: '/cases?new=1', title: t('dashboard.step1Title'), description: t('dashboard.step1Desc') },
    { to: '/clients?new=1', title: t('dashboard.step2Title'), description: t('dashboard.step2Desc') },
    { to: '/chat', title: t('dashboard.step3Title'), description: t('dashboard.step3Desc') },
  ]

  const dismiss = (): void => {
    localStorage.setItem(ONBOARDING_DISMISSED_KEY, 'true')
    setDismissed(true)
  }

  return (
    <div className="card-elevated p-5">
      <div className="flex items-start justify-between gap-3 mb-4">
        <div>
          <h2 className="text-lg font-semibold text-fg">{t('dashboard.quickStartTitle')}</h2>
          <p className="text-sm text-fg-muted">{t('dashboard.quickStartSubtitle')}</p>
        </div>
        <button
          type="button"
          onClick={dismiss}
          aria-label={t('dashboard.quickStartHide')}
          className="shrink-0 text-fg-muted hover:text-fg transition-colors"
        >
          <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
            <line x1="6" y1="6" x2="18" y2="18" />
            <line x1="6" y1="18" x2="18" y2="6" />
          </svg>
        </button>
      </div>
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
        {steps.map((step) => (
          <Link
            key={step.to}
            to={step.to}
            className="block p-4 rounded-xl border border-line hover:border-accent/50 transition-colors"
          >
            <p className="text-sm font-medium text-fg mb-1">{step.title}</p>
            <p className="text-xs text-fg-muted">{step.description}</p>
          </Link>
        ))}
      </div>
    </div>
  )
}

function QuickAskWidget(): JSX.Element {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [question, setQuestion] = useState('')

  const submit = (): void => {
    const trimmed = question.trim()
    navigate(trimmed ? `/chat?ask=${encodeURIComponent(trimmed)}` : '/chat')
  }

  return (
    <div className="card-elevated p-5">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2 mb-3">
        <h2 className="text-lg font-semibold text-fg">{t('dashboard.quickAskTitle')}</h2>
        <div className="flex flex-wrap items-center gap-2">
          <Link to="/cases?new=1">
            <Button variant="secondary" size="sm">{t('dashboard.newCase')}</Button>
          </Link>
          <Link to="/calendar">
            <Button variant="ghost" size="sm">{t('dashboard.calendar')}</Button>
          </Link>
        </div>
      </div>
      <form
        onSubmit={(e) => {
          e.preventDefault()
          submit()
        }}
        className="flex items-center gap-2"
      >
        <input
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          placeholder={t('dashboard.quickAskPlaceholder')}
          className="flex-1 min-w-0 px-3 py-2.5 rounded-lg border border-line bg-bg text-fg text-sm placeholder:text-fg-muted/60 focus:outline-none focus:ring-2 focus:ring-accent"
        />
        <Button type="submit" variant="primary" size="sm">{t('dashboard.ask')}</Button>
      </form>
    </div>
  )
}

function DigestBanner({ data }: { data: DashboardResponse }): JSX.Element {
  const { t, i18n } = useTranslation()
  const now = new Date()
  const dueToday = data.upcomingDeadlines.filter((deadline) => deadline.daysLeft <= 0).length
  const weekCount = data.upcomingDeadlines.length
  const locale = i18n.language.startsWith('ru') ? 'ru-RU' : 'en-US'
  const dateLabel = now.toLocaleDateString(locale, { weekday: 'long', day: 'numeric', month: 'long' })

  let focus: JSX.Element
  if (dueToday > 0) {
    focus = (
      <Trans
        i18nKey="dashboard.focusDueToday"
        count={dueToday}
        components={{ 1: <span className="font-semibold text-danger" /> }}
      />
    )
  } else if (weekCount > 0) {
    focus = (
      <Trans
        i18nKey="dashboard.focusWeek"
        count={weekCount}
        components={{ 1: <span className="font-semibold text-fg" /> }}
      />
    )
  } else {
    focus = <>{t('dashboard.focusNone')}</>
  }

  return (
    <div className="card-elevated p-6 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
      <div className="min-w-0">
        <div className="flex items-baseline gap-3 flex-wrap">
          <h2 className="text-2xl font-semibold text-fg">{greeting(now.getHours(), t)}</h2>
          <span className="text-sm text-fg-muted capitalize">{dateLabel}</span>
        </div>
        <p className="mt-1.5 text-sm text-fg-muted">{focus}</p>
      </div>
      <div className="flex items-center gap-2 shrink-0">
        <FocusPill label={t('dashboard.pillActiveCases')} value={data.activeCases} to="/cases" />
        <FocusPill label={t('dashboard.pillTasks')} value={data.openTasks} />
        <FocusPill label={t('dashboard.pillWeek')} value={weekCount} tone={dueToday > 0 ? 'danger' : 'default'} />
      </div>
    </div>
  )
}

function FocusPill({
  label,
  value,
  to,
  tone = 'default',
}: {
  label: string
  value: number
  to?: string
  tone?: 'default' | 'danger'
}): JSX.Element {
  const toneClass =
    tone === 'danger' && value > 0
      ? 'text-danger'
      : 'text-fg'
  const content = (
    <div className="px-3 py-2 rounded-lg bg-bg border border-line text-center min-w-[72px]">
      <p className={`text-xl font-semibold ${toneClass}`}>{value}</p>
      <p className="text-[11px] text-fg-muted whitespace-nowrap">{label}</p>
    </div>
  )
  return to ? (
    <Link to={to} className="hover:opacity-90 transition-opacity">
      {content}
    </Link>
  ) : (
    content
  )
}

function StatCard({ label, value, to }: { label: string; value: number; to?: string }): JSX.Element {
  const content = (
    <div className="card-elevated p-5 h-full">
      <p className="text-sm text-fg-muted mb-1">{label}</p>
      <p className="text-3xl font-semibold text-fg">{value}</p>
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
  const { t } = useTranslation()
  const maxCount = Math.max(1, ...data.pipeline.map((item) => item.count))
  const total = data.pipeline.reduce((sum, item) => sum + item.count, 0)

  return (
    <div className="card-elevated p-5">
      <h2 className="text-lg font-semibold text-fg mb-4">{t('dashboard.pipelineTitle')}</h2>
      {total === 0 ? (
        <p className="text-sm text-fg-muted">{t('dashboard.noCases')}</p>
      ) : (
        <div className="space-y-3">
          {CASE_STATUS_ORDER.map((status) => {
            const item = data.pipeline.find((entry) => entry.status === status)
            const count = item?.count ?? 0
            return (
              <div key={status}>
                <div className="flex items-center justify-between mb-1">
                  <span className="text-sm text-fg">
                    {caseStatusLabel(status)}
                  </span>
                  <span className="text-sm font-medium text-fg-muted">{count}</span>
                </div>
                <div className="h-2 rounded-full bg-bg overflow-hidden">
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
  const { t } = useTranslation()
  return (
    <div className="card-elevated p-5">
      <h2 className="text-lg font-semibold text-fg mb-4">{t('dashboard.deadlinesTitle')}</h2>
      {deadlines.length === 0 ? (
        <p className="text-sm text-fg-muted">{t('dashboard.noDeadlines')}</p>
      ) : (
        <ul className="space-y-2">
          {deadlines.map((deadline, index) => (
            <li key={`${deadline.caseId}-${deadline.type}-${index}`}>
              <Link
                to={`/cases/${deadline.caseId}`}
                className="flex items-center justify-between gap-3 p-3 rounded-lg border border-line hover:bg-bg transition-colors"
              >
                <div className="min-w-0">
                  <p className="text-sm font-medium text-fg truncate">
                    {deadline.caseTitle}
                  </p>
                  <p className="text-xs text-fg-muted">
                    {deadline.typeName} · {formatDate(deadline.date)}
                  </p>
                </div>
                <Badge variant={deadline.daysLeft <= 3 ? 'danger' : 'warning'}>
                  {daysLeftLabel(deadline.daysLeft, t)}
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
  const { t } = useTranslation()
  return (
    <div className="card-elevated p-5">
      <h2 className="text-lg font-semibold text-fg mb-4">{t('dashboard.recentTitle')}</h2>
      {cases.length === 0 ? (
        <p className="text-sm text-fg-muted">{t('dashboard.noCases')}</p>
      ) : (
        <ul className="divide-y divide-line">
          {cases.map((caseItem) => (
            <li key={caseItem.id}>
              <Link
                to={`/cases/${caseItem.id}`}
                className="flex items-center justify-between gap-3 py-3 hover:opacity-80 transition-opacity"
              >
                <span className="min-w-0 text-sm font-medium text-fg truncate">
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
