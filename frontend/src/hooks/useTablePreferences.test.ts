import { act, renderHook } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import { useTablePreferences, type TablePreferences } from './useTablePreferences'

const DEFAULTS: TablePreferences = {
  visibleColumnIds: ['name', 'status'],
  sort: [],
  groupBy: null,
}

describe('useTablePreferences', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('falls back to defaults when nothing is stored', () => {
    const { result } = renderHook(() => useTablePreferences('cases', DEFAULTS))
    expect(result.current.preferences).toEqual(DEFAULTS)
  })

  it('reads previously stored preferences', () => {
    localStorage.setItem(
      'pravoos.table.cases',
      JSON.stringify({ visibleColumnIds: ['name'], sort: [{ columnId: 'name', direction: 'asc' }], groupBy: 'status' })
    )
    const { result } = renderHook(() => useTablePreferences('cases', DEFAULTS))
    expect(result.current.preferences).toEqual({
      visibleColumnIds: ['name'],
      sort: [{ columnId: 'name', direction: 'asc' }],
      groupBy: 'status',
    })
  })

  it('falls back to defaults when stored JSON is malformed', () => {
    localStorage.setItem('pravoos.table.cases', '{not valid json')
    const { result } = renderHook(() => useTablePreferences('cases', DEFAULTS))
    expect(result.current.preferences).toEqual(DEFAULTS)
  })

  it('falls back field-by-field when stored shape is partially wrong', () => {
    localStorage.setItem(
      'pravoos.table.cases',
      JSON.stringify({ visibleColumnIds: 'not-an-array', sort: [], groupBy: 42 })
    )
    const { result } = renderHook(() => useTablePreferences('cases', DEFAULTS))
    expect(result.current.preferences).toEqual({ visibleColumnIds: DEFAULTS.visibleColumnIds, sort: [], groupBy: null })
  })

  it('setSort persists the new sort and preserves other fields', () => {
    const { result } = renderHook(() => useTablePreferences('cases', DEFAULTS))
    act(() => {
      result.current.setSort([{ columnId: 'status', direction: 'desc' }])
    })
    expect(result.current.preferences.sort).toEqual([{ columnId: 'status', direction: 'desc' }])
    expect(JSON.parse(localStorage.getItem('pravoos.table.cases')!).sort).toEqual([
      { columnId: 'status', direction: 'desc' },
    ])
  })

  it('setGroupBy updates and persists groupBy', () => {
    const { result } = renderHook(() => useTablePreferences('cases', DEFAULTS))
    act(() => {
      result.current.setGroupBy('status')
    })
    expect(result.current.preferences.groupBy).toBe('status')
  })

  it('toggleColumn removes a visible column and re-adds it on the next toggle', () => {
    const { result } = renderHook(() => useTablePreferences('cases', DEFAULTS))
    act(() => {
      result.current.toggleColumn('name')
    })
    expect(result.current.preferences.visibleColumnIds).toEqual(['status'])

    act(() => {
      result.current.toggleColumn('name')
    })
    expect(result.current.preferences.visibleColumnIds).toEqual(['status', 'name'])
  })

  it('applyPreferences merges a partial update over the current preferences', () => {
    const { result } = renderHook(() => useTablePreferences('cases', DEFAULTS))
    act(() => {
      result.current.applyPreferences({ groupBy: 'owner' })
    })
    expect(result.current.preferences).toEqual({ ...DEFAULTS, groupBy: 'owner' })
  })

  it('uses an isolated storage key per tableKey', () => {
    const first = renderHook(() => useTablePreferences('cases', DEFAULTS))
    act(() => {
      first.result.current.setGroupBy('owner')
    })
    const second = renderHook(() => useTablePreferences('clients', DEFAULTS))
    expect(second.result.current.preferences.groupBy).toBeNull()
  })
})
