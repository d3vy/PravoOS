import { useEffect, useRef, useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import type { DataTableColumn } from './DataTable'
import type { Density } from '../../hooks/useDensity'

interface TableToolbarProps<T> {
  columns: DataTableColumn<T>[]
  visibleColumnIds: string[]
  onToggleColumn: (columnId: string) => void
  groupBy?: string | null
  onGroupByChange?: (groupBy: string | null) => void
  density?: Density
  onDensityToggle?: () => void
  children?: ReactNode
}

export function TableToolbar<T>({
  columns,
  visibleColumnIds,
  onToggleColumn,
  groupBy = null,
  onGroupByChange,
  density,
  onDensityToggle,
  children,
}: TableToolbarProps<T>): JSX.Element {
  const { t } = useTranslation()
  const [columnsOpen, setColumnsOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!columnsOpen) return
    const handlePointerDown = (event: MouseEvent): void => {
      if (!containerRef.current?.contains(event.target as Node)) setColumnsOpen(false)
    }
    const handleKeyDown = (event: KeyboardEvent): void => {
      if (event.key === 'Escape') setColumnsOpen(false)
    }
    document.addEventListener('mousedown', handlePointerDown)
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('mousedown', handlePointerDown)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [columnsOpen])

  const groupableColumns = columns.filter((column) => column.groupable)
  const controlClass =
    'px-3 py-1.5 rounded-lg text-xs font-medium border border-line text-fg-muted hover:text-fg transition-colors bg-surface'

  return (
    <div className="flex flex-wrap items-center gap-2">
      {children}
      {groupableColumns.length > 0 && onGroupByChange && (
        <select
          value={groupBy ?? ''}
          onChange={(event) => onGroupByChange(event.target.value || null)}
          aria-label={t('table.groupBy')}
          className={`${controlClass} focus:outline-none focus:ring-2 focus:ring-accent`}
        >
          <option value="">{t('table.noGrouping')}</option>
          {groupableColumns.map((column) => (
            <option key={column.id} value={column.id}>
              {t('table.groupByColumn', { column: column.header })}
            </option>
          ))}
        </select>
      )}
      {density && onDensityToggle && (
        <button type="button" onClick={onDensityToggle} className={controlClass}>
          {density === 'compact' ? t('table.densityCompact') : t('table.densityComfortable')}
        </button>
      )}
      <div className="relative" ref={containerRef}>
        <button
          type="button"
          onClick={() => setColumnsOpen((open) => !open)}
          aria-haspopup="menu"
          aria-expanded={columnsOpen}
          className={controlClass}
        >
          {t('table.columns')}
        </button>
        {columnsOpen && (
          <div
            role="menu"
            className="absolute right-0 mt-1 z-30 min-w-[13rem] rounded-lg border border-line bg-overlay shadow-card p-2"
          >
            {columns.map((column) => {
              const checked = column.alwaysVisible || visibleColumnIds.includes(column.id)
              return (
                <label
                  key={column.id}
                  className={`flex items-center gap-2 px-2 py-1.5 rounded-md text-sm text-fg ${
                    column.alwaysVisible ? 'opacity-50' : 'hover:bg-surface-2 cursor-pointer'
                  }`}
                >
                  <input
                    type="checkbox"
                    checked={checked}
                    disabled={column.alwaysVisible}
                    onChange={() => onToggleColumn(column.id)}
                    className="h-4 w-4 rounded accent-accent"
                  />
                  <span className="truncate">{column.header}</span>
                </label>
              )
            })}
          </div>
        )}
      </div>
    </div>
  )
}
