import { beforeEach, describe, expect, it } from 'vitest'
import { useConfirmStore } from './confirmStore'

describe('useConfirmStore', () => {
  beforeEach(() => {
    useConfirmStore.setState({ request: null })
  })

  it('has no pending request initially', () => {
    expect(useConfirmStore.getState().request).toBeNull()
  })

  it('ask stores the request with the given options', () => {
    void useConfirmStore.getState().ask({ title: 'Delete case?', danger: true })
    const { request } = useConfirmStore.getState()
    expect(request).toMatchObject({ title: 'Delete case?', danger: true })
    expect(typeof request?.resolve).toBe('function')
  })

  it('settle(true) resolves the pending promise with true and clears the request', async () => {
    const promise = useConfirmStore.getState().ask({ title: 'Delete case?' })
    useConfirmStore.getState().settle(true)
    await expect(promise).resolves.toBe(true)
    expect(useConfirmStore.getState().request).toBeNull()
  })

  it('settle(false) resolves the pending promise with false', async () => {
    const promise = useConfirmStore.getState().ask({ title: 'Delete case?' })
    useConfirmStore.getState().settle(false)
    await expect(promise).resolves.toBe(false)
  })

  it('settle without a pending request is a no-op', () => {
    expect(() => useConfirmStore.getState().settle(true)).not.toThrow()
    expect(useConfirmStore.getState().request).toBeNull()
  })

  it('a second ask replaces the first pending request', async () => {
    const firstPromise = useConfirmStore.getState().ask({ title: 'First' })
    const secondPromise = useConfirmStore.getState().ask({ title: 'Second' })
    useConfirmStore.getState().settle(true)

    await expect(secondPromise).resolves.toBe(true)
    expect(useConfirmStore.getState().request).toBeNull()

    let firstSettled = false
    firstPromise.then(() => {
      firstSettled = true
    })
    await Promise.resolve()
    expect(firstSettled).toBe(false)
  })
})
