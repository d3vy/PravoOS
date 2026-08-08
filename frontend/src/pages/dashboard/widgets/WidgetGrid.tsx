import { useCallback, useEffect, useRef, useState } from 'react'
import { motion } from 'framer-motion'
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

interface DragState {
  id: DashboardWidgetId
  pointerId: number
  startX: number
  startY: number
  originLeft: number
  originTop: number
  width: number
  height: number
  active: boolean
}

const GRID_GAP: Record<Density, string> = {
  comfortable: 'gap-6',
  compact: 'gap-3',
}

const DRAG_THRESHOLD_PX = 4
const SWAP_COOLDOWN_MS = 220
const SWAP_MIN_TRAVEL_PX = 6
const LAYOUT_TRANSITION = { duration: 0.2, ease: 'easeOut' } as const

function overlayTransform(dx: number, dy: number): string {
  return `translate3d(${dx}px, ${dy}px, 0) scale(1.02)`
}

export function mergeVisibleOrder(
  order: DashboardWidgetId[],
  hidden: DashboardWidgetId[],
  visibleOrder: DashboardWidgetId[]
): DashboardWidgetId[] {
  const queue = [...visibleOrder]
  return order.map((id) => (hidden.includes(id) ? id : (queue.shift() ?? id)))
}

export function moveItem<T>(items: T[], from: number, to: number): T[] {
  if (from === to || from < 0 || to < 0 || from >= items.length || to >= items.length) {
    return items
  }
  const next = [...items]
  const [moved] = next.splice(from, 1)
  next.splice(to, 0, moved)
  return next
}

