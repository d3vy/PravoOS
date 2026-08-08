import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import i18n from '../../i18n'
import { useToastStore } from '../../store/toastStore'
import { ToastViewport } from './Toast'

beforeEach(async () => {
  await i18n.changeLanguage('ru')
  useToastStore.setState({ toasts: [] })
})

afterEach(() => {
  cleanup()
  vi.useRealTimers()
})

describe('ToastViewport', () => {
  it('renders nothing when there are no toasts', () => {
    render(<ToastViewport />)
    expect(screen.queryByRole('status')).toBeNull()
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('uses role=alert for error toasts and role=status otherwise', () => {
    useToastStore.getState().push({ variant: 'error', message: 'Ошибка сохранения' })
    useToastStore.getState().push({ variant: 'success', message: 'Сохранено' })
    render(<ToastViewport />)
    expect(screen.getByRole('alert')).toHaveTextContent('Ошибка сохранения')
    expect(screen.getByRole('status')).toHaveTextContent('Сохранено')
  })

  it('dismisses the toast when the close button is clicked', async () => {
    useToastStore.getState().push({ variant: 'info', message: 'Информация' })
    render(<ToastViewport />)
    expect(screen.getByText('Информация')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: i18n.t('common.close') }))
    expect(useToastStore.getState().toasts).toHaveLength(0)
  })

  it('invokes the action callback and dismisses the toast', async () => {
    const onClick = vi.fn()
    useToastStore.getState().push({
      variant: 'info',
      message: 'Отменить последнее действие?',
      action: { label: 'Отменить', onClick },
    })
    render(<ToastViewport />)

    await userEvent.click(screen.getByRole('button', { name: 'Отменить' }))
    expect(onClick).toHaveBeenCalledTimes(1)
    expect(useToastStore.getState().toasts).toHaveLength(0)
  })

  it('auto-dismisses after the configured duration', () => {
    vi.useFakeTimers()
    useToastStore.getState().push({ variant: 'info', message: 'Скоро исчезнет', duration: 1000 })
    render(<ToastViewport />)
    expect(useToastStore.getState().toasts).toHaveLength(1)

    vi.advanceTimersByTime(1000)
    expect(useToastStore.getState().toasts).toHaveLength(0)
  })
})
