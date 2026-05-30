import { Link, useNavigate } from 'react-router-dom'
import { useAuthStore } from '../../store/authStore'
import { useTheme } from '../../hooks/useTheme'
import { Button } from '../ui/Button'

export function Navbar(): JSX.Element {
  const { user, clearAuth, isAuthenticated } = useAuthStore()
  const { theme, toggleTheme } = useTheme()
  const navigate = useNavigate()

  const handleLogout = (): void => {
    clearAuth()
    navigate('/')
  }

  const dashboardPath = user?.role === 'ADMIN' ? '/admin/applications' : '/chat'

  return (
    <header className="sticky top-0 z-50 bg-light-surface/80 dark:bg-dark-surface/80 backdrop-blur-sm border-b border-light-border dark:border-dark-border">
      <div className="page-container">
        <nav className="flex items-center justify-between h-16">
          <Link
            to="/"
            className="flex items-center gap-2 font-semibold text-lg text-light-text dark:text-dark-text hover:opacity-80 transition-opacity"
          >
            <span className="text-light-accent dark:text-dark-accent">⚖️</span>
            <span>PravoOS</span>
          </Link>

          <div className="flex items-center gap-3">
            <button
              onClick={toggleTheme}
              aria-label={theme === 'dark' ? 'Светлая тема' : 'Тёмная тема'}
              className="w-9 h-9 flex items-center justify-center rounded-lg text-light-secondary dark:text-dark-secondary hover:text-light-text dark:hover:text-dark-text hover:bg-light-bg dark:hover:bg-dark-bg transition-colors"
            >
              {theme === 'dark' ? (
                <SunIcon />
              ) : (
                <MoonIcon />
              )}
            </button>

            {isAuthenticated() ? (
              <>
                <Link to={dashboardPath}>
                  <Button variant="ghost" size="sm">
                    Рабочий стол
                  </Button>
                </Link>
                <Button variant="secondary" size="sm" onClick={handleLogout}>
                  Выйти
                </Button>
              </>
            ) : (
              <>
                <Link to="/login">
                  <Button variant="ghost" size="sm">
                    Войти
                  </Button>
                </Link>
                <Link to="/apply">
                  <Button variant="primary" size="sm">
                    Подать заявку
                  </Button>
                </Link>
              </>
            )}
          </div>
        </nav>
      </div>
    </header>
  )
}

function SunIcon(): JSX.Element {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="5" />
      <line x1="12" y1="1" x2="12" y2="3" />
      <line x1="12" y1="21" x2="12" y2="23" />
      <line x1="4.22" y1="4.22" x2="5.64" y2="5.64" />
      <line x1="18.36" y1="18.36" x2="19.78" y2="19.78" />
      <line x1="1" y1="12" x2="3" y2="12" />
      <line x1="21" y1="12" x2="23" y2="12" />
      <line x1="4.22" y1="19.78" x2="5.64" y2="18.36" />
      <line x1="18.36" y1="5.64" x2="19.78" y2="4.22" />
    </svg>
  )
}

function MoonIcon(): JSX.Element {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
    </svg>
  )
}
