import { useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { casesApi } from '../../api/cases'
import type { DocumentResponse } from '../../types'
import { Spinner } from '../ui/Spinner'
import { DocumentStatusBadge } from '../ui/Badge'
import { ALLOWED_DOCUMENT_EXTENSIONS } from './caseFormatting'

export function DocumentsSection({ caseId, documents }: { caseId: string; documents: DocumentResponse[] }): JSX.Element {
  const queryClient = useQueryClient()
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
      ALLOWED_DOCUMENT_EXTENSIONS.some((ext) => f.name.toLowerCase().endsWith(ext))
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
