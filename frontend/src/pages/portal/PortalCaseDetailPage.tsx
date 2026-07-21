import { useRef, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { PortalLayout } from '../../components/layout/PortalLayout'
import { CaseStatusBadge } from '../../components/ui/Badge'
import { Spinner } from '../../components/ui/Spinner'
import { CaseMessageThread } from '../../components/messages/CaseMessageThread'
import { PortalSignatureSection } from '../../components/portal/PortalSignatureSection'
import { portalApi } from '../../api/portal'
import i18n from '../../i18n'
import type { DocumentResponse, PortalCaseDetailResponse } from '../../types'

const ALLOWED_EXTENSIONS = ['.pdf', '.docx', '.txt']

function formatDate(value: string | null): string {
  const locale = i18n.language.startsWith('ru') ? 'ru-RU' : 'en-US'
  return value ? new Date(value).toLocaleDateString(locale) : '—'
}

function PortalDocumentsSection({ caseId }: { caseId: string }): JSX.Element {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [isUploading, setIsUploading] = useState(false)
  const [downloadingId, setDownloadingId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const { data: documents = [], isLoading } = useQuery<DocumentResponse[]>({
    queryKey: ['portal', 'cases', caseId, 'documents'],
    queryFn: () => portalApi.listCaseDocuments(caseId),
  })

  const handleFiles = async (files: FileList | null): Promise<void> => {
    if (!files || files.length === 0) return
    setError(null)
    const valid = Array.from(files).filter((f) =>
      ALLOWED_EXTENSIONS.some((ext) => f.name.toLowerCase().endsWith(ext))
    )
    if (valid.length === 0) {
      setError(t('portalCaseDetail.unsupportedFormat'))
      return
    }
    setIsUploading(true)
    try {
      for (const file of valid) {
        await portalApi.uploadCaseDocument(caseId, file, file.name.replace(/\.[^.]+$/, ''))
      }
      queryClient.invalidateQueries({ queryKey: ['portal', 'cases', caseId, 'documents'] })
    } catch {
      setError(t('portalCaseDetail.uploadError'))
    } finally {
      setIsUploading(false)
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
  }

  const handleDownload = async (doc: DocumentResponse): Promise<void> => {
    setError(null)
    setDownloadingId(doc.id)
    try {
      await portalApi.downloadCaseDocument(caseId, doc)
    } catch {
      setError(t('portalCaseDetail.downloadError'))
    } finally {
      setDownloadingId(null)
    }
  }

  return (
    <section>
      <h2 className="text-lg font-semibold text-light-text dark:text-dark-text mb-3">{t('portalCaseDetail.documents')}</h2>

      <div
        onClick={() => fileInputRef.current?.click()}
        className="mb-3 rounded-xl border-2 border-dashed border-light-border dark:border-dark-border hover:border-light-accent/50 dark:hover:border-dark-accent/50 cursor-pointer flex flex-col items-center justify-center gap-2 py-6 px-6 text-center transition-colors"
        role="button"
        tabIndex={0}
      >
        <input
          ref={fileInputRef}
          type="file"
          accept=".pdf,.docx,.txt"
          multiple
          className="sr-only"
          onChange={(e) => void handleFiles(e.target.files)}
        />
        {isUploading ? (
          <Spinner size="md" />
        ) : (
          <p className="text-sm text-light-secondary dark:text-dark-secondary">
            {t('portalCaseDetail.uploadCta')}
          </p>
        )}
      </div>

      {error && <p className="text-sm text-red-600 dark:text-red-400 mb-3">{error}</p>}

      {isLoading ? (
        <div className="flex justify-center py-6">
          <Spinner />
        </div>
      ) : documents.length === 0 ? (
        <p className="text-light-secondary dark:text-dark-secondary">{t('portalCaseDetail.noDocuments')}</p>
      ) : (
        <div className="flex flex-col gap-2">
          {documents.map((doc) => (
            <button
              key={doc.id}
              type="button"
              onClick={() => void handleDownload(doc)}
              disabled={downloadingId === doc.id}
              className="flex items-center gap-3 p-3 rounded-lg bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border text-left hover:border-light-accent/50 dark:hover:border-dark-accent/50 transition-colors disabled:opacity-60"
            >
              <span className="text-xs font-bold uppercase text-light-secondary dark:text-dark-secondary w-9 shrink-0">
                {doc.fileName.split('.').pop()}
              </span>
              <span className="flex-1 min-w-0 text-sm text-light-text dark:text-dark-text truncate">{doc.title}</span>
              {downloadingId === doc.id ? (
                <Spinner size="sm" />
              ) : (
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-light-secondary dark:text-dark-secondary shrink-0">
                  <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
                  <polyline points="7 10 12 15 17 10" />
                  <line x1="12" y1="15" x2="12" y2="3" />
                </svg>
              )}
            </button>
          ))}
        </div>
      )}
    </section>
  )
}

function DetailRow({ label, value }: { label: string; value: string }): JSX.Element {
  return (
    <div className="flex justify-between gap-4 py-2 border-b border-light-border dark:border-dark-border last:border-0">
      <span className="text-light-secondary dark:text-dark-secondary">{label}</span>
      <span className="text-light-text dark:text-dark-text text-right">{value}</span>
    </div>
  )
}

export default function PortalCaseDetailPage(): JSX.Element {
  const { t } = useTranslation()
  const { caseId } = useParams<{ caseId: string }>()
  const { data: caseData, isLoading, isError } = useQuery<PortalCaseDetailResponse>({
    queryKey: ['portal', 'cases', caseId],
    queryFn: () => portalApi.getCase(caseId as string),
    enabled: Boolean(caseId),
  })

  return (
    <PortalLayout>
      <Link
        to="/portal"
        className="inline-flex items-center gap-1 text-sm text-light-secondary dark:text-dark-secondary hover:text-light-text dark:hover:text-dark-text mb-6"
      >
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <polyline points="15 18 9 12 15 6" />
        </svg>
        {t('portalCaseDetail.backToList')}
      </Link>

      {isLoading && (
        <div className="flex justify-center py-16">
          <Spinner />
        </div>
      )}

      {isError && (
        <div className="card-elevated rounded-xl p-8 text-center text-light-secondary dark:text-dark-secondary">
          {t('portalCaseDetail.notFound')}
        </div>
      )}

      {caseData && (
        <div className="space-y-6">
          <div className="flex items-start justify-between gap-4">
            <h1 className="text-2xl font-semibold text-light-text dark:text-dark-text">
              {caseData.title}
            </h1>
            <CaseStatusBadge status={caseData.status} />
          </div>

          {caseData.description && (
            <p className="text-light-secondary dark:text-dark-secondary leading-relaxed whitespace-pre-line">
              {caseData.description}
            </p>
          )}

          <div className="card-elevated rounded-xl p-5">
            <DetailRow label={t('portalCaseDetail.fieldStatus')} value={caseData.statusName} />
            <DetailRow label={t('portalCaseDetail.fieldFilingDeadline')} value={formatDate(caseData.filingDeadline)} />
            <DetailRow label={t('portalCaseDetail.fieldNextHearing')} value={formatDate(caseData.nextHearingDate)} />
            {caseData.arbitrCaseNumber && (
              <DetailRow label={t('portalCaseDetail.fieldCaseNumber')} value={caseData.arbitrCaseNumber} />
            )}
            <DetailRow label={t('portalCaseDetail.fieldCreated')} value={formatDate(caseData.createdAt)} />
          </div>

          <section>
            <h2 className="text-lg font-semibold text-light-text dark:text-dark-text mb-3">
              {t('portalCaseDetail.hearings')}
            </h2>
            {caseData.hearings.length === 0 ? (
              <p className="text-light-secondary dark:text-dark-secondary">{t('portalCaseDetail.noHearings')}</p>
            ) : (
              <ul className="space-y-3">
                {caseData.hearings.map((hearing) => (
                  <li key={hearing.id} className="card-elevated rounded-xl p-4">
                    <div className="flex items-center justify-between gap-4">
                      <span className="font-medium text-light-text dark:text-dark-text">
                        {formatDate(hearing.eventDate)}
                      </span>
                      {hearing.eventType && (
                        <span className="text-sm text-light-secondary dark:text-dark-secondary">
                          {hearing.eventType}
                        </span>
                      )}
                    </div>
                    {hearing.courtName && (
                      <p className="text-sm text-light-secondary dark:text-dark-secondary mt-1">
                        {hearing.courtName}
                      </p>
                    )}
                    {hearing.description && (
                      <p className="text-sm text-light-text dark:text-dark-text mt-1">
                        {hearing.description}
                      </p>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </section>

          <PortalDocumentsSection caseId={caseData.id} />

          <PortalSignatureSection caseId={caseData.id} />

          <CaseMessageThread
            queryKey={['portal', 'cases', caseData.id, 'messages']}
            viewerRole="CLIENT"
            listMessages={() => portalApi.listCaseMessages(caseData.id)}
            sendMessage={(body) => portalApi.sendCaseMessage(caseData.id, body)}
          />
        </div>
      )}
    </PortalLayout>
  )
}
