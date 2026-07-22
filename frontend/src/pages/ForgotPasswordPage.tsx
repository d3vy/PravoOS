import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import { authApi } from '../api/auth'
import { Button } from '../components/ui/Button'
import { Input } from '../components/ui/Input'
import { Logo } from '../components/ui/Logo'
import { ThemeToggle } from '../components/ui/ThemeToggle'
import { LanguageSwitcher } from '../components/ui/LanguageSwitcher'

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export default function ForgotPasswordPage(): JSX.Element {
  const { t } = useTranslation()
  const [email, setEmail] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [submitted, setSubmitted] = useState(false)

  const handleSubmit = async (e: FormEvent): Promise<void> => {
    e.preventDefault()
    if (!EMAIL_REGEX.test(email.trim())) {
      setError(t('auth.invalidEmail'))
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
        {submitted ? (
          <motion.div
            initial={{ opacity: 0, scale: 0.96 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ duration: 0.4 }}
            className="w-full max-w-md text-center"
          >
            <div className="card-elevated rounded-2xl p-10">
              <div className="w-16 h-16 rounded-full bg-success-soft flex items-center justify-center text-success mx-auto mb-6">
                <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M4 4h16v16H4z" opacity="0" />
                  <polyline points="22 6 12 13 2 6" />
                  <path d="M2 6h20v12H2z" />
                </svg>
              </div>
              <h2 className="text-2xl font-semibold text-fg mb-4">
                {t('auth.checkEmail')}
              </h2>
              <p className="text-fg-muted leading-relaxed mb-8">
                {t('auth.resetLinkSent', { email: email.trim() })}
              </p>
              <Link to="/login">
                <Button variant="secondary" size="md">
                  {t('auth.backToLoginArrow')}
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
                <h1 className="font-sans text-2xl font-bold text-fg mb-2 tracking-tight">
                  {t('auth.forgotTitle')}
                </h1>
                <p className="text-sm text-fg-muted font-light">
                  {t('auth.forgotSubtitle')}
                </p>
              </div>

              <form onSubmit={handleSubmit} className="flex flex-col gap-5" noValidate>
                <Input
                  id="email"
                  label={t('common.email')}
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
                  {t('auth.sendLink')}
                </Button>
              </form>

              <div className="mt-6 pt-6 border-t border-line text-center">
                <p className="text-sm text-fg-muted">
                  {t('auth.rememberedPassword')}{' '}
                  <Link to="/login" className="text-fg hover:underline font-medium">
                    {t('auth.loginSubmit')}
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
