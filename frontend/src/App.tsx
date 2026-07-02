import { Routes, Route, Navigate } from 'react-router-dom'
import { useAuthStore } from './store/authStore'
import { useAuthBootstrap } from './hooks/useAuthBootstrap'
import { ProtectedRoute } from './components/layout/ProtectedRoute'
import { FullScreenLoader } from './components/ui/FullScreenLoader'
import { AdminLayout } from './components/layout/AdminLayout'
import LandingPage from './pages/LandingPage'
import LoginPage from './pages/LoginPage'
import ApplyPage from './pages/ApplyPage'
import ApplicationStatusPage from './pages/ApplicationStatusPage'
import EditApplicationPage from './pages/EditApplicationPage'
import ForgotPasswordPage from './pages/ForgotPasswordPage'
import ResetPasswordPage from './pages/ResetPasswordPage'
import VerifyEmailPage from './pages/VerifyEmailPage'
import DashboardPage from './pages/dashboard/DashboardPage'
import ChatPage from './pages/chat/ChatPage'
import CasesPage from './pages/cases/CasesPage'
import CaseDetailPage from './pages/cases/CaseDetailPage'
import ClientsPage from './pages/clients/ClientsPage'
import ClientDetailPage from './pages/clients/ClientDetailPage'
import SearchPage from './pages/search/SearchPage'
import TemplatesPage from './pages/templates/TemplatesPage'
import ApplicationsPage from './pages/admin/ApplicationsPage'
import DocumentsPage from './pages/admin/DocumentsPage'
import AiStatsPage from './pages/admin/AiStatsPage'
import UsersPage from './pages/admin/UsersPage'
import ProfilePage from './pages/profile/ProfilePage'

export default function App(): JSX.Element {
  const { isAuthenticated, bootstrapped, effectiveRole } = useAuthStore()
  useAuthBootstrap()

  if (!bootstrapped) {
    return <FullScreenLoader />
  }

  return (
    <Routes>
      <Route path="/" element={<LandingPage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/apply" element={<ApplyPage />} />
      <Route path="/application/:token" element={<ApplicationStatusPage />} />
      <Route path="/application/:token/edit" element={<EditApplicationPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />
      <Route path="/verify-email" element={<VerifyEmailPage />} />

      <Route
        path="/dashboard"
        element={
          <ProtectedRoute requiredRole="LAWYER">
            <DashboardPage />
          </ProtectedRoute>
        }
      />

      <Route
        path="/chat"
        element={
          <ProtectedRoute requiredRole="LAWYER">
            <ChatPage />
          </ProtectedRoute>
        }
      />

      <Route
        path="/cases"
        element={
          <ProtectedRoute requiredRole="LAWYER">
            <CasesPage />
          </ProtectedRoute>
        }
      />

      <Route
        path="/cases/:caseId"
        element={
          <ProtectedRoute requiredRole="LAWYER">
            <CaseDetailPage />
          </ProtectedRoute>
        }
      />

      <Route
        path="/templates"
        element={
          <ProtectedRoute requiredRole="LAWYER">
            <TemplatesPage />
          </ProtectedRoute>
        }
      />

      <Route
        path="/clients"
        element={
          <ProtectedRoute requiredRole="LAWYER">
            <ClientsPage />
          </ProtectedRoute>
        }
      />

      <Route
        path="/clients/:clientId"
        element={
          <ProtectedRoute requiredRole="LAWYER">
            <ClientDetailPage />
          </ProtectedRoute>
        }
      />

      <Route
        path="/search"
        element={
          <ProtectedRoute requiredRole="LAWYER">
            <SearchPage />
          </ProtectedRoute>
        }
      />

      <Route
        path="/profile"
        element={
          <ProtectedRoute requiredRole="LAWYER">
            <ProfilePage />
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
        <Route path="users" element={<UsersPage />} />
        <Route path="documents" element={<DocumentsPage />} />
        <Route path="ai-stats" element={<AiStatsPage />} />
      </Route>

      {/* Fallback */}
      <Route
        path="*"
        element={
          isAuthenticated() ? (
            <Navigate to={effectiveRole() === 'ADMIN' ? '/admin/applications' : '/chat'} replace />
          ) : (
            <Navigate to="/" replace />
          )
        }
      />
    </Routes>
  )
}
