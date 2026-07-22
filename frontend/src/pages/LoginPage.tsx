import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, Navigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import axios from 'axios'
import { useAuthStore } from '../store/authStore'
import { authApi } from '../api/auth'
import type { UserRole } from '../types'
import { Button } from '../components/ui/Button'
import { Input } from '../components/ui/Input'
import { Logo } from '../components/ui/Logo'
import { ThemeToggle } from '../components/ui/ThemeToggle'
import { LanguageSwitcher } from '../components/ui/LanguageSwitcher'

export default function LoginPage(): JSX.Element {
  const { t } = useTranslation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [lockSeconds, setLockSeconds] = useState(0)
  const [loading, setLoading] = useState(false)
  const [showPassword, setShowPassword] = useState(false)
  const [mfaToken, setMfaToken] = useState<string | null>(null)
  const [mfaCode, setMfaCode] = useState('')

  const { setSession, isAuthenticated, user } = useAuthStore()
  const navigate = useNavigate()

  useEffect(() => {
    if (lockSeconds <= 0) return
    const timer = setInterval(() => setLockSeconds((seconds) => seconds - 1), 1000)
    return () => clearInterval(timer)
  }, [lockSeconds])

  if (isAuthenticated()) {
    return <Navigate to={homePathForRole(user?.role)} replace />
  }

  const isLocked = lockSeconds > 0

  const completeSession = (auth: {
    accessToken: string
    userId: string
    email: string
    role: UserRole
  }): void => {
    setSession(auth.accessToken, {
      userId: auth.userId,
      email: auth.email,
      role: auth.role,
    })
    navigate(homePathForRole(auth.role), { replace: true })
  }

  const handleSubmit = async (e: FormEvent): Promise<void> => {
    e.preventDefault()
    setError(null)
    setLoading(true)

    try {
      const response = await authApi.login({ email, password })
      if (response.mfaRequired && response.mfaToken) {
        setMfaToken(response.mfaToken)
        setMfaCode('')
      } else if (response.accessToken && response.userId && response.email && response.role) {
        completeSession({
          accessToken: response.accessToken,
          userId: response.userId,
          email: response.email,
          role: response.role,
        })
      }
    } catch (err) {
      if (axios.isAxiosError(err) && err.response?.status === 429) {
        const retryAfter = Number(err.response.headers['retry-after'])
        setLockSeconds(Number.isFinite(retryAfter) && retryAfter > 0 ? retryAfter : 900)
      } else {
        setError(t('auth.invalidCredentials'))
      }
    } finally {
      setLoading(false)
    }
  }

  const handleMfaSubmit = async (e: FormEvent): Promise<void> => {
    e.preventDefault()
    if (!mfaToken) return
    setError(null)
    setLoading(true)

    try {
      const auth = await authApi.loginMfa({ mfaToken, code: mfaCode })
      completeSession(auth)
    } catch (err) {
      if (axios.isAxiosError(err) && err.response?.status === 401) {
        const code = err.response.data?.code
        if (code === 'MFA_INVALID_CHALLENGE') {
          setMfaToken(null)
          setError(t('auth.mfaChallengeExpired'))
        } else {
          setError(t('auth.mfaInvalidCode'))
        }
      } else {
        setError(t('auth.mfaConfirmFailed'))
      }
    } finally {
      setLoading(false)
    }
  }

  const cancelMfa = (): void => {
    setMfaToken(null)
    setMfaCode('')
    setError(null)
  }

  const formatLockTime = (totalSeconds: number): string => {
    const minutes = Math.floor(totalSeconds / 60)
    const seconds = totalSeconds % 60
    return `${minutes}:${seconds.toString().padStart(2, '0')}`
  }

  return (
    <div className="auth-shell flex flex-col">
      <header className="flex items-center justify-between px-6 py-4 border-b border-line bg-surface">
        <Link to="/" className="hover:opacity-80 transition-opacity">
          <Logo />
        </Link>
        <div className="flex items-center gap-2">
          <LanguageSwitcher />
          <ThemeToggle />
        </div>
      </header>

      <main className="flex-1 flex items-center justify-center px-4 py-12">
        <motion.div
          initial={{ opacity: 0, y: 24 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4, ease: 'easeOut' }}
          className="w-full max-w-md"
        >
          <div className="card-elevated rounded-2xl p-8">
            {mfaToken ? (
              <>
                <div className="mb-8">
                  <h1 className="font-sans text-2xl font-bold text-fg mb-2 tracking-tight">
                    {t('auth.mfaTitle')}
                  </h1>
                  <p className="text-sm text-fg-muted font-light">
                    {t('auth.mfaSubtitle')}
                  </p>
                </div>

                <form onSubmit={handleMfaSubmit} className="flex flex-col gap-5">
                  <Input
                    id="mfaCode"
                    label={t('auth.mfaCodeLabel')}
                    type="text"
                    inputMode="numeric"
                    autoComplete="one-time-code"
                    placeholder="000000"
                    value={mfaCode}
                    onChange={(e) => setMfaCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                    required
                    autoFocus
                  />

                  {error && (
                    <motion.div
                      initial={{ opacity: 0, y: -8 }}
                      animate={{ opacity: 1, y: 0 }}
                      className="p-3 rounded-lg bg-danger-soft border border-danger/30"
                    >
                      <p className="text-sm text-danger">{error}</p>
                    </motion.div>
                  )}

                  <Button
                    type="submit"
                    variant="primary"
                    size="lg"
                    loading={loading}
                    disabled={mfaCode.length !== 6}
                    className="w-full mt-1"
                  >
                    {t('auth.mfaConfirm')}
                  </Button>

                  <button
                    type="button"
                    onClick={cancelMfa}
                    className="text-sm text-fg-muted hover:text-fg hover:underline"
                  >
                    {t('auth.backToLogin')}
                  </button>
                </form>
              </>
            ) : (
            <>
            <div className="mb-8">
              <h1 className="font-sans text-2xl font-bold text-fg mb-2 tracking-tight">
                {t('auth.loginTitle')}
              </h1>
              <p className="text-sm text-fg-muted font-light">
                {t('auth.loginSubtitle')}
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
                  label={t('auth.passwordLabel')}
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
                      aria-label={showPassword ? t('auth.hidePassword') : t('auth.showPassword')}
                      aria-pressed={showPassword}
                      tabIndex={-1}
                      className="w-8 h-8 flex items-center justify-center rounded-md text-fg-muted hover:text-fg transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-accent"
                    >
                      {showPassword ? <EyeIcon /> : <EyeOffIcon />}
                    </button>
                  }
                />
                <div className="text-right">
                  <Link
                    to="/forgot-password"
                    className="text-xs text-fg-muted hover:text-fg hover:underline"
                  >
                    {t('auth.forgotPassword')}
                  </Link>
                </div>
              </div>

              {isLocked && (
                <motion.div
                  initial={{ opacity: 0, y: -8 }}
                  animate={{ opacity: 1, y: 0 }}
                  className="p-3 rounded-lg bg-warning-soft border border-warning/30"
                >
                  <p className="text-sm text-warning">
                    {t('auth.tooManyAttempts', { time: formatLockTime(lockSeconds) })}
                  </p>
                </motion.div>
              )}

              {!isLocked && error && (
                <motion.div
                  initial={{ opacity: 0, y: -8 }}
                  animate={{ opacity: 1, y: 0 }}
                  className="p-3 rounded-lg bg-danger-soft border border-danger/30"
                >
                  <p className="text-sm text-danger">{error}</p>
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
                {t('auth.loginSubmit')}
              </Button>
            </form>

            <div className="mt-6 pt-6 border-t border-line text-center">
              <p className="text-sm text-fg-muted">
                {t('auth.noAccess')}{' '}
                <Link
                  to="/apply"
                  className="text-fg hover:underline font-medium"
                >
                  {t('auth.apply')}
                </Link>
              </p>
            </div>
            </>
            )}
          </div>
        </motion.div>
      </main>
    </div>
  )
}

function homePathForRole(role: UserRole | undefined): string {
  if (role === 'ADMIN') return '/admin/applications'
  if (role === 'CLIENT') return '/portal'
  return '/dashboard'
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
