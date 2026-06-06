import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { motion } from 'framer-motion'
import axios from 'axios'
import { authApi } from '../api/auth'
import { Button } from '../components/ui/Button'
import { Input } from '../components/ui/Input'
import { Logo } from '../components/ui/Logo'
import { ThemeToggle } from '../components/ui/ThemeToggle'

type Status = 'verifying' | 'success' | 'invalid' | 'missing'

export default function VerifyEmailPage(): JSX.Element {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const token = useRef(searchParams.get('token') ?? '').current
  const verifyStarted = useRef(false)

  const [status, setStatus] = useState<Status>(token ? 'verifying' : 'missing')
  const [resendEmail, setResendEmail] = useState('')
  const [resendDone, setResendDone] = useState(false)
  const [resendLoading, setResendLoading] = useState(false)

  useEffect(() => {
    if (searchParams.has('token')) {
      window.history.replaceState(null, '', window.location.pathname)
    }
  }, [searchParams])

  useEffect(() => {
    if (!token || verifyStarted.current) return
    verifyStarted.current = true

    authApi
      .verifyEmail(token)
      .then(() => setStatus('success'))
      .catch(() => setStatus('invalid'))
  }, [token])

  const handleResend = async (e: FormEvent): Promise<void> => {
    e.preventDefault()
    setResendLoading(true)
    try {
      await authApi.resendVerification(resendEmail)
      setResendDone(true)
    } catch (err) {
      if (axios.isAxiosError(err)) {
        setResendDone(true)
      }
    } finally {
      setResendLoading(false)
    }
  }

  const card = (children: JSX.Element): JSX.Element => (
    <motion.div
      initial={{ opacity: 0, scale: 0.96 }}
      animate={{ opacity: 1, scale: 1 }}
      transition={{ duration: 0.4 }}
      className="w-full max-w-md text-center"
    >
      <div className="card-elevated rounded-2xl p-10">{children}</div>
    </motion.div>
  )

  const successIcon = (
    <div className="w-16 h-16 rounded-full bg-emerald-50 dark:bg-emerald-900/20 flex items-center justify-center text-emerald-600 dark:text-emerald-400 mx-auto mb-6">
      <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
        <polyline points="20 6 9 17 4 12" />
      </svg>
    </div>
  )

  const warningIcon = (
    <div className="w-16 h-16 rounded-full bg-amber-50 dark:bg-amber-900/20 flex items-center justify-center text-amber-600 dark:text-amber-400 mx-auto mb-6">
      <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
        <circle cx="12" cy="12" r="10" />
        <line x1="12" y1="8" x2="12" y2="12" />
        <line x1="12" y1="16" x2="12.01" y2="16" />
      </svg>
    </div>
  )

  const renderBody = (): JSX.Element => {
    if (status === 'verifying') {
      return card(
        <>
          <div className="w-10 h-10 mx-auto mb-6 border-2 border-light-border dark:border-dark-border border-t-light-text dark:border-t-dark-text rounded-full animate-spin" />
          <h2 className="text-2xl font-semibold text-light-text dark:text-dark-text mb-2">Подтверждаем почту…</h2>
          <p className="text-light-secondary dark:text-dark-secondary">Пожалуйста, подождите.</p>
        </>,
      )
    }

    if (status === 'success') {
      return card(
        <>
          {successIcon}
          <h2 className="text-2xl font-semibold text-light-text dark:text-dark-text mb-4">Почта подтверждена</h2>
          <p className="text-light-secondary dark:text-dark-secondary leading-relaxed mb-8">
            Ваша заявка отправлена на рассмотрение. Мы уведомим вас после одобрения.
          </p>
          <Button variant="primary" size="md" onClick={() => navigate('/login', { replace: true })}>
            Перейти ко входу
          </Button>
        </>,
      )
    }

    if (resendDone) {
      return card(
        <>
          {successIcon}
          <h2 className="text-2xl font-semibold text-light-text dark:text-dark-text mb-4">Письмо отправлено</h2>
          <p className="text-light-secondary dark:text-dark-secondary leading-relaxed mb-8">
            Если для этой почты есть незавершённая заявка, мы отправили новое письмо со ссылкой подтверждения.
          </p>
          <Link to="/login">
            <Button variant="primary" size="md">Перейти ко входу</Button>
          </Link>
        </>,
      )
    }

    return card(
      <>
        {warningIcon}
        <h2 className="text-2xl font-semibold text-light-text dark:text-dark-text mb-4">Ссылка недействительна</h2>
        <p className="text-light-secondary dark:text-dark-secondary leading-relaxed mb-8">
          {status === 'missing'
            ? 'Ссылка подтверждения неполная или повреждена.'
            : 'Срок действия ссылки истёк или она уже использована. Запросите новое письмо.'}
        </p>
        <form onSubmit={handleResend} className="flex flex-col gap-4 text-left" noValidate>
          <Input
            id="resendEmail"
            label="Ваша почта"
            type="email"
            placeholder="you@example.com"
            value={resendEmail}
            onChange={(e) => setResendEmail(e.target.value)}
            autoComplete="email"
          />
          <Button type="submit" variant="primary" size="lg" loading={resendLoading} className="w-full">
            Отправить письмо повторно
          </Button>
        </form>
      </>,
    )
  }

  return (
    <div className="min-h-screen bg-white/80 dark:bg-dark-bg/85 flex flex-col">
      <header className="flex items-center justify-between px-6 py-4 border-b border-light-border dark:border-dark-border bg-light-surface dark:bg-dark-surface">
        <Link to="/" className="hover:opacity-80 transition-opacity">
          <Logo />
        </Link>
        <ThemeToggle />
      </header>

      <main className="flex-1 flex items-center justify-center px-4 py-12">{renderBody()}</main>
    </div>
  )
}
