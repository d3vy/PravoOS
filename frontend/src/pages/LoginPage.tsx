import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, Navigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import axios from 'axios'
import { useAuthStore } from '../store/authStore'
import { authApi } from '../api/auth'
import { Button } from '../components/ui/Button'
import { Input } from '../components/ui/Input'
import { Logo } from '../components/ui/Logo'
import { ThemeToggle } from '../components/ui/ThemeToggle'

export default function LoginPage(): JSX.Element {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [lockSeconds, setLockSeconds] = useState(0)
  const [loading, setLoading] = useState(false)
  const [showPassword, setShowPassword] = useState(false)

  const { setSession, isAuthenticated, user } = useAuthStore()
  const navigate = useNavigate()

  useEffect(() => {
    if (lockSeconds <= 0) return
    const timer = setInterval(() => setLockSeconds((seconds) => seconds - 1), 1000)
    return () => clearInterval(timer)
  }, [lockSeconds])

  if (isAuthenticated()) {
    const path = user?.role === 'ADMIN' ? '/admin/applications' : '/dashboard'
    return <Navigate to={path} replace />
  }

  const isLocked = lockSeconds > 0

  const handleSubmit = async (e: FormEvent): Promise<void> => {
    e.preventDefault()
    setError(null)
    setLoading(true)

    try {
      const response = await authApi.login({ email, password })
      setSession(response.accessToken, {
        userId: response.userId,
        email: response.email,
        role: response.role,
      })
      const path = response.role === 'ADMIN' ? '/admin/applications' : '/dashboard'
      navigate(path, { replace: true })
    } catch (err) {
      if (axios.isAxiosError(err) && err.response?.status === 429) {
        const retryAfter = Number(err.response.headers['retry-after'])
        setLockSeconds(Number.isFinite(retryAfter) && retryAfter > 0 ? retryAfter : 900)
      } else {
        setError('Неверный email или пароль. Проверьте данные и попробуйте снова.')
      }
    } finally {
      setLoading(false)
    }
  }

  const formatLockTime = (totalSeconds: number): string => {
    const minutes = Math.floor(totalSeconds / 60)
    const seconds = totalSeconds % 60
    return `${minutes}:${seconds.toString().padStart(2, '0')}`
  }

  return (
    <div className="min-h-screen bg-white/80 dark:bg-dark-bg/85 flex flex-col">
      <header className="flex items-center justify-between px-6 py-4 border-b border-light-border dark:border-dark-border bg-light-surface dark:bg-dark-surface">
        <Link to="/" className="hover:opacity-80 transition-opacity">
          <Logo />
        </Link>
        <ThemeToggle />
      </header>

      <main className="flex-1 flex items-center justify-center px-4 py-12">
        <motion.div
          initial={{ opacity: 0, y: 24 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4, ease: 'easeOut' }}
          className="w-full max-w-md"
        >
          <div className="card-elevated rounded-2xl p-8">
            <div className="mb-8">
              <h1 className="font-sans text-2xl font-bold text-light-text dark:text-dark-text mb-2 tracking-tight">
                Вход в систему
              </h1>
              <p className="text-sm text-light-secondary dark:text-dark-secondary font-light">
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

              <div className="flex flex-col gap-1.5">
                <Input
                  id="password"
                  label="Пароль"
                  type={showPassword ? 'text' : 'password'}
                  placeholder="••••••••"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                  autoComplete="current-password"
                  rightElement={
                    <button
                      type="button"
                      onClick={() => setShowPassword((visible) => !visible)}
                      aria-label={showPassword ? 'Скрыть пароль' : 'Показать пароль'}
                      aria-pressed={showPassword}
                      tabIndex={-1}
                      className="w-8 h-8 flex items-center justify-center rounded-md text-light-secondary dark:text-dark-secondary hover:text-light-text dark:hover:text-dark-text transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-light-accent dark:focus-visible:ring-dark-accent"
                    >
                      {showPassword ? <EyeIcon /> : <EyeOffIcon />}
                    </button>
                  }
                />
                <div className="text-right">
                  <Link
                    to="/forgot-password"
                    className="text-xs text-light-secondary dark:text-dark-secondary hover:text-light-text dark:hover:text-dark-text hover:underline"
                  >
                    Забыли пароль?
                  </Link>
                </div>
              </div>

              {isLocked && (
                <motion.div
                  initial={{ opacity: 0, y: -8 }}
                  animate={{ opacity: 1, y: 0 }}
                  className="p-3 rounded-lg bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-800"
                >
                  <p className="text-sm text-amber-700 dark:text-amber-400">
                    Слишком много неудачных попыток. Повторите через {formatLockTime(lockSeconds)}.
                  </p>
                </motion.div>
              )}

              {!isLocked && error && (
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
                disabled={isLocked}
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
                  className="text-light-text dark:text-dark-text hover:underline font-medium"
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

function EyeIcon(): JSX.Element {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M1 12s4-7 11-7 11 7 11 7-4 7-11 7-11-7-11-7z" />
      <circle cx="12" cy="12" r="3" />
    </svg>
  )
}

function EyeOffIcon(): JSX.Element {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24" />
      <line x1="1" y1="1" x2="23" y2="23" />
    </svg>
  )
}
