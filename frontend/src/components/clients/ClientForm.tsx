import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { clientsApi } from '../../api/clients'
import type { ClientType, ConflictHit, CreateClientRequest } from '../../types'
import { Button } from '../ui/Button'
import { Input } from '../ui/Input'

interface ClientFormProps {
  initial?: CreateClientRequest
  excludeClientId?: string
  submitLabel: string
  isSubmitting: boolean
  error?: string | null
  onSubmit: (data: CreateClientRequest) => void
}

export function ClientForm({
  initial,
  excludeClientId,
  submitLabel,
  isSubmitting,
  error,
  onSubmit,
}: ClientFormProps): JSX.Element {
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
  const [conflicts, setConflicts] = useState<ConflictHit[]>([])

  useEffect(() => {
    const trimmed = name.trim()
    if (trimmed.length < 3) {
      setConflicts([])
      return
    }
    let cancelled = false
    const timer = setTimeout(() => {
      clientsApi
        .checkConflicts(trimmed, excludeClientId)
        .then((hits) => {
          if (!cancelled) {
            setConflicts(hits)
          }
        })
        .catch(() => {
          if (!cancelled) {
            setConflicts([])
          }
        })
    }, 400)
    return () => {
      cancelled = true
      clearTimeout(timer)
    }
  }, [name, excludeClientId])

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

      {conflicts.length > 0 && (
        <div className="rounded-lg border border-warning/40 bg-warning/10 px-3 py-2.5 text-sm text-fg">
          <p className="font-medium mb-1.5">{t('clientForm.conflictWarningTitle')}</p>
          <ul className="flex flex-col gap-1 list-disc list-inside">
            {conflicts.map((hit, index) => (
              <li key={index}>
                {hit.source === 'CASE_PARTY'
                  ? t('clientForm.conflictCaseParty', {
                      name: hit.matchedName,
                      role: hit.role ?? '',
                      caseTitle: hit.caseTitle ?? '',
                    })
                  : t('clientForm.conflictClient', { name: hit.matchedName })}
              </li>
            ))}
          </ul>
        </div>
      )}

      <div className="flex flex-col gap-1.5">
        <label className="text-sm font-medium text-fg">{t('clientForm.typeLabel')}</label>
        <select
          value={type}
          onChange={(e) => setType(e.target.value as ClientType)}
          className="w-full px-3 py-2.5 rounded-lg border border-line bg-bg text-fg text-sm focus:outline-none focus:ring-2 focus:ring-accent"
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
        <label className="text-sm font-medium text-fg">
          {t('clientForm.notesLabel')} <span className="text-fg-muted font-normal">{t('clientForm.notesOptional')}</span>
        </label>
        <textarea
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          rows={3}
          maxLength={5000}
          placeholder={t('clientForm.notesPlaceholder')}
          className="w-full px-3 py-2.5 rounded-lg border border-line bg-bg text-fg text-sm placeholder:text-fg-muted/60 focus:outline-none focus:ring-2 focus:ring-accent resize-none"
        />
      </div>

      {(fieldError || error) && <p className="text-sm text-danger">{fieldError ?? error}</p>}

      <div>
        <Button type="submit" variant="primary" loading={isSubmitting}>
          {submitLabel}
        </Button>
      </div>
    </form>
  )
}
