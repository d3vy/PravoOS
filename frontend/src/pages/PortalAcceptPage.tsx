import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { motion } from 'framer-motion'
import axios from 'axios'
import { authApi } from '../api/auth'
import { useAuthStore } from '../store/authStore'
import { Button } from '../components/ui/Button'
import { Input } from '../components/ui/Input'
import { Logo } from '../components/ui/Logo'
import { Spinner } from '../components/ui/Spinner'
import { ThemeToggle } from '../components/ui/ThemeToggle'
import { validatePassword } from '../utils/password'

interface FieldErrors {
  password?: string
  confirmPassword?: string
}

export default function PortalAcceptPage(): JSX.Element {
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
      if (!password) next.password = 'Введите пароль от аккаунта'
      setErrors(next)
      return Object.keys(next).length === 0
    }
    const passwordError = validatePassword(password)
    if (passwordError) next.password = passwordError
    if (confirmPassword !== password) next.confirmPassword = 'Пароли не совпадают'
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
        setSubmitError('Неверный пароль от аккаунта. Попробуйте снова.')
      } else if (axios.isAxiosError(err) && err.response?.status === 409) {
        setSubmitError('Этот email нельзя использовать для входа в клиентский портал.')
      } else if (axios.isAxiosError(err) && err.response?.status === 400) {
        const message = err.response.data?.message as string | undefined
        setSubmitError(message ?? 'Ссылка недействительна или истекла. Запросите новое приглашение у вашего юриста.')
      } else {
        setSubmitError('Не удалось получить доступ. Попробуйте позже.')
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
        <div className="w-16 h-16 rounded-full bg-amber-50 dark:bg-amber-900/20 flex items-center justify-center text-amber-600 dark:text-amber-400 mx-auto mb-6">
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="12" cy="12" r="10" />
            <line x1="12" y1="8" x2="12" y2="12" />
            <line x1="12" y1="16" x2="12.01" y2="16" />
          </svg>
        </div>
        <h2 className="text-2xl font-semibold text-light-text dark:text-dark-text mb-4">Приглашение недействительно</h2>
        <p className="text-light-secondary dark:text-dark-secondary leading-relaxed mb-8">
          Ссылка-приглашение повреждена или истекла. Запросите новое приглашение у вашего юриста.
        </p>
        <Link to="/login">
          <Button variant="primary" size="md">
            Перейти ко входу
          </Button>
        </Link>
      </div>
    </motion.div>
  )

  return (
    <div className="min-h-screen bg-white/80 dark:bg-dark-bg/85 flex flex-col">
      <header className="flex items-center justify-between px-6 py-4 border-b border-light-border dark:border-dark-border bg-light-surface dark:bg-dark-surface">
        <Link to="/" className="hover:opacity-80 transition-opacity">
          <Logo />
        </Link>
        <ThemeToggle />
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
                <h1 className="font-sans text-2xl font-bold text-light-text dark:text-dark-text mb-2 tracking-tight">
                  Доступ к порталу
                </h1>
                <p className="text-sm text-light-secondary dark:text-dark-secondary font-light">
                  {accountExists
                    ? 'У вас уже есть аккаунт — войдите, чтобы привязать новое дело'
                    : 'Задайте пароль для входа'}
                  {email ? ' — ' : ''}
                  {email && <span className="text-light-text dark:text-dark-text font-medium">{email}</span>}
                </p>
              </div>

              <form onSubmit={handleSubmit} className="flex flex-col gap-5" noValidate>
                <Input
                  id="password"
                  label={accountExists ? 'Пароль от аккаунта' : 'Пароль'}
                  type="password"
                  placeholder={accountExists ? '••••••••' : 'Не менее 8 символов, буква и цифра'}
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
                    label="Повторите пароль"
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
                    className="p-3 rounded-lg bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800"
                  >
                    <p className="text-sm text-red-700 dark:text-red-400">{submitError}</p>
                  </motion.div>
                )}

                <Button type="submit" variant="primary" size="lg" loading={loading} className="w-full mt-1">
                  {accountExists ? 'Войти и привязать дело' : 'Создать доступ и войти'}
                </Button>
              </form>
            </div>
          </motion.div>
        )}
      </main>
    </div>
  )
}
