import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { ClientType, CreateClientRequest } from '../../types'
import { Button } from '../ui/Button'
import { Input } from '../ui/Input'

interface ClientFormProps {
  initial?: CreateClientRequest
  submitLabel: string
  isSubmitting: boolean
  error?: string | null
  onSubmit: (data: CreateClientRequest) => void
}

export function ClientForm({ initial, submitLabel, isSubmitting, error, onSubmit }: ClientFormProps): JSX.Element {
  const { t } = useTranslation()
  const clientTypes: { value: ClientType; label: string }[] = [
    { value: 'INDIVIDUAL', label: t('clientForm.typeIndividual') },
    { value: 'COMPANY', label: t('clientForm.typeCompany') },
  ]
  const [name, setName] = useState(initial?.name ?? '')
  const [type, setType] = useState<ClientType>(initial?.type ?? 'INDIVIDUAL')
  const [phone, setPhone] = useState(initial?.phone ?? '')
  const [email, setEmail] = useState(initial?.email ?? '')
  const [inn, setInn] = useState(initial?.inn ?? '')
  const [notes, setNotes] = useState(initial?.notes ?? '')
  const [fieldError, setFieldError] = useState<string | null>(null)

  const handleSubmit = (e: React.FormEvent): void => {
    e.preventDefault()
    if (!name.trim()) {
      setFieldError(t('clientForm.nameRequired'))
      return
    }
    if (inn.trim() && !/^(\d{10}|\d{12})$/.test(inn.trim())) {
      setFieldError(t('clientForm.innInvalid'))
      return
    }
    setFieldError(null)
    onSubmit({
      name: name.trim(),
      type,
      phone: phone.trim() || undefined,
      email: email.trim() || undefined,
      inn: inn.trim() || undefined,
      notes: notes.trim() || undefined,
    })
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <Input
        label={t('clientForm.nameLabel')}
        placeholder={t('clientForm.namePlaceholder')}
        value={name}
        onChange={(e) => setName(e.target.value)}
        maxLength={300}
      />

      <div className="flex flex-col gap-1.5">
        <label className="text-sm font-medium text-light-text dark:text-dark-text">{t('clientForm.typeLabel')}</label>
        <select
          value={type}
          onChange={(e) => setType(e.target.value as ClientType)}
          className="w-full px-3 py-2.5 rounded-lg border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg text-light-text dark:text-dark-text text-sm focus:outline-none focus:ring-2 focus:ring-light-accent dark:focus:ring-dark-accent"
        >
          {clientTypes.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <Input
          label={t('clientForm.phoneLabel')}
          placeholder="+7 (900) 000-00-00"
          value={phone}
          onChange={(e) => setPhone(e.target.value)}
          maxLength={20}
        />
        <Input
          label={t('clientForm.emailLabel')}
          type="email"
          placeholder="client@example.com"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          maxLength={255}
        />
      </div>

      <Input
        label={t('clientForm.innLabel')}
        placeholder={t('clientForm.innPlaceholder')}
        value={inn}
        onChange={(e) => setInn(e.target.value.replace(/\D/g, '').slice(0, 12))}
        maxLength={12}
      />

      <div className="flex flex-col gap-1.5">
        <label className="text-sm font-medium text-light-text dark:text-dark-text">
          {t('clientForm.notesLabel')} <span className="text-light-secondary dark:text-dark-secondary font-normal">{t('clientForm.notesOptional')}</span>
        </label>
        <textarea
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          rows={3}
          maxLength={5000}
          placeholder={t('clientForm.notesPlaceholder')}
          className="w-full px-3 py-2.5 rounded-lg border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg text-light-text dark:text-dark-text text-sm placeholder:text-light-secondary/60 dark:placeholder:text-dark-secondary/60 focus:outline-none focus:ring-2 focus:ring-light-accent dark:focus:ring-dark-accent resize-none"
        />
      </div>

      {(fieldError || error) && <p className="text-sm text-red-600 dark:text-red-400">{fieldError ?? error}</p>}

      <div>
        <Button type="submit" variant="primary" loading={isSubmitting}>
          {submitLabel}
        </Button>
      </div>
    </form>
  )
}
