import { useCallback, useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { pushApi } from '../api/push'
import {
  PushPermissionDeniedError,
  currentSubscription,
  isPushSupported,
  pushPermission,
  subscribeToPush,
  unsubscribeFromPush,
} from '../pwa/pushClient'

export type PushState =
  | 'unsupported'
  | 'not-configured'
  | 'blocked'
  | 'subscribed'
  | 'unsubscribed'
  | 'loading'

interface UsePushNotifications {
  state: PushState
  isBusy: boolean
  error: string | null
  enable: () => Promise<void>
  disable: () => Promise<void>
}

export function usePushNotifications(): UsePushNotifications {
  const supported = isPushSupported()

  const { data: config, isLoading: configLoading } = useQuery({
    queryKey: ['push-config'],
    queryFn: pushApi.config,
    enabled: supported,
    staleTime: 5 * 60 * 1000,
  })

  const [subscribed, setSubscribed] = useState<boolean | null>(null)
  const [isBusy, setIsBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!supported) {
      return
    }
    let cancelled = false
    currentSubscription()
      .then((subscription) => {
        if (!cancelled) {
          setSubscribed(subscription !== null)
        }
      })
      .catch(() => {
        if (!cancelled) {
          setSubscribed(false)
        }
      })
    return () => {
      cancelled = true
    }
  }, [supported])

  const enable = useCallback(async () => {
    if (!config?.publicKey) {
      return
    }
    setIsBusy(true)
    setError(null)
    try {
      const subscription = await subscribeToPush(config.publicKey)
      await pushApi.subscribe(subscription)
      setSubscribed(true)
    } catch (cause) {
      setError(
        cause instanceof PushPermissionDeniedError
          ? 'Уведомления заблокированы в браузере. Разрешите их в настройках сайта.'
          : 'Не удалось включить push-уведомления. Попробуйте ещё раз.',
      )
    } finally {
      setIsBusy(false)
    }
  }, [config?.publicKey])

  const disable = useCallback(async () => {
    setIsBusy(true)
    setError(null)
    try {
      const endpoint = await unsubscribeFromPush()
      if (endpoint) {
        await pushApi.unsubscribe(endpoint)
      }
      setSubscribed(false)
    } catch {
      setError('Не удалось отключить push-уведомления. Попробуйте ещё раз.')
    } finally {
      setIsBusy(false)
    }
  }, [])

  return {
    state: resolveState({ supported, configLoading, configured: config?.configured, subscribed }),
    isBusy,
    error,
    enable,
    disable,
  }
}

function resolveState({
  supported,
  configLoading,
  configured,
  subscribed,
}: {
  supported: boolean
  configLoading: boolean
  configured: boolean | undefined
  subscribed: boolean | null
}): PushState {
  if (!supported) {
    return 'unsupported'
  }
  if (configLoading || subscribed === null) {
    return 'loading'
  }
  if (!configured) {
    return 'not-configured'
  }
  if (pushPermission() === 'denied') {
    return 'blocked'
  }
  return subscribed ? 'subscribed' : 'unsubscribed'
}
