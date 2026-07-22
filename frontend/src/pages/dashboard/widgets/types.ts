import type { DashboardResponse } from '../../../types'
import type { Density } from '../../../hooks/useDensity'

export interface DashboardWidgetProps {
  data: DashboardResponse
  density: Density
}

export function widgetPadding(density: Density): string {
  return density === 'compact' ? 'p-3' : 'p-5'
}

export function widgetHeadingClass(density: Density): string {
  return density === 'compact' ? 'text-sm font-semibold text-fg mb-2' : 'text-lg font-semibold text-fg mb-4'
}

export function widgetListGap(density: Density): string {
  return density === 'compact' ? 'space-y-1.5' : 'space-y-2'
}

