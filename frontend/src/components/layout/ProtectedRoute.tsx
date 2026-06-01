import { Navigate } from 'react-router-dom'
import { useAuthStore } from '../../store/authStore'
import { FullScreenLoader } from '../ui/FullScreenLoader'
import type { UserRole } from '../../types'
import type { ReactNode } from 'react'

interface ProtectedRouteProps {
  children: ReactNode
  requiredRole?: UserRole
}

export function ProtectedRoute({ children, requiredRole }: ProtectedRouteProps): JSX.Element {
  const { isAuthenticated, user, bootstrapped } = useAuthStore()

  if (!bootstrapped) {
    return <FullScreenLoader />
  }

  if (!isAuthenticated()) {
    return <Navigate to="/login" replace />
  }

  if (requiredRole && user?.role !== requiredRole) {
    const fallback = user?.role === 'ADMIN' ? '/admin/applications' : '/chat'
    return <Navigate to={fallback} replace />
  }

  return <>{children}</>
}
