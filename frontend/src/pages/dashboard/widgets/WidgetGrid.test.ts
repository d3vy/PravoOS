import { describe, expect, it } from 'vitest'
import type { DashboardWidgetId } from '../../../store/dashboardLayoutStore'
import { mergeVisibleOrder, moveItem } from './WidgetGrid'

const order: DashboardWidgetId[] = [
  'moneyOnTable',
  'tasksToday',
  'deadlines',
  'unpaidInvoices',
  'pipeline',
  'recentCases',
]

describe('moveItem', () => {
  it('moves an element forward', () => {
    expect(moveItem([1, 2, 3, 4], 0, 2)).toEqual([2, 3, 1, 4])
  })

  it('moves an element backward', () => {
    expect(moveItem([1, 2, 3, 4], 3, 1)).toEqual([1, 4, 2, 3])
  })

  it('returns the same array for out-of-range or no-op moves', () => {
    const items = [1, 2, 3]
    expect(moveItem(items, 1, 1)).toBe(items)
    expect(moveItem(items, 0, 5)).toBe(items)
    expect(moveItem(items, -1, 0)).toBe(items)
  })
})

describe('mergeVisibleOrder', () => {
  it('keeps hidden widgets pinned to their slots', () => {
    const hidden: DashboardWidgetId[] = ['deadlines', 'pipeline']
    const visible = order.filter((id) => !hidden.includes(id))
    const reordered = moveItem(visible, 0, 3)

    expect(mergeVisibleOrder(order, hidden, reordered)).toEqual([
      'tasksToday',
      'unpaidInvoices',
      'deadlines',
      'recentCases',
      'pipeline',
      'moneyOnTable',
    ])
  })

  it('returns the original order when nothing moved', () => {
    expect(mergeVisibleOrder(order, [], order)).toEqual(order)
  })
})
