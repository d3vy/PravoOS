import { Link, useNavigate } from 'react-router-dom'
import { useAuthStore } from '../../store/authStore'
import { authApi } from '../../api/auth'
import { Button } from '../ui/Button'
import { Logo } from '../ui/Logo'
import { ThemeToggle } from '../ui/ThemeToggle'

export function Navbar(): JSX.Element {
  const { user, clearAuth, isAuthenticated, effectiveRole, viewAsLawyer, toggleViewAsLawyer } = useAuthStore()
  const navigate = useNavigate()

  const handleLogout = async (): Promise<void> => {
    try {
      await authApi.logout()
    } catch {
      // best-effort revocation; local session is cleared regardless
    }
    clearAuth()
    navigate('/')
  }

  const role = effectiveRole()
  const dashboardPath = role === 'ADMIN' ? '/admin/applications' : '/chat'

  return (
    <header className="sticky top-0 z-50 bg-light-bg/90 dark:bg-dark-bg/90 backdrop-blur-md border-b border-light-border dark:border-dark-border">
      <div className="page-container">
        <nav className="flex items-center justify-between h-16">
          <Link to="/" className="hover:opacity-80 transition-opacity">
            <Logo />
          </Link>

          <div className="flex items-center gap-3">
            <ThemeToggle />

            {isAuthenticated() ? (
              <>
                {role === 'LAWYER' && (
                  <>
                    <Link to="/search" className="hidden sm:block">
                      <Button variant="ghost" size="sm">
                        Поиск
                      </Button>
                    </Link>
                    <Link to="/cases" className="hidden sm:block">
                      <Button variant="ghost" size="sm">
                        Дела
                      </Button>
                    </Link>
                    <Link to="/clients" className="hidden sm:block">
                      <Button variant="ghost" size="sm">
                        Клиенты
                      </Button>
                    </Link>
                    <Link to="/chat" className="hidden sm:block">
                      <Button variant="ghost" size="sm">
                        AI-чат
                      </Button>
                    </Link>
                    <Link to="/profile" className="hidden sm:block">
                      <Button variant="ghost" size="sm">
                        Профиль
                      </Button>
                    </Link>
                  </>
                )}
                {role === 'ADMIN' && (
                  <Link to={dashboardPath} className="hidden sm:block">
                    <Button variant="ghost" size="sm">
                      Рабочий стол
                    </Button>
                  </Link>
                )}
                {user?.role === 'ADMIN' && viewAsLawyer && (
                  <button
                    onClick={() => {
                      toggleViewAsLawyer()
                      navigate('/admin/applications')
                    }}
                    className="hidden sm:flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium bg-amber-100 dark:bg-amber-900/30 text-amber-700 dark:text-amber-400 border border-amber-300 dark:border-amber-700 hover:bg-amber-200 dark:hover:bg-amber-900/50 transition-colors"
                  >
                    <span>← Вернуться в админку</span>
                  </button>
                )}
                <Button variant="secondary" size="sm" onClick={() => void handleLogout()}>
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
    </header>
  )
}
