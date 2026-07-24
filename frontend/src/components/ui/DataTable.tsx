import { useMemo, useRef, type ReactNode } from 'react'
import { useVirtualizer } from '@tanstack/react-virtual'
import { useTranslation } from 'react-i18next'
import type { Density } from '../../hooks/useDensity'
import { useMediaQuery } from '../../hooks/useMediaQuery'
import { useSwipeReveal } from '../../hooks/useSwipeReveal'

export type SortDirection = 'asc' | 'desc'

export interface SortRule {
  columnId: string
  direction: SortDirection
}

export type CellValue = string | number | boolean | null | undefined

export interface DataTableColumn<T> {
  id: string
  header: string
  value?: (row: T) => CellValue
  render?: (row: T) => ReactNode
  width?: string
  align?: 'left' | 'right'
  sortable?: boolean
  groupable?: boolean
  groupLabel?: (row: T) => string
  alwaysVisible?: boolean
  defaultHidden?: boolean
}

interface DataTableProps<T> {
  rows: T[]
  columns: DataTableColumn<T>[]
  rowId: (row: T) => string
  rowHref?: (row: T) => string
  onRowClick?: (row: T) => void
  rowActions?: (row: T) => ReactNode
  sort?: SortRule[]
  onSortChange?: (sort: SortRule[]) => void
  visibleColumnIds?: string[]
  groupBy?: string | null
  selectedIds?: Set<string>
  onToggleRow?: (id: string) => void
  onToggleAll?: () => void
  selectionLabel?: (row: T) => string
  density?: Density
  maxBodyHeight?: number
  virtualizeThreshold?: number
  emptyState?: ReactNode
}

type FlatItem<T> =
  | { kind: 'group'; key: string; label: string; count: number }
  | { kind: 'row'; key: string; row: T }

const VIRTUALIZE_THRESHOLD = 60
const MAX_BODY_HEIGHT = 620
const CARD_MODE_QUERY = '(max-width: 639px)'
const SWIPE_REVEAL_WIDTH = 192

function compareValues(a: CellValue, b: CellValue): number {
  if (a === b) return 0
  if (a === null || a === undefined) return 1
  if (b === null || b === undefined) return -1
  if (typeof a === 'number' && typeof b === 'number') return a - b
  if (typeof a === 'boolean' && typeof b === 'boolean') return Number(a) - Number(b)
  return String(a).localeCompare(String(b), undefined, { numeric: true, sensitivity: 'base' })
}

export function nextSortState(current: SortRule[], columnId: string, additive: boolean): SortRule[] {
  const existing = current.find((rule) => rule.columnId === columnId)
  const rest = current.filter((rule) => rule.columnId !== columnId)
  if (!existing) {
    const added: SortRule = { columnId, direction: 'asc' }
    return additive ? [...current, added] : [added]
  }
  if (existing.direction === 'asc') {
    const flipped: SortRule = { columnId, direction: 'desc' }
    return additive ? [...rest, flipped] : [flipped]
  }
  return additive ? rest : []
}

export function sortRows<T>(rows: T[], sort: SortRule[], columns: DataTableColumn<T>[]): T[] {
  if (sort.length === 0) return rows
  const valueById = new Map(columns.map((column) => [column.id, column.value]))
  return [...rows].sort((left, right) => {
    for (const rule of sort) {
      const value = valueById.get(rule.columnId)
      if (!value) continue
      const result = compareValues(value(left), value(right))
      if (result !== 0) return rule.direction === 'asc' ? result : -result
    }
    return 0
  })
}

