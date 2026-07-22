import { useCallback, useState } from 'react'
import type { SortRule } from '../components/ui/DataTable'

export interface TablePreferences {
  visibleColumnIds: string[]
  sort: SortRule[]
  groupBy: string | null
}

function storageKey(tableKey: string): string {
  return `pravoos.table.${tableKey}`
}

function readPreferences(tableKey: string, fallback: TablePreferences): TablePreferences {
  try {
    const raw = localStorage.getItem(storageKey(tableKey))
    if (!raw) return fallback
    const parsed = JSON.parse(raw) as Partial<TablePreferences>
    return {
      visibleColumnIds: Array.isArray(parsed.visibleColumnIds) ? parsed.visibleColumnIds : fallback.visibleColumnIds,
      sort: Array.isArray(parsed.sort) ? parsed.sort : fallback.sort,
      groupBy: typeof parsed.groupBy === 'string' ? parsed.groupBy : null,
    }
  } catch {
    return fallback
  }
}

export interface TablePreferencesApi {
  preferences: TablePreferences
  setSort: (sort: SortRule[]) => void
  setGroupBy: (groupBy: string | null) => void
  toggleColumn: (columnId: string) => void
  applyPreferences: (next: Partial<TablePreferences>) => void
}

export function useTablePreferences(tableKey: string, defaults: TablePreferences): TablePreferencesApi {
  const [preferences, setPreferences] = useState<TablePreferences>(() => readPreferences(tableKey, defaults))

  const persist = useCallback(
    (next: TablePreferences) => {
      setPreferences(next)
      localStorage.setItem(storageKey(tableKey), JSON.stringify(next))
    },
    [tableKey]
  )

  const setSort = useCallback(
    (sort: SortRule[]) => persist({ ...preferences, sort }),
    [persist, preferences]
  )

  const setGroupBy = useCallback(
    (groupBy: string | null) => persist({ ...preferences, groupBy }),
    [persist, preferences]
  )

  const toggleColumn = useCallback(
    (columnId: string) => {
      const visible = preferences.visibleColumnIds.includes(columnId)
      const visibleColumnIds = visible
        ? preferences.visibleColumnIds.filter((id) => id !== columnId)
        : [...preferences.visibleColumnIds, columnId]
      persist({ ...preferences, visibleColumnIds })
    },
    [persist, preferences]
  )

  const applyPreferences = useCallback(
    (next: Partial<TablePreferences>) => persist({ ...preferences, ...next }),
    [persist, preferences]
  )

  return { preferences, setSort, setGroupBy, toggleColumn, applyPreferences }
}
