import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import i18n from '../../i18n'
import { SavedViewBar } from './SavedViewBar'
import type { SavedView } from '../../hooks/useSavedViews'

interface Config {
  status: string
}

const views: SavedView<Config>[] = [
  { id: '1', name: 'Мои дела', sharedWithTeam: false, owned: true, orgId: null, config: null },
  { id: '2', name: 'Командный вид', sharedWithTeam: true, owned: false, orgId: 'org-1', config: null },
]

beforeEach(async () => {
  await i18n.changeLanguage('ru')
})

afterEach(cleanup)

describe('SavedViewBar', () => {
  it('renders each view and a shared mark only for shared views', () => {
    render(
      <SavedViewBar views={views} activeViewId={null} canShare onApply={vi.fn()} onSave={vi.fn()} onDelete={vi.fn()} />
    )
    expect(screen.getByText('Мои дела')).toBeInTheDocument()
    expect(screen.getByText('Командный вид')).toBeInTheDocument()
    expect(screen.getByText('команда')).toBeInTheDocument()
  })

  it('shows a delete button only for owned views', () => {
    render(
      <SavedViewBar views={views} activeViewId={null} canShare onApply={vi.fn()} onSave={vi.fn()} onDelete={vi.fn()} />
    )
    expect(screen.getByRole('button', { name: 'Удалить вид «Мои дела»' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Удалить вид «Командный вид»/ })).toBeNull()
  })

  it('calls onApply when a view chip is clicked', async () => {
    const onApply = vi.fn()
    render(
      <SavedViewBar views={views} activeViewId={null} canShare onApply={onApply} onSave={vi.fn()} onDelete={vi.fn()} />
    )
    await userEvent.click(screen.getByText('Мои дела'))
    expect(onApply).toHaveBeenCalledWith(views[0])
  })

  it('calls onDelete with the view id', async () => {
    const onDelete = vi.fn()
    render(
      <SavedViewBar views={views} activeViewId={null} canShare onApply={vi.fn()} onSave={vi.fn()} onDelete={onDelete} />
    )
    await userEvent.click(screen.getByRole('button', { name: 'Удалить вид «Мои дела»' }))
    expect(onDelete).toHaveBeenCalledWith('1')
  })

  it('does not call onSave when submitting a blank name', async () => {
    const onSave = vi.fn()
    render(
      <SavedViewBar views={[]} activeViewId={null} canShare onApply={vi.fn()} onSave={onSave} onDelete={vi.fn()} />
    )
    await userEvent.click(screen.getByText('+ Сохранить вид'))
    await userEvent.click(screen.getByText('Сохранить'))
    expect(onSave).not.toHaveBeenCalled()
  })

  it('trims the name and calls onSave, then resets the naming state', async () => {
    const onSave = vi.fn()
    render(
      <SavedViewBar views={[]} activeViewId={null} canShare onApply={vi.fn()} onSave={onSave} onDelete={vi.fn()} />
    )
    await userEvent.click(screen.getByText('+ Сохранить вид'))
    await userEvent.type(screen.getByPlaceholderText('Название вида'), '  Срочные дела  ')
    await userEvent.click(screen.getByText('Сохранить'))
    expect(onSave).toHaveBeenCalledWith('Срочные дела', false)
    expect(screen.queryByPlaceholderText('Название вида')).toBeNull()
  })

  it('submits on Enter and cancels on Escape', async () => {
    const onSave = vi.fn()
    render(
      <SavedViewBar views={[]} activeViewId={null} canShare onApply={vi.fn()} onSave={onSave} onDelete={vi.fn()} />
    )
    await userEvent.click(screen.getByText('+ Сохранить вид'))
    const input = screen.getByPlaceholderText('Название вида')
    await userEvent.type(input, 'Черновик{Escape}')
    expect(screen.queryByPlaceholderText('Название вида')).toBeNull()
    expect(onSave).not.toHaveBeenCalled()

    await userEvent.click(screen.getByText('+ Сохранить вид'))
    await userEvent.type(screen.getByPlaceholderText('Название вида'), 'Готово{Enter}')
    expect(onSave).toHaveBeenCalledWith('Готово', false)
  })

  it('passes shared=true to onSave only when the share checkbox is checked and canShare is true', async () => {
    const onSave = vi.fn()
    render(
      <SavedViewBar views={[]} activeViewId={null} canShare onApply={vi.fn()} onSave={onSave} onDelete={vi.fn()} />
    )
    await userEvent.click(screen.getByText('+ Сохранить вид'))
    await userEvent.type(screen.getByPlaceholderText('Название вида'), 'Общий вид')
    await userEvent.click(screen.getByRole('checkbox', { name: 'Виден команде' }))
    await userEvent.click(screen.getByText('Сохранить'))
    expect(onSave).toHaveBeenCalledWith('Общий вид', true)
  })

  it('hides the share checkbox when canShare is false', async () => {
    render(
      <SavedViewBar
        views={[]}
        activeViewId={null}
        canShare={false}
        onApply={vi.fn()}
        onSave={vi.fn()}
        onDelete={vi.fn()}
      />
    )
    await userEvent.click(screen.getByText('+ Сохранить вид'))
    expect(screen.queryByRole('checkbox')).toBeNull()
  })
})