export function DataTable<T>({
  rows,
  columns,
  rowId,
  rowHref,
  onRowClick,
  rowActions,
  sort = [],
  onSortChange,
  visibleColumnIds,
  groupBy,
  selectedIds,
  onToggleRow,
  onToggleAll,
  selectionLabel,
  density = 'comfortable',
  maxBodyHeight = MAX_BODY_HEIGHT,
  virtualizeThreshold = VIRTUALIZE_THRESHOLD,
  emptyState,
}: DataTableProps<T>): JSX.Element {
  const { t } = useTranslation()
  const scrollRef = useRef<HTMLDivElement>(null)
  const isCardMode = useMediaQuery(CARD_MODE_QUERY)

  const shownColumns = useMemo(
    () => columns.filter((column) => !visibleColumnIds || column.alwaysVisible || visibleColumnIds.includes(column.id)),
    [columns, visibleColumnIds]
  )

  const sortedRows = useMemo(() => sortRows(rows, sort, columns), [rows, sort, columns])

  const items = useMemo<FlatItem<T>[]>(() => {
    if (!groupBy) {
      return sortedRows.map((row) => ({ kind: 'row', key: rowId(row), row }))
    }
    const groupColumn = columns.find((column) => column.id === groupBy)
    const labelOf = (row: T): string => {
      if (groupColumn?.groupLabel) return groupColumn.groupLabel(row)
      const raw = groupColumn?.value?.(row)
      return raw === null || raw === undefined || raw === '' ? t('table.groupEmpty') : String(raw)
    }
    const buckets = new Map<string, T[]>()
    sortedRows.forEach((row) => {
      const label = labelOf(row)
      const bucket = buckets.get(label)
      if (bucket) bucket.push(row)
      else buckets.set(label, [row])
    })
    const flat: FlatItem<T>[] = []
    Array.from(buckets.entries())
      .sort(([left], [right]) => left.localeCompare(right, undefined, { sensitivity: 'base' }))
      .forEach(([label, bucketRows]) => {
        flat.push({ kind: 'group', key: `group:${label}`, label, count: bucketRows.length })
        bucketRows.forEach((row) => flat.push({ kind: 'row', key: rowId(row), row }))
      })
    return flat
  }, [sortedRows, groupBy, columns, rowId, t])

  const selectable = Boolean(selectedIds && onToggleRow)
  const rowPadding = density === 'compact' ? 'py-1.5' : 'py-3'
  const estimatedRowHeight = density === 'compact' ? 38 : 54
  const virtualized = items.length > virtualizeThreshold

  const virtualizer = useVirtualizer({
    count: items.length,
    getScrollElement: () => scrollRef.current,
    estimateSize: () => estimatedRowHeight,
    overscan: 12,
    enabled: virtualized,
  })

  const gridTemplate = [
    selectable ? '2.25rem' : null,
    ...shownColumns.map((column) => column.width ?? 'minmax(0, 1fr)'),
    rowActions ? 'auto' : null,
  ]
    .filter(Boolean)
    .join(' ')

  const allSelected =
    selectable && sortedRows.length > 0 && sortedRows.every((row) => selectedIds?.has(rowId(row)))

  const sortIndicator = (columnId: string): string => {
    const index = sort.findIndex((rule) => rule.columnId === columnId)
    if (index < 0) return ''
    const arrow = sort[index].direction === 'asc' ? '↑' : '↓'
    return sort.length > 1 ? `${arrow}${index + 1}` : arrow
  }

  const ariaSort = (columnId: string): 'ascending' | 'descending' | 'none' => {
    const rule = sort.find((item) => item.columnId === columnId)
    if (!rule) return 'none'
    return rule.direction === 'asc' ? 'ascending' : 'descending'
  }

  const renderRow = (row: T): JSX.Element => {
    const id = rowId(row)
    const href = rowHref?.(row)
    const isSelected = selectedIds?.has(id) ?? false

    if (isCardMode) {
      return (
        <CardRow
          row={row}
          columns={shownColumns}
          href={href}
          isSelected={isSelected}
          selectable={selectable}
          onToggleRow={onToggleRow ? () => onToggleRow(id) : undefined}
          selectionLabel={selectionLabel?.(row)}
          onRowClick={onRowClick ? () => onRowClick(row) : undefined}
          actions={rowActions?.(row)}
        />
      )
    }

    return (
      <div
        role="row"
        className={`group grid items-center gap-3 px-3 border-b border-line last:border-b-0 transition-colors ${rowPadding} ${
          isSelected ? 'bg-accent/5' : 'hover:bg-surface-2'
        }`}
        style={{ gridTemplateColumns: gridTemplate }}
        onClick={onRowClick ? () => onRowClick(row) : undefined}
      >
        {selectable && (
          <div role="cell" className="flex items-center">
            <input
              type="checkbox"
              checked={isSelected}
              onChange={() => onToggleRow?.(id)}
              onClick={(event) => event.stopPropagation()}
              aria-label={selectionLabel?.(row) ?? t('table.selectRow')}
              className="h-4 w-4 rounded accent-accent cursor-pointer"
            />
          </div>
        )}
        {shownColumns.map((column, index) => {
          const content = column.render ? column.render(row) : formatValue(column.value?.(row))
          const cellClass = `min-w-0 text-sm ${column.align === 'right' ? 'text-right tabular-nums' : ''}`
          if (index === 0 && href) {
            return (
              <div role="cell" key={column.id} className={cellClass}>
                <a
                  href={href}
                  onClick={(event) => {
                    if (event.metaKey || event.ctrlKey || event.shiftKey) return
                    event.preventDefault()
                    onRowClick?.(row)
                  }}
                  className="block truncate text-fg hover:text-accent transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-accent rounded"
                >
                  {content}
                </a>
              </div>
            )
          }
          return (
            <div role="cell" key={column.id} className={`${cellClass} text-fg-muted`}>
              {content}
            </div>
          )
        })}
        {rowActions && (
          <div
            role="cell"
            className="flex items-center justify-end gap-1 opacity-0 focus-within:opacity-100 group-hover:opacity-100 transition-opacity"
            onClick={(event) => event.stopPropagation()}
          >
            {rowActions(row)}
          </div>
        )}
      </div>
    )
  }

  const body =
    items.length === 0 ? (
      <div className="p-6">{emptyState ?? <p className="text-sm text-fg-muted">{t('table.empty')}</p>}</div>
    ) : virtualized ? (
      <div style={{ height: virtualizer.getTotalSize(), position: 'relative' }}>
        {virtualizer.getVirtualItems().map((virtualRow) => {
          const item = items[virtualRow.index]
          return (
            <div
              key={item.key}
              ref={virtualizer.measureElement}
              data-index={virtualRow.index}
              style={{
                position: 'absolute',
                top: 0,
                left: 0,
                width: '100%',
                transform: `translateY(${virtualRow.start}px)`,
              }}
            >
              {item.kind === 'group' ? <GroupRow label={item.label} count={item.count} /> : renderRow(item.row)}
            </div>
          )
        })}
      </div>
    ) : (
      items.map((item) =>
        item.kind === 'group' ? (
          <GroupRow key={item.key} label={item.label} count={item.count} />
        ) : (
          <div key={item.key}>{renderRow(item.row)}</div>
        )
      )
    )

  return (
    <div role="table" className="rounded-xl border border-line bg-surface overflow-hidden">
      {!isCardMode && (
      <div
        role="row"
        className="grid items-center gap-3 px-3 py-2 border-b border-line bg-surface-2 sticky top-0 z-10"
        style={{ gridTemplateColumns: gridTemplate }}
      >
        {selectable && (
          <div role="columnheader" className="flex items-center">
            <input
              type="checkbox"
              checked={allSelected}
              onChange={() => onToggleAll?.()}
              aria-label={t('table.selectAll')}
              className="h-4 w-4 rounded accent-accent cursor-pointer"
            />
          </div>
        )}
        {shownColumns.map((column) => (
          <div
            role="columnheader"
            key={column.id}
            aria-sort={column.sortable ? ariaSort(column.id) : undefined}
            className={`min-w-0 text-xs font-semibold uppercase tracking-wide text-fg-muted ${
              column.align === 'right' ? 'text-right' : ''
            }`}
          >
            {column.sortable && onSortChange ? (
              <button
                type="button"
                onClick={(event) => onSortChange(nextSortState(sort, column.id, event.shiftKey))}
                title={t('table.sortHint')}
                className="inline-flex items-center gap-1 hover:text-fg transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-accent rounded"
              >
                <span className="truncate">{column.header}</span>
                <span className="text-[10px] text-accent">{sortIndicator(column.id)}</span>
              </button>
            ) : (
              <span className="truncate">{column.header}</span>
            )}
          </div>
        ))}
        {rowActions && <div role="columnheader" className="sr-only">{t('table.actions')}</div>}
      </div>
      )}
      <div
        ref={scrollRef}
        style={virtualized ? { maxHeight: maxBodyHeight, overflowY: 'auto' } : undefined}
        role="rowgroup"
      >
        {body}
      </div>
    </div>
  )
}

