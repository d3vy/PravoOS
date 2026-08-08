import { act, renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { PushPermission } from '../pwa/pushClient'
import type { ReactNode } from 'react'

const { pushConfigMock, subscribeMock, unsubscribeMock } = vi.hoisted(() => ({
  pushConfigMock: vi.fn(),
  subscribeMock: vi.fn(),
  unsubscribeMock: vi.fn(),
}))

vi.mock('../api/push', () => ({
  pushApi: {
    config: pushConfigMock,
    subscribe: subscribeMock,
    unsubscribe: unsubscribeMock,
  },
}))

const {
  isPushSupportedMock,
  currentSubscriptionMock,
  pushPermissionMock,
  subscribeToPushMock,
  unsubscribeFromPushMock,
  PushPermissionDeniedErrorMock,
} = vi.hoisted(() => {
  class PushPermissionDeniedErrorMock extends Error {}
  return {
    isPushSupportedMock: vi.fn(() => true),
    currentSubscriptionMock: vi.fn(),
    pushPermissionMock: vi.fn<[], PushPermission>(() => 'default'),
    subscribeToPushMock: vi.fn(),
    unsubscribeFromPushMock: vi.fn(),
    PushPermissionDeniedErrorMock,
  }
})

vi.mock('../pwa/pushClient', () => ({
  isPushSupported: isPushSupportedMock,
  currentSubscription: currentSubscriptionMock,
  pushPermission: pushPermissionMock,
  subscribeToPush: subscribeToPushMock,
  unsubscribeFromPush: unsubscribeFromPushMock,
  PushPermissionDeniedError: PushPermissionDeniedErrorMock,
}))

import { usePushNotifications } from './usePushNotifications'
import i18n from '../i18n'

function wrapper({ children }: { children: ReactNode }) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
}

describe('usePushNotifications', () => {
  beforeEach(() => {
    isPushSupportedMock.mockReturnValue(true)
    pushConfigMock.mockReset()
    subscribeMock.mockReset()
    unsubscribeMock.mockReset()
    currentSubscriptionMock.mockReset()
    pushPermissionMock.mockReset().mockReturnValue('default')
    subscribeToPushMock.mockReset()
    unsubscribeFromPushMock.mockReset()
  })

  it('reports unsupported when the platform lacks push support', async () => {
    isPushSupportedMock.mockReturnValue(false)
    const { result } = renderHook(() => usePushNotifications(), { wrapper })
    await waitFor(() => expect(result.current.state).toBe('unsupported'))
  })

  it('reports not-configured when there is no VAPID key configured server-side', async () => {
    pushConfigMock.mockResolvedValue({ configured: false, publicKey: null })
    currentSubscriptionMock.mockResolvedValue(null)
    const { result } = renderHook(() => usePushNotifications(), { wrapper })
    await waitFor(() => expect(result.current.state).toBe('not-configured'))
  })

  it('reports blocked when the browser permission is denied', async () => {
    pushConfigMock.mockResolvedValue({ configured: true, publicKey: 'key' })
    currentSubscriptionMock.mockResolvedValue(null)
    pushPermissionMock.mockReturnValue('denied')
    const { result } = renderHook(() => usePushNotifications(), { wrapper })
    await waitFor(() => expect(result.current.state).toBe('blocked'))
  })

  it('reports unsubscribed when configured and not currently subscribed', async () => {
    pushConfigMock.mockResolvedValue({ configured: true, publicKey: 'key' })
    currentSubscriptionMock.mockResolvedValue(null)
    const { result } = renderHook(() => usePushNotifications(), { wrapper })
    await waitFor(() => expect(result.current.state).toBe('unsubscribed'))
  })

  it('reports subscribed when a push subscription already exists', async () => {
    pushConfigMock.mockResolvedValue({ configured: true, publicKey: 'key' })
    currentSubscriptionMock.mockResolvedValue({} as PushSubscription)
    const { result } = renderHook(() => usePushNotifications(), { wrapper })
    await waitFor(() => expect(result.current.state).toBe('subscribed'))
  })

  it('enable subscribes and flips state to subscribed', async () => {
    pushConfigMock.mockResolvedValue({ configured: true, publicKey: 'key' })
    currentSubscriptionMock.mockResolvedValue(null)
    subscribeToPushMock.mockResolvedValue({ endpoint: 'e', keys: { p256dh: 'a', auth: 'b' } })
    subscribeMock.mockResolvedValue(undefined)

    const { result } = renderHook(() => usePushNotifications(), { wrapper })
    await waitFor(() => expect(result.current.state).toBe('unsubscribed'))

    await act(async () => {
      await result.current.enable()
    })

    expect(subscribeToPushMock).toHaveBeenCalledWith('key')
    expect(subscribeMock).toHaveBeenCalled()
    expect(result.current.state).toBe('subscribed')
    expect(result.current.error).toBeNull()
  })

  it('enable surfaces a blocked-specific error when permission is denied', async () => {
    pushConfigMock.mockResolvedValue({ configured: true, publicKey: 'key' })
    currentSubscriptionMock.mockResolvedValue(null)
    subscribeToPushMock.mockRejectedValue(new PushPermissionDeniedErrorMock())

    const { result } = renderHook(() => usePushNotifications(), { wrapper })
    await waitFor(() => expect(result.current.state).toBe('unsubscribed'))

    await act(async () => {
      await result.current.enable()
    })

    expect(result.current.error).toBe(i18n.t('push.blocked'))
  })

  it('enable is a no-op without a public key', async () => {
    pushConfigMock.mockResolvedValue({ configured: false, publicKey: null })
    currentSubscriptionMock.mockResolvedValue(null)

    const { result } = renderHook(() => usePushNotifications(), { wrapper })
    await waitFor(() => expect(result.current.state).toBe('not-configured'))

    await act(async () => {
      await result.current.enable()
    })
    expect(subscribeToPushMock).not.toHaveBeenCalled()
  })

  it('disable unsubscribes and flips state to unsubscribed', async () => {
    pushConfigMock.mockResolvedValue({ configured: true, publicKey: 'key' })
    currentSubscriptionMock.mockResolvedValue({} as PushSubscription)
    unsubscribeFromPushMock.mockResolvedValue('endpoint-1')
    unsubscribeMock.mockResolvedValue(undefined)

    const { result } = renderHook(() => usePushNotifications(), { wrapper })
    await waitFor(() => expect(result.current.state).toBe('subscribed'))

    await act(async () => {
      await result.current.disable()
    })

    expect(unsubscribeMock).toHaveBeenCalledWith('endpoint-1')
    expect(result.current.state).toBe('unsubscribed')
  })

  it('disable surfaces a generic error on failure', async () => {
    pushConfigMock.mockResolvedValue({ configured: true, publicKey: 'key' })
    currentSubscriptionMock.mockResolvedValue({} as PushSubscription)
    unsubscribeFromPushMock.mockRejectedValue(new Error('boom'))

    const { result } = renderHook(() => usePushNotifications(), { wrapper })
    await waitFor(() => expect(result.current.state).toBe('subscribed'))

    await act(async () => {
      await result.current.disable()
    })

    expect(result.current.error).toBe(i18n.t('push.disableFailed'))
  })
})
