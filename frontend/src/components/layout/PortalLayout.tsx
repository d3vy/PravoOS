import { Link } from 'react-router-dom'
import type { ReactNode } from 'react'
import { useAuthStore } from '../../store/authStore'
import { Button } from '../ui/Button'
import { Logo } from '../ui/Logo'
import { ThemeToggle } from '../ui/ThemeToggle'
import { LanguageSwitcher } from '../ui/LanguageSwitcher'
import { authApi } from '../../api/auth'
import { useTranslation } from 'react-i18next'

interface PortalLayoutProps {
  children: ReactNode
}

export function PortalLayout({ children }: PortalLayoutProps): JSX.Element {
  const { user, clearAuth } = useAuthStore()
  const { t } = useTranslation()

  const handleLogout = async (): Promise<void> => {
    try {
      await authApi.logout()
    } finally {
      clearAuth()
      window.location.href = '/login'
    }
  }

  return (
    <div className="min-h-screen bg-bg flex flex-col">
      <header className="flex items-center justify-between px-6 py-4 border-b border-line bg-surface">
        <Link to="/portal" className="hover:opacity-80 transition-opacity">
          <Logo />
        </Link>
        <div className="flex items-center gap-3">
          {user?.email && (
            <span className="hidden sm:inline text-sm text-fg-muted">
              {user.email}
            </span>
          )}
          <LanguageSwitcher />
          <ThemeToggle />
          <Button variant="ghost" size="sm" onClick={handleLogout}>
            {t('nav.logout')}
          </Button>
        </div>
      </header>

      <main className="flex-1 w-full max-w-4xl mx-auto px-4 sm:px-6 py-8">{children}</main>
    </div>
  )
}
