import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import i18n from '../../i18n'
import { Pagination } from './Pagination'

beforeEach(async () => {
  await i18n.changeLanguage('ru')
})

afterEach(cleanup)

describe('Pagination', () => {
  it('renders nothing when everything fits on a single page', () => {
    const { container } = render(
      <Pagination page={0} pageSize={20} total={10} onPageChange={vi.fn()} />
    )
    expect(container.firstChild).toBeNull()
  })

  it('renders nothing when there are no rows at all', () => {
    const { container } = render(
      <Pagination page={0} pageSize={20} total={0} onPageChange={vi.fn()} />
    )
    expect(container.firstChild).toBeNull()
  })

  it('disables prev on the first page and next on the last page', () => {
    const { rerender } = render(
      <Pagination page={0} pageSize={10} total={25} onPageChange={vi.fn()} />
    )
    expect(screen.getByRole('button', { name: '‹ Назад' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Вперёд ›' })).not.toBeDisabled()

    rerender(<Pagination page={2} pageSize={10} total={25} onPageChange={vi.fn()} />)
    expect(screen.getByRole('button', { name: '‹ Назад' })).not.toBeDisabled()
    expect(screen.getByRole('button', { name: 'Вперёд ›' })).toBeDisabled()
  })

  it('calls onPageChange with the adjacent page index', async () => {
    const onPageChange = vi.fn()
    render(<Pagination page={1} pageSize={10} total={25} onPageChange={onPageChange} />)

    const buttons = screen.getAllByRole('button')
    await userEvent.click(buttons[0])
    expect(onPageChange).toHaveBeenCalledWith(0)

    await userEvent.click(buttons[1])
    expect(onPageChange).toHaveBeenCalledWith(2)
  })
})
