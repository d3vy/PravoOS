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
  viewAsLawyer: boolean
  setSession: (accessToken: string, user: AuthUser) => void
  setAccessToken: (accessToken: string) => void
  setBootstrapped: (value: boolean) => void
  clearAuth: () => void
  isAuthenticated: () => boolean
  hasRole: (role: UserRole) => boolean
  effectiveRole: () => UserRole | undefined
  toggleViewAsLawyer: () => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      accessToken: null,
      user: null,
      bootstrapped: false,
      viewAsLawyer: false,
      setSession: (accessToken, user) => set({ accessToken, user }),
      setAccessToken: (accessToken) => set({ accessToken }),
      setBootstrapped: (value) => set({ bootstrapped: value }),
      clearAuth: () => set({ accessToken: null, user: null, viewAsLawyer: false }),
      isAuthenticated: () => get().accessToken !== null,
      hasRole: (role) => get().user?.role === role,
      effectiveRole: () => {
        const { user, viewAsLawyer } = get()
        if (!user) return undefined
        return user.role === 'ADMIN' && viewAsLawyer ? 'LAWYER' : user.role
      },
      toggleViewAsLawyer: () => set((state) => ({ viewAsLawyer: !state.viewAsLawyer })),
    }),
    {
      name: 'pravoos-auth',
      partialize: (state) => ({ user: state.user }),
    }
  )
)
