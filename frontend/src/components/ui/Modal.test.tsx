import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import '../../i18n'
import { Modal } from './Modal'

afterEach(cleanup)

describe('Modal', () => {
  it('renders nothing when closed', () => {
    render(
      <Modal open={false} onClose={vi.fn()} title="Заголовок">
        content
      </Modal>
    )
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('renders the title, children and footer when open', () => {
    render(
      <Modal open onClose={vi.fn()} title="Заголовок" footer={<button type="button">OK</button>}>
        <p>Тело окна</p>
      </Modal>
    )
    const dialog = screen.getByRole('dialog')
    expect(dialog).toHaveAttribute('aria-modal', 'true')
    expect(screen.getByText('Заголовок')).toBeInTheDocument()
    expect(screen.getByText('Тело окна')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'OK' })).toBeInTheDocument()
  })

  it('omits the header entirely when no title is given', () => {
    render(
      <Modal open onClose={vi.fn()}>
        content
      </Modal>
    )
    expect(screen.queryByRole('button', { name: /close|закрыть/i })).toBeNull()
  })

  it('calls onClose when the header close button is clicked', async () => {
    const onClose = vi.fn()
    render(
      <Modal open onClose={onClose} title="Заголовок">
        content
      </Modal>
    )
    await userEvent.click(screen.getByRole('button', { name: /close|закрыть/i }))
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('calls onClose on Escape', () => {
    const onClose = vi.fn()
    render(
      <Modal open onClose={onClose} title="Заголовок">
        content
      </Modal>
    )
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('calls onClose when the backdrop is clicked but not when the dialog panel is clicked', async () => {
    const onClose = vi.fn()
    render(
      <Modal open onClose={onClose} title="Заголовок">
        <p>Тело окна</p>
      </Modal>
    )
    await userEvent.click(screen.getByText('Тело окна'))
    expect(onClose).not.toHaveBeenCalled()

    await userEvent.click(screen.getByRole('dialog').parentElement as HTMLElement)
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('moves focus into the dialog and restores it to the previously focused element on close', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const trigger = document.createElement('button')
    trigger.textContent = 'open'
    document.body.appendChild(trigger)
    trigger.focus()
    expect(document.activeElement).toBe(trigger)

    const { rerender } = render(
      <Modal open onClose={vi.fn()} title="Заголовок" footer={<button type="button">OK</button>}>
        content
      </Modal>
    )

    await vi.advanceTimersByTimeAsync(30)
    expect(document.activeElement).toBe(screen.getByRole('button', { name: /close|закрыть/i }))

    rerender(
      <Modal open={false} onClose={vi.fn()} title="Заголовок">
        content
      </Modal>
    )
    expect(document.activeElement).toBe(trigger)

    document.body.removeChild(trigger)
    vi.useRealTimers()
  })

  it('locks body scroll while open and restores it on close', () => {
    const { rerender } = render(
      <Modal open onClose={vi.fn()} title="Заголовок">
        content
      </Modal>
    )
    expect(document.body.style.overflow).toBe('hidden')

    rerender(
      <Modal open={false} onClose={vi.fn()} title="Заголовок">
        content
      </Modal>
    )
    expect(document.body.style.overflow).toBe('')
  })
})