export function WidgetGrid({ data, density }: WidgetGridProps): JSX.Element {
  const { t } = useTranslation()
  const { order, hidden } = useDashboardLayoutStore()
  const visible = order.filter((id) => !hidden.includes(id))

  const [drag, setDrag] = useState<DragState | null>(null)
  const [preview, setPreview] = useState<DashboardWidgetId[] | null>(null)

  const tileRefs = useRef(new Map<DashboardWidgetId, HTMLElement>())
  const overlayRef = useRef<HTMLDivElement | null>(null)
  const offsetRef = useRef({ dx: 0, dy: 0 })
  const previewRef = useRef<DashboardWidgetId[]>(visible)
  const lastSwapRef = useRef({ time: 0, x: 0, y: 0 })

  const layout = preview ?? visible

  const registerTile = useCallback((id: DashboardWidgetId, element: HTMLElement | null) => {
    if (element) {
      tileRefs.current.set(id, element)
    } else {
      tileRefs.current.delete(id)
    }
  }, [])

  const commitOrder = useCallback((visibleOrder: DashboardWidgetId[]) => {
    const state = useDashboardLayoutStore.getState()
    state.setOrder(mergeVisibleOrder(state.order, state.hidden, visibleOrder))
  }, [])

  const findTileUnderPointer = useCallback(
    (clientX: number, clientY: number, exclude: DashboardWidgetId): DashboardWidgetId | null => {
      let closest: DashboardWidgetId | null = null
      let closestDistance = Number.POSITIVE_INFINITY
      tileRefs.current.forEach((element, id) => {
        if (id === exclude) {
          return
        }
        const rect = element.getBoundingClientRect()
        if (clientX < rect.left || clientX > rect.right || clientY < rect.top || clientY > rect.bottom) {
          return
        }
        const distance = Math.hypot(
          clientX - (rect.left + rect.width / 2),
          clientY - (rect.top + rect.height / 2)
        )
        if (distance < closestDistance) {
          closestDistance = distance
          closest = id
        }
      })
      return closest
    },
    []
  )

  const handlePointerDown = useCallback(
    (id: DashboardWidgetId, event: React.PointerEvent<HTMLButtonElement>) => {
      if (event.pointerType === 'mouse' && event.button !== 0) {
        return
      }
      const tile = tileRefs.current.get(id)
      if (!tile) {
        return
      }
      event.preventDefault()
      const rect = tile.getBoundingClientRect()
      previewRef.current = visible
      lastSwapRef.current = { time: 0, x: event.clientX, y: event.clientY }
      offsetRef.current = { dx: 0, dy: 0 }
      setPreview(null)
      setDrag({
        id,
        pointerId: event.pointerId,
        startX: event.clientX,
        startY: event.clientY,
        originLeft: rect.left,
        originTop: rect.top,
        width: rect.width,
        height: rect.height,
        active: false,
      })
    },
    [visible]
  )

  useEffect(() => {
    if (!drag) {
      return
    }

    const endDrag = (commit: boolean): void => {
      if (commit && drag.active) {
        commitOrder(previewRef.current)
      }
      setDrag(null)
      setPreview(null)
      offsetRef.current = { dx: 0, dy: 0 }
    }

    const handleMove = (event: PointerEvent): void => {
      if (event.pointerId !== drag.pointerId) {
        return
      }
      const dx = event.clientX - drag.startX
      const dy = event.clientY - drag.startY
      offsetRef.current = { dx, dy }
      if (overlayRef.current) {
        overlayRef.current.style.transform = overlayTransform(dx, dy)
      }

      if (!drag.active) {
        if (Math.hypot(dx, dy) > DRAG_THRESHOLD_PX) {
          setDrag({ ...drag, active: true })
        }
        return
      }

      const now = performance.now()
      const { time, x, y } = lastSwapRef.current
      if (now - time < SWAP_COOLDOWN_MS) {
        return
      }
      if (Math.hypot(event.clientX - x, event.clientY - y) < SWAP_MIN_TRAVEL_PX) {
        return
      }

      const target = findTileUnderPointer(event.clientX, event.clientY, drag.id)
      if (!target) {
        return
      }
      const current = previewRef.current
      const next = moveItem(current, current.indexOf(drag.id), current.indexOf(target))
      if (next === current) {
        return
      }
      previewRef.current = next
      lastSwapRef.current = { time: now, x: event.clientX, y: event.clientY }
      setPreview(next)
    }

    const handleUp = (event: PointerEvent): void => {
      if (event.pointerId === drag.pointerId) {
        endDrag(true)
      }
    }

    const handleCancel = (event: PointerEvent): void => {
      if (event.pointerId === drag.pointerId) {
        endDrag(false)
      }
    }

    const handleKeyDown = (event: KeyboardEvent): void => {
      if (event.key === 'Escape') {
        endDrag(false)
      }
    }

    window.addEventListener('pointermove', handleMove)
    window.addEventListener('pointerup', handleUp)
    window.addEventListener('pointercancel', handleCancel)
    window.addEventListener('keydown', handleKeyDown)
    return () => {
      window.removeEventListener('pointermove', handleMove)
      window.removeEventListener('pointerup', handleUp)
      window.removeEventListener('pointercancel', handleCancel)
      window.removeEventListener('keydown', handleKeyDown)
    }
  }, [drag, commitOrder, findTileUnderPointer])

  const handleKeyDown = useCallback(
    (id: DashboardWidgetId, event: React.KeyboardEvent<HTMLButtonElement>) => {
      const delta =
        event.key === 'ArrowLeft' || event.key === 'ArrowUp'
          ? -1
          : event.key === 'ArrowRight' || event.key === 'ArrowDown'
            ? 1
            : 0
      if (delta === 0) {
        return
      }
      event.preventDefault()
      const from = visible.indexOf(id)
      commitOrder(moveItem(visible, from, from + delta))
    },
    [visible, commitOrder]
  )

  if (visible.length === 0) {
    return <p className="text-sm text-fg-muted card-elevated p-5">{t('dashboard.widgetsAllHidden')}</p>
  }

  const DraggedWidget = drag?.active ? WIDGET_COMPONENTS[drag.id] : null

  return (
    <>
      <div
        className={`grid grid-cols-1 lg:grid-cols-2 ${GRID_GAP[density]} ${drag?.active ? 'select-none' : ''}`}
      >
        {layout.map((id) => {
          const WidgetComponent = WIDGET_COMPONENTS[id]
          if (drag?.active && drag.id === id) {
            return (
              <motion.div
                key={id}
                layout
                transition={LAYOUT_TRANSITION}
                style={{ height: drag.height }}
                className="rounded-2xl border-2 border-dashed border-accent/50 bg-accent/5"
              />
            )
          }
          return (
            <motion.div
              key={id}
              layout
              transition={LAYOUT_TRANSITION}
              ref={(element) => registerTile(id, element)}
              className="group relative rounded-2xl"
            >
              <button
                type="button"
                aria-label={t('dashboard.widgetDragHandle')}
                className="absolute top-3 right-3 z-10 cursor-grab active:cursor-grabbing text-fg-muted opacity-0 group-hover:opacity-100 focus-visible:opacity-100 transition-opacity touch-none"
                onPointerDown={(event) => handlePointerDown(id, event)}
                onKeyDown={(event) => handleKeyDown(id, event)}
              >
                <svg className="w-4 h-4" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                  <circle cx="9" cy="6" r="1.5" />
                  <circle cx="15" cy="6" r="1.5" />
                  <circle cx="9" cy="12" r="1.5" />
                  <circle cx="15" cy="12" r="1.5" />
                  <circle cx="9" cy="18" r="1.5" />
                  <circle cx="15" cy="18" r="1.5" />
                </svg>
              </button>
              <WidgetComponent data={data} density={density} />
            </motion.div>
          )
        })}
      </div>

      {drag?.active && DraggedWidget ? (
        <div
          ref={overlayRef}
          className="fixed z-50 pointer-events-none shadow-2xl rounded-2xl"
          style={{
            left: drag.originLeft,
            top: drag.originTop,
            width: drag.width,
            height: drag.height,
            transform: overlayTransform(offsetRef.current.dx, offsetRef.current.dy),
            opacity: 0.95,
          }}
        >
          <DraggedWidget data={data} density={density} />
        </div>
      ) : null}
    </>
  )
}
