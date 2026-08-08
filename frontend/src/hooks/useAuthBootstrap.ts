import { useEffect } from 'react'
import { useAuthStore } from '../store/authStore'
import { clearLocalSession } from '../store/session'
import { refreshSession } from '../api/client'

export function useAuthBootstrap(): void {
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
