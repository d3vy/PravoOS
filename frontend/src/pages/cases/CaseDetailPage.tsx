import { useState, useRef, useEffect } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { motion, AnimatePresence } from 'framer-motion'
import { casesApi } from '../../api/cases'
import { clientsApi } from '../../api/clients'
import { organizationsApi } from '../../api/organizations'
import { useAuthStore } from '../../store/authStore'
import { templatesApi } from '../../api/templates'
import { workflowsApi } from '../../api/workflows'
import { contractReviewsApi } from '../../api/contractReviews'
import { documentComparisonsApi } from '../../api/documentComparisons'
import { citationsApi } from '../../api/citations'
import type { AiResponseDto, CaseDraftSummaryDto, CaseHearingEvent, CaseResponse, ClientResponse, ContractReviewDto, ContractRiskLevel, DiffChange, DiffChangeType, DocumentComparisonDto, DocumentResponse, DraftTypeInfo, Organization, OrganizationMember, WorkflowInfo } from '../../types'
import { CitationList, citationSummary } from '../../components/ui/CitationList'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'
import { Spinner } from '../../components/ui/Spinner'
import { DocumentStatusBadge } from '../../components/ui/Badge'
import { CaseStatusSelect } from '../../components/cases/CaseStatusSelect'
import { DateField } from '../../components/cases/DateField'
import { CaseTasksSection } from '../../components/cases/CaseTasksSection'
import { WorkflowProcessSection } from '../../components/cases/WorkflowProcessSection'
import { CaseAnalyticsSection } from '../../components/cases/CaseAnalyticsSection'
import { CaseMessageThread } from '../../components/messages/CaseMessageThread'
import { RatingButtons } from '../../components/ui/RatingButtons'
import type { CaseStatus } from '../../types'

const WORKFLOW_TABS = [
  { id: 'analysis', label: 'Анализ', ids: ['DEBTOR_SOLVENCY_ANALYSIS', 'CHALLENGE_TRANSACTIONS', 'CREDITOR_CLAIMS', 'SUBSIDIARY_LIABILITY', 'BANKRUPTCY_ESTATE'] },
  { id: 'documents', label: 'Документы', ids: ['DOCUMENT_CHECKLIST', 'DATA_EXTRACTION'] },
  { id: 'summary', label: 'Итоги', ids: ['CASE_SUMMARY', 'RISK_MAP', 'CHRONOLOGY'] },
] as const

const ALLOWED_EXTENSIONS = ['.pdf', '.docx']
const POLLING_INTERVAL_MS = 5000

export default function CaseDetailPage(): JSX.Element {
  const { caseId = '' } = useParams()
  const { hash } = useLocation()
  const queryClient = useQueryClient()

  useEffect(() => {
    if (caseId === '') return
    void casesApi.markMessagesRead(caseId).then(() => {
      queryClient.invalidateQueries({ queryKey: ['messageThreads'] })
    })
  }, [caseId, queryClient])

  const { data: caseItem, isLoading: caseLoading } = useQuery<CaseResponse>({
    queryKey: ['case', caseId],
    queryFn: () => casesApi.get(caseId),
    enabled: caseId !== '',
  })

  const { data: documents = [] } = useQuery<DocumentResponse[]>({
    queryKey: ['case-documents', caseId],
    queryFn: () => casesApi.getDocuments(caseId),
    enabled: caseId !== '',
    refetchInterval: (query) =>
      query.state.data?.some((d) => d.status === 'PROCESSING') ? POLLING_INTERVAL_MS : false,
  })

  const { data: workflows = [] } = useQuery<WorkflowInfo[]>({
    queryKey: ['workflows'],
    queryFn: workflowsApi.getAll,
  })

  const { data: drafts = [] } = useQuery<CaseDraftSummaryDto[]>({
    queryKey: ['case-drafts', caseId],
    queryFn: () => casesApi.getDrafts(caseId),
    enabled: caseId !== '',
  })

  const { data: draftTypes = [] } = useQuery<DraftTypeInfo[]>({
    queryKey: ['draft-types'],
    queryFn: casesApi.getDraftTypes,
  })

  const { data: responses = [] } = useQuery<AiResponseDto[]>({
    queryKey: ['case-responses', caseId],
    queryFn: () => casesApi.getResponses(caseId),
    enabled: caseId !== '',
  })

  const { data: contractReviews = [] } = useQuery<ContractReviewDto[]>({
    queryKey: ['case-contract-reviews', caseId],
    queryFn: () => contractReviewsApi.listByCase(caseId),
    enabled: caseId !== '',
  })

  const { data: comparisons = [] } = useQuery<DocumentComparisonDto[]>({
    queryKey: ['case-comparisons', caseId],
    queryFn: () => documentComparisonsApi.listByCase(caseId),
    enabled: caseId !== '',
  })

  useEffect(() => {
    if (caseLoading || hash !== '#messages') return
    document.getElementById('messages')?.scrollIntoView({ behavior: 'smooth' })
  }, [caseLoading, hash])

  if (caseLoading) {
    return (
      <div className="bg-light-bg dark:bg-dark-bg">
        <div className="flex justify-center py-24">
          <Spinner size="lg" />
        </div>
      </div>
    )
  }

  if (!caseItem) {
    return (
      <div className="bg-light-bg dark:bg-dark-bg">
        <div className="page-container py-16 text-center">
          <p className="text-light-secondary dark:text-dark-secondary mb-4">Дело не найдено</p>
          <Link to="/cases" className="text-light-accent dark:text-dark-accent text-sm">
            ← Ко всем делам
          </Link>
        </div>
      </div>
    )
  }

  return (
    <div className="bg-light-bg dark:bg-dark-bg">
      <div className="page-container py-8 max-w-4xl">
        <Link to="/cases" className="text-sm text-light-secondary dark:text-dark-secondary hover:text-light-accent dark:hover:text-dark-accent mb-4 inline-block">
          ← Ко всем делам
        </Link>

        <CaseHeaderSection caseItem={caseItem} queryClient={queryClient} />

        <DocumentsSection caseId={caseId} documents={documents} queryClient={queryClient} />

        <CaseTasksSection caseId={caseId} />

        <div id="messages" className="mb-10 scroll-mt-20">
          <CaseMessageThread
            queryKey={['case', caseId, 'messages']}
            viewerRole="LAWYER"
            listMessages={() => casesApi.listMessages(caseId)}
            sendMessage={(body) => casesApi.sendMessage(caseId, body)}
          />
        </div>

        <ArbitrSection caseItem={caseItem} queryClient={queryClient} />

        <CaseAnalyticsSection caseId={caseId} />

        <WorkflowSection caseId={caseId} workflows={workflows} queryClient={queryClient} />

        <WorkflowProcessSection caseId={caseId} />

        <ContractReviewSection caseId={caseId} documents={documents} reviews={contractReviews} queryClient={queryClient} />

        <ComparisonSection caseId={caseId} documents={documents} comparisons={comparisons} queryClient={queryClient} />

        <DraftSection caseId={caseId} draftTypes={draftTypes} drafts={drafts} queryClient={queryClient} />

        <ResponsesSection caseId={caseId} responses={responses} queryClient={queryClient} />
      </div>
    </div>
  )
}

