import { create } from 'zustand'
import { persist } from 'zustand/middleware'

export type DashboardWidgetId =
  | 'moneyOnTable'
  | 'tasksToday'
  | 'deadlines'
  | 'unpaidInvoices'
  | 'pipeline'
  | 'recentCases'

export const DASHBOARD_WIDGET_IDS: DashboardWidgetId[] = [
  'moneyOnTable',
  'tasksToday',
  'deadlines',
  'unpaidInvoices',
  'pipeline',
  'recentCases',
]

const DEFAULT_ORDER: DashboardWidgetId[] = [...DASHBOARD_WIDGET_IDS]

interface DashboardLayoutState {
  order: DashboardWidgetId[]
  hidden: DashboardWidgetId[]
  setOrder: (order: DashboardWidgetId[]) => void
  toggleWidget: (id: DashboardWidgetId) => void
  resetLayout: () => void
}

function sanitizeOrder(order: DashboardWidgetId[]): DashboardWidgetId[] {
  const known = order.filter((id) => DASHBOARD_WIDGET_IDS.includes(id))
  const missing = DASHBOARD_WIDGET_IDS.filter((id) => !known.includes(id))
  return [...known, ...missing]
}

export const useDashboardLayoutStore = create<DashboardLayoutState>()(
  persist(
    (set, get) => ({
      order: DEFAULT_ORDER,
      hidden: [],
      setOrder: (order) => set({ order: sanitizeOrder(order) }),
      toggleWidget: (id) => {
        const { hidden } = get()
        set({
          hidden: hidden.includes(id) ? hidden.filter((item) => item !== id) : [...hidden, id],
        })
      },
      resetLayout: () => set({ order: DEFAULT_ORDER, hidden: [] }),
    }),
    {
      name: 'pravoos-dashboard-layout',
      version: 1,
      merge: (persisted, current) => {
        const merged = { ...current, ...(persisted as Partial<DashboardLayoutState>) }
        return { ...merged, order: sanitizeOrder(merged.order) }
      },
    }
  )
)
