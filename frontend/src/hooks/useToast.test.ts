import { renderHook } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import { useToast } from './useToast'
import { useToastStore } from '../store/toastStore'

describe('useToast', () => {
  beforeEach(() => {
    useToastStore.setState({ toasts: [] })
  })

  it('success/error/info push toasts with the matching variant', () => {
    const { result } = renderHook(() => useToast())
    result.current.success('Saved')
    result.current.error('Failed')
    result.current.info('FYI')

    const variants = useToastStore.getState().toasts.map((t) => t.variant)
    expect(variants).toEqual(['success', 'error', 'info'])
  })

  it('undo pushes a success toast with a default 5s duration and an action', () => {
    const { result } = renderHook(() => useToast())
    const onUndo = () => undefined
    result.current.undo('Deleted case', onUndo, 'Undo')

    const toast = useToastStore.getState().toasts[0]
    expect(toast).toMatchObject({ variant: 'success', message: 'Deleted case', duration: 5000 })
    expect(toast.action).toEqual({ label: 'Undo', onClick: onUndo })
  })

  it('undo honors a custom duration', () => {
    const { result } = renderHook(() => useToast())
    result.current.undo('Deleted case', () => undefined, 'Undo', { duration: 1500 })
    expect(useToastStore.getState().toasts[0].duration).toBe(1500)
  })
})
