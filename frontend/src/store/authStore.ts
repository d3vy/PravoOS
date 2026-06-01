import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { UserRole } from '../types'

interface AuthUser {
  userId: string
  email: string
  role: UserRole
}

interface AuthState {
  accessToken: string | null
  user: AuthUser | null
  bootstrapped: boolean
  setSession: (accessToken: string, user: AuthUser) => void
  setAccessToken: (accessToken: string) => void
  setBootstrapped: (value: boolean) => void
  clearAuth: () => void
  isAuthenticated: () => boolean
  hasRole: (role: UserRole) => boolean
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      accessToken: null,
      user: null,
      bootstrapped: false,
      setSession: (accessToken, user) => set({ accessToken, user }),
      setAccessToken: (accessToken) => set({ accessToken }),
      setBootstrapped: (value) => set({ bootstrapped: value }),
      clearAuth: () => set({ accessToken: null, user: null }),
      isAuthenticated: () => get().accessToken !== null,
      hasRole: (role) => get().user?.role === role,
    }),
    {
      name: 'pravoos-auth',
      partialize: (state) => ({ user: state.user }),
    }
  )
)
