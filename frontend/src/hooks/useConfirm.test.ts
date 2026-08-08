import { renderHook } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import { useConfirm } from './useConfirm'
import { useConfirmStore } from '../store/confirmStore'

describe('useConfirm', () => {
  beforeEach(() => {
    useConfirmStore.setState({ request: null })
  })

  it('delegates to the confirm store ask action', () => {
    const { result } = renderHook(() => useConfirm())
    void result.current({ title: 'Delete client?' })
    expect(useConfirmStore.getState().request).toMatchObject({ title: 'Delete client?' })
  })

  it('resolves once the store settles the request', async () => {
    const { result } = renderHook(() => useConfirm())
    const promise = result.current({ title: 'Delete client?' })
    useConfirmStore.getState().settle(true)
    await expect(promise).resolves.toBe(true)
  })
})
