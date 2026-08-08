import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { EmptyState } from './EmptyState'

afterEach(cleanup)

function renderState(props: Parameters<typeof EmptyState>[0]) {
  return render(
    <MemoryRouter>
      <EmptyState {...props} />
    </MemoryRouter>
  )
}

describe('EmptyState', () => {
  it('renders the description and the default generic illustration', () => {
    const { container } = renderState({ description: 'Пока пусто' })
    expect(screen.getByText('Пока пусто')).toBeInTheDocument()
    expect(container.querySelector('svg')).not.toBeNull()
  })

  it('omits the illustration when illustration is "none"', () => {
    const { container } = renderState({ description: 'Пока пусто', illustration: 'none' })
    expect(container.querySelector('svg')).toBeNull()
  })

  it('renders the title only when provided', () => {
    const { rerender } = renderState({ description: 'Пока пусто' })
    expect(screen.queryByText('Ничего нет')).toBeNull()
    rerender(
      <MemoryRouter>
        <EmptyState description="Пока пусто" title="Ничего нет" />
      </MemoryRouter>
    )
    expect(screen.getByText('Ничего нет')).toBeInTheDocument()
  })

  it('renders the action as a link when action.to is set', () => {
    renderState({
      description: 'Пока пусто',
      action: { label: 'Создать дело', to: '/cases/new' },
    })
    const link = screen.getByRole('link', { name: 'Создать дело' })
    expect(link).toHaveAttribute('href', '/cases/new')
  })

  it('renders the action as a button and calls onClick when no "to" is set', async () => {
    const onClick = vi.fn()
    renderState({
      description: 'Пока пусто',
      action: { label: 'Создать дело', onClick },
    })
    const button = screen.getByRole('button', { name: 'Создать дело' })
    await userEvent.click(button)
    expect(onClick).toHaveBeenCalledTimes(1)
  })

  it('renders no action block when action is omitted', () => {
    renderState({ description: 'Пока пусто' })
    expect(screen.queryByRole('link')).toBeNull()
    expect(screen.queryByRole('button')).toBeNull()
  })
})
