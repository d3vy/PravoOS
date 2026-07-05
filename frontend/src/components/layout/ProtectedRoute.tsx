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
  const { isAuthenticated, bootstrapped, effectiveRole } = useAuthStore()

  if (!bootstrapped) {
    return <FullScreenLoader />
  }

  if (!isAuthenticated()) {
    return <Navigate to="/login" replace />
  }

  if (requiredRole && effectiveRole() !== requiredRole) {
    const role = effectiveRole()
    const fallback = role === 'ADMIN' ? '/admin/applications' : role === 'CLIENT' ? '/portal' : '/dashboard'
    return <Navigate to={fallback} replace />
  }

  return <>{children}</>
}
