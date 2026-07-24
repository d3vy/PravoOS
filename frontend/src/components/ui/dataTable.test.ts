import { describe, expect, it } from 'vitest'
import { nextSortState, sortRows, type DataTableColumn, type SortRule } from './DataTable'

interface Row {
  id: string
  name: string
  amount: number
}

const columns: DataTableColumn<Row>[] = [
  { id: 'name', header: 'Name', value: (row) => row.name },
  { id: 'amount', header: 'Amount', value: (row) => row.amount },
]

const rows: Row[] = [
  { id: '1', name: 'Борис', amount: 30 },
  { id: '2', name: 'Анна', amount: 30 },
  { id: '3', name: 'Виктор', amount: 10 },
]

describe('nextSortState', () => {
  it('cycles ascending, descending and off for a single column', () => {
    const ascending = nextSortState([], 'name', false)
    expect(ascending).toEqual([{ columnId: 'name', direction: 'asc' }])

    const descending = nextSortState(ascending, 'name', false)
    expect(descending).toEqual([{ columnId: 'name', direction: 'desc' }])

    expect(nextSortState(descending, 'name', false)).toEqual([])
  })

  it('replaces the sort when another column is clicked without shift', () => {
    const current: SortRule[] = [{ columnId: 'name', direction: 'asc' }]
    expect(nextSortState(current, 'amount', false)).toEqual([{ columnId: 'amount', direction: 'asc' }])
  })

  it('appends to the sort when shift is held', () => {
    const current: SortRule[] = [{ columnId: 'amount', direction: 'desc' }]
    expect(nextSortState(current, 'name', true)).toEqual([
      { columnId: 'amount', direction: 'desc' },
      { columnId: 'name', direction: 'asc' },
    ])
  })
})

describe('sortRows', () => {
  it('returns the original order without sort rules', () => {
    expect(sortRows(rows, [], columns).map((row) => row.id)).toEqual(['1', '2', '3'])
  })

  it('sorts by a single column descending', () => {
    const sorted = sortRows(rows, [{ columnId: 'amount', direction: 'desc' }], columns)
    expect(sorted.map((row) => row.amount)).toEqual([30, 30, 10])
  })

  it('breaks ties with the second sort rule', () => {
    const sorted = sortRows(
      rows,
      [
        { columnId: 'amount', direction: 'desc' },
        { columnId: 'name', direction: 'asc' },
      ],
      columns
    )
    expect(sorted.map((row) => row.id)).toEqual(['2', '1', '3'])
  })

  it('does not mutate the source rows', () => {
    const source = [...rows]
    sortRows(source, [{ columnId: 'name', direction: 'asc' }], columns)
    expect(source.map((row) => row.id)).toEqual(['1', '2', '3'])
  })

  it('always keeps empty values last regardless of sort direction', () => {
    const withEmpty: Row[] = [
      { id: '1', name: 'Борис', amount: 30 },
      { id: '2', name: 'Анна', amount: null as unknown as number },
      { id: '3', name: 'Виктор', amount: 10 },
    ]

    const ascending = sortRows(withEmpty, [{ columnId: 'amount', direction: 'asc' }], columns)
    expect(ascending.map((row) => row.id)).toEqual(['3', '1', '2'])

    const descending = sortRows(withEmpty, [{ columnId: 'amount', direction: 'desc' }], columns)
    expect(descending.map((row) => row.id)).toEqual(['1', '3', '2'])
  })
})
