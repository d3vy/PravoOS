import { useState } from 'react'
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

export function CaseHeaderSection({ caseItem }: { caseItem: CaseResponse }): JSX.Element {
  const queryClient = useQueryClient()
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
      await casesApi.exportCase(caseItem.id, format, `Дело_${safeTitle}`)
    } catch {
      setExportError('Не удалось сформировать файл. Попробуйте снова.')
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
    onError: () => setError('Не удалось изменить организацию дела.'),
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
    onError: () => setError('Не удалось передать владельца дела.'),
  })

  const handleTransfer = (): void => {
    if (!transferTo) return
    const member = orgMembers.find((m) => m.userId === transferTo)
    const name = member?.fullName || member?.email || 'выбранного участника'
    if (!window.confirm(`Передать дело участнику «${name}»? Вы перестанете быть владельцем дела.`)) return
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
    onError: () => setError('Не удалось сохранить изменения. Попробуйте снова.'),
  })

  const handleSave = (): void => {
    if (!title.trim()) {
      setError('Укажите название дела')
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
      <section className="mb-6 p-6 rounded-xl bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border">
        <div className="flex flex-col gap-4">
          <Input label="Название дела" value={title} onChange={(e) => setTitle(e.target.value)} maxLength={500} />
          <div>
            <label className="block text-sm font-medium text-light-text dark:text-dark-text mb-1.5">Описание</label>
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              rows={3}
              maxLength={5000}
              className="w-full px-3 py-2.5 rounded-lg border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg text-light-text dark:text-dark-text text-sm focus:outline-none focus:ring-2 focus:ring-light-accent dark:focus:ring-dark-accent resize-none"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-light-text dark:text-dark-text mb-1.5">Клиент</label>
            <select
              value={clientId}
              onChange={(e) => setClientId(e.target.value)}
              className="w-full px-3 py-2.5 rounded-lg border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg text-light-text dark:text-dark-text text-sm focus:outline-none focus:ring-2 focus:ring-light-accent dark:focus:ring-dark-accent"
            >
              <option value="">Без клиента</option>
              {clients.map((client) => (
                <option key={client.id} value={client.id}>
                  {client.name} ({client.typeName})
                </option>
              ))}
            </select>
          </div>
          <div className="grid gap-4 sm:grid-cols-3">
            <DateField label="Срок подачи" value={filingDeadline} onChange={setFilingDeadline} />
            <DateField label="Заседание" value={nextHearingDate} onChange={setNextHearingDate} />
            <DateField label="Истечение срока" value={expiresAt} onChange={setExpiresAt} />
          </div>
          <Input
            label="Номер дела в КАД.Арбитр"
            value={arbitrCaseNumber}
            onChange={(e) => setArbitrCaseNumber(e.target.value)}
            maxLength={50}
            placeholder="А40-12345/2024"
          />
          {isOwner && (
            <div className="flex flex-col gap-4 pt-4 border-t border-light-border dark:border-dark-border">
              <div>
                <label className="block text-sm font-medium text-light-text dark:text-dark-text mb-1.5">
                  Организация
                </label>
                <select
                  value={caseItem.orgId ?? ''}
                  disabled={changeOrgMutation.isPending}
                  onChange={(e) => changeOrgMutation.mutate(e.target.value || null)}
                  className="w-full px-3 py-2.5 rounded-lg border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg text-light-text dark:text-dark-text text-sm focus:outline-none focus:ring-2 focus:ring-light-accent dark:focus:ring-dark-accent disabled:opacity-60"
                >
                  <option value="">Личное дело</option>
                  {organizations.map((org) => (
                    <option key={org.id} value={org.id}>
                      {org.name}
                    </option>
                  ))}
                </select>
                <p className="mt-1 text-xs text-light-secondary dark:text-dark-secondary">
                  Дело в организации видят и редактируют её участники. «Личное дело» — только вы.
                </p>
              </div>
              {caseItem.orgId && (
                <div>
                  <label className="block text-sm font-medium text-light-text dark:text-dark-text mb-1.5">
                    Передать владельца
                  </label>
                  <div className="flex gap-2">
                    <select
                      value={transferTo}
                      onChange={(e) => setTransferTo(e.target.value)}
                      className="flex-1 px-3 py-2.5 rounded-lg border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg text-light-text dark:text-dark-text text-sm focus:outline-none focus:ring-2 focus:ring-light-accent dark:focus:ring-dark-accent"
                    >
                      <option value="">Выберите участника</option>
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
                      Передать
                    </Button>
                  </div>
                </div>
              )}
            </div>
          )}
          {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}
          <div className="flex gap-2">
            <Button variant="primary" loading={updateMutation.isPending} onClick={handleSave}>
              Сохранить
            </Button>
            <Button variant="ghost" onClick={handleCancel}>
              Отмена
            </Button>
          </div>
        </div>
      </section>
    )
  }

  return (
    <div className="mb-6">
      <div className="flex items-start justify-between gap-4 mb-2">
        <h1 className="text-3xl font-semibold text-light-text dark:text-dark-text">{caseItem.title}</h1>
        <div className="flex flex-wrap items-center gap-2 shrink-0">
          <Button variant="secondary" size="sm" onClick={() => setIsEditing(true)}>
            Редактировать
          </Button>
          <Button
            variant="ghost"
            size="sm"
            loading={exporting === 'docx'}
            disabled={exporting !== null}
            onClick={() => void handleExport('docx')}
          >
            Экспорт .docx
          </Button>
          <Button
            variant="ghost"
            size="sm"
            loading={exporting === 'pdf'}
            disabled={exporting !== null}
            onClick={() => void handleExport('pdf')}
          >
            Экспорт .pdf
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
            className="text-sm text-light-accent dark:text-dark-accent hover:underline"
          >
            Клиент: {caseItem.clientName}
          </Link>
        )}
      </div>
      <DeadlineList caseItem={caseItem} />
      {caseItem.description && (
        <p className="text-sm text-light-secondary dark:text-dark-secondary">{caseItem.description}</p>
      )}
      {exportError && <p className="text-sm text-red-600 dark:text-red-400 mt-2">{exportError}</p>}
    </div>
  )
}

export function DeadlineList({ caseItem }: { caseItem: CaseResponse }): JSX.Element | null {
  const deadlines = [
    { label: 'Срок подачи', value: caseItem.filingDeadline },
    { label: 'Заседание', value: caseItem.nextHearingDate },
    { label: 'Истечение срока', value: caseItem.expiresAt },
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
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const target = new Date(`${value}T00:00:00`)
  const daysLeft = Math.round((target.getTime() - today.getTime()) / (1000 * 60 * 60 * 24))

  const urgent = daysLeft >= 0 && daysLeft <= 3
  const overdue = daysLeft < 0
  const tone = overdue || urgent
    ? 'border-red-300 text-red-700 dark:border-red-500/40 dark:text-red-400'
    : 'border-light-border text-light-secondary dark:border-dark-border dark:text-dark-secondary'

  const formatted = target.toLocaleDateString('ru-RU')
  const suffix = overdue ? ' (просрочено)' : daysLeft === 0 ? ' (сегодня)' : urgent ? ` (через ${daysLeft} дн.)` : ''

  return (
    <span className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-md border text-xs ${tone}`}>
      <span className="font-medium">{label}:</span> {formatted}{suffix}
    </span>
  )
}
