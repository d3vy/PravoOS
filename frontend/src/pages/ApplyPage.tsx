import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import { authApi } from '../api/auth'
import { Button } from '../components/ui/Button'
import { Input } from '../components/ui/Input'
import { useTheme } from '../hooks/useTheme'

interface FormData {
  fullName: string
  email: string
  password: string
  barNumber: string
  specialization: string
  phone: string
}

interface FormErrors {
  fullName?: string
  email?: string
  password?: string
  barNumber?: string
  specialization?: string
  phone?: string
}

function validateForm(data: FormData): FormErrors {
  const errors: FormErrors = {}
  if (!data.fullName.trim()) errors.fullName = 'Укажите полное имя'
  if (!data.email.includes('@')) errors.email = 'Введите корректный email'
  if (data.password.length < 8) errors.password = 'Пароль — не менее 8 символов'
  if (!data.barNumber.trim()) errors.barNumber = 'Укажите номер адвоката'
  if (!data.specialization.trim()) errors.specialization = 'Укажите специализацию'
  if (!data.phone.trim()) errors.phone = 'Укажите контактный телефон'
  return errors
}

export default function ApplyPage(): JSX.Element {
  const [formData, setFormData] = useState<FormData>({
    fullName: '',
    email: '',
    password: '',
    barNumber: '',
    specialization: '',
    phone: '',
  })
  const [errors, setErrors] = useState<FormErrors>({})
  const [loading, setLoading] = useState(false)
  const [submitted, setSubmitted] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)

  const { theme, toggleTheme } = useTheme()

  const updateField = (field: keyof FormData) => (e: React.ChangeEvent<HTMLInputElement>): void => {
    setFormData((prev) => ({ ...prev, [field]: e.target.value }))
    if (errors[field]) {
      setErrors((prev) => ({ ...prev, [field]: undefined }))
    }
  }

  const handleSubmit = async (e: FormEvent): Promise<void> => {
    e.preventDefault()
    const validationErrors = validateForm(formData)
    if (Object.keys(validationErrors).length > 0) {
      setErrors(validationErrors)
      return
    }

    setLoading(true)
    setSubmitError(null)

    try {
      await authApi.apply(formData)
      setSubmitted(true)
    } catch {
      setSubmitError('Произошла ошибка при отправке заявки. Попробуйте позже или свяжитесь с администратором.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-light-bg dark:bg-dark-bg flex flex-col">
      <header className="flex items-center justify-between px-6 py-4 border-b border-light-border dark:border-dark-border bg-light-surface dark:bg-dark-surface">
        <Link
          to="/"
          className="flex items-center gap-2 font-semibold text-light-text dark:text-dark-text hover:opacity-80 transition-opacity"
        >
          <span>⚖️</span>
          <span>PravoOS</span>
        </Link>
        <button
          onClick={toggleTheme}
          aria-label={theme === 'dark' ? 'Светлая тема' : 'Тёмная тема'}
          className="w-9 h-9 flex items-center justify-center rounded-lg text-light-secondary dark:text-dark-secondary hover:bg-light-bg dark:hover:bg-dark-bg transition-colors"
        >
          {theme === 'dark' ? '☀' : '☾'}
        </button>
      </header>

      <main className="flex-1 flex items-center justify-center px-4 py-12">
        {submitted ? (
          <motion.div
            initial={{ opacity: 0, scale: 0.96 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ duration: 0.4 }}
            className="w-full max-w-md text-center"
          >
            <div className="bg-light-surface dark:bg-dark-surface rounded-2xl border border-light-border dark:border-dark-border p-10 shadow-sm">
              <div className="w-16 h-16 rounded-full bg-emerald-50 dark:bg-emerald-900/20 flex items-center justify-center text-3xl mx-auto mb-6">
                ✓
              </div>
              <h2 className="text-2xl font-bold text-light-text dark:text-dark-text mb-4">
                Заявка подана
              </h2>
              <p className="text-light-secondary dark:text-dark-secondary leading-relaxed mb-8">
                Администратор рассмотрит её и свяжется с вами. Обычно это занимает не более одного
                рабочего дня.
              </p>
              <Link to="/">
                <Button variant="secondary" size="md">
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
            <div className="bg-light-surface dark:bg-dark-surface rounded-2xl border border-light-border dark:border-dark-border p-8 shadow-sm">
              <div className="mb-8">
                <h1 className="text-2xl font-bold text-light-text dark:text-dark-text mb-2">
                  Заявка на доступ
                </h1>
                <p className="text-sm text-light-secondary dark:text-dark-secondary">
                  Заполните форму — администратор проверит данные и предоставит доступ к системе
                </p>
              </div>

              <form onSubmit={handleSubmit} className="flex flex-col gap-5" noValidate>
                <Input
                  id="fullName"
                  label="Полное имя"
                  type="text"
                  placeholder="Иванов Иван Иванович"
                  value={formData.fullName}
                  onChange={updateField('fullName')}
                  error={errors.fullName}
                  autoFocus
                />

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
                  <Input
                    id="email"
                    label="Email"
                    type="email"
                    placeholder="ivanov@lawfirm.ru"
                    value={formData.email}
                    onChange={updateField('email')}
                    error={errors.email}
                  />
                  <Input
                    id="phone"
                    label="Телефон"
                    type="tel"
                    placeholder="+7 999 000-00-00"
                    value={formData.phone}
                    onChange={updateField('phone')}
                    error={errors.phone}
                  />
                </div>

                <Input
                  id="password"
                  label="Пароль"
                  type="password"
                  placeholder="Не менее 8 символов"
                  value={formData.password}
                  onChange={updateField('password')}
                  error={errors.password}
                />

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
                  <Input
                    id="barNumber"
                    label="Номер адвоката"
                    type="text"
                    placeholder="77/1234"
                    value={formData.barNumber}
                    onChange={updateField('barNumber')}
                    error={errors.barNumber}
                  />
                  <Input
                    id="specialization"
                    label="Специализация"
                    type="text"
                    placeholder="Корпоративное право"
                    value={formData.specialization}
                    onChange={updateField('specialization')}
                    error={errors.specialization}
                  />
                </div>

                {submitError && (
                  <motion.div
                    initial={{ opacity: 0, y: -8 }}
                    animate={{ opacity: 1, y: 0 }}
                    className="p-3 rounded-lg bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800"
                  >
                    <p className="text-sm text-red-700 dark:text-red-400">{submitError}</p>
                  </motion.div>
                )}

                <Button
                  type="submit"
                  variant="primary"
                  size="lg"
                  loading={loading}
                  className="w-full mt-1"
                >
                  Отправить заявку
                </Button>
              </form>

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
