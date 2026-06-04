import { useState, useRef } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { motion, AnimatePresence } from 'framer-motion'
import { casesApi } from '../../api/cases'
import { workflowsApi } from '../../api/workflows'
import type { AiResponseDto, CaseDraftSummaryDto, CaseResponse, DocumentResponse, DraftTypeInfo, WorkflowInfo } from '../../types'
import { Navbar } from '../../components/layout/Navbar'
import { Button } from '../../components/ui/Button'
import { Spinner } from '../../components/ui/Spinner'
import { DocumentStatusBadge } from '../../components/ui/Badge'
import { RatingButtons } from '../../components/ui/RatingButtons'

const WORKFLOW_TABS = [
  { id: 'analysis', label: 'Анализ', ids: ['DEBTOR_SOLVENCY_ANALYSIS', 'CHALLENGE_TRANSACTIONS', 'CREDITOR_CLAIMS', 'SUBSIDIARY_LIABILITY', 'BANKRUPTCY_ESTATE'] },
  { id: 'documents', label: 'Документы', ids: ['DOCUMENT_CHECKLIST', 'DATA_EXTRACTION'] },
  { id: 'summary', label: 'Итоги', ids: ['CASE_SUMMARY', 'RISK_MAP', 'CHRONOLOGY'] },
] as const

const ALLOWED_EXTENSIONS = ['.pdf', '.docx']
const POLLING_INTERVAL_MS = 5000

export default function CaseDetailPage(): JSX.Element {
  const { caseId = '' } = useParams()
  const queryClient = useQueryClient()

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

  if (caseLoading) {
    return (
      <div className="min-h-screen bg-light-bg dark:bg-dark-bg">
        <Navbar />
        <div className="flex justify-center py-24">
          <Spinner size="lg" />
        </div>
      </div>
    )
  }

  if (!caseItem) {
    return (
      <div className="min-h-screen bg-light-bg dark:bg-dark-bg">
        <Navbar />
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
    <div className="min-h-screen bg-light-bg dark:bg-dark-bg">
      <Navbar />
      <div className="page-container py-8 max-w-4xl">
        <Link to="/cases" className="text-sm text-light-secondary dark:text-dark-secondary hover:text-light-accent dark:hover:text-dark-accent mb-4 inline-block">
          ← Ко всем делам
        </Link>

        <h1 className="text-3xl font-semibold text-light-text dark:text-dark-text mb-2">
          {caseItem.title}
        </h1>
        {caseItem.description && (
          <p className="text-sm text-light-secondary dark:text-dark-secondary mb-8">{caseItem.description}</p>
        )}

        <DocumentsSection caseId={caseId} documents={documents} queryClient={queryClient} />

        <WorkflowSection caseId={caseId} workflows={workflows} queryClient={queryClient} />

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

function DocumentsSection({ caseId, documents, queryClient }: SectionProps & { documents: DocumentResponse[] }): JSX.Element {
  const [isUploading, setIsUploading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

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

function DraftSection({ caseId, draftTypes, drafts, queryClient }: SectionProps & { draftTypes: DraftTypeInfo[]; drafts: CaseDraftSummaryDto[] }): JSX.Element {
  const [selectedDraftType, setSelectedDraftType] = useState('')
  const [downloadingId, setDownloadingId] = useState<string | null>(null)

  const generateMutation = useMutation({
    mutationFn: () => casesApi.generateDraft(caseId, { draftType: selectedDraftType }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['case-drafts', caseId] })
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
                <Button
                  variant="secondary"
                  size="sm"
                  loading={downloadingId === draft.id}
                  onClick={() => void handleDownload(draft)}
                >
                  Скачать .docx
                </Button>
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
