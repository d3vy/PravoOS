import type { PushSubscriptionRequest } from '../types'
import { urlBase64ToUint8Array } from './vapid'

export type PushPermission = 'granted' | 'denied' | 'default' | 'unsupported'

export function isPushSupported(): boolean {
  return (
    typeof window !== 'undefined' &&
    'serviceWorker' in navigator &&
    'PushManager' in window &&
    'Notification' in window
  )
}

export function pushPermission(): PushPermission {
  if (!isPushSupported()) {
    return 'unsupported'
  }
  return Notification.permission
}

export async function currentSubscription(): Promise<PushSubscription | null> {
  if (!isPushSupported()) {
    return null
  }
  const registration = await navigator.serviceWorker.ready
  return registration.pushManager.getSubscription()
}

export async function subscribeToPush(vapidPublicKey: string): Promise<PushSubscriptionRequest> {
  const permission = await Notification.requestPermission()
  if (permission !== 'granted') {
    throw new PushPermissionDeniedError()
  }
  const registration = await navigator.serviceWorker.ready
  const existing = await registration.pushManager.getSubscription()
  const subscription =
    existing ??
    (await registration.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: urlBase64ToUint8Array(vapidPublicKey),
    }))
  return toRequest(subscription)
}

export async function unsubscribeFromPush(): Promise<string | null> {
  const subscription = await currentSubscription()
  if (!subscription) {
    return null
  }
  const endpoint = subscription.endpoint
  await subscription.unsubscribe()
  return endpoint
}

export class PushPermissionDeniedError extends Error {
  constructor() {
    super('Push permission denied')
    this.name = 'PushPermissionDeniedError'
  }
}

function toRequest(subscription: PushSubscription): PushSubscriptionRequest {
  const json = subscription.toJSON()
  const keys = json.keys ?? {}
  if (!keys.p256dh || !keys.auth) {
    throw new Error('Push subscription has no encryption keys')
  }
  return {
    endpoint: subscription.endpoint,
    p256dh: keys.p256dh,
    auth: keys.auth,
    userAgent: navigator.userAgent.slice(0, 255),
  }
}
