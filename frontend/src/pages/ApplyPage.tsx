import { useState } from 'react'
import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import axios from 'axios'
import { authApi } from '../api/auth'
import type { ApplicationSubmissionResponse } from '../types'
import { ApplicationForm, type ApplicationFormData } from '../components/ApplicationForm'
import { Button } from '../components/ui/Button'
import { Logo } from '../components/ui/Logo'
import { ThemeToggle } from '../components/ui/ThemeToggle'

const EMPTY_FORM: ApplicationFormData = {
  fullName: '',
  email: '',
  password: '',
  barNumber: '',
  specialization: '',
  phone: '',
}

function submissionErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const status = error.response?.status
    const message = error.response?.data?.message as string | undefined
    if (status === 409 && message?.includes('Pending application')) {
      return 'Заявка с этим email уже находится на рассмотрении.'
    }
    if (status === 409) {
      return (
        'Пользователь с таким email уже зарегистрирован. ' +
        'Попробуйте войти в аккаунт или восстановить пароль.'
      )
    }
    if (status === 400 && message) {
      return message
    }
  }
  return 'Произошла ошибка при отправке заявки. Попробуйте позже или свяжитесь с администратором.'
}

function ApplicationField({
  label,
  value,
  highlight = false,
}: {
  label: string
  value: string
  highlight?: boolean
}): JSX.Element {
  return (
    <div className="flex items-baseline justify-between gap-4">
      <dt className="text-light-secondary dark:text-dark-secondary shrink-0">{label}</dt>
      <dd
        className={`text-right break-all ${
          highlight
            ? 'font-semibold text-light-accent dark:text-dark-accent'
            : 'text-light-text dark:text-dark-text'
        }`}
      >
        {value}
      </dd>
    </div>
  )
}

export default function ApplyPage(): JSX.Element {
  const [submission, setSubmission] = useState<ApplicationSubmissionResponse | null>(null)

  const handleSubmit = async (data: ApplicationFormData): Promise<void> => {
    try {
      const result = await authApi.apply(data)
      setSubmission(result)
    } catch (error) {
      throw new Error(submissionErrorMessage(error))
    }
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
        {submission ? (
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
              <h2 className="text-2xl font-semibold text-light-text dark:text-dark-text mb-4">
                Заявка подана
              </h2>

              <div className="text-left rounded-xl border border-light-accent/30 dark:border-dark-accent/30 bg-light-accent/5 dark:bg-dark-accent/10 p-5 mb-6">
                <p className="font-semibold text-light-text dark:text-dark-text mb-1">
                  Подтвердите email
                </p>
                <p className="text-sm text-light-secondary dark:text-dark-secondary leading-relaxed">
                  Мы отправили письмо на {submission.application.email} — перейдите по ссылке в
                  нём, иначе мы не сможем выдать вам доступ.
                </p>
              </div>

              <p className="text-light-secondary dark:text-dark-secondary leading-relaxed mb-6">
                Администратор рассмотрит заявку и свяжется с вами. Обычно это занимает не более
                одного рабочего дня.
              </p>

              <div className="text-left rounded-xl border border-light-border dark:border-dark-border bg-light-surface dark:bg-dark-surface p-5 mb-4">
                <p className="text-xs font-medium uppercase tracking-wide text-light-secondary dark:text-dark-secondary mb-3">
                  Отправленные данные
                </p>
                <dl className="flex flex-col gap-2.5 text-sm">
                  <ApplicationField label="Полное имя" value={submission.application.fullName} />
                  <ApplicationField label="Email" value={submission.application.email} highlight />
                  <ApplicationField label="Телефон" value={submission.application.phone} />
                  <ApplicationField label="Номер адвоката" value={submission.application.barNumber} />
                  <ApplicationField label="Специализация" value={submission.application.specialization} />
                </dl>
              </div>

              <p className="text-xs text-light-secondary dark:text-dark-secondary mb-4">
                Если в данных ошибка, откройте заявку по ссылке ниже и нажмите «Редактировать».
              </p>

              <div className="text-left rounded-xl border border-light-border dark:border-dark-border bg-light-surface dark:bg-dark-surface p-4 mb-6">
                <p className="text-xs text-light-secondary dark:text-dark-secondary mb-2">
                  Сохраните ссылку, чтобы в любой момент проверить или изменить заявку:
                </p>
                <Link
                  to={`/application/${submission.statusToken}`}
                  className="text-sm font-medium text-light-accent dark:text-dark-accent hover:underline break-all"
                >
                  Открыть статус заявки →
                </Link>
              </div>

              <Link to="/">
                <Button variant="ghost" size="md" className="w-full">
                  ← Вернуться на главную
                </Button>
              </Link>
            </div>
          </motion.div>
        ) : (
          <motion.div
            initial={{ opacity: 0, y: 24 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.4, ease: 'easeOut' }}
            className="w-full max-w-lg"
          >
            <div className="card-elevated rounded-2xl p-8">
              <div className="mb-8">
                <h1 className="text-2xl font-bold text-light-text dark:text-dark-text mb-2 tracking-tight">
                  Заявка на доступ
                </h1>
                <p className="text-sm text-light-secondary dark:text-dark-secondary">
                  Заполните форму — администратор проверит данные и предоставит доступ к системе
                </p>
              </div>

              <ApplicationForm
                initialValues={EMPTY_FORM}
                passwordRequired
                submitLabel="Отправить заявку"
                onSubmit={handleSubmit}
              />

              <div className="mt-6 pt-6 border-t border-light-border dark:border-dark-border text-center">
                <p className="text-sm text-light-secondary dark:text-dark-secondary">
                  Уже есть доступ?{' '}
                  <Link
                    to="/login"
                    className="text-light-accent dark:text-dark-accent hover:underline font-medium"
                  >
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
