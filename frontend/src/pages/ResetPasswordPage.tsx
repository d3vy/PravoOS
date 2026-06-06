import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { motion } from 'framer-motion'
import axios from 'axios'
import { authApi } from '../api/auth'
import { Button } from '../components/ui/Button'
import { Input } from '../components/ui/Input'
import { Logo } from '../components/ui/Logo'
import { ThemeToggle } from '../components/ui/ThemeToggle'

interface FieldErrors {
  password?: string
  confirmPassword?: string
}

export default function ResetPasswordPage(): JSX.Element {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token') ?? ''
  const navigate = useNavigate()

  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [errors, setErrors] = useState<FieldErrors>({})
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [done, setDone] = useState(false)

  const validate = (): boolean => {
    const next: FieldErrors = {}
    if (password.length < 8) next.password = 'Пароль — не менее 8 символов'
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
      await authApi.resetPassword(token, password)
      setDone(true)
    } catch (err) {
      if (axios.isAxiosError(err) && err.response?.status === 400) {
        const message = err.response.data?.message as string | undefined
        setSubmitError(message ?? 'Ссылка недействительна или истекла. Запросите сброс пароля заново.')
      } else {
        setSubmitError('Не удалось сбросить пароль. Попробуйте позже.')
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
        <div className="w-16 h-16 rounded-full bg-amber-50 dark:bg-amber-900/20 flex items-center justify-center text-amber-600 dark:text-amber-400 mx-auto mb-6">
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="12" cy="12" r="10" />
            <line x1="12" y1="8" x2="12" y2="12" />
            <line x1="12" y1="16" x2="12.01" y2="16" />
          </svg>
        </div>
        <h2 className="text-2xl font-semibold text-light-text dark:text-dark-text mb-4">Ссылка недействительна</h2>
        <p className="text-light-secondary dark:text-dark-secondary leading-relaxed mb-8">{text}</p>
        <Link to="/forgot-password">
          <Button variant="primary" size="md">
            Запросить новую ссылку
          </Button>
        </Link>
      </div>
    </motion.div>
  )

  return (
    <div className="min-h-screen bg-light-bg dark:bg-dark-bg flex flex-col">
      <header className="flex items-center justify-between px-6 py-4 border-b border-light-border dark:border-dark-border bg-light-surface dark:bg-dark-surface">
        <Link to="/" className="hover:opacity-80 transition-opacity">
          <Logo />
        </Link>
        <ThemeToggle />
      </header>

      <main className="flex-1 flex items-center justify-center px-4 py-12">
        {!token ? (
          renderInvalidLink('Ссылка для сброса пароля неполная или повреждена. Запросите сброс пароля заново.')
        ) : done ? (
          <motion.div
            initial={{ opacity: 0, scale: 0.96 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ duration: 0.4 }}
            className="w-full max-w-md text-center"
          >
            <div className="card-elevated rounded-2xl p-10">
              <div className="w-16 h-16 rounded-full bg-emerald-50 dark:bg-emerald-900/20 flex items-center justify-center text-emerald-600 dark:text-emerald-400 mx-auto mb-6">
                <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                  <polyline points="20 6 9 17 4 12" />
                </svg>
              </div>
              <h2 className="text-2xl font-semibold text-light-text dark:text-dark-text mb-4">Пароль изменён</h2>
              <p className="text-light-secondary dark:text-dark-secondary leading-relaxed mb-8">
                Теперь вы можете войти в систему с новым паролем.
              </p>
              <Button variant="primary" size="md" onClick={() => navigate('/login', { replace: true })}>
                Перейти ко входу
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
                <h1 className="font-sans text-2xl font-bold text-light-text dark:text-dark-text mb-2 tracking-tight">
                  Новый пароль
                </h1>
                <p className="text-sm text-light-secondary dark:text-dark-secondary font-light">
                  Введите новый пароль и повторите его для подтверждения
                </p>
              </div>

              <form onSubmit={handleSubmit} className="flex flex-col gap-5" noValidate>
                <Input
                  id="password"
                  label="Новый пароль"
                  type="password"
                  placeholder="Не менее 8 символов"
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
                  Сохранить пароль
                </Button>
              </form>
            </div>
          </motion.div>
        )}
      </main>
    </div>
  )
}
