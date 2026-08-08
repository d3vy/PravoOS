import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import i18n from '../../i18n'
import { useConfirmStore } from '../../store/confirmStore'
import { ConfirmDialogHost } from './ConfirmDialog'

beforeEach(async () => {
  await i18n.changeLanguage('ru')
  useConfirmStore.setState({ request: null })
})

afterEach(cleanup)

describe('ConfirmDialogHost', () => {
  it('is closed until ask() is called', () => {
    render(<ConfirmDialogHost />)
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('opens with the requested title/description and resolves true on confirm', async () => {
    render(<ConfirmDialogHost />)
    const promise = useConfirmStore.getState().ask({
      title: 'Удалить дело?',
      description: 'Это действие необратимо',
    })

    expect(await screen.findByRole('dialog')).toBeInTheDocument()
    expect(screen.getByText('Удалить дело?')).toBeInTheDocument()
    expect(screen.getByText('Это действие необратимо')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: i18n.t('common.confirm') }))
    await expect(promise).resolves.toBe(true)
    expect(useConfirmStore.getState().request).toBeNull()
  })

  it('resolves false and closes when cancelled', async () => {
    render(<ConfirmDialogHost />)
    const promise = useConfirmStore.getState().ask({ title: 'Удалить дело?' })
    await screen.findByRole('dialog')

    await userEvent.click(screen.getByRole('button', { name: i18n.t('common.cancel') }))
    await expect(promise).resolves.toBe(false)
  })

  it('uses custom confirm/cancel labels when provided', async () => {
    render(<ConfirmDialogHost />)
    useConfirmStore.getState().ask({
      title: 'Отозвать доступ?',
      confirmLabel: 'Отозвать',
      cancelLabel: 'Не сейчас',
    })
    await screen.findByRole('dialog')
    expect(screen.getByRole('button', { name: 'Отозвать' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Не сейчас' })).toBeInTheDocument()
  })

  it('applies the danger variant to the confirm button when danger is set', async () => {
    render(<ConfirmDialogHost />)
    useConfirmStore.getState().ask({ title: 'Удалить безвозвратно?', danger: true })
    const confirmButton = await screen.findByRole('button', { name: i18n.t('common.confirm') })
    expect(confirmButton.className).toContain('bg-red-600')
  })
})
