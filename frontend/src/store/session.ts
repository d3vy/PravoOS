import { useAuthStore } from './authStore'
import { useRecentEntitiesStore } from './recentEntitiesStore'

export function clearLocalSession(): void {
  useAuthStore.getState().clearAuth()
  useRecentEntitiesStore.setState({ entries: [] })
  useRecentEntitiesStore.persist.clearStorage()
}
