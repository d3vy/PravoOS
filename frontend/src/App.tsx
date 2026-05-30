import { Routes, Route, Navigate } from 'react-router-dom'
import { useAuthStore } from './store/authStore'
import { ProtectedRoute } from './components/layout/ProtectedRoute'
import { AdminLayout } from './components/layout/AdminLayout'
import LandingPage from './pages/LandingPage'
import LoginPage from './pages/LoginPage'
import ApplyPage from './pages/ApplyPage'
import ChatPage from './pages/chat/ChatPage'
import ApplicationsPage from './pages/admin/ApplicationsPage'
import DocumentsPage from './pages/admin/DocumentsPage'

export default function App(): JSX.Element {
  const { isAuthenticated, user } = useAuthStore()

  return (
    <Routes>
      <Route path="/" element={<LandingPage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/apply" element={<ApplyPage />} />

      <Route
        path="/chat"
        element={
          <ProtectedRoute>
            <ChatPage />
          </ProtectedRoute>
        }
      />

      <Route
        path="/admin"
        element={
          <ProtectedRoute requiredRole="ADMIN">
            <AdminLayout />
          </ProtectedRoute>
        }
      >
        <Route index element={<Navigate to="/admin/applications" replace />} />
        <Route path="applications" element={<ApplicationsPage />} />
        <Route path="documents" element={<DocumentsPage />} />
      </Route>

      {/* Fallback */}
      <Route
        path="*"
        element={
          isAuthenticated() ? (
            <Navigate to={user?.role === 'ADMIN' ? '/admin/applications' : '/chat'} replace />
          ) : (
            <Navigate to="/" replace />
          )
        }
      />
    </Routes>
  )
}
