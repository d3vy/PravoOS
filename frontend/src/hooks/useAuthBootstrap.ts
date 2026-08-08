import { useEffect } from 'react'
import { useAuthStore } from '../store/authStore'
import { clearLocalSession } from '../store/session'
import { refreshSession } from '../api/client'
import { syncCookieDecision } from '../utils/applyCookieDecision'

export function useAuthBootstrap(): void {
  const userId = useAuthStore((state) => state.user?.userId)

  useEffect(() => {
    if (userId) {
      syncCookieDecision()
    }
  }, [userId])

  useEffect(() => {
    const { accessToken, user, setBootstrapped } = useAuthStore.getState()

    if (accessToken || !user) {
      setBootstrapped(true)
      return
    }

    refreshSession()
      .catch(() => clearLocalSession())
      .finally(() => setBootstrapped(true))
  }, [])
}
