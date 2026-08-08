import { beforeEach, describe, expect, it } from 'vitest'
import { DASHBOARD_WIDGET_IDS, useDashboardLayoutStore } from './dashboardLayoutStore'

describe('useDashboardLayoutStore', () => {
  beforeEach(() => {
    useDashboardLayoutStore.setState({ order: [...DASHBOARD_WIDGET_IDS], hidden: [] })
  })

  it('defaults to the full widget order and nothing hidden', () => {
    const state = useDashboardLayoutStore.getState()
    expect(state.order).toEqual(DASHBOARD_WIDGET_IDS)
    expect(state.hidden).toEqual([])
  })

  it('setOrder accepts a full valid reordering', () => {
    const reordered = [...DASHBOARD_WIDGET_IDS].reverse()
    useDashboardLayoutStore.getState().setOrder(reordered)
    expect(useDashboardLayoutStore.getState().order).toEqual(reordered)
  })

  it('setOrder appends widgets missing from a partial order', () => {
    useDashboardLayoutStore.getState().setOrder(['pipeline', 'recentCases'])
    const { order } = useDashboardLayoutStore.getState()
    expect(order.slice(0, 2)).toEqual(['pipeline', 'recentCases'])
    expect(order).toHaveLength(DASHBOARD_WIDGET_IDS.length)
    expect(new Set(order)).toEqual(new Set(DASHBOARD_WIDGET_IDS))
  })

  it('setOrder drops unknown widget ids', () => {
    useDashboardLayoutStore
      .getState()
      .setOrder(['pipeline', 'notAWidget' as (typeof DASHBOARD_WIDGET_IDS)[number]])
    const { order } = useDashboardLayoutStore.getState()
    expect(order).not.toContain('notAWidget')
    expect(order[0]).toBe('pipeline')
  })

  it('toggleWidget hides a visible widget', () => {
    useDashboardLayoutStore.getState().toggleWidget('pipeline')
    expect(useDashboardLayoutStore.getState().hidden).toEqual(['pipeline'])
  })

  it('toggleWidget shows a hidden widget again', () => {
    useDashboardLayoutStore.getState().toggleWidget('pipeline')
    useDashboardLayoutStore.getState().toggleWidget('pipeline')
    expect(useDashboardLayoutStore.getState().hidden).toEqual([])
  })

  it('resetLayout restores default order and clears hidden widgets', () => {
    useDashboardLayoutStore.getState().setOrder([...DASHBOARD_WIDGET_IDS].reverse())
    useDashboardLayoutStore.getState().toggleWidget('pipeline')
    useDashboardLayoutStore.getState().resetLayout()
    const state = useDashboardLayoutStore.getState()
    expect(state.order).toEqual(DASHBOARD_WIDGET_IDS)
    expect(state.hidden).toEqual([])
  })

  it('persist merge sanitizes a stale persisted order missing newer widgets', () => {
    const merge = useDashboardLayoutStore.persist.getOptions().merge!
    const current = useDashboardLayoutStore.getInitialState()
    const merged = merge({ order: ['recentCases', 'pipeline'], hidden: ['tasksToday'] }, current) as {
      order: typeof DASHBOARD_WIDGET_IDS
      hidden: typeof DASHBOARD_WIDGET_IDS
    }
    expect(merged.order.slice(0, 2)).toEqual(['recentCases', 'pipeline'])
    expect(new Set(merged.order)).toEqual(new Set(DASHBOARD_WIDGET_IDS))
    expect(merged.hidden).toEqual(['tasksToday'])
  })
})
