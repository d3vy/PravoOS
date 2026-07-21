/// <reference lib="webworker" />
import { cleanupOutdatedCaches, createHandlerBoundToURL, precacheAndRoute } from 'workbox-precaching'
import { NavigationRoute, registerRoute } from 'workbox-routing'
import { NetworkFirst, StaleWhileRevalidate } from 'workbox-strategies'
import { ExpirationPlugin } from 'workbox-expiration'
import { clientsClaim } from 'workbox-core'

declare const self: ServiceWorkerGlobalScope

interface PushPayload {
  title: string
  body: string
  url: string
  tag: string
}

const FALLBACK_PAYLOAD: PushPayload = {
  title: 'PravoOS',
  body: 'New notification',
  url: '/dashboard',
  tag: 'pravoos-generic',
}

precacheAndRoute(self.__WB_MANIFEST)
cleanupOutdatedCaches()

registerRoute(new NavigationRoute(createHandlerBoundToURL('/index.html'), { denylist: [/^\/api\//, /^\/grafana(\/|$)/] }))

registerRoute(
  ({ request }) => request.destination === 'image',
  new StaleWhileRevalidate({
    cacheName: 'pravoos-images',
    plugins: [new ExpirationPlugin({ maxEntries: 60, maxAgeSeconds: 7 * 24 * 60 * 60 })],
  }),
)

registerRoute(
  ({ url, request }) => url.pathname.startsWith('/api/') && request.method === 'GET',
  new NetworkFirst({
    cacheName: 'pravoos-api',
    networkTimeoutSeconds: 5,
    plugins: [new ExpirationPlugin({ maxEntries: 40, maxAgeSeconds: 60 * 60 })],
  }),
)

void self.skipWaiting()
clientsClaim()

self.addEventListener('push', (event) => {
  const payload = parsePayload(event.data)
  event.waitUntil(
    self.registration.showNotification(payload.title, {
      body: payload.body,
      tag: payload.tag,
      icon: '/icons/pwa-192x192.png',
      badge: '/icons/badge-72x72.png',
      data: { url: payload.url },
      renotify: true,
    } as NotificationOptions),
  )
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const targetUrl = (event.notification.data?.url as string | undefined) ?? FALLBACK_PAYLOAD.url
  event.waitUntil(openTarget(targetUrl))
})

function parsePayload(data: PushMessageData | null): PushPayload {
  if (!data) {
    return FALLBACK_PAYLOAD
  }
  try {
    const parsed = data.json() as Partial<PushPayload>
    return {
      title: parsed.title ?? FALLBACK_PAYLOAD.title,
      body: parsed.body ?? FALLBACK_PAYLOAD.body,
      url: parsed.url ?? FALLBACK_PAYLOAD.url,
      tag: parsed.tag ?? FALLBACK_PAYLOAD.tag,
    }
  } catch {
    return { ...FALLBACK_PAYLOAD, body: data.text() || FALLBACK_PAYLOAD.body }
  }
}

async function openTarget(targetUrl: string): Promise<void> {
  const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true })
  const absoluteUrl = new URL(targetUrl, self.location.origin).href
  for (const client of windows) {
    if (client.url === absoluteUrl) {
      await client.focus()
      return
    }
  }
  const existing = windows[0]
  if (existing && 'navigate' in existing) {
    await existing.focus()
    await existing.navigate(absoluteUrl)
    return
  }
  await self.clients.openWindow(absoluteUrl)
}
