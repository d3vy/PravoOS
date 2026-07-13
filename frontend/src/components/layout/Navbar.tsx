import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuthStore } from '../../store/authStore'
import { authApi } from '../../api/auth'
import { Button } from '../ui/Button'
import { Logo } from '../ui/Logo'
import { NavTabs } from '../ui/NavTabs'
import { ThemeToggle } from '../ui/ThemeToggle'
import { lawyerNavSections } from './lawyerNav'

const LAWYER_MOBILE_NAV_INDICATOR_ID = 'lawyer-mobile-nav-indicator'

export function Navbar(): JSX.Element {
  const { clearAuth, isAuthenticated, effectiveRole } = useAuthStore()
  const navigate = useNavigate()
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)
  const mobileMenuRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!mobileMenuOpen) return
    const handleClickOutside = (event: MouseEvent): void => {
      if (mobileMenuRef.current && !mobileMenuRef.current.contains(event.target as Node)) {
        setMobileMenuOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [mobileMenuOpen])

  const handleLogout = async (): Promise<void> => {
    try {
      await authApi.logout()
    } catch {
      // best-effort revocation; local session is cleared regardless
    }
    clearAuth()
    setMobileMenuOpen(false)
    navigate('/')
  }

  const role = effectiveRole()
  const dashboardPath = role === 'ADMIN' ? '/admin/applications' : '/chat'
  const authenticated = isAuthenticated()

  return (
    <header
      ref={mobileMenuRef}
      className="sticky top-0 z-50 bg-light-bg/90 dark:bg-dark-bg/90 backdrop-blur-md border-b border-light-border dark:border-dark-border"
    >
      <div className="page-container">
        <nav className="flex items-center justify-between h-16">
          <Link to={authenticated ? dashboardPath : '/'} className="hover:opacity-80 transition-opacity">
            <Logo />
          </Link>

          <div className="flex items-center gap-3">
            <ThemeToggle />

            {authenticated ? (
              <>
                {role === 'ADMIN' && (
                  <Link to={dashboardPath} className="hidden md:block">
                    <Button variant="ghost" size="sm">
                      Рабочий стол
                    </Button>
                  </Link>
                )}
                <div className="hidden md:block">
                  <Button variant="secondary" size="sm" onClick={() => void handleLogout()}>
                    Выйти
                  </Button>
                </div>
                <button
                  type="button"
                  aria-label="Меню"
                  aria-expanded={mobileMenuOpen}
                  onClick={() => setMobileMenuOpen((open) => !open)}
                  className="md:hidden flex items-center justify-center w-10 h-10 rounded-lg text-light-secondary dark:text-dark-secondary hover:text-light-text dark:hover:text-dark-text hover:bg-light-surface dark:hover:bg-dark-surface transition-colors"
                >
                  <HamburgerIcon open={mobileMenuOpen} />
                </button>
              </>
            ) : (
              <>
                <Link to="/login">
                  <Button variant="ghost" size="sm">
                    Войти
                  </Button>
                </Link>
                <Link to="/apply" className="hidden sm:block">
                  <Button variant="primary" size="sm">
                    Подать заявку
                  </Button>
                </Link>
              </>
            )}
          </div>
        </nav>
      </div>

      {authenticated && mobileMenuOpen && (
        <div className="md:hidden border-t border-light-border dark:border-dark-border bg-light-bg/95 dark:bg-dark-bg/95 backdrop-blur-md">
          <div className="page-container py-3 flex flex-col gap-1">
            {role === 'LAWYER' &&
              lawyerNavSections.map((section) => (
                <div key={section.title} className="mb-2">
                  <p className="eyebrow px-4 mb-1">{section.title}</p>
                  <NavTabs
                    items={section.items}
                    indicatorId={LAWYER_MOBILE_NAV_INDICATOR_ID}
                    orientation="vertical"
                    onNavigate={() => setMobileMenuOpen(false)}
                  />
                </div>
              ))}
            {role === 'ADMIN' && (
              <Link
                to={dashboardPath}
                onClick={() => setMobileMenuOpen(false)}
                className="px-4 py-3 rounded-lg text-sm font-medium text-light-text dark:text-dark-text hover:bg-light-surface dark:hover:bg-dark-surface transition-colors"
              >
                Рабочий стол
              </Link>
            )}
            <button
              onClick={() => void handleLogout()}
              className="text-left px-4 py-3 rounded-lg text-sm font-medium text-light-text dark:text-dark-text hover:bg-light-surface dark:hover:bg-dark-surface transition-colors"
            >
              Выйти
            </button>
          </div>
        </div>
      )}
    </header>
  )
}

function HamburgerIcon({ open }: { open: boolean }): JSX.Element {
  return (
    <svg className="w-6 h-6" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
      {open ? (
        <>
          <line x1="6" y1="6" x2="18" y2="18" />
          <line x1="6" y1="18" x2="18" y2="6" />
        </>
      ) : (
        <>
          <line x1="4" y1="7" x2="20" y2="7" />
          <line x1="4" y1="12" x2="20" y2="12" />
          <line x1="4" y1="17" x2="20" y2="17" />
        </>
      )}
    </svg>
  )
}