interface CardRowProps<T> {
  row: T
  columns: DataTableColumn<T>[]
  href?: string
  isSelected: boolean
  selectable: boolean
  onToggleRow?: () => void
  selectionLabel?: string
  onRowClick?: () => void
  actions?: ReactNode
}

function CardRow<T>({
  row,
  columns,
  href,
  isSelected,
  selectable,
  onToggleRow,
  selectionLabel,
  onRowClick,
  actions,
}: CardRowProps<T>): JSX.Element {
  const { t } = useTranslation()
  const swipe = useSwipeReveal(SWIPE_REVEAL_WIDTH)
  const [primaryColumn, ...restColumns] = columns
  const primaryContent = primaryColumn?.render ? primaryColumn.render(row) : formatValue(primaryColumn?.value?.(row))

  const handleActivate = (): void => {
    if (swipe.offsetX !== 0) {
      swipe.reset()
      return
    }
    onRowClick?.()
  }

  return (
    <div role="row" className="relative overflow-hidden border-b border-line last:border-b-0">
      {actions && (
        <div
          className="absolute inset-y-0 right-0 flex items-stretch divide-x divide-line/60"
          style={{ width: SWIPE_REVEAL_WIDTH }}
        >
          {actions}
        </div>
      )}
      <div
        className="relative bg-surface px-3 py-3 flex flex-col gap-2"
        style={{
          transform: `translateX(${swipe.offsetX}px)`,
          transition: swipe.dragging ? 'none' : 'transform 150ms ease-out',
        }}
        onTouchStart={actions ? swipe.onTouchStart : undefined}
        onTouchMove={actions ? swipe.onTouchMove : undefined}
        onTouchEnd={actions ? swipe.onTouchEnd : undefined}
      >
        <div className="flex items-start gap-3">
          {selectable && (
            <input
              type="checkbox"
              checked={isSelected}
              onChange={() => onToggleRow?.()}
              onClick={(event) => event.stopPropagation()}
              aria-label={selectionLabel ?? t('table.selectRow')}
              className="mt-1 h-5 w-5 rounded accent-accent cursor-pointer shrink-0"
            />
          )}
          <div className="min-w-0 flex-1">
            {href ? (
              <a
                href={href}
                onClick={(event) => {
                  if (event.metaKey || event.ctrlKey || event.shiftKey) return
                  event.preventDefault()
                  handleActivate()
                }}
                className="block min-h-[2.75rem] flex items-center font-medium text-fg hover:text-accent transition-colors truncate"
              >
                {primaryContent}
              </a>
            ) : (
              <button
                type="button"
                onClick={handleActivate}
                className="block min-h-[2.75rem] w-full text-left flex items-center font-medium text-fg truncate"
              >
                {primaryContent}
              </button>
            )}
          </div>
        </div>

        {restColumns.length > 0 && (
          <dl className="grid grid-cols-2 gap-x-3 gap-y-1.5 pl-0">
            {restColumns.map((column) => {
              const content = column.render ? column.render(row) : formatValue(column.value?.(row))
              return (
                <div key={column.id} className="min-w-0">
                  <dt className="text-[11px] uppercase tracking-wide text-fg-muted truncate">{column.header}</dt>
                  <dd className="text-sm text-fg truncate">{content}</dd>
                </div>
              )
            })}
          </dl>
        )}
      </div>
    </div>
  )
}

function GroupRow({ label, count }: { label: string; count: number }): JSX.Element {
  return (
    <div className="flex items-center gap-2 px-3 py-1.5 bg-bg border-b border-line">
      <span className="text-xs font-semibold text-fg truncate">{label}</span>
      <span className="text-xs text-fg-muted">{count}</span>
    </div>
  )
}

function formatValue(value: CellValue): ReactNode {
  if (value === null || value === undefined || value === '') return '—'
  if (typeof value === 'boolean') return value ? '✓' : '—'
  return value
}
