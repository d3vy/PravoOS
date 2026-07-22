import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import i18n from '../../i18n'
import { Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { casesApi } from '../../api/cases'
import { clientsApi } from '../../api/clients'
import { organizationsApi } from '../../api/organizations'
import { useAuthStore } from '../../store/authStore'
import type { CaseResponse, CaseStatus, ClientResponse, Organization, OrganizationMember } from '../../types'
import { Button } from '../ui/Button'
import { Input } from '../ui/Input'
import { CaseStatusSelect } from './CaseStatusSelect'
import { DateField } from './DateField'
import { useConfirm } from '../../hooks/useConfirm'

export function CaseHeaderSection({ caseItem }: { caseItem: CaseResponse }): JSX.Element {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const confirm = useConfirm()
  const [isEditing, setIsEditing] = useState(false)
  const [title, setTitle] = useState(caseItem.title)
  const [description, setDescription] = useState(caseItem.description ?? '')
  const [clientId, setClientId] = useState(caseItem.clientId ?? '')
  const [filingDeadline, setFilingDeadline] = useState(caseItem.filingDeadline ?? '')
  const [nextHearingDate, setNextHearingDate] = useState(caseItem.nextHearingDate ?? '')
  const [expiresAt, setExpiresAt] = useState(caseItem.expiresAt ?? '')
  const [arbitrCaseNumber, setArbitrCaseNumber] = useState(caseItem.arbitrCaseNumber ?? '')
  const [transferTo, setTransferTo] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [exporting, setExporting] = useState<'docx' | 'pdf' | null>(null)
  const [exportError, setExportError] = useState<string | null>(null)

  const currentUserId = useAuthStore((state) => state.user?.userId)
  const isOwner = currentUserId === caseItem.ownerId

  const statusMutation = useMutation({
    mutationFn: (status: CaseStatus) => casesApi.updateStatus(caseItem.id, status),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['case', caseItem.id] })
      queryClient.invalidateQueries({ queryKey: ['cases'] })
    },
  })

  const handleExport = async (format: 'docx' | 'pdf'): Promise<void> => {
    setExporting(format)
    setExportError(null)
    try {
      const safeTitle = caseItem.title.replace(/[^\wА-Яа-яёЁ]+/g, '_').slice(0, 80)
      await casesApi.exportCase(caseItem.id, format, t('cases.exportFileName', { title: safeTitle }))
    } catch {
      setExportError(t('cases.exportError'))
    } finally {
      setExporting(null)
    }
  }

  const { data: clients = [] } = useQuery<ClientResponse[]>({
    queryKey: ['clients'],
    queryFn: clientsApi.getAll,
    enabled: isEditing,
  })

  const { data: organizations = [] } = useQuery<Organization[]>({
    queryKey: ['organizations'],
    queryFn: organizationsApi.list,
    enabled: isEditing && isOwner,
  })

  const { data: orgMembers = [] } = useQuery<OrganizationMember[]>({
    queryKey: ['org-members', caseItem.orgId],
    queryFn: () => organizationsApi.members(caseItem.orgId as string),
    enabled: isEditing && isOwner && Boolean(caseItem.orgId),
  })

  const changeOrgMutation = useMutation({
    mutationFn: (newOrgId: string | null) => casesApi.changeOrg(caseItem.id, newOrgId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['case', caseItem.id] })
      queryClient.invalidateQueries({ queryKey: ['cases'] })
      setError(null)
    },
    onError: () => setError(t('cases.changeOrgError')),
  })

  const transferMutation = useMutation({
    mutationFn: (newOwnerId: string) => casesApi.transferOwner(caseItem.id, newOwnerId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['case', caseItem.id] })
      queryClient.invalidateQueries({ queryKey: ['cases'] })
      setTransferTo('')
      setIsEditing(false)
      setError(null)
    },
    onError: () => setError(t('cases.transferError')),
  })

  const handleTransfer = async (): Promise<void> => {
    if (!transferTo) return
    const member = orgMembers.find((m) => m.userId === transferTo)
    const name = member?.fullName || member?.email || t('cases.selectedMemberFallback')
    const confirmed = await confirm({
      title: t('cases.transferOwner'),
      description: t('cases.transferConfirm', { name }),
      confirmLabel: t('cases.transfer'),
      danger: true,
    })
    if (!confirmed) return
    transferMutation.mutate(transferTo)
  }

  const updateMutation = useMutation({
    mutationFn: () =>
      casesApi.update(caseItem.id, {
        title: title.trim(),
        description: description.trim() || undefined,
        clientId: clientId || null,
        filingDeadline: filingDeadline || null,
        nextHearingDate: nextHearingDate || null,
        expiresAt: expiresAt || null,
        arbitrCaseNumber: arbitrCaseNumber.trim() || null,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['case', caseItem.id] })
      queryClient.invalidateQueries({ queryKey: ['cases'] })
      setIsEditing(false)
      setError(null)
    },
    onError: () => setError(t('cases.updateError')),
  })

  const handleSave = (): void => {
    if (!title.trim()) {
      setError(t('cases.titleRequired'))
      return
    }
    updateMutation.mutate()
  }

  const handleCancel = (): void => {
    setTitle(caseItem.title)
    setDescription(caseItem.description ?? '')
    setClientId(caseItem.clientId ?? '')
    setFilingDeadline(caseItem.filingDeadline ?? '')
    setNextHearingDate(caseItem.nextHearingDate ?? '')
    setExpiresAt(caseItem.expiresAt ?? '')
    setArbitrCaseNumber(caseItem.arbitrCaseNumber ?? '')
    setError(null)
    setIsEditing(false)
  }

  if (isEditing) {
    return (
      <section className="mb-6 p-6 rounded-xl bg-surface border border-line">
        <div className="flex flex-col gap-4">
          <Input label={t('cases.titleLabel')} value={title} onChange={(e) => setTitle(e.target.value)} maxLength={500} />
          <div>
            <label className="block text-sm font-medium text-fg mb-1.5">{t('common.description')}</label>
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              rows={3}
              maxLength={5000}
              className="w-full px-3 py-2.5 rounded-lg border border-line bg-bg text-fg text-sm focus:outline-none focus:ring-2 focus:ring-accent resize-none"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-fg mb-1.5">{t('cases.clientLabel')}</label>
            <select
              value={clientId}
              onChange={(e) => setClientId(e.target.value)}
              className="w-full px-3 py-2.5 rounded-lg border border-line bg-bg text-fg text-sm focus:outline-none focus:ring-2 focus:ring-accent"
            >
              <option value="">{t('cases.noClient')}</option>
              {clients.map((client) => (
                <option key={client.id} value={client.id}>
                  {client.name} ({client.typeName})
                </option>
              ))}
            </select>
          </div>
          <div className="grid gap-4 sm:grid-cols-3">
            <DateField label={t('cases.filingDeadline')} value={filingDeadline} onChange={setFilingDeadline} />
            <DateField label={t('cases.hearing')} value={nextHearingDate} onChange={setNextHearingDate} />
            <DateField label={t('cases.expiresAt')} value={expiresAt} onChange={setExpiresAt} />
          </div>
          <Input
            label={t('cases.arbitrNumberEditLabel')}
            value={arbitrCaseNumber}
            onChange={(e) => setArbitrCaseNumber(e.target.value)}
            maxLength={50}
            placeholder={t('cases.arbitrPlaceholder')}
          />
          {isOwner && (
            <div className="flex flex-col gap-4 pt-4 border-t border-line">
              <div>
                <label className="block text-sm font-medium text-fg mb-1.5">
                  {t('cases.orgLabel')}
                </label>
                <select
                  value={caseItem.orgId ?? ''}
                  disabled={changeOrgMutation.isPending}
                  onChange={(e) => changeOrgMutation.mutate(e.target.value || null)}
                  className="w-full px-3 py-2.5 rounded-lg border border-line bg-bg text-fg text-sm focus:outline-none focus:ring-2 focus:ring-accent disabled:opacity-60"
                >
                  <option value="">{t('cases.personalCase')}</option>
                  {organizations.map((org) => (
                    <option key={org.id} value={org.id}>
                      {org.name}
                    </option>
                  ))}
                </select>
                <p className="mt-1 text-xs text-fg-muted">
                  {t('cases.orgHintEdit')}
                </p>
              </div>
              {caseItem.orgId && (
                <div>
                  <label className="block text-sm font-medium text-fg mb-1.5">
                    {t('cases.transferOwner')}
                  </label>
                  <div className="flex gap-2">
                    <select
                      value={transferTo}
                      onChange={(e) => setTransferTo(e.target.value)}
                      className="flex-1 px-3 py-2.5 rounded-lg border border-line bg-bg text-fg text-sm focus:outline-none focus:ring-2 focus:ring-accent"
                    >
                      <option value="">{t('cases.selectMember')}</option>
                      {orgMembers
                        .filter((member) => member.userId !== caseItem.ownerId)
                        .map((member) => (
                          <option key={member.userId} value={member.userId}>
                            {member.fullName || member.email || member.userId}
                          </option>
                        ))}
                    </select>
                    <Button
                      variant="secondary"
                      disabled={!transferTo}
                      loading={transferMutation.isPending}
                      onClick={handleTransfer}
                    >
                      {t('cases.transfer')}
                    </Button>
                  </div>
                </div>
              )}
            </div>
          )}
          {error && <p className="text-sm text-danger">{error}</p>}
          <div className="flex gap-2">
            <Button variant="primary" loading={updateMutation.isPending} onClick={handleSave}>
              {t('common.save')}
            </Button>
            <Button variant="ghost" onClick={handleCancel}>
              {t('common.cancel')}
            </Button>
          </div>
        </div>
      </section>
    )
  }

  return (
    <div className="mb-6">
      <div className="flex items-start justify-between gap-4 mb-2">
        <h1 className="text-3xl font-semibold text-fg">{caseItem.title}</h1>
        <div className="flex flex-wrap items-center gap-2 shrink-0">
          <Button variant="secondary" size="sm" onClick={() => setIsEditing(true)}>
            {t('common.edit')}
          </Button>
          <Button
            variant="ghost"
            size="sm"
            loading={exporting === 'docx'}
            disabled={exporting !== null}
            onClick={() => void handleExport('docx')}
          >
            {t('cases.exportDocx')}
          </Button>
          <Button
            variant="ghost"
            size="sm"
            loading={exporting === 'pdf'}
            disabled={exporting !== null}
            onClick={() => void handleExport('pdf')}
          >
            {t('cases.exportPdf')}
          </Button>
        </div>
      </div>
      <div className="flex items-center gap-3 mb-2">
        <CaseStatusSelect
          value={caseItem.status}
          disabled={statusMutation.isPending}
          onChange={(status) => statusMutation.mutate(status)}
        />
        {caseItem.clientId && caseItem.clientName && (
          <Link
            to={`/clients/${caseItem.clientId}`}
            className="text-sm text-accent hover:underline"
          >
            {t('cases.clientPrefix', { name: caseItem.clientName })}
          </Link>
        )}
      </div>
      <DeadlineList caseItem={caseItem} />
      {caseItem.description && (
        <p className="text-sm text-fg-muted">{caseItem.description}</p>
      )}
      {exportError && <p className="text-sm text-danger mt-2">{exportError}</p>}
    </div>
  )
}

