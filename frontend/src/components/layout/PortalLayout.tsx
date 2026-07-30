import { Link, useLocation } from 'react-router-dom'
import type { ReactNode } from 'react'
import { useAuthStore } from '../../store/authStore'
import { Button } from '../ui/Button'
import { Logo } from '../ui/Logo'
import { ThemeToggle } from '../ui/ThemeToggle'
import { LanguageSwitcher } from '../ui/LanguageSwitcher'
import { SkipLink } from '../ui/SkipLink'
import { authApi } from '../../api/auth'
import { useTranslation } from 'react-i18next'

interface PortalLayoutProps {
  children: ReactNode
}

export function PortalLayout({ children }: PortalLayoutProps): JSX.Element {
  const { user, clearAuth } = useAuthStore()
  const { t } = useTranslation()
  const location = useLocation()

  const navLinkClass = (active: boolean): string =>
    `text-sm font-medium transition-colors ${
      active ? 'text-accent' : 'text-fg-muted hover:text-fg'
    }`

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
      <SkipLink />
      <header className="flex items-center justify-between px-6 py-4 border-b border-line bg-surface">
        <div className="flex items-center gap-6">
          <Link to="/portal" className="hover:opacity-80 transition-opacity">
            <Logo />
          </Link>
          <nav className="hidden sm:flex items-center gap-4">
            <Link to="/portal" className={navLinkClass(location.pathname.startsWith('/portal/cases') || location.pathname === '/portal')}>
              {t('portalCases.navLabel')}
            </Link>
            <Link to="/portal/invoices" className={navLinkClass(location.pathname.startsWith('/portal/invoices'))}>
              {t('portalInvoices.navLabel')}
            </Link>
          </nav>
        </div>
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

      <main id="main-content" tabIndex={-1} className="flex-1 w-full max-w-4xl mx-auto px-4 sm:px-6 py-8 focus:outline-none">{children}</main>
    </div>
  )
}
