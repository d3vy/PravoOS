import { useState, type FormEvent } from 'react'
import { motion } from 'framer-motion'
import { Button } from './ui/Button'
import { Input } from './ui/Input'
import { validatePassword } from '../utils/password'

export interface ApplicationFormData {
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

interface ApplicationFormProps {
  initialValues: ApplicationFormData
  passwordRequired: boolean
  submitLabel: string
  passwordHint?: string
  onSubmit: (data: ApplicationFormData) => Promise<void>
}

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const BAR_NUMBER_REGEX = /^\d{1,3}\/\d{1,6}$/
const PHONE_MAX_DIGITS = 11

function sanitizePhone(value: string): string {
  const raw = value.replace(/\D/g, '')
  if (!raw) return ''

  let digits = raw[0] === '8' ? `7${raw.slice(1)}` : raw
  if (digits[0] !== '7') digits = `7${digits}`
  digits = digits.slice(0, PHONE_MAX_DIGITS)

  const area = digits.slice(1, 4)
  const part1 = digits.slice(4, 7)
  const part2 = digits.slice(7, 9)
  const part3 = digits.slice(9, 11)

  let formatted = '+7'
  if (area) formatted += ` (${area}`
  if (area.length === 3) formatted += ')'
  if (part1) formatted += ` ${part1}`
  if (part2) formatted += `-${part2}`
  if (part3) formatted += `-${part3}`
  return formatted
}

function sanitizeBarNumber(value: string): string {
  const cleaned = value.replace(/[^\d/]/g, '')
  if (cleaned.includes('/')) {
    const slashIdx = cleaned.indexOf('/')
    const prefix = cleaned.slice(0, slashIdx).slice(0, 3)
    const suffix = cleaned.slice(slashIdx + 1).replace(/\D/g, '').slice(0, 6)
    return `${prefix}/${suffix}`
  }
  if (cleaned.length > 2) {
    return `${cleaned.slice(0, 2)}/${cleaned.slice(2, 8)}`
  }
  return cleaned
}

function validatePhone(phone: string): string | undefined {
  const digits = phone.replace(/\D/g, '')
  if (!digits) return 'Укажите контактный телефон'
  if (digits.length < 11) return 'Введите корректный телефон'
  return undefined
}

function validateBarNumber(barNumber: string): string | undefined {
  const trimmed = barNumber.trim()
  if (!trimmed) return 'Укажите номер адвоката'
  if (!BAR_NUMBER_REGEX.test(trimmed)) return 'Некорректный номер адвоката (пример: 77/1234)'
  return undefined
}

function validate(data: ApplicationFormData, passwordRequired: boolean): FormErrors {
  const errors: FormErrors = {}
  if (!data.fullName.trim()) errors.fullName = 'Укажите полное имя'
  if (!EMAIL_REGEX.test(data.email.trim())) errors.email = 'Введите корректный email'
  if (passwordRequired || data.password.length > 0) {
    const passwordError = validatePassword(data.password)
    if (passwordError) errors.password = passwordError
  }
  const barNumberError = validateBarNumber(data.barNumber)
  if (barNumberError) errors.barNumber = barNumberError
  if (!data.specialization.trim()) errors.specialization = 'Укажите специализацию'
  const phoneError = validatePhone(data.phone)
  if (phoneError) errors.phone = phoneError
  return errors
}

export function ApplicationForm({
  initialValues,
  passwordRequired,
  submitLabel,
  passwordHint,
  onSubmit,
}: ApplicationFormProps): JSX.Element {
  const [formData, setFormData] = useState<ApplicationFormData>(initialValues)
  const [errors, setErrors] = useState<FormErrors>({})
  const [loading, setLoading] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)

  const updateField =
    (field: keyof ApplicationFormData) => (e: React.ChangeEvent<HTMLInputElement>): void => {
      const value =
        field === 'phone' ? sanitizePhone(e.target.value) :
        field === 'barNumber' ? sanitizeBarNumber(e.target.value) :
        e.target.value
      setFormData((prev) => ({ ...prev, [field]: value }))
      if (errors[field]) {
        setErrors((prev) => ({ ...prev, [field]: undefined }))
      }
    }

  const handlePhoneBlur = (): void => {
    setErrors((prev) => ({ ...prev, phone: validatePhone(formData.phone) }))
  }

  const handleBarNumberBlur = (): void => {
    setErrors((prev) => ({ ...prev, barNumber: validateBarNumber(formData.barNumber) }))
  }

  const handleSubmit = async (e: FormEvent): Promise<void> => {
    e.preventDefault()
    const validationErrors = validate(formData, passwordRequired)
    if (Object.keys(validationErrors).length > 0) {
      setErrors(validationErrors)
      return
    }

    setLoading(true)
    setSubmitError(null)
    try {
      await onSubmit(formData)
    } catch (error) {
      setSubmitError(error instanceof Error ? error.message : 'Произошла ошибка. Попробуйте позже.')
    } finally {
      setLoading(false)
    }
  }

  return (
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
          inputMode="tel"
          placeholder="+7 (999) 000-00-00"
          value={formData.phone}
          onChange={updateField('phone')}
          onBlur={handlePhoneBlur}
          error={errors.phone}
        />
      </div>

      <Input
        id="password"
        label="Пароль"
        type="password"
        placeholder={passwordRequired ? 'Не менее 8 символов, буква и цифра' : passwordHint ?? 'Оставьте пустым, чтобы не менять'}
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
          onBlur={handleBarNumberBlur}
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

      <Button type="submit" variant="primary" size="lg" loading={loading} className="w-full mt-1">
        {submitLabel}
      </Button>
    </form>
  )
}
