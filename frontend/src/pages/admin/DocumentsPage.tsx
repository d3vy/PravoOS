import { useState, useRef, useCallback, type DragEvent } from 'react'
import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import { motion, AnimatePresence } from 'framer-motion'
import { documentsApi } from '../../api/documents'
import { DEFAULT_PAGE_SIZE, type Page } from '../../api/pagination'
import type { DocumentResponse, LegislationResponse } from '../../types'
import { DocumentStatusBadge } from '../../components/ui/Badge'
import { Button } from '../../components/ui/Button'
import { Spinner } from '../../components/ui/Spinner'
import { Pagination } from '../../components/ui/Pagination'

const ALLOWED_TYPES = ['application/pdf', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document']
const ALLOWED_EXTENSIONS = ['.pdf', '.docx']
const POLLING_INTERVAL_MS = 5000

function isAllowedFile(file: File): boolean {
  if (ALLOWED_TYPES.includes(file.type)) return true
  const lowerName = file.name.toLowerCase()
  return ALLOWED_EXTENSIONS.some((ext) => lowerName.endsWith(ext))
}

export default function DocumentsPage(): JSX.Element {
  const [isDragging, setIsDragging] = useState(false)
  const [uploadError, setUploadError] = useState<string | null>(null)
  const [isUploading, setIsUploading] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const dragCounterRef = useRef(0)
  const [page, setPage] = useState(0)
  const queryClient = useQueryClient()

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
        setUploadError('Поддерживаются только файлы PDF и DOCX')
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
        setUploadError('Ошибка при загрузке файла. Проверьте формат и размер.')
      } finally {
        setIsUploading(false)
        if (fileInputRef.current) fileInputRef.current.value = ''
      }
    },
    [queryClient]
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
      <div className="mb-8">
        <h1 className="text-3xl font-semibold text-light-text dark:text-dark-text mb-1">Документы</h1>
        <p className="text-sm text-light-secondary dark:text-dark-secondary">
          Загрузите PDF и DOCX-файлы для формирования базы знаний AI-ассистента
        </p>
      </div>

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
            ? 'border-light-accent dark:border-dark-accent bg-light-accent/5 dark:bg-dark-accent/10'
            : 'border-light-border dark:border-dark-border hover:border-light-accent/50 dark:hover:border-dark-accent/50 hover:bg-light-surface dark:hover:bg-dark-surface'
          }
        `}
        role="button"
        tabIndex={0}
        aria-label="Зона загрузки документов"
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
            <p className="text-sm text-light-secondary dark:text-dark-secondary">
              Загрузка документов...
            </p>
          </>
        ) : (
          <>
            <div className="w-12 h-12 rounded-full bg-light-bg dark:bg-dark-bg border border-light-border dark:border-dark-border flex items-center justify-center text-light-secondary dark:text-dark-secondary">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
                <polyline points="17 8 12 3 7 8" />
                <line x1="12" y1="3" x2="12" y2="15" />
              </svg>
            </div>
            <div>
              <p className="text-sm font-medium text-light-text dark:text-dark-text mb-1">
                Перетащите файлы сюда или нажмите для выбора
              </p>
              <p className="text-xs text-light-secondary dark:text-dark-secondary">
                PDF, DOCX — нормативные акты, судебная практика, регламенты
              </p>
            </div>
          </>
        )}
      </div>

      {uploadError && (
        <motion.div
          initial={{ opacity: 0, y: -8 }}
          animate={{ opacity: 1, y: 0 }}
          className="mb-6 p-3 rounded-lg bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800"
        >
          <p className="text-sm text-red-700 dark:text-red-400">{uploadError}</p>
        </motion.div>
      )}

      {/* Documents list */}
      <div>
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-sm font-semibold text-light-text dark:text-dark-text">
            Загруженные документы
            {total > 0 && (
              <span className="ml-2 text-light-secondary dark:text-dark-secondary font-normal">
                ({total})
              </span>
            )}
          </h2>
          {documents.some((d) => d.status === 'PROCESSING') && (
            <div className="flex items-center gap-1.5 text-xs text-amber-600 dark:text-amber-400">
              <Spinner size="sm" className="border-amber-200 border-t-amber-500 dark:border-amber-800 dark:border-t-amber-400" />
              Обработка...
            </div>
          )}
        </div>

        {isLoading ? (
          <div className="flex justify-center py-16">
            <Spinner size="lg" />
          </div>
        ) : documents.length === 0 ? (
          <div className="text-center py-16 rounded-xl border border-dashed border-light-border dark:border-dark-border">
            <p className="text-light-secondary dark:text-dark-secondary text-sm">
              Нет загруженных документов. Начните с загрузки правовой базы.
            </p>
          </div>
        ) : (
          <div className="flex flex-col gap-2">
            <AnimatePresence>
              {documents.map((doc, index) => (
                <DocumentRow
                  key={doc.id}
                  doc={doc}
                  index={index}
                  onDelete={() => deleteMutation.mutate(doc.id)}
                  isDeleting={deleteMutation.isPending && deleteMutation.variables === doc.id}
                />
              ))}
            </AnimatePresence>
          </div>
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
    onError: () => setError('Не удалось загрузить НПА. Проверьте поля и формат файла.'),
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
        <h2 className="text-sm font-semibold text-light-text dark:text-dark-text">Законодательство (НПА)</h2>
        <p className="text-xs text-light-secondary dark:text-dark-secondary mt-0.5">
          Актуальные редакции норм. Новая редакция статьи автоматически заменяет прежнюю.
        </p>
      </div>

      <form
        onSubmit={handleSubmit}
        className="mb-6 p-4 rounded-xl bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border grid gap-3 sm:grid-cols-2 lg:grid-cols-4"
      >
        <input
          type="text"
          value={actCanonical}
          onChange={(e) => setActCanonical(e.target.value)}
          placeholder="Акт (напр. ГК РФ)"
          className="px-3 py-2 rounded-lg bg-light-bg dark:bg-dark-bg border border-light-border dark:border-dark-border text-sm text-light-text dark:text-dark-text"
        />
        <input
          type="text"
          value={articleNumber}
          onChange={(e) => setArticleNumber(e.target.value)}
          placeholder="Статья (напр. 450)"
          className="px-3 py-2 rounded-lg bg-light-bg dark:bg-dark-bg border border-light-border dark:border-dark-border text-sm text-light-text dark:text-dark-text"
        />
        <input
          type="date"
          value={editionDate}
          onChange={(e) => setEditionDate(e.target.value)}
          className="px-3 py-2 rounded-lg bg-light-bg dark:bg-dark-bg border border-light-border dark:border-dark-border text-sm text-light-text dark:text-dark-text"
        />
        <input
          ref={legislationFileRef}
          type="file"
          accept=".pdf,.docx,.txt"
          onChange={(e) => setFile(e.target.files?.[0] ?? null)}
          className="text-xs text-light-secondary dark:text-dark-secondary file:mr-3 file:px-3 file:py-2 file:rounded-lg file:border file:border-light-border dark:file:border-dark-border file:bg-light-bg dark:file:bg-dark-bg file:text-light-text dark:file:text-dark-text"
        />
        <div className="sm:col-span-2 lg:col-span-4 flex items-center justify-between gap-3">
          {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}
          <Button type="submit" size="sm" disabled={!canSubmit} loading={uploadMutation.isPending} className="ml-auto">
            Загрузить НПА
          </Button>
        </div>
      </form>

      {isLoading ? (
        <div className="flex justify-center py-8">
          <Spinner size="md" />
        </div>
      ) : legislation.length === 0 ? (
        <div className="text-center py-10 rounded-xl border border-dashed border-light-border dark:border-dark-border">
          <p className="text-light-secondary dark:text-dark-secondary text-sm">
            Нормативные акты ещё не загружены.
          </p>
        </div>
      ) : (
        <div className="flex flex-col gap-2">
          {legislation.map((norm) => (
            <div
              key={norm.id}
              className="flex items-center gap-4 p-4 rounded-xl bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border"
            >
              <div className="flex-1 min-w-0">
                <p className="font-medium text-light-text dark:text-dark-text text-sm truncate">
                  ст. {norm.articleNumber} {norm.actCanonical}
                </p>
                <p className="text-xs text-light-secondary dark:text-dark-secondary truncate mt-0.5">
                  ред. от {new Date(norm.editionDate).toLocaleDateString('ru-RU')}
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

interface DocumentRowProps {
  doc: DocumentResponse
  index: number
  onDelete: () => void
  isDeleting: boolean
}

function DocumentRow({ doc, index, onDelete, isDeleting }: DocumentRowProps): JSX.Element {
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [isOpening, setIsOpening] = useState(false)

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
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, scale: 0.98 }}
      transition={{ duration: 0.2, delay: index * 0.03 }}
      className="flex items-center gap-4 p-4 rounded-xl bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border"
    >
      <div className="w-9 h-9 rounded-lg bg-light-bg dark:bg-dark-bg border border-light-border dark:border-dark-border flex items-center justify-center text-light-secondary dark:text-dark-secondary shrink-0">
        <FileIcon fileName={doc.fileName} />
      </div>

      <div className="flex-1 min-w-0">
        <p className="font-medium text-light-text dark:text-dark-text text-sm truncate">
          {doc.title}
        </p>
        <p className="text-xs text-light-secondary dark:text-dark-secondary truncate mt-0.5">
          {doc.fileName} · {new Date(doc.uploadedAt).toLocaleDateString('ru-RU')}
        </p>
      </div>

      <DocumentStatusBadge status={doc.status} />

      <Button
        variant="ghost"
        size="sm"
        onClick={() => void handleOpen()}
        loading={isOpening}
        disabled={isOpening}
        className="shrink-0"
      >
        Открыть
      </Button>

      <Button
        variant={confirmDelete ? 'danger' : 'ghost'}
        size="sm"
        onClick={handleDeleteClick}
        loading={isDeleting}
        disabled={isDeleting}
        className="shrink-0"
      >
        {confirmDelete ? 'Подтвердить' : 'Удалить'}
      </Button>
    </motion.div>
  )
}

function FileIcon({ fileName }: { fileName: string }): JSX.Element {
  const ext = fileName.split('.').pop()?.toLowerCase()
  return (
    <span className="text-xs font-bold uppercase">
      {ext === 'pdf' ? 'PDF' : 'DOC'}
    </span>
  )
}
