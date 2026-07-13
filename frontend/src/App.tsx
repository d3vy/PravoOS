import { Routes, Route, Navigate } from 'react-router-dom'
import { useAuthStore } from './store/authStore'
import { useAuthBootstrap } from './hooks/useAuthBootstrap'
import { ProtectedRoute } from './components/layout/ProtectedRoute'
import { FullScreenLoader } from './components/ui/FullScreenLoader'
import { AdminLayout } from './components/layout/AdminLayout'
import { LawyerLayout } from './components/layout/LawyerLayout'
import type { UserRole } from './types'
import LandingPage from './pages/LandingPage'
import LoginPage from './pages/LoginPage'
import ApplyPage from './pages/ApplyPage'
import ApplicationStatusPage from './pages/ApplicationStatusPage'
import EditApplicationPage from './pages/EditApplicationPage'
import ForgotPasswordPage from './pages/ForgotPasswordPage'
import ResetPasswordPage from './pages/ResetPasswordPage'
import VerifyEmailPage from './pages/VerifyEmailPage'
import PortalAcceptPage from './pages/PortalAcceptPage'
import PortalCasesPage from './pages/portal/PortalCasesPage'
import PortalCaseDetailPage from './pages/portal/PortalCaseDetailPage'
import DashboardPage from './pages/dashboard/DashboardPage'
import ChatPage from './pages/chat/ChatPage'
import CalendarPage from './pages/calendar/CalendarPage'
import CasesPage from './pages/cases/CasesPage'
import CaseDetailPage from './pages/cases/CaseDetailPage'
import DraftEditorPage from './pages/cases/DraftEditorPage'
import ClientsPage from './pages/clients/ClientsPage'
import ClientDetailPage from './pages/clients/ClientDetailPage'
import SearchPage from './pages/search/SearchPage'
import TemplatesPage from './pages/templates/TemplatesPage'
import WorkflowsPage from './pages/workflows/WorkflowsPage'
import TeamPage from './pages/team/TeamPage'
import InvitePage from './pages/team/InvitePage'
import ApplicationsPage from './pages/admin/ApplicationsPage'
import DocumentsPage from './pages/admin/DocumentsPage'
import AiStatsPage from './pages/admin/AiStatsPage'
import UsersPage from './pages/admin/UsersPage'
import ProfilePage from './pages/profile/ProfilePage'
import SettingsPage from './pages/settings/SettingsPage'

function homePathForRole(role: UserRole | undefined): string {
  if (role === 'ADMIN') return '/admin/applications'
  if (role === 'CLIENT') return '/portal'
  return '/chat'
}

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
      <Route path="/portal/accept" element={<PortalAcceptPage />} />

      <Route
        path="/portal"
        element={
          <ProtectedRoute requiredRole="CLIENT">
            <PortalCasesPage />
          </ProtectedRoute>
        }
      />

      <Route
        path="/portal/cases/:caseId"
        element={
          <ProtectedRoute requiredRole="CLIENT">
            <PortalCaseDetailPage />
          </ProtectedRoute>
        }
      />

      <Route
        element={
          <ProtectedRoute requiredRole="LAWYER">
            <LawyerLayout />
          </ProtectedRoute>
        }
      >
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/chat" element={<ChatPage />} />
        <Route path="/calendar" element={<CalendarPage />} />
        <Route path="/cases" element={<CasesPage />} />
        <Route path="/cases/:caseId" element={<CaseDetailPage />} />
        <Route path="/cases/:caseId/drafts/:draftId" element={<DraftEditorPage />} />
        <Route path="/templates" element={<TemplatesPage />} />
        <Route path="/workflows" element={<WorkflowsPage />} />
        <Route path="/clients" element={<ClientsPage />} />
        <Route path="/clients/:clientId" element={<ClientDetailPage />} />
        <Route path="/search" element={<SearchPage />} />
        <Route path="/team" element={<TeamPage />} />
        <Route path="/invite" element={<InvitePage />} />
        <Route path="/profile" element={<ProfilePage />} />
        <Route path="/settings" element={<SettingsPage />} />
      </Route>

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
            <Navigate to={homePathForRole(effectiveRole())} replace />
          ) : (
            <Navigate to="/" replace />
          )
        }
      />
    </Routes>
  )
}
