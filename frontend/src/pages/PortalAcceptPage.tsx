import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { motion } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import axios from 'axios'
import { authApi } from '../api/auth'
import { useAuthStore } from '../store/authStore'
import { Button } from '../components/ui/Button'
import { Input } from '../components/ui/Input'
import { Logo } from '../components/ui/Logo'
import { Spinner } from '../components/ui/Spinner'
import { ThemeToggle } from '../components/ui/ThemeToggle'
import { LanguageSwitcher } from '../components/ui/LanguageSwitcher'
import { LegalFooter } from '../components/legal/LegalFooter'
import { validatePassword } from '../utils/password'

interface FieldErrors {
  password?: string
  confirmPassword?: string
}

export default function PortalAcceptPage(): JSX.Element {
  const { t } = useTranslation()
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const setSession = useAuthStore((state) => state.setSession)
  const [token] = useState(() => searchParams.get('token') ?? '')

  useEffect(() => {
    if (searchParams.has('token')) {
      window.history.replaceState(null, '', window.location.pathname)
    }
  }, [searchParams])

  const [email, setEmail] = useState<string | null>(null)
  const [accountExists, setAccountExists] = useState(false)
  const [previewLoading, setPreviewLoading] = useState(true)
  const [linkInvalid, setLinkInvalid] = useState(false)

  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [errors, setErrors] = useState<FieldErrors>({})
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (!token) {
      setPreviewLoading(false)
      setLinkInvalid(true)
      return
    }
    let active = true
    authApi
      .portalInvitePreview(token)
      .then((preview) => {
        if (active) {
          setEmail(preview.email)
          setAccountExists(preview.accountExists)
        }
      })
      .catch(() => {
        if (active) setLinkInvalid(true)
      })
      .finally(() => {
        if (active) setPreviewLoading(false)
      })
    return () => {
      active = false
    }
  }, [token])

  const validate = (): boolean => {
    const next: FieldErrors = {}
    if (accountExists) {
      if (!password) next.password = t('portalAccept.enterPassword')
      setErrors(next)
      return Object.keys(next).length === 0
    }
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
      const response = await authApi.portalAccept(token, password)
      if (response.accessToken && response.userId && response.email && response.role) {
        setSession(response.accessToken, {
          userId: response.userId,
          email: response.email,
          role: response.role,
        })
        navigate('/portal', { replace: true })
      }
    } catch (err) {
      if (axios.isAxiosError(err) && err.response?.status === 401) {
        setSubmitError(t('portalAccept.wrongPassword'))
      } else if (axios.isAxiosError(err) && err.response?.status === 409) {
        setSubmitError(t('portalAccept.emailNotAllowed'))
      } else if (axios.isAxiosError(err) && err.response?.status === 400) {
        const message = err.response.data?.message as string | undefined
        setSubmitError(message ?? t('portalAccept.linkInvalid'))
      } else {
        setSubmitError(t('portalAccept.accessFailed'))
      }
    } finally {
      setLoading(false)
    }
  }

  const renderInvalidLink = (): JSX.Element => (
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
        <h2 className="text-2xl font-semibold text-fg mb-4">{t('portalAccept.invalidTitle')}</h2>
        <p className="text-fg-muted leading-relaxed mb-8">
          {t('portalAccept.invalidText')}
        </p>
        <Link to="/login">
          <Button variant="primary" size="md">
            {t('auth.goToLogin')}
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
        {previewLoading ? (
          <Spinner size="lg" />
        ) : linkInvalid ? (
          renderInvalidLink()
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
                  {t('portalAccept.title')}
                </h1>
                <p className="text-sm text-fg-muted font-light">
                  {accountExists
                    ? t('portalAccept.subtitleExisting')
                    : t('portalAccept.subtitleNew')}
                  {email ? ' — ' : ''}
                  {email && <span className="text-fg font-medium">{email}</span>}
                </p>
              </div>

              <form onSubmit={handleSubmit} className="flex flex-col gap-5" noValidate>
                <Input
                  id="password"
                  label={accountExists ? t('portalAccept.accountPassword') : t('auth.passwordLabel')}
                  type="password"
                  placeholder={accountExists ? '••••••••' : t('auth.newPasswordPlaceholder')}
                  value={password}
                  onChange={(e) => {
                    setPassword(e.target.value)
                    if (errors.password) setErrors((prev) => ({ ...prev, password: undefined }))
                  }}
                  error={errors.password}
                  autoComplete={accountExists ? 'current-password' : 'new-password'}
                  autoFocus
                />

                {!accountExists && (
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
                )}

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
                  {accountExists ? t('portalAccept.submitExisting') : t('portalAccept.submitNew')}
                </Button>
              </form>
            </div>
          </motion.div>
        )}
      </main>

      <LegalFooter />
    </div>
  )
}
