import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { CaseStatusBadge } from '../../../components/ui/Badge'
import { widgetHeadingClass, widgetPadding, type DashboardWidgetProps } from './types'

export function RecentCasesWidget({ data, density }: DashboardWidgetProps): JSX.Element {
  const { t } = useTranslation()
  const cases = data.recentCases

  return (
    <div className={`card-elevated ${widgetPadding(density)} h-full`}>
      <h2 className={widgetHeadingClass(density)}>{t('dashboard.recentTitle')}</h2>
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
                <span className="min-w-0 text-sm font-medium text-fg truncate">{caseItem.title}</span>
                <CaseStatusBadge status={caseItem.status} />
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
