import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import i18n from '../../i18n'
import { TableToolbar } from './TableToolbar'
import type { DataTableColumn } from './DataTable'

interface Row {
  id: string
  status: string
}

const columns: DataTableColumn<Row>[] = [
  { id: 'id', header: 'ID', alwaysVisible: true },
  { id: 'status', header: 'Статус', groupable: true },
  { id: 'extra', header: 'Доп.' },
]

beforeEach(async () => {
  await i18n.changeLanguage('ru')
})

afterEach(cleanup)

describe('TableToolbar', () => {
  it('does not render a group-by select when no column is groupable', () => {
    render(
      <TableToolbar
        columns={[{ id: 'id', header: 'ID' }]}
        visibleColumnIds={['id']}
        onToggleColumn={vi.fn()}
      />
    )
    expect(screen.queryByLabelText('Группировка')).toBeNull()
  })

  it('does not render a group-by select when onGroupByChange is missing', () => {
    render(<TableToolbar columns={columns} visibleColumnIds={['id']} onToggleColumn={vi.fn()} />)
    expect(screen.queryByLabelText('Группировка')).toBeNull()
  })

  it('renders the group-by select with only groupable columns and calls onGroupByChange', async () => {
    const onGroupByChange = vi.fn()
    render(
      <TableToolbar
        columns={columns}
        visibleColumnIds={['id', 'status']}
        onToggleColumn={vi.fn()}
        groupBy={null}
        onGroupByChange={onGroupByChange}
      />
    )
    const select = screen.getByLabelText('Группировка')
    expect(screen.getByRole('option', { name: 'По: Статус' })).toBeInTheDocument()
    expect(screen.queryByRole('option', { name: 'По: Доп.' })).toBeNull()

    await userEvent.selectOptions(select, 'status')
    expect(onGroupByChange).toHaveBeenCalledWith('status')
  })

  it('sends null when the no-grouping option is chosen', async () => {
    const onGroupByChange = vi.fn()
    render(
      <TableToolbar
        columns={columns}
        visibleColumnIds={['id', 'status']}
        onToggleColumn={vi.fn()}
        groupBy="status"
        onGroupByChange={onGroupByChange}
      />
    )
    await userEvent.selectOptions(screen.getByLabelText('Группировка'), '')
    expect(onGroupByChange).toHaveBeenCalledWith(null)
  })

  it('toggles the density label and calls onDensityToggle', async () => {
    const onDensityToggle = vi.fn()
    render(
      <TableToolbar
        columns={columns}
        visibleColumnIds={['id']}
        onToggleColumn={vi.fn()}
        density="compact"
        onDensityToggle={onDensityToggle}
      />
    )
    const button = screen.getByRole('button', { name: 'Плотно' })
    await userEvent.click(button)
    expect(onDensityToggle).toHaveBeenCalledTimes(1)
  })

  it('does not render the density button without density/onDensityToggle', () => {
    render(<TableToolbar columns={columns} visibleColumnIds={['id']} onToggleColumn={vi.fn()} />)
    expect(screen.queryByText('Плотно')).toBeNull()
    expect(screen.queryByText('Просторно')).toBeNull()
  })

  it('opens the columns menu, reflects visibility and always-visible columns as disabled', async () => {
    render(
      <TableToolbar columns={columns} visibleColumnIds={['id', 'status']} onToggleColumn={vi.fn()} />
    )
    await userEvent.click(screen.getByRole('button', { name: 'Колонки' }))
    const menu = screen.getByRole('menu')
    expect(menu).toBeInTheDocument()

    const idCheckbox = screen.getByRole('checkbox', { name: 'ID' })
    expect(idCheckbox).toBeChecked()
    expect(idCheckbox).toBeDisabled()

    const statusCheckbox = screen.getByRole('checkbox', { name: 'Статус' })
    expect(statusCheckbox).toBeChecked()
    expect(statusCheckbox).not.toBeDisabled()

    const extraCheckbox = screen.getByRole('checkbox', { name: 'Доп.' })
    expect(extraCheckbox).not.toBeChecked()
  })

  it('calls onToggleColumn when a non-always-visible column checkbox is toggled', async () => {
    const onToggleColumn = vi.fn()
    render(
      <TableToolbar columns={columns} visibleColumnIds={['id', 'status']} onToggleColumn={onToggleColumn} />
    )
    await userEvent.click(screen.getByRole('button', { name: 'Колонки' }))
    await userEvent.click(screen.getByRole('checkbox', { name: 'Статус' }))
    expect(onToggleColumn).toHaveBeenCalledWith('status')
  })

  it('closes the columns menu when clicking outside', async () => {
    render(
      <div>
        <TableToolbar columns={columns} visibleColumnIds={['id']} onToggleColumn={vi.fn()} />
        <button type="button">outside</button>
      </div>
    )
    await userEvent.click(screen.getByRole('button', { name: 'Колонки' }))
    expect(screen.getByRole('menu')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'outside' }))
    await waitFor(() => expect(screen.queryByRole('menu')).toBeNull())
  })

  it('closes the columns menu on Escape', async () => {
    render(<TableToolbar columns={columns} visibleColumnIds={['id']} onToggleColumn={vi.fn()} />)
    await userEvent.click(screen.getByRole('button', { name: 'Колонки' }))
    expect(screen.getByRole('menu')).toBeInTheDocument()
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await waitFor(() => expect(screen.queryByRole('menu')).toBeNull())
  })

  it('renders children before the built-in controls', () => {
    render(
      <TableToolbar columns={columns} visibleColumnIds={['id']} onToggleColumn={vi.fn()}>
        <span>custom filter</span>
      </TableToolbar>
    )
    expect(screen.getByText('custom filter')).toBeInTheDocument()
  })
})
