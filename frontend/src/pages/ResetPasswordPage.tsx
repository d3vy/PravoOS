import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { motion } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import axios from 'axios'
import { authApi } from '../api/auth'
import { Button } from '../components/ui/Button'
import { Input } from '../components/ui/Input'
import { Logo } from '../components/ui/Logo'
import { ThemeToggle } from '../components/ui/ThemeToggle'
import { LanguageSwitcher } from '../components/ui/LanguageSwitcher'
import { validatePassword } from '../utils/password'

interface FieldErrors {
  password?: string
  confirmPassword?: string
}

export default function ResetPasswordPage(): JSX.Element {
  const { t } = useTranslation()
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const [token] = useState(() => searchParams.get('token') ?? '')

  useEffect(() => {
    if (searchParams.has('token')) {
      window.history.replaceState(null, '', window.location.pathname)
    }
  }, [searchParams])

  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [errors, setErrors] = useState<FieldErrors>({})
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [done, setDone] = useState(false)

  const validate = (): boolean => {
    const next: FieldErrors = {}
    const passwordError = validatePassword(password)
    if (passwordError) next.password = passwordError
    if (confirmPassword !== password) next.confirmPassword = t('auth.passwordsDontMatch')
    setErrors(next)
    return Object.keys(next).length === 0
  }

  const handleSubmit = async (e: FormEvent): Promise<void> => {
    e.preventDefault()
    if (!validate()) return

    setSubmitError(null)
    setLoading(true)
    try {
      await authApi.resetPassword(token, password)
      setDone(true)
    } catch (err) {
      if (axios.isAxiosError(err) && err.response?.status === 400) {
        const message = err.response.data?.message as string | undefined
        setSubmitError(message ?? t('auth.resetLinkInvalid'))
      } else {
        setSubmitError(t('auth.resetFailed'))
      }
    } finally {
      setLoading(false)
    }
  }

  const renderInvalidLink = (text: string): JSX.Element => (
    <motion.div
      initial={{ opacity: 0, scale: 0.96 }}
      animate={{ opacity: 1, scale: 1 }}
      transition={{ duration: 0.4 }}
      className="w-full max-w-md text-center"
    >
      <div className="card-elevated rounded-2xl p-10">
        <div className="w-16 h-16 rounded-full bg-warning-soft flex items-center justify-center text-warning mx-auto mb-6">
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="12" cy="12" r="10" />
            <line x1="12" y1="8" x2="12" y2="12" />
            <line x1="12" y1="16" x2="12.01" y2="16" />
          </svg>
        </div>
        <h2 className="text-2xl font-semibold text-fg mb-4">{t('auth.linkInvalidTitle')}</h2>
        <p className="text-fg-muted leading-relaxed mb-8">{text}</p>
        <Link to="/forgot-password">
          <Button variant="primary" size="md">
            {t('auth.requestNewLink')}
          </Button>
        </Link>
      </div>
    </motion.div>
  )

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
        {!token ? (
          renderInvalidLink(t('auth.resetLinkIncomplete'))
        ) : done ? (
          <motion.div
            initial={{ opacity: 0, scale: 0.96 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ duration: 0.4 }}
            className="w-full max-w-md text-center"
          >
            <div className="card-elevated rounded-2xl p-10">
              <div className="w-16 h-16 rounded-full bg-success-soft flex items-center justify-center text-success mx-auto mb-6">
                <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                  <polyline points="20 6 9 17 4 12" />
                </svg>
              </div>
              <h2 className="text-2xl font-semibold text-fg mb-4">{t('auth.passwordChanged')}</h2>
              <p className="text-fg-muted leading-relaxed mb-8">
                {t('auth.canSignInNow')}
              </p>
              <Button variant="primary" size="md" onClick={() => navigate('/login', { replace: true })}>
                {t('auth.goToLogin')}
              </Button>
            </div>
          </motion.div>
        ) : (
          <motion.div
            initial={{ opacity: 0, y: 24 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.4, ease: 'easeOut' }}
            className="w-full max-w-md"
          >
            <div className="card-elevated rounded-2xl p-8">
              <div className="mb-8">
                <h1 className="font-sans text-2xl font-bold text-fg mb-2 tracking-tight">
                  {t('auth.newPasswordTitle')}
                </h1>
                <p className="text-sm text-fg-muted font-light">
                  {t('auth.newPasswordSubtitle')}
                </p>
              </div>

              <form onSubmit={handleSubmit} className="flex flex-col gap-5" noValidate>
                <Input
                  id="password"
                  label={t('auth.newPasswordLabel')}
                  type="password"
                  placeholder={t('auth.newPasswordPlaceholder')}
                  value={password}
                  onChange={(e) => {
                    setPassword(e.target.value)
                    if (errors.password) setErrors((prev) => ({ ...prev, password: undefined }))
                  }}
                  error={errors.password}
                  autoComplete="new-password"
                  autoFocus
                />

                <Input
                  id="confirmPassword"
                  label={t('auth.repeatPasswordLabel')}
                  type="password"
                  placeholder="••••••••"
                  value={confirmPassword}
                  onChange={(e) => {
                    setConfirmPassword(e.target.value)
                    if (errors.confirmPassword) setErrors((prev) => ({ ...prev, confirmPassword: undefined }))
                  }}
                  error={errors.confirmPassword}
                  autoComplete="new-password"
                />

                {submitError && (
                  <motion.div
                    initial={{ opacity: 0, y: -8 }}
                    animate={{ opacity: 1, y: 0 }}
                    className="p-3 rounded-lg bg-danger-soft border border-danger/30"
                  >
                    <p className="text-sm text-danger">{submitError}</p>
                  </motion.div>
                )}

                <Button type="submit" variant="primary" size="lg" loading={loading} className="w-full mt-1">
                  {t('auth.savePassword')}
                </Button>
              </form>
            </div>
          </motion.div>
        )}
      </main>
    </div>
  )
}
