import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { portalApi } from '../../api/portal'
import i18n from '../../i18n'
import type { DocumentResponse, SignatureRequestResponse } from '../../types'
import { Button } from '../ui/Button'
import { Spinner } from '../ui/Spinner'
import { SignatureStatusBadge } from '../ui/SignatureStatusBadge'

const errorMessage = (error: unknown): string => {
  const response = (error as { response?: { data?: { message?: string } } })?.response
  return response?.data?.message ?? i18n.t('portalSignature.actionError')
}

export function PortalSignatureSection({ caseId }: { caseId: string }): JSX.Element | null {
  const { t } = useTranslation()
  const queryClient = useQueryClient()

  const { data: signatures = [], isLoading } = useQuery<SignatureRequestResponse[]>({
    queryKey: ['portal', 'cases', caseId, 'signatures'],
    queryFn: () => portalApi.listCaseSignatures(caseId),
  })

  const { data: documents = [] } = useQuery<DocumentResponse[]>({
    queryKey: ['portal', 'cases', caseId, 'documents'],
    queryFn: () => portalApi.listCaseDocuments(caseId),
  })

  if (isLoading) {
    return null
  }
  if (signatures.length === 0) {
    return null
  }

  const findDocument = (documentId: string): DocumentResponse | undefined =>
    documents.find((doc) => doc.id === documentId)

  const invalidate = (): void => {
    queryClient.invalidateQueries({ queryKey: ['portal', 'cases', caseId, 'signatures'] })
  }

  return (
    <section>
      <h2 className="text-lg font-semibold text-fg mb-3">{t('portalSignature.title')}</h2>
      <div className="flex flex-col gap-3">
        {signatures.map((signature) => (
          <SignatureCard
            key={signature.id}
            signature={signature}
            document={findDocument(signature.documentId)}
            caseId={caseId}
            onChanged={invalidate}
          />
        ))}
      </div>
    </section>
  )
}

