import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Badge } from '../../../components/ui/Badge'
import { widgetHeadingClass, widgetPadding, type DashboardWidgetProps } from './types'

export function TasksTodayWidget({ data, density }: DashboardWidgetProps): JSX.Element {
  const { t } = useTranslation()
  const tasks = data.tasksToday

  return (
    <div className={`card-elevated ${widgetPadding(density)} h-full`}>
      <h2 className={widgetHeadingClass(density)}>{t('dashboard.tasksTodayTitle')}</h2>
      {tasks.length === 0 ? (
        <p className="text-sm text-fg-muted">{t('dashboard.tasksTodayEmpty')}</p>
      ) : (
        <ul className="space-y-2">
          {tasks.map((task) => (
            <li key={task.id}>
              <Link
                to={`/cases/${task.caseId}`}
                className="flex items-center justify-between gap-3 p-3 rounded-lg border border-line hover:bg-bg transition-colors"
              >
                <div className="min-w-0">
                  <p className="text-sm font-medium text-fg truncate">{task.text}</p>
                  <p className="text-xs text-fg-muted truncate">{task.caseTitle}</p>
                </div>
                {task.daysOverdue > 0 ? (
                  <Badge variant="danger">{t('dashboard.tasksTodayOverdue', { count: task.daysOverdue })}</Badge>
                ) : (
                  <Badge variant="warning">{t('dashboard.today')}</Badge>
                )}
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
