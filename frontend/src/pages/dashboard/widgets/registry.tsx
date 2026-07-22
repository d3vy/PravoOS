import type { DashboardWidgetId } from '../../../store/dashboardLayoutStore'
import { DeadlinesWidget } from './DeadlinesWidget'
import { MoneyOnTableWidget } from './MoneyOnTableWidget'
import { PipelineWidget } from './PipelineWidget'
import { RecentCasesWidget } from './RecentCasesWidget'
import { TasksTodayWidget } from './TasksTodayWidget'
import { UnpaidInvoicesWidget } from './UnpaidInvoicesWidget'
import type { DashboardWidgetProps } from './types'

export const WIDGET_COMPONENTS: Record<DashboardWidgetId, (props: DashboardWidgetProps) => JSX.Element> = {
  moneyOnTable: MoneyOnTableWidget,
  tasksToday: TasksTodayWidget,
  deadlines: DeadlinesWidget,
  unpaidInvoices: UnpaidInvoicesWidget,
  pipeline: PipelineWidget,
  recentCases: RecentCasesWidget,
}

export const WIDGET_TITLE_KEYS: Record<DashboardWidgetId, string> = {
  moneyOnTable: 'dashboard.moneyOnTableTitle',
  tasksToday: 'dashboard.tasksTodayTitle',
  deadlines: 'dashboard.deadlinesTitle',
  unpaidInvoices: 'dashboard.unpaidInvoicesTitle',
  pipeline: 'dashboard.pipelineTitle',
  recentCases: 'dashboard.recentTitle',
}