function SignatureCard({
  signature,
  document,
  caseId,
  onChanged,
}: {
  signature: SignatureRequestResponse
  document: DocumentResponse | undefined
  caseId: string
  onChanged: () => void
}): JSX.Element {
  const { t } = useTranslation()
  const [signerName, setSignerName] = useState('')
  const [consent, setConsent] = useState(false)
  const [declining, setDeclining] = useState(false)
  const [reason, setReason] = useState('')
  const [downloading, setDownloading] = useState(false)
  const [signatureFile, setSignatureFile] = useState<File | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  const signMutation = useMutation({
    mutationFn: () => portalApi.signDocument(signature.id, signerName.trim()),
    onSuccess: onChanged,
    onError: (error) => setActionError(errorMessage(error)),
  })

  const cmsMutation = useMutation({
    mutationFn: () => portalApi.signDocumentWithCms(signature.id, signatureFile as File),
    onSuccess: onChanged,
    onError: (error) => setActionError(errorMessage(error)),
  })

  const declineMutation = useMutation({
    mutationFn: () => portalApi.declineSignature(signature.id, reason.trim()),
    onSuccess: onChanged,
    onError: (error) => setActionError(errorMessage(error)),
  })

  const handleDownload = async (): Promise<void> => {
    if (!document) return
    setDownloading(true)
    setActionError(null)
    try {
      await portalApi.downloadCaseDocument(caseId, document)
    } catch {
      setActionError(t('portalSignature.downloadError'))
    } finally {
      setDownloading(false)
    }
  }

  const canSign = signerName.trim().length > 0 && consent
  const requiresCms = signature.provider === 'DETACHED_CMS'

  const handleProtocolDownload = (): void => {
    portalApi
      .downloadSignatureProtocol(signature.id)
      .catch((error: unknown) => setActionError(errorMessage(error)))
  }

  return (
    <div className="card-elevated rounded-xl p-4">
      <div className="flex items-center gap-3 mb-2">
        <span className="flex-1 min-w-0 text-sm font-medium text-fg truncate">
          {document?.title ?? t('portalSignature.documentFallback')}
        </span>
        <SignatureStatusBadge status={signature.status} />
      </div>

      {signature.message && (
        <p className="text-sm text-fg-muted mb-2">{signature.message}</p>
      )}

      {document && (
        <button
          type="button"
          onClick={() => void handleDownload()}
          disabled={downloading}
          className="text-sm text-accent hover:underline disabled:opacity-60 mb-3 inline-flex items-center gap-1"
        >
          {downloading ? <Spinner size="sm" /> : t('portalSignature.downloadCheck')}
        </button>
      )}

      {signature.status === 'PENDING' && !declining && requiresCms && (
        <div className="flex flex-col gap-2 mt-1">
          <p className="text-sm font-medium text-fg">{t('portalSignature.cmsTitle')}</p>
          <p className="text-xs text-fg-muted">{t('portalSignature.cmsHint')}</p>
          <label className="text-xs text-fg-muted">
            <span className="block mb-1">{t('portalSignature.cmsChooseFile')}</span>
            <input
              type="file"
              accept=".sig,.p7s,.sgn,application/pkcs7-signature"
              onChange={(e) => setSignatureFile(e.target.files?.[0] ?? null)}
              className="block w-full text-sm text-fg"
            />
          </label>
          {signatureFile && (
            <p className="text-xs text-fg-muted">
              {t('portalSignature.cmsSelected', { name: signatureFile.name })}
            </p>
          )}
          <div className="flex gap-2">
            <Button
              variant="primary"
              disabled={signatureFile === null}
              loading={cmsMutation.isPending}
              onClick={() => cmsMutation.mutate()}
            >
              {t('portalSignature.cmsUpload')}
            </Button>
            <button
              type="button"
              onClick={() => setDeclining(true)}
              className="text-sm px-3 py-2 rounded-lg border border-line text-fg-muted hover:text-red-600 dark:hover:text-red-400"
            >
              {t('portalSignature.decline')}
            </button>
          </div>
        </div>
      )}

      {signature.status === 'PENDING' && !declining && !requiresCms && (
        <div className="flex flex-col gap-2 mt-1">
          <label className="block text-xs text-fg-muted">
            {t('portalSignature.signerName')}
            <input
              type="text"
              value={signerName}
              maxLength={300}
              onChange={(e) => setSignerName(e.target.value)}
              placeholder={t('portalSignature.signerPlaceholder')}
              className="mt-1 w-full px-3 py-2.5 rounded-lg border border-line bg-bg text-fg text-sm focus:outline-none focus:ring-2 focus:ring-accent"
            />
          </label>
          <label className="flex items-start gap-2 text-xs text-fg-muted">
            <input
              type="checkbox"
              checked={consent}
              onChange={(e) => setConsent(e.target.checked)}
              className="mt-0.5"
            />
            <span>
              {t('portalSignature.consent')}
            </span>
          </label>
          <div className="flex gap-2">
            <Button
              variant="primary"
              disabled={!canSign}
              loading={signMutation.isPending}
              onClick={() => signMutation.mutate()}
            >
              {t('portalSignature.sign')}
            </Button>
            <button
              type="button"
              onClick={() => setDeclining(true)}
              className="text-sm px-3 py-2 rounded-lg border border-line text-fg-muted hover:text-red-600 dark:hover:text-red-400"
            >
              {t('portalSignature.decline')}
            </button>
          </div>
        </div>
      )}

      {signature.status === 'PENDING' && declining && (
        <div className="flex flex-col gap-2 mt-1">
          <textarea
            value={reason}
            maxLength={1000}
            onChange={(e) => setReason(e.target.value)}
            placeholder={t('portalSignature.declineReasonPlaceholder')}
            rows={2}
            className="w-full px-3 py-2.5 rounded-lg border border-line bg-bg text-fg text-sm focus:outline-none focus:ring-2 focus:ring-accent"
          />
          <div className="flex gap-2">
            <Button
              variant="secondary"
              loading={declineMutation.isPending}
              onClick={() => declineMutation.mutate()}
            >
              {t('portalSignature.confirmDecline')}
            </Button>
            <button
              type="button"
              onClick={() => setDeclining(false)}
              className="text-sm px-3 py-2 rounded-lg border border-line text-fg-muted"
            >
              {t('portalSignature.back')}
            </button>
          </div>
        </div>
      )}

      {signature.status === 'SIGNED' && (
        <div className="flex flex-col gap-1">
          <p className="text-xs text-success">
            {t('portalSignature.signedPrefix', { name: signature.signerName })}
            {signature.signedAt && ` · ${new Date(signature.signedAt).toLocaleString(i18n.language.startsWith('ru') ? 'ru-RU' : 'en-US')}`}
          </p>
          {signature.certificateSubject && (
            <p className="text-xs text-fg-muted [overflow-wrap:anywhere]">
              {t('portalSignature.signedWithCertificate', { subject: signature.certificateSubject })}
            </p>
          )}
          <button
            type="button"
            onClick={handleProtocolDownload}
            className="self-start text-xs text-accent hover:underline"
          >
            {t('portalSignature.downloadProtocol')}
          </button>
        </div>
      )}
      {signature.status === 'DECLINED' && (
        <p className="text-xs text-danger">
          {signature.declineReason
            ? t('portalSignature.declinedWithReason', { reason: signature.declineReason })
            : t('portalSignature.declined')}
        </p>
      )}

      {actionError && <p className="text-sm text-danger mt-2">{actionError}</p>}
    </div>
  )
}
