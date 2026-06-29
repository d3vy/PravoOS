import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { motion } from 'framer-motion'
import axios from 'axios'
import { authApi } from '../api/auth'
import type { ApplicationFormData } from '../components/ApplicationForm'
import { ApplicationForm } from '../components/ApplicationForm'
import { Button } from '../components/ui/Button'
import { Logo } from '../components/ui/Logo'
import { ThemeToggle } from '../components/ui/ThemeToggle'

type LoadState = 'loading' | 'editable' | 'locked' | 'notFound'

function updateErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const status = error.response?.status
    const message = error.response?.data?.message as string | undefined
    if (message && (status === 400 || status === 404 || status === 409)) {
      return message
    }
  }
  return 'Не удалось сохранить изменения. Попробуйте позже.'
}

export default function EditApplicationPage(): JSX.Element {
  const { token } = useParams<{ token: string }>()
  const navigate = useNavigate()
  const [state, setState] = useState<LoadState>('loading')
  const [initialValues, setInitialValues] = useState<ApplicationFormData | null>(null)

  useEffect(() => {
    if (!token) {
      setState('notFound')
      return
    }
    let active = true
    authApi
      .getApplicationStatus(token)
      .then((data) => {
        if (!active) return
        if (data.status !== 'PENDING') {
          setState('locked')
          return
        }
        setInitialValues({
          fullName: data.fullName,
          email: data.email,
          password: '',
          specialization: data.specialization,
          phone: data.phone,
        })
        setState('editable')
      })
      .catch(() => {
        if (active) setState('notFound')
      })
    return () => {
      active = false
    }
  }, [token])

  const handleSubmit = async (data: ApplicationFormData): Promise<void> => {
    if (!token) return
    try {
      await authApi.updateApplication(token, {
        email: data.email.trim(),
        fullName: data.fullName,
        specialization: data.specialization,
        phone: data.phone,
        ...(data.password ? { password: data.password } : {}),
      })
      navigate(`/application/${token}`, { replace: true })
    } catch (error) {
      throw new Error(updateErrorMessage(error))
    }
  }

  const renderBody = (): JSX.Element => {
    if (state === 'loading') {
      return (
        <div className="card-elevated rounded-2xl p-10 text-center">
          <div className="w-10 h-10 mx-auto mb-6 border-2 border-light-border dark:border-dark-border border-t-light-text dark:border-t-dark-text rounded-full animate-spin" />
          <p className="text-light-secondary dark:text-dark-secondary">Загружаем заявку…</p>
        </div>
      )
    }

    if (state === 'notFound') {
      return (
        <div className="card-elevated rounded-2xl p-10 text-center">
          <h2 className="text-2xl font-semibold text-light-text dark:text-dark-text mb-4">Заявка не найдена</h2>
          <p className="text-light-secondary dark:text-dark-secondary leading-relaxed mb-8">
            Ссылка неполная или повреждена. Проверьте, что скопировали её целиком.
          </p>
          <Link to="/apply">
            <Button variant="primary" size="md">Подать заявку</Button>
          </Link>
        </div>
      )
    }

    if (state === 'locked') {
      return (
        <div className="card-elevated rounded-2xl p-10 text-center">
          <h2 className="text-2xl font-semibold text-light-text dark:text-dark-text mb-4">Редактирование недоступно</h2>
          <p className="text-light-secondary dark:text-dark-secondary leading-relaxed mb-8">
            Заявка уже рассмотрена, изменить её нельзя.
          </p>
          <Link to={`/application/${token}`}>
            <Button variant="secondary" size="md">К статусу заявки</Button>
          </Link>
        </div>
      )
    }

    return (
      <div className="card-elevated rounded-2xl p-8">
        <div className="mb-8">
          <h1 className="text-2xl font-bold text-light-text dark:text-dark-text mb-2 tracking-tight">
            Редактирование заявки
          </h1>
          <p className="text-sm text-light-secondary dark:text-dark-secondary">
            Исправьте данные и сохраните. При смене email мы заново отправим письмо для подтверждения почты.
          </p>
        </div>

        <ApplicationForm
          initialValues={initialValues!}
          passwordRequired={false}
          submitLabel="Сохранить изменения"
          passwordHint="Оставьте пустым, чтобы не менять пароль"
          onSubmit={handleSubmit}
        />

        <div className="mt-6 pt-6 border-t border-light-border dark:border-dark-border text-center">
          <Link
            to={`/application/${token}`}
            className="text-sm text-light-secondary dark:text-dark-secondary hover:underline"
          >
            ← Отмена
          </Link>
        </div>
      </div>
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

      <main className="flex-1 flex items-center justify-center px-4 py-12">
        <motion.div
          initial={{ opacity: 0, y: 24 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4, ease: 'easeOut' }}
          className="w-full max-w-lg"
        >
          {renderBody()}
        </motion.div>
      </main>
    </div>
  )
}
