import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { invoicesApi } from '../../api/invoices'
import type { BillingProfileRequest } from '../../types'
import { Button } from '../ui/Button'
import { Input } from '../ui/Input'

const EMPTY_PROFILE: BillingProfileRequest = {
  name: '',
  inn: '',
  kpp: '',
  ogrn: '',
  legalAddress: '',
  bankName: '',
  bankBic: '',
  bankAccount: '',
  corrAccount: '',
  email: '',
  phone: '',
}

const FIELDS: Array<{ key: keyof BillingProfileRequest; labelKey: string }> = [
  { key: 'inn', labelKey: 'invoices.supplierInn' },
  { key: 'kpp', labelKey: 'invoices.supplierKpp' },
  { key: 'ogrn', labelKey: 'invoices.supplierOgrn' },
  { key: 'legalAddress', labelKey: 'invoices.supplierAddress' },
  { key: 'bankName', labelKey: 'invoices.supplierBank' },
  { key: 'bankBic', labelKey: 'invoices.supplierBic' },
  { key: 'bankAccount', labelKey: 'invoices.supplierAccount' },
  { key: 'corrAccount', labelKey: 'invoices.supplierCorrAccount' },
  { key: 'email', labelKey: 'invoices.supplierEmail' },
  { key: 'phone', labelKey: 'invoices.supplierPhone' },
]

export function BillingProfileCard(): JSX.Element {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [expanded, setExpanded] = useState(false)
  const [form, setForm] = useState<BillingProfileRequest>(EMPTY_PROFILE)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  const { data: profile } = useQuery({
    queryKey: ['billingProfile'],
    queryFn: invoicesApi.getBillingProfile,
  })

  useEffect(() => {
    if (profile) {
      setForm({ ...EMPTY_PROFILE, ...profile })
    }
  }, [profile])

  const saveMutation = useMutation({
    mutationFn: () => invoicesApi.saveBillingProfile(form),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['billingProfile'] })
      setError(null)
      setSaved(true)
    },
    onError: () => setError(t('invoices.supplierSaveError')),
  })

  const handleSave = (): void => {
    if (!form.name.trim()) {
      setError(t('invoices.supplierNameRequired'))
      return
    }
    setSaved(false)
    saveMutation.mutate()
  }

  const setField = (key: keyof BillingProfileRequest, value: string): void => {
    setForm((prev) => ({ ...prev, [key]: value }))
    setSaved(false)
  }

  return (
    <section className="mb-6 rounded-xl border border-line bg-surface p-4">
      <button
        type="button"
        aria-expanded={expanded}
        onClick={() => setExpanded((prev) => !prev)}
        className="flex w-full items-center justify-between gap-3 text-left"
      >
        <span>
          <span className="block text-sm font-semibold text-fg">{t('invoices.supplierTitle')}</span>
          <span className="block text-xs text-fg-muted">
            {profile?.name ? profile.name : t('invoices.supplierEmptyHint')}
          </span>
        </span>
        <span className="text-xs text-fg-muted">{expanded ? '−' : '+'}</span>
      </button>

      {expanded && (
        <div className="mt-4 flex flex-col gap-3">
          <Input
            label={t('invoices.supplierName')}
            value={form.name}
            onChange={(e) => setField('name', e.target.value)}
            maxLength={500}
          />
          <div className="grid gap-3 sm:grid-cols-2">
            {FIELDS.map((field) => (
              <Input
                key={field.key}
                label={t(field.labelKey)}
                value={(form[field.key] as string | null) ?? ''}
                onChange={(e) => setField(field.key, e.target.value)}
              />
            ))}
          </div>
          {error && <p className="text-sm text-danger">{error}</p>}
          {saved && <p className="text-sm text-fg-muted">{t('invoices.supplierSaved')}</p>}
          <div>
            <Button variant="primary" loading={saveMutation.isPending} onClick={handleSave}>
              {t('common.save')}
            </Button>
          </div>
        </div>
      )}
    </section>
  )
}
