import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import type { TFunction } from 'i18next'
import { Badge } from '../../../components/ui/Badge'
import { widgetHeadingClass, widgetPadding, type DashboardWidgetProps } from './types'

function formatDate(isoDate: string): string {
  const [year, month, day] = isoDate.split('-')
  return `${day}.${month}.${year}`
}

function daysLeftLabel(daysLeft: number, t: TFunction): string {
  if (daysLeft <= 0) return t('dashboard.today')
  if (daysLeft === 1) return t('dashboard.tomorrow')
  return t('dashboard.inDays', { count: daysLeft })
}

export function DeadlinesWidget({ data, density }: DashboardWidgetProps): JSX.Element {
  const { t } = useTranslation()
  const deadlines = data.upcomingDeadlines

  return (
    <div className={`card-elevated ${widgetPadding(density)} h-full`}>
      <h2 className={widgetHeadingClass(density)}>{t('dashboard.deadlinesTitle')}</h2>
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
                  <p className="text-sm font-medium text-fg truncate">{deadline.caseTitle}</p>
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
