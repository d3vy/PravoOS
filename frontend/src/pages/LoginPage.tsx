import { useState, type FormEvent } from 'react'
import { Link, useNavigate, Navigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import { useAuthStore } from '../store/authStore'
import { authApi } from '../api/auth'
import { Button } from '../components/ui/Button'
import { Input } from '../components/ui/Input'
import { useTheme } from '../hooks/useTheme'

export default function LoginPage(): JSX.Element {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  const { setAuth, isAuthenticated, user } = useAuthStore()
  const { theme, toggleTheme } = useTheme()
  const navigate = useNavigate()

  if (isAuthenticated()) {
    const path = user?.role === 'ADMIN' ? '/admin/applications' : '/chat'
    return <Navigate to={path} replace />
  }

  const handleSubmit = async (e: FormEvent): Promise<void> => {
    e.preventDefault()
    setError(null)
    setLoading(true)

    try {
      const response = await authApi.login({ email, password })
      setAuth(response.token, {
        userId: response.userId,
        email: response.email,
        role: response.role,
      })
      const path = response.role === 'ADMIN' ? '/admin/applications' : '/chat'
      navigate(path, { replace: true })
    } catch {
      setError('Неверный email или пароль. Проверьте данные и попробуйте снова.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-light-bg dark:bg-dark-bg flex flex-col">
      <header className="flex items-center justify-between px-6 py-4 border-b border-light-border dark:border-dark-border bg-light-surface dark:bg-dark-surface">
        <Link
          to="/"
          className="flex items-center gap-2 font-semibold text-light-text dark:text-dark-text hover:opacity-80 transition-opacity"
        >
          <span>⚖️</span>
          <span>PravoOS</span>
        </Link>
        <button
          onClick={toggleTheme}
          aria-label={theme === 'dark' ? 'Светлая тема' : 'Тёмная тема'}
          className="w-9 h-9 flex items-center justify-center rounded-lg text-light-secondary dark:text-dark-secondary hover:bg-light-bg dark:hover:bg-dark-bg transition-colors"
        >
          {theme === 'dark' ? '☀' : '☾'}
        </button>
      </header>

      <main className="flex-1 flex items-center justify-center px-4 py-12">
        <motion.div
          initial={{ opacity: 0, y: 24 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4, ease: 'easeOut' }}
          className="w-full max-w-md"
        >
          <div className="bg-light-surface dark:bg-dark-surface rounded-2xl border border-light-border dark:border-dark-border p-8 shadow-sm">
            <div className="mb-8">
              <h1 className="text-2xl font-bold text-light-text dark:text-dark-text mb-2">
                Вход в систему
              </h1>
              <p className="text-sm text-light-secondary dark:text-dark-secondary">
                Введите ваши данные для доступа к PravoOS
              </p>
            </div>

            <form onSubmit={handleSubmit} className="flex flex-col gap-5">
              <Input
                id="email"
                label="Email"
                type="email"
                placeholder="example@lawfirm.ru"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
                autoComplete="email"
                autoFocus
              />

              <Input
                id="password"
                label="Пароль"
                type="password"
                placeholder="••••••••"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                autoComplete="current-password"
              />

              {error && (
                <motion.div
                  initial={{ opacity: 0, y: -8 }}
                  animate={{ opacity: 1, y: 0 }}
                  className="p-3 rounded-lg bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800"
                >
                  <p className="text-sm text-red-700 dark:text-red-400">{error}</p>
                </motion.div>
              )}

              <Button
                type="submit"
                variant="primary"
                size="lg"
                loading={loading}
                className="w-full mt-1"
              >
                Войти
              </Button>
            </form>

            <div className="mt-6 pt-6 border-t border-light-border dark:border-dark-border text-center">
              <p className="text-sm text-light-secondary dark:text-dark-secondary">
                Нет доступа?{' '}
                <Link
                  to="/apply"
                  className="text-light-accent dark:text-dark-accent hover:underline font-medium"
                >
                  Подать заявку
                </Link>
              </p>
            </div>
          </div>
        </motion.div>
      </main>
    </div>
  )
}
