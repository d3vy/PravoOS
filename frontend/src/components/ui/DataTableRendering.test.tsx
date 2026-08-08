import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import i18n from '../../i18n'
import { DataTable, type DataTableColumn } from './DataTable'
import * as useMediaQueryModule from '../../hooks/useMediaQuery'

interface Row {
  id: string
  name: string
  status: string
  archived?: boolean
}

const rows: Row[] = [
  { id: '1', name: 'Иванов', status: 'Открыто' },
  { id: '2', name: 'Петров', status: 'Закрыто', archived: true },
]

const columns: DataTableColumn<Row>[] = [
  { id: 'name', header: 'Имя', value: (row) => row.name, sortable: true },
  { id: 'status', header: 'Статус', value: (row) => row.status, groupable: true, groupLabel: (row) => row.status },
  { id: 'archived', header: 'Архив', value: (row) => row.archived },
]

beforeEach(async () => {
  await i18n.changeLanguage('ru')
  vi.spyOn(useMediaQueryModule, 'useMediaQuery').mockReturnValue(false)
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('DataTable (row mode)', () => {
  it('renders the empty state message when there are no rows', () => {
    render(<DataTable rows={[]} columns={columns} rowId={(row) => row.id} />)
    expect(screen.getByText('Нет строк.')).toBeInTheDocument()
  })

  it('renders a custom emptyState node instead of the default message', () => {
    render(
      <DataTable rows={[]} columns={columns} rowId={(row) => row.id} emptyState={<span>Ничего не найдено</span>} />
    )
    expect(screen.getByText('Ничего не найдено')).toBeInTheDocument()
    expect(screen.queryByText('Нет строк.')).toBeNull()
  })

  it('formats missing/boolean cell values with the fallback glyphs', () => {
    render(<DataTable rows={rows} columns={columns} rowId={(row) => row.id} />)
    const dataRows = screen.getAllByRole('row').slice(1)
    expect(within(dataRows[0]).getByText('—')).toBeInTheDocument()
    expect(within(dataRows[1]).getByText('✓')).toBeInTheDocument()
  })

  it('only renders columns present in visibleColumnIds, but keeps alwaysVisible columns', () => {
    const withAlwaysVisible: DataTableColumn<Row>[] = [
      { ...columns[0], alwaysVisible: true },
      columns[1],
      columns[2],
    ]
    render(
      <DataTable rows={rows} columns={withAlwaysVisible} rowId={(row) => row.id} visibleColumnIds={['status']} />
    )
    expect(screen.getByRole('columnheader', { name: 'Имя' })).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: 'Статус' })).toBeInTheDocument()
    expect(screen.queryByRole('columnheader', { name: 'Архив' })).toBeNull()
  })

  it('calls onSortChange with the next sort state when a sortable header is clicked', async () => {
    const onSortChange = vi.fn()
    render(<DataTable rows={rows} columns={columns} rowId={(row) => row.id} sort={[]} onSortChange={onSortChange} />)
    await userEvent.click(screen.getByRole('button', { name: /Имя/ }))
    expect(onSortChange).toHaveBeenCalledWith([{ columnId: 'name', direction: 'asc' }])
  })

  it('does not render a sort button for non-sortable columns or without onSortChange', () => {
    render(<DataTable rows={rows} columns={columns} rowId={(row) => row.id} />)
    expect(screen.queryByRole('button', { name: /Статус/ })).toBeNull()
  })

  it('shows the sort arrow and aria-sort on the active column', () => {
    render(
      <DataTable
        rows={rows}
        columns={columns}
        rowId={(row) => row.id}
        sort={[{ columnId: 'name', direction: 'desc' }]}
        onSortChange={vi.fn()}
      />
    )
    const header = screen.getByRole('columnheader', { name: /Имя/ })
    expect(header).toHaveAttribute('aria-sort', 'descending')
    expect(screen.getByText('↓')).toBeInTheDocument()
  })

  it('groups rows by the given column and shows a group header with a count', () => {
    render(<DataTable rows={rows} columns={columns} rowId={(row) => row.id} groupBy="status" />)
    expect(screen.getByText('Открыто', { selector: 'span' })).toBeInTheDocument()
    expect(screen.getByText('Закрыто', { selector: 'span' })).toBeInTheDocument()
  })

  it('renders a "select all" checkbox that reflects and toggles full selection', async () => {
    const onToggleAll = vi.fn()
    const { rerender } = render(
      <DataTable
        rows={rows}
        columns={columns}
        rowId={(row) => row.id}
        selectedIds={new Set()}
        onToggleRow={vi.fn()}
        onToggleAll={onToggleAll}
      />
    )
    const selectAll = screen.getByRole('checkbox', { name: 'Выбрать все на странице' })
    expect(selectAll).not.toBeChecked()
    await userEvent.click(selectAll)
    expect(onToggleAll).toHaveBeenCalledTimes(1)

    rerender(
      <DataTable
        rows={rows}
        columns={columns}
        rowId={(row) => row.id}
        selectedIds={new Set(['1', '2'])}
        onToggleRow={vi.fn()}
        onToggleAll={onToggleAll}
      />
    )
    expect(screen.getByRole('checkbox', { name: 'Выбрать все на странице' })).toBeChecked()
  })

  it('toggles an individual row selection without triggering onRowClick', async () => {
    const onToggleRow = vi.fn()
    const onRowClick = vi.fn()
    render(
      <DataTable
        rows={rows}
        columns={columns}
        rowId={(row) => row.id}
        selectedIds={new Set()}
        onToggleRow={onToggleRow}
        onRowClick={onRowClick}
      />
    )
    const checkboxes = screen.getAllByRole('checkbox', { name: 'Выбрать строку' })
    await userEvent.click(checkboxes[0])
    expect(onToggleRow).toHaveBeenCalledWith('1')
    expect(onRowClick).not.toHaveBeenCalled()
  })

  it('calls onRowClick when a row is clicked', async () => {
    const onRowClick = vi.fn()
    render(<DataTable rows={rows} columns={columns} rowId={(row) => row.id} onRowClick={onRowClick} />)
    const dataRows = screen.getAllByRole('row').slice(1)
    await userEvent.click(dataRows[0])
    expect(onRowClick).toHaveBeenCalledWith(rows[0])
  })

  it('renders the first column as a link when rowHref is given, and clicking it calls onRowClick instead of navigating', async () => {
    const onRowClick = vi.fn()
    render(
      <DataTable
        rows={rows}
        columns={columns}
        rowId={(row) => row.id}
        rowHref={(row) => `/rows/${row.id}`}
        onRowClick={onRowClick}
      />
    )
    const link = screen.getByRole('link', { name: 'Иванов' })
    expect(link).toHaveAttribute('href', '/rows/1')
    await userEvent.click(link)
    expect(onRowClick).toHaveBeenCalledWith(rows[0])
  })

  it('renders rowActions for each row', () => {
    render(
      <DataTable
        rows={rows}
        columns={columns}
        rowId={(row) => row.id}
        rowActions={(row) => <button type="button">edit-{row.id}</button>}
      />
    )
    expect(screen.getByRole('button', { name: 'edit-1' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'edit-2' })).toBeInTheDocument()
  })

  it('supports custom render() over the raw value formatter', () => {
    const customColumns: DataTableColumn<Row>[] = [
      { id: 'name', header: 'Имя', render: (row) => <em>{row.name.toUpperCase()}</em> },
    ]
    render(<DataTable rows={rows} columns={customColumns} rowId={(row) => row.id} />)
    expect(screen.getByText('ИВАНОВ')).toBeInTheDocument()
  })
})

describe('DataTable (card mode)', () => {
  beforeEach(() => {
    vi.spyOn(useMediaQueryModule, 'useMediaQuery').mockReturnValue(true)
  })

  it('renders rows as cards without a column header row', () => {
    render(<DataTable rows={rows} columns={columns} rowId={(row) => row.id} />)
    expect(screen.queryByRole('columnheader')).toBeNull()
    expect(screen.getByText('Иванов')).toBeInTheDocument()
  })

  it('renders the primary column as a clickable link/button and the rest as a definition list', async () => {
    const onRowClick = vi.fn()
    render(<DataTable rows={rows} columns={columns} rowId={(row) => row.id} onRowClick={onRowClick} />)
    const button = screen.getByRole('button', { name: 'Иванов' })
    await userEvent.click(button)
    expect(onRowClick).toHaveBeenCalledWith(rows[0])
    expect(screen.getAllByText('Статус')[0]).toBeInTheDocument()
  })

  it('renders the primary column as a link when rowHref is set', () => {
    render(<DataTable rows={rows} columns={columns} rowId={(row) => row.id} rowHref={(row) => `/rows/${row.id}`} />)
    expect(screen.getByRole('link', { name: 'Иванов' })).toHaveAttribute('href', '/rows/1')
  })
})
