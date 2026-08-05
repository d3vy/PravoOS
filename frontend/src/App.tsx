import { lazy, Suspense } from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import { useAuthStore } from './store/authStore'
import { useAuthBootstrap } from './hooks/useAuthBootstrap'
import { useLanguageSync } from './hooks/useLanguage'
import { ProtectedRoute } from './components/layout/ProtectedRoute'
import { FullScreenLoader } from './components/ui/FullScreenLoader'
import { AdminLayout } from './components/layout/AdminLayout'
import { LawyerLayout } from './components/layout/LawyerLayout'
import type { UserRole } from './types'

const LandingPage = lazy(() => import('./pages/LandingPage'))
const LoginPage = lazy(() => import('./pages/LoginPage'))
const ApplyPage = lazy(() => import('./pages/ApplyPage'))
const ApplicationStatusPage = lazy(() => import('./pages/ApplicationStatusPage'))
const EditApplicationPage = lazy(() => import('./pages/EditApplicationPage'))
const ForgotPasswordPage = lazy(() => import('./pages/ForgotPasswordPage'))
const ResetPasswordPage = lazy(() => import('./pages/ResetPasswordPage'))
const VerifyEmailPage = lazy(() => import('./pages/VerifyEmailPage'))
const PortalAcceptPage = lazy(() => import('./pages/PortalAcceptPage'))
const PortalCasesPage = lazy(() => import('./pages/portal/PortalCasesPage'))
const PortalCaseDetailPage = lazy(() => import('./pages/portal/PortalCaseDetailPage'))
const PortalInvoicesPage = lazy(() => import('./pages/portal/PortalInvoicesPage'))
const PortalInvoiceDetailPage = lazy(() => import('./pages/portal/PortalInvoiceDetailPage'))
const DashboardPage = lazy(() => import('./pages/dashboard/DashboardPage'))
const ChatPage = lazy(() => import('./pages/chat/ChatPage'))
const CalendarPage = lazy(() => import('./pages/calendar/CalendarPage'))
const CasesPage = lazy(() => import('./pages/cases/CasesPage'))
const CaseDetailPage = lazy(() => import('./pages/cases/CaseDetailPage'))
const DraftEditorPage = lazy(() => import('./pages/cases/DraftEditorPage'))
const MessagesPage = lazy(() => import('./pages/messages/MessagesPage'))
const ClientsPage = lazy(() => import('./pages/clients/ClientsPage'))
const ClientDetailPage = lazy(() => import('./pages/clients/ClientDetailPage'))
const SearchPage = lazy(() => import('./pages/search/SearchPage'))
const TabularReviewPage = lazy(() => import('./pages/review/TabularReviewPage'))
const TemplatesPage = lazy(() => import('./pages/templates/TemplatesPage'))
const WorkflowsPage = lazy(() => import('./pages/workflows/WorkflowsPage'))
const TeamPage = lazy(() => import('./pages/team/TeamPage'))
const InvitePage = lazy(() => import('./pages/team/InvitePage'))
const ApplicationsPage = lazy(() => import('./pages/admin/ApplicationsPage'))
const DocumentsPage = lazy(() => import('./pages/admin/DocumentsPage'))
const AiStatsPage = lazy(() => import('./pages/admin/AiStatsPage'))
const UsersPage = lazy(() => import('./pages/admin/UsersPage'))
const BillingPage = lazy(() => import('./pages/billing/BillingPage'))
const InvoicesPage = lazy(() => import('./pages/invoices/InvoicesPage'))
const InvoiceDetailPage = lazy(() => import('./pages/invoices/InvoiceDetailPage'))
const MailboxesPage = lazy(() => import('./pages/mailboxes/MailboxesPage'))
const ProfilePage = lazy(() => import('./pages/profile/ProfilePage'))
const SettingsPage = lazy(() => import('./pages/settings/SettingsPage'))

function homePathForRole(role: UserRole | undefined): string {
  if (role === 'ADMIN') return '/admin/applications'
  if (role === 'CLIENT') return '/portal'
  return '/dashboard'
}

export default function App(): JSX.Element {
  const { isAuthenticated, bootstrapped, effectiveRole } = useAuthStore()
  useAuthBootstrap()
  useLanguageSync()

  if (!bootstrapped) {
    return <FullScreenLoader />
  }

  return (
    <Suspense fallback={<FullScreenLoader />}>
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
          path="/portal/invoices"
          element={
            <ProtectedRoute requiredRole="CLIENT">
              <PortalInvoicesPage />
            </ProtectedRoute>
          }
        />

        <Route
          path="/portal/invoices/:invoiceId"
          element={
            <ProtectedRoute requiredRole="CLIENT">
              <PortalInvoiceDetailPage />
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
          <Route path="/messages" element={<MessagesPage />} />
          <Route path="/review" element={<TabularReviewPage />} />
          <Route path="/review/:reviewId" element={<TabularReviewPage />} />
          <Route path="/templates" element={<TemplatesPage />} />
          <Route path="/workflows" element={<WorkflowsPage />} />
          <Route path="/clients" element={<ClientsPage />} />
          <Route path="/clients/:clientId" element={<ClientDetailPage />} />
          <Route path="/invoices" element={<InvoicesPage />} />
          <Route path="/invoices/:invoiceId" element={<InvoiceDetailPage />} />
          <Route path="/emails" element={<MailboxesPage />} />
          <Route path="/search" element={<SearchPage />} />
          <Route path="/team" element={<TeamPage />} />
          <Route path="/invite" element={<InvitePage />} />
          <Route path="/billing" element={<BillingPage />} />
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
    </Suspense>
  )
}
