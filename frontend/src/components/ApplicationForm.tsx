import { useState, type FormEvent } from 'react'
import { motion } from 'framer-motion'
import { Trans, useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { Button } from './ui/Button'
import { Input } from './ui/Input'
import { validatePassword } from '../utils/password'
import i18n from '../i18n'

export interface ApplicationFormData {
  fullName: string
  email: string
  password: string
  specialization: string
  phone: string
  personalDataConsent: boolean
  crossBorderConsent: boolean
  marketingConsent: boolean
}

type TextField = 'fullName' | 'email' | 'password' | 'specialization' | 'phone'

type ConsentField = 'personalDataConsent' | 'crossBorderConsent' | 'marketingConsent'

interface FormErrors {
  fullName?: string
  email?: string
  password?: string
  specialization?: string
  phone?: string
  personalDataConsent?: string
  crossBorderConsent?: string
}

interface ApplicationFormProps {
  initialValues: ApplicationFormData
  passwordRequired: boolean
  submitLabel: string
  passwordHint?: string
  consentsRequired?: boolean
  onSubmit: (data: ApplicationFormData) => Promise<void>
}

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
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

function validatePhone(phone: string): string | undefined {
  const digits = phone.replace(/\D/g, '')
  if (!digits) return i18n.t('applicationForm.phoneRequired')
  if (digits.length < 11) return i18n.t('applicationForm.phoneInvalid')
  return undefined
}

function validate(
  data: ApplicationFormData,
  passwordRequired: boolean,
  consentsRequired: boolean,
): FormErrors {
  const errors: FormErrors = {}
  if (!data.fullName.trim()) errors.fullName = i18n.t('applicationForm.nameRequired')
  if (!EMAIL_REGEX.test(data.email.trim())) errors.email = i18n.t('applicationForm.emailInvalid')
  if (passwordRequired || data.password.length > 0) {
    const passwordError = validatePassword(data.password)
    if (passwordError) errors.password = passwordError
  }
  if (!data.specialization.trim()) errors.specialization = i18n.t('applicationForm.specializationRequired')
  const phoneError = validatePhone(data.phone)
  if (phoneError) errors.phone = phoneError
  if (consentsRequired) {
    if (!data.personalDataConsent) {
      errors.personalDataConsent = i18n.t('applicationForm.consentPersonalDataRequired')
    }
    if (!data.crossBorderConsent) {
      errors.crossBorderConsent = i18n.t('applicationForm.consentCrossBorderRequired')
    }
  }
  return errors
}

function ConsentCheckbox({
  id,
  checked,
  onChange,
  error,
  children,
}: {
  id: string
  checked: boolean
  onChange: (checked: boolean) => void
  error?: string
  children: React.ReactNode
}): JSX.Element {
  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={id} className="flex items-start gap-3 cursor-pointer">
        <input
          id={id}
          type="checkbox"
          checked={checked}
          onChange={(e) => onChange(e.target.checked)}
          aria-invalid={error ? true : undefined}
          className={`mt-0.5 h-4 w-4 shrink-0 rounded border accent-accent ${
            error ? 'border-danger' : 'border-line'
          }`}
        />
        <span className="text-xs leading-relaxed text-fg-muted">{children}</span>
      </label>
      {error && <p className="text-xs text-danger pl-7">{error}</p>}
    </div>
  )
}

export function ApplicationForm({
  initialValues,
  passwordRequired,
  submitLabel,
  passwordHint,
  consentsRequired = false,
  onSubmit,
}: ApplicationFormProps): JSX.Element {
  const { t } = useTranslation()
  const [formData, setFormData] = useState<ApplicationFormData>(initialValues)
  const [errors, setErrors] = useState<FormErrors>({})
  const [loading, setLoading] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)

  const updateField =
    (field: TextField) => (e: React.ChangeEvent<HTMLInputElement>): void => {
      const value =
        field === 'phone' ? sanitizePhone(e.target.value) :
        e.target.value
      setFormData((prev) => ({ ...prev, [field]: value }))
      if (errors[field]) {
        setErrors((prev) => ({ ...prev, [field]: undefined }))
      }
    }

  const updateConsent =
    (field: ConsentField) =>
    (checked: boolean): void => {
      setFormData((prev) => ({ ...prev, [field]: checked }))
      setErrors((prev) => ({ ...prev, [field]: undefined }))
    }

  const handlePhoneBlur = (): void => {
    setErrors((prev) => ({ ...prev, phone: validatePhone(formData.phone) }))
  }

  const handleSubmit = async (e: FormEvent): Promise<void> => {
    e.preventDefault()
    const validationErrors = validate(formData, passwordRequired, consentsRequired)
    if (Object.keys(validationErrors).length > 0) {
      setErrors(validationErrors)
      return
    }

    setLoading(true)
    setSubmitError(null)
    try {
      await onSubmit(formData)
    } catch (error) {
      setSubmitError(error instanceof Error ? error.message : t('applicationForm.genericError'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-5" noValidate>
      <Input
        id="fullName"
        label={t('applicationForm.nameLabel')}
        type="text"
        placeholder={t('applicationForm.namePlaceholder')}
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
          label={t('applicationForm.phoneLabel')}
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
        label={t('applicationForm.passwordLabel')}
        type="password"
        placeholder={passwordRequired ? t('applicationForm.passwordPlaceholderRequired') : passwordHint ?? t('applicationForm.passwordPlaceholderOptional')}
        value={formData.password}
        onChange={updateField('password')}
        error={errors.password}
      />

      <Input
        id="specialization"
        label={t('applicationForm.specializationLabel')}
        type="text"
        placeholder={t('applicationForm.specializationPlaceholder')}
        value={formData.specialization}
        onChange={updateField('specialization')}
        error={errors.specialization}
      />

      {consentsRequired && (
        <div className="flex flex-col gap-3 rounded-xl border border-line bg-surface p-4">
          <ConsentCheckbox
            id="personalDataConsent"
            checked={formData.personalDataConsent}
            onChange={updateConsent('personalDataConsent')}
            error={errors.personalDataConsent}
          >
            <Trans
              i18nKey="applicationForm.consentPersonalData"
              components={[
                <Link key="consent" to="/legal/consent" target="_blank" className="text-accent hover:underline" />,
                <Link key="privacy" to="/legal/privacy" target="_blank" className="text-accent hover:underline" />,
              ]}
            />
          </ConsentCheckbox>

          <ConsentCheckbox
            id="crossBorderConsent"
            checked={formData.crossBorderConsent}
            onChange={updateConsent('crossBorderConsent')}
            error={errors.crossBorderConsent}
          >
            <Trans
              i18nKey="applicationForm.consentCrossBorder"
              components={[
                <Link key="crossBorder" to="/legal/cross-border" target="_blank" className="text-accent hover:underline" />,
              ]}
            />
          </ConsentCheckbox>

          <ConsentCheckbox
            id="marketingConsent"
            checked={formData.marketingConsent}
            onChange={updateConsent('marketingConsent')}
          >
            {t('applicationForm.consentMarketing')}
          </ConsentCheckbox>
        </div>
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
        {submitLabel}
      </Button>
    </form>
  )
}
