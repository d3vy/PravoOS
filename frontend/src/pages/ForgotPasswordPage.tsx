import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import { authApi } from '../api/auth'
import { Button } from '../components/ui/Button'
import { Input } from '../components/ui/Input'
import { Logo } from '../components/ui/Logo'
import { ThemeToggle } from '../components/ui/ThemeToggle'

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export default function ForgotPasswordPage(): JSX.Element {
  const [email, setEmail] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [submitted, setSubmitted] = useState(false)

  const handleSubmit = async (e: FormEvent): Promise<void> => {
    e.preventDefault()
    if (!EMAIL_REGEX.test(email.trim())) {
      setError('Введите корректный email')
      return
    }

    setError(null)
    setLoading(true)
    try {
      await authApi.forgotPassword(email.trim())
      setSubmitted(true)
    } catch {
      setSubmitted(true)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-light-bg dark:bg-dark-bg flex flex-col">
      <header className="flex items-center justify-between px-6 py-4 border-b border-light-border dark:border-dark-border bg-light-surface dark:bg-dark-surface">
        <Link to="/" className="hover:opacity-80 transition-opacity">
          <Logo />
        </Link>
        <ThemeToggle />
      </header>

      <main className="flex-1 flex items-center justify-center px-4 py-12">
        {submitted ? (
          <motion.div
            initial={{ opacity: 0, scale: 0.96 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ duration: 0.4 }}
            className="w-full max-w-md text-center"
          >
            <div className="card-elevated rounded-2xl p-10">
              <div className="w-16 h-16 rounded-full bg-emerald-50 dark:bg-emerald-900/20 flex items-center justify-center text-emerald-600 dark:text-emerald-400 mx-auto mb-6">
                <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M4 4h16v16H4z" opacity="0" />
                  <polyline points="22 6 12 13 2 6" />
                  <path d="M2 6h20v12H2z" />
                </svg>
              </div>
              <h2 className="text-2xl font-semibold text-light-text dark:text-dark-text mb-4">
                Проверьте почту
              </h2>
              <p className="text-light-secondary dark:text-dark-secondary leading-relaxed mb-8">
                Если аккаунт с адресом <span className="font-medium text-light-text dark:text-dark-text">{email.trim()}</span> существует,
                мы отправили на него письмо со ссылкой для сброса пароля. Ссылка действительна один час.
              </p>
              <Link to="/login">
                <Button variant="secondary" size="md">
                  ← Вернуться ко входу
                </Button>
              </Link>
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
                  Восстановление пароля
                </h1>
                <p className="text-sm text-light-secondary dark:text-dark-secondary font-light">
                  Укажите email — мы отправим ссылку для сброса пароля
                </p>
              </div>

              <form onSubmit={handleSubmit} className="flex flex-col gap-5" noValidate>
                <Input
                  id="email"
                  label="Email"
                  type="email"
                  placeholder="example@lawfirm.ru"
                  value={email}
                  onChange={(e) => {
                    setEmail(e.target.value)
                    if (error) setError(null)
                  }}
                  error={error ?? undefined}
                  autoComplete="email"
                  autoFocus
                />

                <Button type="submit" variant="primary" size="lg" loading={loading} className="w-full mt-1">
                  Отправить ссылку
                </Button>
              </form>

              <div className="mt-6 pt-6 border-t border-light-border dark:border-dark-border text-center">
                <p className="text-sm text-light-secondary dark:text-dark-secondary">
                  Вспомнили пароль?{' '}
                  <Link to="/login" className="text-light-text dark:text-dark-text hover:underline font-medium">
                    Войти
                  </Link>
                </p>
              </div>
            </div>
          </motion.div>
        )}
      </main>
    </div>
  )
}