export function DeadlineList({ caseItem }: { caseItem: CaseResponse }): JSX.Element | null {
  const { t } = useTranslation()
  const deadlines = [
    { label: t('cases.filingDeadline'), value: caseItem.filingDeadline },
    { label: t('cases.hearing'), value: caseItem.nextHearingDate },
    { label: t('cases.expiresAt'), value: caseItem.expiresAt },
  ].filter((deadline) => deadline.value)

  if (deadlines.length === 0) {
    return null
  }

  return (
    <div className="flex flex-wrap gap-2 mb-2">
      {deadlines.map((deadline) => (
        <DeadlineBadge key={deadline.label} label={deadline.label} value={deadline.value as string} />
      ))}
    </div>
  )
}

function DeadlineBadge({ label, value }: { label: string; value: string }): JSX.Element {
  const { t } = useTranslation()
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const target = new Date(`${value}T00:00:00`)
  const daysLeft = Math.round((target.getTime() - today.getTime()) / (1000 * 60 * 60 * 24))

  const urgent = daysLeft >= 0 && daysLeft <= 3
  const overdue = daysLeft < 0
  const tone = overdue || urgent
    ? 'border-red-300 text-red-700 dark:border-red-500/40 dark:text-red-400'
    : 'border-line text-fg-muted'

  const formatted = target.toLocaleDateString(i18n.language.startsWith('ru') ? 'ru-RU' : 'en-US')
  const suffix = overdue
    ? t('cases.deadlineOverdue')
    : daysLeft === 0
      ? t('cases.deadlineToday')
      : urgent
        ? t('cases.deadlineDaysLeft', { count: daysLeft })
        : ''

  return (
    <span className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-md border text-xs ${tone}`}>
      <span className="font-medium">{label}:</span> {formatted}{suffix}
    </span>
  )
}
