import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Trans, useTranslation } from 'react-i18next'
import { dashboardApi } from '../../api/dashboard'
import { Button } from '../../components/ui/Button'
import { Skeleton } from '../../components/ui/Skeleton'
import { useDensity } from '../../hooks/useDensity'
import type { DashboardResponse } from '../../types'
import type { TFunction } from 'i18next'
import { WidgetGrid } from './widgets/WidgetGrid'
import { WidgetPickerModal } from './widgets/WidgetPickerModal'
import { PageHeader } from '../../components/ui/PageHeader'

export function greeting(hour: number, t: TFunction): string {
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
  const [density, toggleDensity] = useDensity()
  const [pickerOpen, setPickerOpen] = useState(false)

  return (
    <div className="bg-bg">
      <div className="page-container py-8">
        <PageHeader
          eyebrow={t('dashboard.eyebrow')}
          title={t('dashboard.title')}
          actions={
            !isLoading &&
            !isError && (
              <>
                <Button variant="secondary" size="sm" onClick={toggleDensity}>
                  {density === 'compact' ? t('dashboard.densityComfortable') : t('dashboard.densityCompact')}
                </Button>
                <Button variant="secondary" size="sm" onClick={() => setPickerOpen(true)}>
                  {t('dashboard.widgetsCustomize')}
                </Button>
              </>
            )
          }
        />

        {isLoading && <DashboardSkeleton />}

        {isError && (
          <div className="card-elevated p-6 text-fg-muted">
            {t('dashboard.loadError')}
          </div>
        )}

        {data && <DashboardContent data={data} density={density} />}

        <WidgetPickerModal open={pickerOpen} onClose={() => setPickerOpen(false)} />
      </div>
    </div>
  )
}

function DashboardSkeleton(): JSX.Element {
  return (
    <div className="space-y-6" aria-hidden="true">
      <div className="card-elevated p-6 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div className="min-w-0 flex-1">
          <Skeleton className="h-7 w-48 mb-2" />
          <Skeleton className="h-4 w-64" />
        </div>
        <div className="flex items-center gap-2 shrink-0">
          {[0, 1, 2].map((i) => (
            <Skeleton key={i} className="h-14 w-[72px] rounded-lg" />
          ))}
        </div>
      </div>

      <div className="card-elevated p-5">
        <Skeleton className="h-5 w-40 mb-3" />
        <Skeleton className="h-11 w-full rounded-lg" />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {[0, 1, 2, 3].map((i) => (
          <div key={i} className="card-elevated p-5">
            <Skeleton className="h-5 w-32 mb-4" />
            <div className="space-y-3">
              {[0, 1, 2].map((j) => (
                <Skeleton key={j} className="h-8 w-full" />
              ))}
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

function DashboardContent({
  data,
  density,
}: {
  data: DashboardResponse
  density: 'comfortable' | 'compact'
}): JSX.Element {
  return (
    <div className={density === 'compact' ? 'space-y-3' : 'space-y-6'}>
      <DigestBanner data={data} />

      {data.activeCases === 0 && data.recentCases.length === 0 && <QuickStartCard />}

      <QuickAskWidget />

      <WidgetGrid data={data} density={density} />
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

export function DigestBanner({ data }: { data: DashboardResponse }): JSX.Element {
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
