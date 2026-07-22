import { Reorder, useDragControls } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import { useDashboardLayoutStore } from '../../../store/dashboardLayoutStore'
import type { DashboardWidgetId } from '../../../store/dashboardLayoutStore'
import type { DashboardResponse } from '../../../types'
import type { Density } from '../../../hooks/useDensity'
import { WIDGET_COMPONENTS } from './registry'

interface WidgetGridProps {
  data: DashboardResponse
  density: Density
}

const GRID_GAP: Record<Density, string> = {
  comfortable: 'gap-6',
  compact: 'gap-3',
}

export function WidgetGrid({ data, density }: WidgetGridProps): JSX.Element {
  const { t } = useTranslation()
  const { order, hidden, setOrder } = useDashboardLayoutStore()
  const visible = order.filter((id) => !hidden.includes(id))

  if (visible.length === 0) {
    return <p className="text-sm text-fg-muted card-elevated p-5">{t('dashboard.widgetsAllHidden')}</p>
  }

  return (
    <Reorder.Group
      as="div"
      axis="y"
      values={order}
      onReorder={setOrder}
      className={`grid grid-cols-1 lg:grid-cols-2 ${GRID_GAP[density]}`}
    >
      {visible.map((id) => (
        <WidgetTile key={id} id={id} data={data} density={density} />
      ))}
    </Reorder.Group>
  )
}

function WidgetTile({ id, data, density }: { id: DashboardWidgetId; data: DashboardResponse; density: Density }): JSX.Element {
  const dragControls = useDragControls()
  const WidgetComponent = WIDGET_COMPONENTS[id]

  return (
    <Reorder.Item value={id} as="div" dragListener={false} dragControls={dragControls} className="group relative">
      <div
        className="absolute top-3 right-3 z-10 cursor-grab active:cursor-grabbing text-fg-muted opacity-0 group-hover:opacity-100 transition-opacity touch-none"
        onPointerDown={(event) => dragControls.start(event)}
      >
        <svg className="w-4 h-4" viewBox="0 0 24 24" fill="currentColor">
          <circle cx="9" cy="6" r="1.5" />
          <circle cx="15" cy="6" r="1.5" />
          <circle cx="9" cy="12" r="1.5" />
          <circle cx="15" cy="12" r="1.5" />
          <circle cx="9" cy="18" r="1.5" />
          <circle cx="15" cy="18" r="1.5" />
        </svg>
      </div>
      <WidgetComponent data={data} density={density} />
    </Reorder.Item>
  )
}
