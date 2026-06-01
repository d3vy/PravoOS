import { useEffect } from 'react'
import { useAuthStore } from '../store/authStore'
import { refreshSession } from '../api/client'

export function useAuthBootstrap(): void {
  useEffect(() => {
    const { accessToken, user, setBootstrapped, clearAuth } = useAuthStore.getState()

    if (accessToken || !user) {
      setBootstrapped(true)
      return
    }

    refreshSession()
      .catch(() => clearAuth())
      .finally(() => setBootstrapped(true))
  }, [])
}
