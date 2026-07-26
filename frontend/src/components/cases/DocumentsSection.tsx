import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQueryClient } from '@tanstack/react-query'
import { casesApi } from '../../api/cases'
import type { DocumentResponse } from '../../types'
import { Spinner } from '../ui/Spinner'
import { DocumentStatusBadge } from '../ui/Badge'
import { ALLOWED_DOCUMENT_EXTENSIONS } from './caseFormatting'
import { DocumentInsightPanel } from '../documents/DocumentInsightPanel'

export function DocumentsSection({ caseId, documents }: { caseId: string; documents: DocumentResponse[] }): JSX.Element {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [isUploading, setIsUploading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [togglingId, setTogglingId] = useState<string | null>(null)
  const [openInsightId, setOpenInsightId] = useState<string | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const toggleVisibility = async (doc: DocumentResponse): Promise<void> => {
    setError(null)
    setTogglingId(doc.id)
    try {
      await casesApi.setDocumentVisibility(caseId, doc.id, !doc.visibleToClient)
      queryClient.invalidateQueries({ queryKey: ['case-documents', caseId] })
    } catch {
      setError(t('caseDocs.visibilityError'))
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
      setError(t('caseDocs.onlyPdfDocx'))
      return
    }
    setIsUploading(true)
    try {
      for (const file of valid) {
        await casesApi.uploadDocument(caseId, file, file.name.replace(/\.[^.]+$/, ''))
      }
      queryClient.invalidateQueries({ queryKey: ['case-documents', caseId] })
    } catch {
      setError(t('caseDocs.uploadError'))
    } finally {
      setIsUploading(false)
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
  }

  return (
    <section className="mb-10">
      <h2 className="text-sm font-semibold text-fg mb-3">
        {t('caseDocs.title')} {documents.length > 0 && <span className="font-normal text-fg-muted">({documents.length})</span>}
      </h2>

      <div
        onClick={() => fileInputRef.current?.click()}
        className="mb-3 rounded-xl border-2 border-dashed border-line hover:border-accent/50 cursor-pointer flex flex-col items-center justify-center gap-2 py-8 px-6 text-center transition-colors"
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
          <p className="text-sm text-fg-muted">
            {t('caseDocs.uploadCta')}
          </p>
        )}
      </div>

      {error && <p className="text-sm text-danger mb-3">{error}</p>}

      {documents.length > 0 && (
        <div className="flex flex-col gap-2">
          {documents.map((doc) => (
            <div key={doc.id} className="rounded-lg bg-surface border border-line">
              <div className="flex items-center gap-3 p-3">
                <span className="text-xs font-bold uppercase text-fg-muted w-9 shrink-0">
                  {doc.fileName.split('.').pop()}
                </span>
                <p className="flex-1 min-w-0 text-sm text-fg truncate">{doc.title}</p>
                {doc.status === 'READY' && (
                  <button
                    type="button"
                    onClick={() => setOpenInsightId(openInsightId === doc.id ? null : doc.id)}
                    aria-expanded={openInsightId === doc.id}
                    className={`text-xs px-2 py-1 rounded-md border transition-colors ${
                      openInsightId === doc.id
                        ? 'border-accent text-accent'
                        : 'border-line text-fg-muted hover:text-fg'
                    }`}
                  >
                    {t('caseDocs.insights')}
                  </button>
                )}
                <button
                  type="button"
                  onClick={() => void toggleVisibility(doc)}
                  disabled={togglingId === doc.id}
                  title={doc.visibleToClient ? t('caseDocs.visibleTooltip') : t('caseDocs.hiddenTooltip')}
                  className={`text-xs px-2 py-1 rounded-md border transition-colors disabled:opacity-60 ${
                    doc.visibleToClient
                      ? 'border-emerald-500/40 text-success bg-emerald-500/10'
                      : 'border-line text-fg-muted'
                  }`}
                >
                  {doc.visibleToClient ? t('caseDocs.visibleShort') : t('caseDocs.hiddenShort')}
                </button>
                <DocumentStatusBadge status={doc.status} />
              </div>
              {openInsightId === doc.id && (
                <div className="px-3 pb-3 border-t border-line pt-3">
                  <DocumentInsightPanel documentId={doc.id} />
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </section>
  )
}
