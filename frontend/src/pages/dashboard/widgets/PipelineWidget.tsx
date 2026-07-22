import { useTranslation } from 'react-i18next'
import { CASE_STATUS_ORDER, caseStatusLabel } from '../../../components/ui/Badge'
import type { CaseStatus } from '../../../types'
import { widgetHeadingClass, widgetPadding, type DashboardWidgetProps } from './types'

const STATUS_BAR_COLOR: Record<CaseStatus, string> = {
  INTAKE: 'bg-zinc-400 dark:bg-zinc-500',
  IN_PROGRESS: 'bg-blue-500',
  SUBMITTED: 'bg-amber-500',
  CLOSED_WON: 'bg-emerald-500',
  CLOSED_LOST: 'bg-red-500',
}

export function PipelineWidget({ data, density }: DashboardWidgetProps): JSX.Element {
  const { t } = useTranslation()
  const maxCount = Math.max(1, ...data.pipeline.map((item) => item.count))
  const total = data.pipeline.reduce((sum, item) => sum + item.count, 0)

  return (
    <div className={`card-elevated ${widgetPadding(density)} h-full`}>
      <h2 className={widgetHeadingClass(density)}>{t('dashboard.pipelineTitle')}</h2>
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
                  <span className="text-sm text-fg">{caseStatusLabel(status)}</span>
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
