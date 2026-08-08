import { renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { refreshSessionMock } = vi.hoisted(() => ({
  refreshSessionMock: vi.fn(),
}))

vi.mock('../api/client', () => ({
  refreshSession: refreshSessionMock,
}))

import { useAuthBootstrap } from './useAuthBootstrap'
import { useAuthStore } from '../store/authStore'

describe('useAuthBootstrap', () => {
  beforeEach(() => {
    refreshSessionMock.mockReset()
    useAuthStore.setState({ accessToken: null, user: null, bootstrapped: false })
  })

  it('marks bootstrapped immediately when an access token is already present', async () => {
    useAuthStore.setState({ accessToken: 'token', user: { userId: 'u1', email: 'a@b.com', role: 'LAWYER' } })
    renderHook(() => useAuthBootstrap())

    await waitFor(() => expect(useAuthStore.getState().bootstrapped).toBe(true))
    expect(refreshSessionMock).not.toHaveBeenCalled()
  })

  it('marks bootstrapped immediately when there is no persisted user', async () => {
    renderHook(() => useAuthBootstrap())

    await waitFor(() => expect(useAuthStore.getState().bootstrapped).toBe(true))
    expect(refreshSessionMock).not.toHaveBeenCalled()
  })

  it('attempts a session refresh when a user is persisted but there is no access token', async () => {
    refreshSessionMock.mockResolvedValue('new-token')
    useAuthStore.setState({ user: { userId: 'u1', email: 'a@b.com', role: 'LAWYER' } })

    renderHook(() => useAuthBootstrap())

    await waitFor(() => expect(useAuthStore.getState().bootstrapped).toBe(true))
    expect(refreshSessionMock).toHaveBeenCalledTimes(1)
  })

  it('clears auth when the refresh fails, and still marks bootstrapped', async () => {
    refreshSessionMock.mockRejectedValue(new Error('expired'))
    useAuthStore.setState({ user: { userId: 'u1', email: 'a@b.com', role: 'LAWYER' } })

    renderHook(() => useAuthBootstrap())

    await waitFor(() => expect(useAuthStore.getState().bootstrapped).toBe(true))
    expect(useAuthStore.getState().user).toBeNull()
    expect(useAuthStore.getState().accessToken).toBeNull()
  })
})
