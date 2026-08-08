import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import '../../i18n'
import { RatingButtons } from './RatingButtons'

afterEach(cleanup)

describe('RatingButtons', () => {
  it('marks neither button pressed when rating is null', () => {
    render(<RatingButtons rating={null} onRate={vi.fn()} />)
    const buttons = screen.getAllByRole('button')
    expect(buttons.every((button) => button.getAttribute('aria-pressed') === 'false')).toBe(true)
  })

  it('marks the helpful button pressed when rating is 1', () => {
    render(<RatingButtons rating={1} onRate={vi.fn()} />)
    const [up, down] = screen.getAllByRole('button')
    expect(up).toHaveAttribute('aria-pressed', 'true')
    expect(down).toHaveAttribute('aria-pressed', 'false')
  })

  it('marks the not-helpful button pressed when rating is -1', () => {
    render(<RatingButtons rating={-1} onRate={vi.fn()} />)
    const [up, down] = screen.getAllByRole('button')
    expect(up).toHaveAttribute('aria-pressed', 'false')
    expect(down).toHaveAttribute('aria-pressed', 'true')
  })

  it('calls onRate with 1 or -1 depending on which button is clicked', async () => {
    const onRate = vi.fn()
    render(<RatingButtons rating={null} onRate={onRate} />)
    const [up, down] = screen.getAllByRole('button')
    await userEvent.click(up)
    expect(onRate).toHaveBeenLastCalledWith(1)
    await userEvent.click(down)
    expect(onRate).toHaveBeenLastCalledWith(-1)
  })

  it('disables both buttons when disabled is true', () => {
    render(<RatingButtons rating={null} onRate={vi.fn()} disabled />)
    for (const button of screen.getAllByRole('button')) {
      expect(button).toBeDisabled()
    }
  })
})