interface SectionProps {
  caseId: string
  queryClient: ReturnType<typeof useQueryClient>
}

function CaseHeaderSection({ caseItem, queryClient }: { caseItem: CaseResponse; queryClient: ReturnType<typeof useQueryClient> }): JSX.Element {
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
  })

  const { data: organizations = [] } = useQuery<Organization[]>({
    queryKey: ['organizations'],
    queryFn: organizationsApi.list,
    enabled: isOwner,
  })

  const { data: orgMembers = [] } = useQuery<OrganizationMember[]>({
    queryKey: ['org-members', caseItem.orgId],
    queryFn: () => organizationsApi.members(caseItem.orgId as string),
    enabled: isOwner && Boolean(caseItem.orgId),
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
      <section className="mb-8 p-6 rounded-xl bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border">
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
    <div className="mb-8">
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

function DeadlineList({ caseItem }: { caseItem: CaseResponse }): JSX.Element | null {
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

function ArbitrSection({ caseItem, queryClient }: { caseItem: CaseResponse; queryClient: ReturnType<typeof useQueryClient> }): JSX.Element {
  const hasNumber = Boolean(caseItem.arbitrCaseNumber)

  const { data: hearings = [], isLoading } = useQuery<CaseHearingEvent[]>({
    queryKey: ['case-hearings', caseItem.id],
    queryFn: () => casesApi.getHearings(caseItem.id),
    enabled: hasNumber,
  })

  const syncMutation = useMutation({
    mutationFn: () => casesApi.syncArbitr(caseItem.id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['case-hearings', caseItem.id] })
      queryClient.invalidateQueries({ queryKey: ['case', caseItem.id] })
    },
  })

  return (
    <section className="mb-10">
      <div className="flex items-center justify-between mb-3">
        <h2 className="text-sm font-semibold text-light-text dark:text-dark-text">
          События по делу (КАД.Арбитр)
        </h2>
        {hasNumber && (
          <Button
            variant="ghost"
            size="sm"
            loading={syncMutation.isPending}
            onClick={() => syncMutation.mutate()}
          >
            Обновить из КАД
          </Button>
        )}
      </div>

      {!hasNumber ? (
        <p className="text-sm text-light-secondary dark:text-dark-secondary">
          Укажите номер дела в КАД.Арбитр в карточке дела, чтобы отслеживать заседания и события.
        </p>
      ) : (
        <>
          <div className="flex flex-wrap items-center gap-3 mb-3 text-sm">
            <span className="text-light-secondary dark:text-dark-secondary">
              Номер: <span className="text-light-text dark:text-dark-text font-medium">{caseItem.arbitrCaseNumber}</span>
            </span>
            {caseItem.arbitrCardUrl && (
              <a
                href={caseItem.arbitrCardUrl}
                target="_blank"
                rel="noreferrer"
                className="text-light-accent dark:text-dark-accent hover:underline"
              >
                Открыть на kad.arbitr.ru ↗
              </a>
            )}
          </div>

          {syncMutation.isError && (
            <p className="text-sm text-red-600 dark:text-red-400 mb-3">
              Не удалось обновить данные из КАД. Попробуйте позже.
            </p>
          )}

          {isLoading ? (
            <Spinner size="md" />
          ) : hearings.length === 0 ? (
            <p className="text-sm text-light-secondary dark:text-dark-secondary">
              Событий пока нет. Нажмите «Обновить из КАД».
            </p>
          ) : (
            <div className="flex flex-col gap-2">
              {hearings.map((event) => (
                <div
                  key={event.id}
                  className="p-3 rounded-lg bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border"
                >
                  <div className="flex items-center justify-between gap-3 mb-0.5">
                    <span className="text-sm font-medium text-light-text dark:text-dark-text">
                      {event.eventType ?? 'Событие'}
                    </span>
                    <span className="text-xs text-light-secondary dark:text-dark-secondary shrink-0">
                      {event.eventDate ? new Date(event.eventDate).toLocaleDateString('ru-RU') : ''}
                    </span>
                  </div>
                  {event.description && (
                    <p className="text-xs text-light-secondary dark:text-dark-secondary">{event.description}</p>
                  )}
                  {event.courtName && (
                    <p className="text-xs text-light-secondary dark:text-dark-secondary mt-0.5">{event.courtName}</p>
                  )}
                </div>
              ))}
            </div>
          )}
        </>
      )}
    </section>
  )
}

function DocumentsSection({ caseId, documents, queryClient }: SectionProps & { documents: DocumentResponse[] }): JSX.Element {
  const [isUploading, setIsUploading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [togglingId, setTogglingId] = useState<string | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const toggleVisibility = async (doc: DocumentResponse): Promise<void> => {
    setError(null)
    setTogglingId(doc.id)
    try {
      await casesApi.setDocumentVisibility(caseId, doc.id, !doc.visibleToClient)
      queryClient.invalidateQueries({ queryKey: ['case-documents', caseId] })
    } catch {
      setError('Не удалось изменить видимость документа.')
    } finally {
      setTogglingId(null)
    }
  }

  const handleFiles = async (files: FileList | null): Promise<void> => {
    if (!files || files.length === 0) return
    setError(null)
    const valid = Array.from(files).filter((f) =>
      ALLOWED_EXTENSIONS.some((ext) => f.name.toLowerCase().endsWith(ext))
    )
    if (valid.length === 0) {
      setError('Поддерживаются только PDF и DOCX')
      return
    }
    setIsUploading(true)
    try {
      for (const file of valid) {
        await casesApi.uploadDocument(caseId, file, file.name.replace(/\.[^.]+$/, ''))
      }
      queryClient.invalidateQueries({ queryKey: ['case-documents', caseId] })
    } catch {
      setError('Ошибка загрузки. Проверьте формат и размер файла.')
    } finally {
      setIsUploading(false)
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
  }

  return (
    <section className="mb-10">
      <h2 className="text-sm font-semibold text-light-text dark:text-dark-text mb-3">
        Документы дела {documents.length > 0 && <span className="font-normal text-light-secondary dark:text-dark-secondary">({documents.length})</span>}
      </h2>

      <div
        onClick={() => fileInputRef.current?.click()}
        className="mb-3 rounded-xl border-2 border-dashed border-light-border dark:border-dark-border hover:border-light-accent/50 dark:hover:border-dark-accent/50 cursor-pointer flex flex-col items-center justify-center gap-2 py-8 px-6 text-center transition-colors"
        role="button"
        tabIndex={0}
      >
        <input
          ref={fileInputRef}
          type="file"
          accept=".pdf,.docx"
          multiple
          className="sr-only"
          onChange={(e) => void handleFiles(e.target.files)}
        />
        {isUploading ? (
          <Spinner size="md" />
        ) : (
          <p className="text-sm text-light-secondary dark:text-dark-secondary">
            Загрузить документы дела (PDF, DOCX)
          </p>
        )}
      </div>

      {error && <p className="text-sm text-red-600 dark:text-red-400 mb-3">{error}</p>}

      {documents.length > 0 && (
        <div className="flex flex-col gap-2">
          {documents.map((doc) => (
            <div
              key={doc.id}
              className="flex items-center gap-3 p-3 rounded-lg bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border"
            >
              <span className="text-xs font-bold uppercase text-light-secondary dark:text-dark-secondary w-9 shrink-0">
                {doc.fileName.split('.').pop()}
              </span>
              <p className="flex-1 min-w-0 text-sm text-light-text dark:text-dark-text truncate">{doc.title}</p>
              <button
                type="button"
                onClick={() => void toggleVisibility(doc)}
                disabled={togglingId === doc.id}
                title={doc.visibleToClient ? 'Виден клиенту в портале' : 'Скрыт от клиента'}
                className={`text-xs px-2 py-1 rounded-md border transition-colors disabled:opacity-60 ${
                  doc.visibleToClient
                    ? 'border-emerald-500/40 text-emerald-600 dark:text-emerald-400 bg-emerald-500/10'
                    : 'border-light-border dark:border-dark-border text-light-secondary dark:text-dark-secondary'
                }`}
              >
                {doc.visibleToClient ? 'Виден клиенту' : 'Скрыт'}
              </button>
              <DocumentStatusBadge status={doc.status} />
            </div>
          ))}
        </div>
      )}
    </section>
  )
}

function WorkflowSection({ caseId, workflows, queryClient }: SectionProps & { workflows: WorkflowInfo[] }): JSX.Element {
  const [activeTab, setActiveTab] = useState<string>('analysis')
  const [selectedId, setSelectedId] = useState('')
  const [question, setQuestion] = useState('')

  const runMutation = useMutation({
    mutationFn: () => casesApi.runWorkflow(caseId, selectedId, question.trim() || undefined),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['case-responses', caseId] })
      setQuestion('')
    },
  })

  const currentTab = WORKFLOW_TABS.find((t) => t.id === activeTab)
  const visibleWorkflows = workflows.filter((w) => currentTab?.ids.includes(w.id as never))
  const selectedWorkflow = visibleWorkflows.find((w) => w.id === selectedId)

  const handleTabChange = (tabId: string): void => {
    setActiveTab(tabId)
    setSelectedId('')
  }

  return (
    <section className="mb-10 p-5 rounded-xl bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border">
      <h2 className="text-sm font-semibold text-light-text dark:text-dark-text mb-3">AI-анализ дела</h2>

      <div className="flex gap-1 mb-4 p-1 rounded-lg bg-light-bg dark:bg-dark-bg">
        {WORKFLOW_TABS.map((tab) => (
          <button
            key={tab.id}
            type="button"
            onClick={() => handleTabChange(tab.id)}
            className={`flex-1 px-3 py-1.5 rounded-md text-xs font-medium transition-colors ${
              activeTab === tab.id
                ? 'bg-light-surface dark:bg-dark-surface text-light-text dark:text-dark-text shadow-sm'
                : 'text-light-secondary dark:text-dark-secondary hover:text-light-text dark:hover:text-dark-text'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      <div className="flex flex-col gap-3">
        <div className="grid gap-2 sm:grid-cols-2">
          {visibleWorkflows.map((workflow) => (
            <button
              key={workflow.id}
              type="button"
              onClick={() => setSelectedId(workflow.id)}
              className={`text-left p-3 rounded-lg border text-sm transition-colors ${
                selectedId === workflow.id
                  ? 'border-light-accent dark:border-dark-accent bg-light-accent/5 dark:bg-dark-accent/10 text-light-text dark:text-dark-text'
                  : 'border-light-border dark:border-dark-border text-light-secondary dark:text-dark-secondary hover:border-light-accent/50 dark:hover:border-dark-accent/50'
              }`}
            >
              {workflow.displayName}
            </button>
          ))}
        </div>

        {selectedWorkflow && (
          <p className="text-xs text-light-secondary dark:text-dark-secondary">{selectedWorkflow.instruction}</p>
        )}

        <textarea
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          rows={2}
          maxLength={2000}
          placeholder="Дополнительный вопрос (опционально)"
          className="w-full px-3 py-2.5 rounded-lg border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg text-light-text dark:text-dark-text text-sm placeholder:text-light-secondary/60 dark:placeholder:text-dark-secondary/60 focus:outline-none focus:ring-2 focus:ring-light-accent dark:focus:ring-dark-accent resize-none"
        />

        {runMutation.isError && (
          <p className="text-sm text-red-600 dark:text-red-400">Ошибка запуска анализа. Попробуйте снова.</p>
        )}

        <div>
          <Button
            variant="primary"
            disabled={!selectedId}
            loading={runMutation.isPending}
            onClick={() => runMutation.mutate()}
          >
            Запустить анализ
          </Button>
        </div>
      </div>
    </section>
  )
}

const RISK_LEVEL_META: Record<ContractRiskLevel, { label: string; tone: string }> = {
  HIGH: { label: 'Высокий', tone: 'border-red-300 text-red-700 bg-red-50 dark:border-red-500/40 dark:text-red-400 dark:bg-red-500/10' },
  MEDIUM: { label: 'Средний', tone: 'border-amber-300 text-amber-700 bg-amber-50 dark:border-amber-500/40 dark:text-amber-400 dark:bg-amber-500/10' },
  LOW: { label: 'Низкий', tone: 'border-light-border text-light-secondary bg-light-bg dark:border-dark-border dark:text-dark-secondary dark:bg-dark-bg' },
}

function riskScoreTone(score: number): string {
  if (score >= 66) return 'text-red-600 dark:text-red-400'
  if (score >= 33) return 'text-amber-600 dark:text-amber-400'
  return 'text-emerald-600 dark:text-emerald-400'
}

function ContractReviewSection({ caseId, documents, reviews, queryClient }: SectionProps & { documents: DocumentResponse[]; reviews: ContractReviewDto[] }): JSX.Element {
  const [selectedDocId, setSelectedDocId] = useState('')
  const readyDocuments = documents.filter((doc) => doc.status === 'READY')

  const reviewMutation = useMutation({
    mutationFn: () => contractReviewsApi.create(selectedDocId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['case-contract-reviews', caseId] })
      setSelectedDocId('')
    },
  })

  return (
    <section className="mb-10 p-5 rounded-xl bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border">
      <h2 className="text-sm font-semibold text-light-text dark:text-dark-text mb-1">AI-ревью договора</h2>
      <p className="text-xs text-light-secondary dark:text-dark-secondary mb-3">
        Выберите загруженный договор — AI выделит рискованные условия и предложит правки.
      </p>

      {readyDocuments.length === 0 ? (
        <p className="text-sm text-light-secondary dark:text-dark-secondary">
          Загрузите документ дела (PDF, DOCX) и дождитесь обработки, чтобы запустить ревью.
        </p>
      ) : (
        <div className="flex flex-col gap-3">
          <select
            value={selectedDocId}
            onChange={(e) => setSelectedDocId(e.target.value)}
            className="w-full px-3 py-2.5 rounded-lg border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg text-light-text dark:text-dark-text text-sm focus:outline-none focus:ring-2 focus:ring-light-accent dark:focus:ring-dark-accent"
          >
            <option value="">Выберите договор</option>
            {readyDocuments.map((doc) => (
              <option key={doc.id} value={doc.id}>
                {doc.title}
              </option>
            ))}
          </select>

          {reviewMutation.isError && (
            <p className="text-sm text-red-600 dark:text-red-400">Не удалось выполнить ревью. Попробуйте снова.</p>
          )}

          <div>
            <Button
              variant="primary"
              disabled={!selectedDocId}
              loading={reviewMutation.isPending}
              onClick={() => reviewMutation.mutate()}
            >
              Проанализировать риски
            </Button>
          </div>
        </div>
      )}

      {reviews.length > 0 && (
        <div className="flex flex-col gap-4 mt-5">
          {reviews.map((review) => (
            <ContractReviewCard key={review.id} review={review} />
          ))}
        </div>
      )}
    </section>
  )
}

function ContractReviewCard({ review }: { review: ContractReviewDto }): JSX.Element {
  return (
    <div className="p-4 rounded-lg border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg">
      <div className="flex items-start justify-between gap-3 mb-2">
        <div className="min-w-0">
          <p className="text-sm font-medium text-light-text dark:text-dark-text truncate">{review.documentTitle}</p>
          <p className="text-xs text-light-secondary dark:text-dark-secondary">
            {new Date(review.createdAt).toLocaleString('ru-RU')}
          </p>
        </div>
        <div className="text-right shrink-0">
          <span className={`text-lg font-semibold ${riskScoreTone(review.riskScore)}`}>{review.riskScore}</span>
          <span className="text-xs text-light-secondary dark:text-dark-secondary">/100</span>
          {review.highRiskCount > 0 && (
            <p className="text-xs text-red-600 dark:text-red-400">{review.highRiskCount} высоких</p>
          )}
        </div>
      </div>

      <p className="text-sm text-light-text dark:text-dark-text whitespace-pre-wrap mb-3">{review.summary}</p>

      {review.findings.length === 0 ? (
        <p className="text-xs text-light-secondary dark:text-dark-secondary">Существенных рисков не выявлено.</p>
      ) : (
        <div className="flex flex-col gap-2">
          {review.findings.map((risk, i) => {
            const meta = RISK_LEVEL_META[risk.level]
            return (
              <div key={i} className={`p-3 rounded-lg border ${meta.tone}`}>
                <div className="flex items-center justify-between gap-2 mb-1">
                  <span className="text-xs font-semibold uppercase tracking-wide">{meta.label} · {risk.category}</span>
                </div>
                <p className="text-sm font-medium text-light-text dark:text-dark-text mb-1">{risk.clause}</p>
                {risk.explanation && (
                  <p className="text-xs text-light-secondary dark:text-dark-secondary mb-1">{risk.explanation}</p>
                )}
                {risk.recommendation && (
                  <p className="text-xs text-light-text dark:text-dark-text">
                    <span className="font-semibold">Рекомендация:</span> {risk.recommendation}
                  </p>
                )}
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}

const DIFF_TYPE_META: Record<DiffChangeType, { label: string; tone: string }> = {
  ADDED: { label: 'Добавлено', tone: 'text-emerald-600 dark:text-emerald-400' },
  REMOVED: { label: 'Удалено', tone: 'text-red-600 dark:text-red-400' },
  MODIFIED: { label: 'Изменено', tone: 'text-amber-600 dark:text-amber-400' },
}

function ComparisonSection({ caseId, documents, comparisons, queryClient }: SectionProps & { documents: DocumentResponse[]; comparisons: DocumentComparisonDto[] }): JSX.Element {
  const [baseDocId, setBaseDocId] = useState('')
  const [revisedDocId, setRevisedDocId] = useState('')
  const readyDocuments = documents.filter((doc) => doc.status === 'READY')

  const compareMutation = useMutation({
    mutationFn: () => documentComparisonsApi.create(baseDocId, revisedDocId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['case-comparisons', caseId] })
      setBaseDocId('')
      setRevisedDocId('')
    },
  })

  const canCompare = baseDocId !== '' && revisedDocId !== '' && baseDocId !== revisedDocId

  return (
    <section className="mb-10 p-5 rounded-xl bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border">
      <h2 className="text-sm font-semibold text-light-text dark:text-dark-text mb-1">Сравнение версий (редлайн)</h2>
      <p className="text-xs text-light-secondary dark:text-dark-secondary mb-3">
        Выберите исходную и новую версию — AI подсветит изменения и оценит риск каждой правки.
      </p>

      {readyDocuments.length < 2 ? (
        <p className="text-sm text-light-secondary dark:text-dark-secondary">
          Загрузите минимум две версии документа (PDF, DOCX) и дождитесь обработки.
        </p>
      ) : (
        <div className="flex flex-col gap-3">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="block text-xs text-light-secondary dark:text-dark-secondary mb-1">Исходная версия (БЫЛО)</label>
              <select
                value={baseDocId}
                onChange={(e) => setBaseDocId(e.target.value)}
                className="w-full px-3 py-2.5 rounded-lg border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg text-light-text dark:text-dark-text text-sm focus:outline-none focus:ring-2 focus:ring-light-accent dark:focus:ring-dark-accent"
              >
                <option value="">Выберите документ</option>
                {readyDocuments.map((doc) => (
                  <option key={doc.id} value={doc.id}>{doc.title}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-xs text-light-secondary dark:text-dark-secondary mb-1">Новая версия (СТАЛО)</label>
              <select
                value={revisedDocId}
                onChange={(e) => setRevisedDocId(e.target.value)}
                className="w-full px-3 py-2.5 rounded-lg border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg text-light-text dark:text-dark-text text-sm focus:outline-none focus:ring-2 focus:ring-light-accent dark:focus:ring-dark-accent"
              >
                <option value="">Выберите документ</option>
                {readyDocuments.map((doc) => (
                  <option key={doc.id} value={doc.id}>{doc.title}</option>
                ))}
              </select>
            </div>
          </div>

          {baseDocId !== '' && baseDocId === revisedDocId && (
            <p className="text-sm text-amber-600 dark:text-amber-400">Выберите две разные версии.</p>
          )}
          {compareMutation.isError && (
            <p className="text-sm text-red-600 dark:text-red-400">Не удалось сравнить версии. Попробуйте снова.</p>
          )}

          <div>
            <Button
              variant="primary"
              disabled={!canCompare}
              loading={compareMutation.isPending}
              onClick={() => compareMutation.mutate()}
            >
              Сравнить и оценить риски
            </Button>
          </div>
        </div>
      )}

      {comparisons.length > 0 && (
        <div className="flex flex-col gap-4 mt-5">
          {comparisons.map((comparison) => (
            <ComparisonCard key={comparison.id} comparison={comparison} />
          ))}
        </div>
      )}
    </section>
  )
}

function ComparisonCard({ comparison }: { comparison: DocumentComparisonDto }): JSX.Element {
  return (
    <div className="p-4 rounded-lg border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg">
      <div className="flex items-start justify-between gap-3 mb-2">
        <div className="min-w-0">
          <p className="text-sm font-medium text-light-text dark:text-dark-text [overflow-wrap:anywhere]">
            <span className="text-red-600 dark:text-red-400">{comparison.baseDocumentTitle}</span>
            {' → '}
            <span className="text-emerald-600 dark:text-emerald-400">{comparison.revisedDocumentTitle}</span>
          </p>
          <p className="text-xs text-light-secondary dark:text-dark-secondary">
            {new Date(comparison.createdAt).toLocaleString('ru-RU')} · {comparison.changeCount} изм.
          </p>
        </div>
        <div className="text-right shrink-0">
          <span className={`text-lg font-semibold ${riskScoreTone(comparison.riskScore)}`}>{comparison.riskScore}</span>
          <span className="text-xs text-light-secondary dark:text-dark-secondary">/100</span>
          {comparison.highRiskCount > 0 && (
            <p className="text-xs text-red-600 dark:text-red-400">{comparison.highRiskCount} высоких</p>
          )}
        </div>
      </div>

      <p className="text-sm text-light-text dark:text-dark-text whitespace-pre-wrap mb-3">{comparison.summary}</p>

      {comparison.changes.length === 0 ? (
        <p className="text-xs text-light-secondary dark:text-dark-secondary">Различий не обнаружено.</p>
      ) : (
        <div className="flex flex-col gap-2">
          {comparison.changes.map((change) => (
            <DiffChangeRow key={change.order} change={change} />
          ))}
        </div>
      )}
    </div>
  )
}

function DiffChangeRow({ change }: { change: DiffChange }): JSX.Element {
  const typeMeta = DIFF_TYPE_META[change.type]
  const riskMeta = change.riskLevel ? RISK_LEVEL_META[change.riskLevel] : null
  return (
    <div className="p-3 rounded-lg border border-light-border dark:border-dark-border">
      <div className="flex items-center gap-2 mb-2">
        <span className={`text-xs font-semibold uppercase tracking-wide ${typeMeta.tone}`}>{typeMeta.label}</span>
        {riskMeta && (
          <span className={`text-xs font-semibold px-1.5 py-0.5 rounded border ${riskMeta.tone}`}>{riskMeta.label} риск</span>
        )}
      </div>
      {change.baseText && (
        <p className="text-sm mb-1 px-2 py-1 rounded bg-red-50 text-red-800 line-through decoration-red-400/60 dark:bg-red-500/10 dark:text-red-300 [overflow-wrap:anywhere] whitespace-pre-wrap">
          {change.baseText}
        </p>
      )}
      {change.revisedText && (
        <p className="text-sm mb-1 px-2 py-1 rounded bg-emerald-50 text-emerald-800 dark:bg-emerald-500/10 dark:text-emerald-300 [overflow-wrap:anywhere] whitespace-pre-wrap">
          {change.revisedText}
        </p>
      )}
      {change.comment && (
        <p className="text-xs text-light-secondary dark:text-dark-secondary mt-1">{change.comment}</p>
      )}
    </div>
  )
}

function DraftSection({ caseId, draftTypes, drafts, queryClient }: SectionProps & { draftTypes: DraftTypeInfo[]; drafts: CaseDraftSummaryDto[] }): JSX.Element {
  const [selectedDraftType, setSelectedDraftType] = useState('')
  const [selectedTemplate, setSelectedTemplate] = useState('')
  const [downloadingId, setDownloadingId] = useState<string | null>(null)

  const generateMutation = useMutation({
    mutationFn: () => casesApi.generateDraft(caseId, { draftType: selectedDraftType }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['case-drafts', caseId] })
    },
  })

  const { data: templates = [] } = useQuery({
    queryKey: ['templates'],
    queryFn: templatesApi.getAll,
  })

  const applyTemplateMutation = useMutation({
    mutationFn: () => templatesApi.applyToCase(caseId, selectedTemplate),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['case-drafts', caseId] })
      setSelectedTemplate('')
    },
  })

  const handleDownload = async (draft: CaseDraftSummaryDto): Promise<void> => {
    setDownloadingId(draft.id)
    try {
      await casesApi.downloadDraft(draft.id, draft.title)
    } finally {
      setDownloadingId(null)
    }
  }

  return (
    <section className="mb-10 p-5 rounded-xl bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border">
      <h2 className="text-sm font-semibold text-light-text dark:text-dark-text mb-3">Черновик документа</h2>

      <div className="flex flex-col gap-3">
        <select
          value={selectedDraftType}
          onChange={(e) => setSelectedDraftType(e.target.value)}
          className="w-full px-3 py-2.5 rounded-lg border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg text-light-text dark:text-dark-text text-sm focus:outline-none focus:ring-2 focus:ring-light-accent dark:focus:ring-dark-accent"
        >
          <option value="">Выберите тип документа</option>
          {draftTypes.map((type) => (
            <option key={type.id} value={type.id}>
              {type.displayName}
            </option>
          ))}
        </select>

        {generateMutation.isError && (
          <p className="text-sm text-red-600 dark:text-red-400">Ошибка генерации. Попробуйте снова.</p>
        )}

        <div>
          <Button
            variant="primary"
            disabled={!selectedDraftType}
            loading={generateMutation.isPending}
            onClick={() => generateMutation.mutate()}
          >
            Сгенерировать
          </Button>
        </div>

        <div className="pt-3 mt-1 border-t border-light-border dark:border-dark-border flex flex-col gap-2">
          <span className="text-xs font-semibold text-light-secondary dark:text-dark-secondary">
            Применить шаблон
          </span>
          {templates.length === 0 ? (
            <p className="text-xs text-light-secondary dark:text-dark-secondary">
              Шаблонов нет.{' '}
              <Link to="/templates" className="text-light-accent dark:text-dark-accent">
                Создать шаблон
              </Link>
            </p>
          ) : (
            <>
              <select
                value={selectedTemplate}
                onChange={(e) => setSelectedTemplate(e.target.value)}
                className="w-full px-3 py-2.5 rounded-lg border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg text-light-text dark:text-dark-text text-sm focus:outline-none focus:ring-2 focus:ring-light-accent dark:focus:ring-dark-accent"
              >
                <option value="">Выберите шаблон</option>
                {templates.map((template) => (
                  <option key={template.id} value={template.id}>
                    {template.name}
                  </option>
                ))}
              </select>
              {applyTemplateMutation.isError && (
                <p className="text-sm text-red-600 dark:text-red-400">Не удалось применить шаблон.</p>
              )}
              <div>
                <Button
                  variant="secondary"
                  disabled={!selectedTemplate}
                  loading={applyTemplateMutation.isPending}
                  onClick={() => applyTemplateMutation.mutate()}
                >
                  Применить шаблон
                </Button>
              </div>
            </>
          )}
        </div>

        {drafts.length > 0 && (
          <div className="flex flex-col gap-2 mt-2">
            {drafts.map((draft) => (
              <div
                key={draft.id}
                className="p-3 rounded-lg border border-light-border dark:border-dark-border"
              >
                <div className="flex items-center justify-between mb-1.5">
                  <span className="text-xs font-medium text-light-text dark:text-dark-text">{draft.draftTypeName}</span>
                  <span className="text-xs text-light-secondary dark:text-dark-secondary">
                    {new Date(draft.createdAt).toLocaleString('ru-RU')}
                  </span>
                </div>
                <p className="text-xs text-light-secondary dark:text-dark-secondary mb-2 line-clamp-2">
                  {draft.title}
                </p>
                <div className="flex items-center gap-2">
                  <Link to={`/cases/${caseId}/drafts/${draft.id}`}>
                    <Button variant="primary" size="sm">
                      Редактировать
                    </Button>
                  </Link>
                  <Button
                    variant="secondary"
                    size="sm"
                    loading={downloadingId === draft.id}
                    onClick={() => void handleDownload(draft)}
                  >
                    Скачать .docx
                  </Button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </section>
  )
}

function CopyButton({ text }: { text: string }): JSX.Element {
  const [copied, setCopied] = useState(false)
  const handleCopy = (): void => {
    void navigator.clipboard.writeText(text).then(() => {
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    })
  }
  return (
    <button
      onClick={handleCopy}
      title="Скопировать"
      className="inline-flex items-center gap-1 text-xs text-light-secondary dark:text-dark-secondary hover:text-light-text dark:hover:text-dark-text transition-colors"
    >
      {copied ? (
        <>
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><polyline points="20 6 9 17 4 12" /></svg>
          Скопировано
        </>
      ) : (
        <>
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <rect x="9" y="9" width="13" height="13" rx="2" ry="2" />
            <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
          </svg>
          Копировать
        </>
      )}
    </button>
  )
}

function CitationCheckPanel({ responseId }: { responseId: string }): JSX.Element {
  const checkMutation = useMutation({
    mutationFn: () => citationsApi.checkResponse(responseId),
  })
  const result = checkMutation.data

  return (
    <div className="mb-4 pt-3 border-t border-light-border dark:border-dark-border">
      <div className="flex items-center gap-3 mb-2">
        <Button
          variant="ghost"
          size="sm"
          loading={checkMutation.isPending}
          onClick={() => checkMutation.mutate()}
        >
          Проверить ссылки
        </Button>
        {result && (
          <span className="text-xs text-light-secondary dark:text-dark-secondary">
            {citationSummary(result)}
          </span>
        )}
      </div>

      {checkMutation.isError && (
        <p className="text-sm text-red-600 dark:text-red-400">Не удалось проверить ссылки. Попробуйте снова.</p>
      )}

      {result && <CitationList result={result} />}
    </div>
  )
}

function ResponsesSection({ caseId, responses, queryClient }: SectionProps & { responses: AiResponseDto[] }): JSX.Element {
  const rateMutation = useMutation({
    mutationFn: ({ responseId, rating }: { responseId: string; rating: number }) =>
      workflowsApi.rateResponse(responseId, { rating }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['case-responses', caseId] }),
  })

  if (responses.length === 0) {
    return (
      <section>
        <h2 className="text-sm font-semibold text-light-text dark:text-dark-text mb-3">Заключения AI</h2>
        <p className="text-sm text-light-secondary dark:text-dark-secondary">
          Здесь появятся результаты анализа после запуска workflow.
        </p>
      </section>
    )
  }

  return (
    <section>
      <h2 className="text-sm font-semibold text-light-text dark:text-dark-text mb-3">
        Заключения AI <span className="font-normal text-light-secondary dark:text-dark-secondary">({responses.length})</span>
      </h2>
      <div className="flex flex-col gap-4">
        <AnimatePresence>
          {responses.map((response) => (
            <motion.div
              key={response.id}
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              className="p-5 rounded-xl bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border"
            >
              <div className="flex items-center justify-between mb-3">
                <span className="text-xs font-medium px-2.5 py-0.5 rounded-full bg-light-accent/10 dark:bg-dark-accent/10 text-light-accent dark:text-dark-accent">
                  {response.workflowName}
                </span>
                <span className="text-xs text-light-secondary dark:text-dark-secondary">
                  {new Date(response.createdAt).toLocaleString('ru-RU')}
                </span>
              </div>

              <p className="text-sm text-light-text dark:text-dark-text whitespace-pre-wrap mb-4">
                {response.result}
              </p>

              {response.sources.length > 0 && (
                <div className="mb-4 pt-3 border-t border-light-border dark:border-dark-border">
                  <p className="text-xs font-semibold text-light-secondary dark:text-dark-secondary mb-2">
                    Источники ({response.sources.length})
                  </p>
                  <div className="flex flex-col gap-2">
                    {response.sources.map((source, i) => (
                      <div key={i} className="text-xs text-light-secondary dark:text-dark-secondary">
                        <span className="font-medium text-light-text dark:text-dark-text">{source.title}</span>
                        {source.fragment && <span className="block mt-0.5 opacity-80">{source.fragment}</span>}
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {response.followUps && response.followUps.length > 0 && (
                <div className="mb-4 pt-3 border-t border-light-border dark:border-dark-border">
                  <p className="text-xs font-semibold text-light-secondary dark:text-dark-secondary mb-2">
                    Уточняющие вопросы
                  </p>
                  <div className="flex flex-col gap-1.5">
                    {response.followUps.map((q, i) => (
                      <p key={i} className="text-xs text-light-secondary dark:text-dark-secondary pl-2 border-l-2 border-light-border dark:border-dark-border">
                        {q}
                      </p>
                    ))}
                  </div>
                </div>
              )}

              <CitationCheckPanel responseId={response.id} />

              <div className="flex items-center gap-4 pt-3 border-t border-light-border dark:border-dark-border">
                <CopyButton text={response.result} />
                <div className="flex items-center gap-2 ml-auto">
                  <span className="text-xs text-light-secondary dark:text-dark-secondary">Оценка:</span>
                  <RatingButtons
                    rating={response.rating}
                    onRate={(rating) => rateMutation.mutate({ responseId: response.id, rating })}
                    disabled={rateMutation.isPending}
                  />
                </div>
              </div>
            </motion.div>
          ))}
        </AnimatePresence>
      </div>
    </section>
  )
}
