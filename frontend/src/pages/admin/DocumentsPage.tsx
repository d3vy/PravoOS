import { useMemo, useState, useRef, useCallback, type DragEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import { motion } from 'framer-motion'
import i18n from '../../i18n'
import { documentsApi } from '../../api/documents'
import { DEFAULT_PAGE_SIZE, type Page } from '../../api/pagination'
import type { DocumentResponse, LegislationResponse } from '../../types'
import { DocumentStatusBadge } from '../../components/ui/Badge'
import { Button } from '../../components/ui/Button'
import { Spinner } from '../../components/ui/Spinner'
import { Pagination } from '../../components/ui/Pagination'
import { EmptyState } from '../../components/ui/EmptyState'
import { DataTable, type DataTableColumn } from '../../components/ui/DataTable'
import { Modal } from '../../components/ui/Modal'
import { DocumentSummaryCard } from '../../components/documents/DocumentSummaryCard'
import { TableToolbar } from '../../components/ui/TableToolbar'
import { useDensity } from '../../hooks/useDensity'
import { useTablePreferences } from '../../hooks/useTablePreferences'
import { PageHeader } from '../../components/ui/PageHeader'

const ALLOWED_TYPES = ['application/pdf', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document']
const ALLOWED_EXTENSIONS = ['.pdf', '.docx']
const POLLING_INTERVAL_MS = 5000

function isAllowedFile(file: File): boolean {
  if (ALLOWED_TYPES.includes(file.type)) return true
  const lowerName = file.name.toLowerCase()
  return ALLOWED_EXTENSIONS.some((ext) => lowerName.endsWith(ext))
}

export default function DocumentsPage(): JSX.Element {
  const { t } = useTranslation()
  const [isDragging, setIsDragging] = useState(false)
  const [uploadError, setUploadError] = useState<string | null>(null)
  const [isUploading, setIsUploading] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const dragCounterRef = useRef(0)
  const [page, setPage] = useState(0)
  const queryClient = useQueryClient()
  const [density, toggleDensity] = useDensity()
  const { preferences, setSort, setGroupBy, toggleColumn } = useTablePreferences('admin-documents', {
    visibleColumnIds: ['title', 'fileName', 'status', 'uploadedAt'],
    sort: [],
    groupBy: null,
  })

  const documentColumns = useMemo<DataTableColumn<DocumentResponse>[]>(
    () => [
      {
        id: 'title',
        header: t('documents.columnTitle'),
        alwaysVisible: true,
        sortable: true,
        width: 'minmax(0, 2fr)',
        value: (row) => row.title,
      },
      { id: 'fileName', header: t('documents.columnFile'), sortable: true, value: (row) => row.fileName },
      {
        id: 'status',
        header: t('documents.columnStatus'),
        sortable: true,
        groupable: true,
        width: '9rem',
        value: (row) => row.status,
        render: (row) => <DocumentStatusBadge status={row.status} />,
      },
      {
        id: 'uploadedAt',
        header: t('documents.columnUploaded'),
        sortable: true,
        width: '10rem',
        value: (row) => row.uploadedAt,
        render: (row) =>
          new Date(row.uploadedAt).toLocaleDateString(i18n.language.startsWith('ru') ? 'ru-RU' : 'en-US'),
      },
    ],
    [t]
  )

  const { data: documentsPage, isLoading } = useQuery<Page<DocumentResponse>>({
    queryKey: ['documents', page],
    queryFn: () => documentsApi.list(page),
    placeholderData: keepPreviousData,
    refetchInterval: (query) => {
      const hasProcessing = query.state.data?.items?.some((d) => d.status === 'PROCESSING')
      return hasProcessing ? POLLING_INTERVAL_MS : false
    },
  })
  const documents = documentsPage?.items ?? []
  const total = documentsPage?.total ?? 0

  const deleteMutation = useMutation({
    mutationFn: documentsApi.delete,
    onMutate: async (id) => {
      await queryClient.cancelQueries({ queryKey: ['documents'] })
      queryClient.setQueryData(['documents', page], (old: Page<DocumentResponse> | undefined) =>
        old ? { ...old, items: old.items.filter((d) => d.id !== id) } : old
      )
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['documents'] })
    },
  })

  const handleFiles = useCallback(
    async (files: FileList | null): Promise<void> => {
      if (!files || files.length === 0) return
      setUploadError(null)

      const validFiles = Array.from(files).filter(isAllowedFile)
      if (validFiles.length === 0) {
        setUploadError(t('documents.onlyPdfDocx'))
        return
      }

      setIsUploading(true)
      try {
        for (const file of validFiles) {
          const title = file.name.replace(/\.[^.]+$/, '')
          await documentsApi.upload(file, title)
        }
        queryClient.invalidateQueries({ queryKey: ['documents'] })
      } catch {
        setUploadError(t('documents.uploadFileError'))
      } finally {
        setIsUploading(false)
        if (fileInputRef.current) fileInputRef.current.value = ''
      }
    },
    [queryClient, t]
  )

  const handleDragOver = (e: DragEvent<HTMLDivElement>): void => {
    e.preventDefault()
  }

  const handleDragEnter = (e: DragEvent<HTMLDivElement>): void => {
    e.preventDefault()
    dragCounterRef.current += 1
    setIsDragging(true)
  }

  const handleDragLeave = (e: DragEvent<HTMLDivElement>): void => {
    e.preventDefault()
    dragCounterRef.current -= 1
    if (dragCounterRef.current <= 0) {
      dragCounterRef.current = 0
      setIsDragging(false)
    }
  }

  const handleDrop = (e: DragEvent<HTMLDivElement>): void => {
    e.preventDefault()
    dragCounterRef.current = 0
    setIsDragging(false)
    void handleFiles(e.dataTransfer.files)
  }

  const handleFileInput = (e: React.ChangeEvent<HTMLInputElement>): void => {
    void handleFiles(e.target.files)
  }

  return (
    <div className="p-6 lg:p-8">
      <PageHeader title={t('documents.title')} description={t('documents.subtitle')} />

      {/* Upload zone */}
      <div
        onDragEnter={handleDragEnter}
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        onDrop={handleDrop}
        onClick={() => fileInputRef.current?.click()}
        className={`
          relative mb-8 rounded-xl border-2 border-dashed cursor-pointer transition-all duration-150
          flex flex-col items-center justify-center gap-3 py-12 px-6 text-center
          ${isDragging
            ? 'border-accent bg-accent/5'
            : 'border-line hover:border-accent/50 hover:bg-surface'
          }
        `}
        role="button"
        tabIndex={0}
        aria-label={t('documents.uploadZoneLabel')}
        onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') fileInputRef.current?.click() }}
      >
        <input
          ref={fileInputRef}
          type="file"
          accept=".pdf,.docx"
          multiple
          className="sr-only"
          onChange={handleFileInput}
        />

        {isUploading ? (
          <>
            <Spinner size="md" />
            <p className="text-sm text-fg-muted">
              {t('documents.uploading')}
            </p>
          </>
        ) : (
          <>
            <div className="w-12 h-12 rounded-full bg-bg border border-line flex items-center justify-center text-fg-muted">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
                <polyline points="17 8 12 3 7 8" />
                <line x1="12" y1="3" x2="12" y2="15" />
              </svg>
            </div>
            <div>
              <p className="text-sm font-medium text-fg mb-1">
                {t('documents.dropHint')}
              </p>
              <p className="text-xs text-fg-muted">
                {t('documents.allowedTypesHint')}
              </p>
            </div>
          </>
        )}
      </div>

      {uploadError && (
        <motion.div
          initial={{ opacity: 0, y: -8 }}
          animate={{ opacity: 1, y: 0 }}
          className="mb-6 p-3 rounded-lg bg-danger-soft border border-danger/30"
        >
          <p className="text-sm text-danger">{uploadError}</p>
        </motion.div>
      )}

      {/* Documents list */}
      <div>
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-sm font-semibold text-fg">
            {t('documents.uploaded')}
            {total > 0 && (
              <span className="ml-2 text-fg-muted font-normal">
                ({total})
              </span>
            )}
          </h2>
          {documents.some((d) => d.status === 'PROCESSING') && (
            <div className="flex items-center gap-1.5 text-xs text-warning">
              <Spinner size="sm" className="border-amber-200 border-t-amber-500 dark:border-amber-800 dark:border-t-amber-400" />
              {t('documents.processing')}
            </div>
          )}
        </div>

        {isLoading ? (
          <div className="flex justify-center py-16">
            <Spinner size="lg" />
          </div>
        ) : (
          <>
            <div className="flex justify-end mb-3">
              <TableToolbar
                columns={documentColumns}
                visibleColumnIds={preferences.visibleColumnIds}
                onToggleColumn={toggleColumn}
                groupBy={preferences.groupBy}
                onGroupByChange={setGroupBy}
                density={density}
                onDensityToggle={toggleDensity}
              />
            </div>
            <DataTable
              rows={documents}
              columns={documentColumns}
              rowId={(row) => row.id}
              sort={preferences.sort}
              onSortChange={setSort}
              visibleColumnIds={preferences.visibleColumnIds}
              groupBy={preferences.groupBy}
              density={density}
              rowActions={(doc) => (
                <DocumentRowActions
                  doc={doc}
                  onDelete={() => deleteMutation.mutate(doc.id)}
                  isDeleting={deleteMutation.isPending && deleteMutation.variables === doc.id}
                />
              )}
              emptyState={<EmptyState illustration="documents" description={t('documents.empty')} />}
            />
          </>
        )}

        {!isLoading && (
          <Pagination page={page} pageSize={DEFAULT_PAGE_SIZE} total={total} onPageChange={setPage} />
        )}
      </div>

      <LegislationSection />
    </div>
  )
}

