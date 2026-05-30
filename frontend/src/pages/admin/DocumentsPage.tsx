import { useState, useRef, useCallback, type DragEvent } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { motion, AnimatePresence } from 'framer-motion'
import { documentsApi } from '../../api/documents'
import type { DocumentResponse } from '../../types'
import { DocumentStatusBadge } from '../../components/ui/Badge'
import { Button } from '../../components/ui/Button'
import { Spinner } from '../../components/ui/Spinner'

const ALLOWED_TYPES = ['application/pdf', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document']
const POLLING_INTERVAL_MS = 5000

export default function DocumentsPage(): JSX.Element {
  const [isDragging, setIsDragging] = useState(false)
  const [uploadError, setUploadError] = useState<string | null>(null)
  const [isUploading, setIsUploading] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const queryClient = useQueryClient()

  const { data: documents = [], isLoading } = useQuery<DocumentResponse[]>({
    queryKey: ['documents'],
    queryFn: documentsApi.getAll,
    refetchInterval: (query) => {
      const data = query.state.data
      const hasProcessing = data?.some((d) => d.status === 'PROCESSING')
      return hasProcessing ? POLLING_INTERVAL_MS : false
    },
  })

  const deleteMutation = useMutation({
    mutationFn: documentsApi.delete,
    onMutate: async (id) => {
      await queryClient.cancelQueries({ queryKey: ['documents'] })
      queryClient.setQueryData(['documents'], (old: DocumentResponse[] | undefined) =>
        old ? old.filter((d) => d.id !== id) : old
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

      const validFiles = Array.from(files).filter((f) => ALLOWED_TYPES.includes(f.type))
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
    setIsDragging(true)
  }

  const handleDragLeave = (): void => {
    setIsDragging(false)
  }

  const handleDrop = (e: DragEvent<HTMLDivElement>): void => {
    e.preventDefault()
    setIsDragging(false)
    void handleFiles(e.dataTransfer.files)
  }

  const handleFileInput = (e: React.ChangeEvent<HTMLInputElement>): void => {
    void handleFiles(e.target.files)
  }

  return (
    <div className="p-6 lg:p-8">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-light-text dark:text-dark-text mb-1">Документы</h1>
        <p className="text-sm text-light-secondary dark:text-dark-secondary">
          Загрузите PDF и DOCX-файлы для формирования базы знаний AI-ассистента
        </p>
      </div>

      {/* Upload zone */}
      <div
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
            {documents.length > 0 && (
              <span className="ml-2 text-light-secondary dark:text-dark-secondary font-normal">
                ({documents.length})
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
                  document={doc}
                  index={index}
                  onDelete={() => deleteMutation.mutate(doc.id)}
                  isDeleting={deleteMutation.isPending && deleteMutation.variables === doc.id}
                />
              ))}
            </AnimatePresence>
          </div>
        )}
      </div>
    </div>
  )
}

interface DocumentRowProps {
  document: DocumentResponse
  index: number
  onDelete: () => void
  isDeleting: boolean
}

function DocumentRow({ document, index, onDelete, isDeleting }: DocumentRowProps): JSX.Element {
  const [confirmDelete, setConfirmDelete] = useState(false)

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
        <FileIcon fileName={document.fileName} />
      </div>

      <div className="flex-1 min-w-0">
        <p className="font-medium text-light-text dark:text-dark-text text-sm truncate">
          {document.title}
        </p>
        <p className="text-xs text-light-secondary dark:text-dark-secondary truncate mt-0.5">
          {document.fileName} · {new Date(document.uploadedAt).toLocaleDateString('ru-RU')}
        </p>
      </div>

      <DocumentStatusBadge status={document.status} />

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
