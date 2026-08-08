import { beforeEach, describe, expect, it } from 'vitest'
import { useAuthStore } from './authStore'

describe('useAuthStore', () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: null, user: null, bootstrapped: false })
  })

  it('starts unauthenticated', () => {
    expect(useAuthStore.getState().isAuthenticated()).toBe(false)
  })

  it('setSession stores the token and user', () => {
    useAuthStore.getState().setSession('token-1', { userId: 'u1', email: 'a@b.com', role: 'LAWYER' })
    const state = useAuthStore.getState()
    expect(state.accessToken).toBe('token-1')
    expect(state.user).toEqual({ userId: 'u1', email: 'a@b.com', role: 'LAWYER' })
    expect(state.isAuthenticated()).toBe(true)
  })

  it('setAccessToken updates only the token', () => {
    useAuthStore.getState().setSession('token-1', { userId: 'u1', email: 'a@b.com', role: 'LAWYER' })
    useAuthStore.getState().setAccessToken('token-2')
    const state = useAuthStore.getState()
    expect(state.accessToken).toBe('token-2')
    expect(state.user).toEqual({ userId: 'u1', email: 'a@b.com', role: 'LAWYER' })
  })

  it('clearAuth resets token and user', () => {
    useAuthStore.getState().setSession('token-1', { userId: 'u1', email: 'a@b.com', role: 'LAWYER' })
    useAuthStore.getState().clearAuth()
    const state = useAuthStore.getState()
    expect(state.accessToken).toBeNull()
    expect(state.user).toBeNull()
    expect(state.isAuthenticated()).toBe(false)
  })

  it('hasRole matches the current user role', () => {
    useAuthStore.getState().setSession('token-1', { userId: 'u1', email: 'a@b.com', role: 'ADMIN' })
    expect(useAuthStore.getState().hasRole('ADMIN')).toBe(true)
    expect(useAuthStore.getState().hasRole('LAWYER')).toBe(false)
  })

  it('hasRole is false when there is no user', () => {
    expect(useAuthStore.getState().hasRole('ADMIN')).toBe(false)
  })

  it('effectiveRole returns the current role or undefined', () => {
    expect(useAuthStore.getState().effectiveRole()).toBeUndefined()
    useAuthStore.getState().setSession('token-1', { userId: 'u1', email: 'a@b.com', role: 'CLIENT' })
    expect(useAuthStore.getState().effectiveRole()).toBe('CLIENT')
  })

  it('setBootstrapped toggles the bootstrapped flag', () => {
    useAuthStore.getState().setBootstrapped(true)
    expect(useAuthStore.getState().bootstrapped).toBe(true)
  })
})