function LegislationSection(): JSX.Element {
  const { t } = useTranslation()
  const dateLocale = i18n.language.startsWith('ru') ? 'ru-RU' : 'en-US'
  const queryClient = useQueryClient()
  const [actCanonical, setActCanonical] = useState('')
  const [articleNumber, setArticleNumber] = useState('')
  const [editionDate, setEditionDate] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [error, setError] = useState<string | null>(null)
  const legislationFileRef = useRef<HTMLInputElement>(null)

  const { data: legislationPage, isLoading } = useQuery<Page<LegislationResponse>>({
    queryKey: ['legislation'],
    queryFn: () => documentsApi.listLegislation(0, 100),
    refetchInterval: (query) =>
      query.state.data?.items?.some((n) => n.status === 'PROCESSING') ? POLLING_INTERVAL_MS : false,
  })
  const legislation = legislationPage?.items ?? []

  const uploadMutation = useMutation({
    mutationFn: documentsApi.uploadLegislation,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['legislation'] })
      setActCanonical('')
      setArticleNumber('')
      setEditionDate('')
      setFile(null)
      if (legislationFileRef.current) legislationFileRef.current.value = ''
    },
    onError: () => setError(t('documents.uploadLegislationError')),
  })

  const canSubmit = actCanonical.trim() && articleNumber.trim() && editionDate && file

  const handleSubmit = (e: React.FormEvent): void => {
    e.preventDefault()
    setError(null)
    if (!file || !canSubmit) return
    uploadMutation.mutate({ file, actCanonical: actCanonical.trim(), articleNumber: articleNumber.trim(), editionDate })
  }

  return (
    <div className="mt-12">
      <div className="mb-4">
        <h2 className="text-sm font-semibold text-fg">{t('documents.legislationTitle')}</h2>
        <p className="text-xs text-fg-muted mt-0.5">
          {t('documents.legislationSubtitle')}
        </p>
      </div>

      <form
        onSubmit={handleSubmit}
        className="mb-6 p-4 rounded-xl bg-surface border border-line grid gap-3 sm:grid-cols-2 lg:grid-cols-4"
      >
        <input
          type="text"
          value={actCanonical}
          onChange={(e) => setActCanonical(e.target.value)}
          placeholder={t('documents.actPlaceholder')}
          className="px-3 py-2 rounded-lg bg-bg border border-line text-sm text-fg"
        />
        <input
          type="text"
          value={articleNumber}
          onChange={(e) => setArticleNumber(e.target.value)}
          placeholder={t('documents.articlePlaceholder')}
          className="px-3 py-2 rounded-lg bg-bg border border-line text-sm text-fg"
        />
        <input
          type="date"
          value={editionDate}
          onChange={(e) => setEditionDate(e.target.value)}
          className="px-3 py-2 rounded-lg bg-bg border border-line text-sm text-fg"
        />
        <input
          ref={legislationFileRef}
          type="file"
          accept=".pdf,.docx,.txt"
          onChange={(e) => setFile(e.target.files?.[0] ?? null)}
          className="text-xs text-fg-muted file:mr-3 file:px-3 file:py-2 file:rounded-lg file:border file:border-line file:bg-bg file:text-fg"
        />
        <div className="sm:col-span-2 lg:col-span-4 flex items-center justify-between gap-3">
          {error && <p className="text-sm text-danger">{error}</p>}
          <Button type="submit" size="sm" disabled={!canSubmit} loading={uploadMutation.isPending} className="ml-auto">
            {t('documents.uploadLegislation')}
          </Button>
        </div>
      </form>

      {isLoading ? (
        <div className="flex justify-center py-8">
          <Spinner size="md" />
        </div>
      ) : legislation.length === 0 ? (
        <div className="text-center py-10 rounded-xl border border-dashed border-line">
          <p className="text-fg-muted text-sm">
            {t('documents.noLegislation')}
          </p>
        </div>
      ) : (
        <div className="flex flex-col gap-2">
          {legislation.map((norm) => (
            <div
              key={norm.id}
              className="flex items-center gap-4 p-4 rounded-xl bg-surface border border-line"
            >
              <div className="flex-1 min-w-0">
                <p className="font-medium text-fg text-sm truncate">
                  {t('documents.article')} {norm.articleNumber} {norm.actCanonical}
                </p>
                <p className="text-xs text-fg-muted truncate mt-0.5">
                  {t('documents.editionFrom')} {new Date(norm.editionDate).toLocaleDateString(dateLocale)}
                </p>
              </div>
              <DocumentStatusBadge status={norm.status} />
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

interface DocumentRowActionsProps {
  doc: DocumentResponse
  onDelete: () => void
  isDeleting: boolean
}

function DocumentRowActions({ doc, onDelete, isDeleting }: DocumentRowActionsProps): JSX.Element {
  const { t } = useTranslation()
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [isOpening, setIsOpening] = useState(false)
  const [summaryOpen, setSummaryOpen] = useState(false)

  const handleOpen = async (): Promise<void> => {
    const newWindow = window.open('', '_blank')
    setIsOpening(true)
    try {
      const blob = await documentsApi.getContent(doc.id)
      const url = URL.createObjectURL(blob)
      if (newWindow) {
        newWindow.location.href = url
      } else {
        window.location.href = url
      }
      setTimeout(() => URL.revokeObjectURL(url), 60000)
    } catch {
      newWindow?.close()
    } finally {
      setIsOpening(false)
    }
  }

  const handleDeleteClick = (): void => {
    if (confirmDelete) {
      onDelete()
    } else {
      setConfirmDelete(true)
      setTimeout(() => setConfirmDelete(false), 3000)
    }
  }

  return (
    <>
      <Button variant="ghost" size="sm" onClick={() => void handleOpen()} loading={isOpening} disabled={isOpening}>
        {t('documents.open')}
      </Button>
      {doc.status === 'READY' && (
        <Button variant="ghost" size="sm" onClick={() => setSummaryOpen(true)}>
          {t('documentSummary.title')}
        </Button>
      )}
      <Modal
        open={summaryOpen}
        onClose={() => setSummaryOpen(false)}
        title={doc.title}
        size="lg"
      >
        <DocumentSummaryCard documentId={doc.id} />
      </Modal>
      <Button
        variant={confirmDelete ? 'danger' : 'ghost'}
        size="sm"
        onClick={handleDeleteClick}
        loading={isDeleting}
        disabled={isDeleting}
      >
        {confirmDelete ? t('documents.confirm') : t('documents.delete')}
      </Button>
    </>
  )
}

