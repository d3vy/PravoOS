import { beforeEach, describe, expect, it } from 'vitest'
import { useToastStore } from './toastStore'

describe('useToastStore', () => {
  beforeEach(() => {
    useToastStore.setState({ toasts: [] })
  })

  it('starts with no toasts', () => {
    expect(useToastStore.getState().toasts).toEqual([])
  })

  it('push adds a toast with a default duration and returns its id', () => {
    const id = useToastStore.getState().push({ variant: 'success', message: 'Saved' })
    const { toasts } = useToastStore.getState()
    expect(toasts).toHaveLength(1)
    expect(toasts[0]).toMatchObject({ id, variant: 'success', message: 'Saved', duration: 4000 })
  })

  it('push honors a custom duration and action', () => {
    const action = { label: 'Undo', onClick: () => undefined }
    useToastStore.getState().push({ variant: 'error', message: 'Failed', duration: 10000, action })
    const toast = useToastStore.getState().toasts[0]
    expect(toast.duration).toBe(10000)
    expect(toast.action).toBe(action)
  })

  it('push generates unique ids for consecutive toasts', () => {
    const id1 = useToastStore.getState().push({ variant: 'info', message: 'One' })
    const id2 = useToastStore.getState().push({ variant: 'info', message: 'Two' })
    expect(id1).not.toBe(id2)
    expect(useToastStore.getState().toasts).toHaveLength(2)
  })

  it('dismiss removes only the matching toast', () => {
    const id1 = useToastStore.getState().push({ variant: 'info', message: 'One' })
    const id2 = useToastStore.getState().push({ variant: 'info', message: 'Two' })
    useToastStore.getState().dismiss(id1)
    const { toasts } = useToastStore.getState()
    expect(toasts).toHaveLength(1)
    expect(toasts[0].id).toBe(id2)
  })

  it('dismiss with an unknown id is a no-op', () => {
    useToastStore.getState().push({ variant: 'info', message: 'One' })
    useToastStore.getState().dismiss('not-an-id')
    expect(useToastStore.getState().toasts).toHaveLength(1)
  })
})
