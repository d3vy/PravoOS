import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import i18n from '../../i18n'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { signaturesApi } from '../../api/signatures'
import type { DocumentResponse, SignatureProviderType, SignatureRequestResponse } from '../../types'
import { Button } from '../ui/Button'
import { SignatureStatusBadge } from '../ui/SignatureStatusBadge'

const errorMessage = (error: unknown): string => {
  const response = (error as { response?: { data?: { message?: string } } })?.response
  return response?.data?.message ?? i18n.t('signature.actionError')
}

export function CaseSignatureSection({
  caseId,
  documents,
}: {
  caseId: string
  documents: DocumentResponse[]
}): JSX.Element {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [documentId, setDocumentId] = useState('')
  const [message, setMessage] = useState('')
  const [provider, setProvider] = useState<SignatureProviderType>('SIMPLE')
  const [actionError, setActionError] = useState<string | null>(null)

  const signableDocuments = documents.filter((doc) => doc.status === 'READY' && doc.visibleToClient)

  const { data: signatures = [] } = useQuery<SignatureRequestResponse[]>({
    queryKey: ['case-signatures', caseId],
    queryFn: () => signaturesApi.listByCase(caseId),
    enabled: caseId !== '',
  })

  const invalidate = (): void => {
    queryClient.invalidateQueries({ queryKey: ['case-signatures', caseId] })
  }

  const createMutation = useMutation({
    mutationFn: () =>
      signaturesApi.create(caseId, { documentId, provider, message: message.trim() || undefined }),
    onSuccess: () => {
      invalidate()
      setDocumentId('')
      setMessage('')
      setActionError(null)
    },
    onError: (error) => setActionError(errorMessage(error)),
  })

  const cancelMutation = useMutation({
    mutationFn: (signatureId: string) => signaturesApi.cancel(caseId, signatureId),
    onSuccess: invalidate,
    onError: (error) => setActionError(errorMessage(error)),
  })

  const downloadProtocol = (signatureId: string): void => {
    signaturesApi
      .downloadProtocol(caseId, signatureId)
      .catch((error: unknown) => setActionError(errorMessage(error)))
  }

  const downloadSignatureFile = (signatureId: string): void => {
    signaturesApi
      .downloadSignatureFile(caseId, signatureId)
      .catch((error: unknown) => setActionError(errorMessage(error)))
  }

  const documentTitle = (id: string): string =>
    documents.find((doc) => doc.id === id)?.title ?? t('signature.documentFallback')

  return (
    <section className="mb-10 p-5 rounded-xl bg-surface border border-line">
      <h2 className="text-sm font-semibold text-fg mb-1">{t('signature.title')}</h2>
      <p className="text-xs text-fg-muted mb-3">
        {t('signature.hint')}
      </p>

      {signableDocuments.length === 0 ? (
        <p className="text-sm text-fg-muted">
          {t('signature.noDocsHint')}
        </p>
      ) : (
        <div className="flex flex-col gap-3">
          <div>
            <label className="block text-xs text-fg-muted mb-1">{t('signature.documentLabel')}</label>
            <select
              value={documentId}
              onChange={(e) => setDocumentId(e.target.value)}
              className="w-full px-3 py-2.5 rounded-lg border border-line bg-bg text-fg text-sm focus:outline-none focus:ring-2 focus:ring-accent"
            >
              <option value="">{t('signature.selectDocument')}</option>
              {signableDocuments.map((doc) => (
                <option key={doc.id} value={doc.id}>{doc.title}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-xs text-fg-muted mb-1">
              {t('signature.messageLabel')}
            </label>
            <input
              type="text"
              value={message}
              maxLength={1000}
              onChange={(e) => setMessage(e.target.value)}
              placeholder={t('signature.messagePlaceholder')}
              className="w-full px-3 py-2.5 rounded-lg border border-line bg-bg text-fg text-sm focus:outline-none focus:ring-2 focus:ring-accent"
            />
          </div>
          <div>
            <label className="block text-xs text-fg-muted mb-1">
              {t('signature.providerLabel')}
            </label>
            <select
              value={provider}
              onChange={(e) => setProvider(e.target.value as SignatureProviderType)}
              className="w-full px-3 py-2.5 rounded-lg border border-line bg-bg text-fg text-sm focus:outline-none focus:ring-2 focus:ring-accent"
            >
              <option value="SIMPLE">{t('signature.providerSimple')}</option>
              <option value="DETACHED_CMS">{t('signature.providerCms')}</option>
            </select>
            {provider === 'DETACHED_CMS' && (
              <p className="text-xs text-fg-muted mt-1">
                {t('signature.providerCmsHint')}
              </p>
            )}
          </div>
          <div>
            <Button
              variant="primary"
              disabled={documentId === ''}
              loading={createMutation.isPending}
              onClick={() => createMutation.mutate()}
            >
              {t('signature.sendForSignature')}
            </Button>
          </div>
        </div>
      )}

      {actionError && <p className="text-sm text-danger mt-3">{actionError}</p>}

      {signatures.length > 0 && (
        <div className="flex flex-col gap-3 mt-5">
          {signatures.map((signature) => (
            <div
              key={signature.id}
              className="p-4 rounded-lg border border-line bg-bg"
            >
              <div className="flex items-center gap-3 mb-2">
                <p className="flex-1 min-w-0 text-sm font-medium text-fg truncate">
                  {documentTitle(signature.documentId)}
                </p>
                <SignatureStatusBadge status={signature.status} />
                {signature.status === 'PENDING' && (
                  <button
                    type="button"
                    onClick={() => cancelMutation.mutate(signature.id)}
                    disabled={cancelMutation.isPending}
                    className="text-xs px-2 py-1 rounded-md border border-line text-fg-muted hover:text-red-600 dark:hover:text-red-400 disabled:opacity-60"
                  >
                    {t('signature.cancel')}
                  </button>
                )}
              </div>
              {signature.message && (
                <p className="text-xs text-fg-muted mb-1">{signature.message}</p>
              )}
              {signature.status === 'SIGNED' && (
                <p className="text-xs text-success">
                  {t('signature.signedBy', { name: signature.signerName })}
                  {signature.signedAt && ` · ${new Date(signature.signedAt).toLocaleString(i18n.language.startsWith('ru') ? 'ru-RU' : 'en-US')}`}
                  {signature.signerIp && ` · IP ${signature.signerIp}`}
                </p>
              )}
              {signature.status === 'SIGNED' && signature.certificateSubject && (
                <div className="text-xs text-fg-muted mt-1 [overflow-wrap:anywhere]">
                  <p>{t('signature.certificate', { subject: signature.certificateSubject })}</p>
                  {signature.certificateSerial && (
                    <p>{t('signature.certificateSerial', { serial: signature.certificateSerial })}</p>
                  )}
                  {signature.certificateValidTo && (
                    <p>
                      {t('signature.certificateValidTo', {
                        date: new Date(signature.certificateValidTo).toLocaleDateString(
                          i18n.language.startsWith('ru') ? 'ru-RU' : 'en-US'
                        ),
                      })}
                    </p>
                  )}
                </div>
              )}
              {signature.status === 'SIGNED' && (
                <div className="flex flex-wrap gap-3 mt-2">
                  <button
                    type="button"
                    onClick={() => downloadProtocol(signature.id)}
                    className="text-xs text-accent hover:underline"
                  >
                    {t('signature.downloadProtocol')}
                  </button>
                  {signature.hasSignatureFile && (
                    <button
                      type="button"
                      onClick={() => downloadSignatureFile(signature.id)}
                      className="text-xs text-accent hover:underline"
                    >
                      {t('signature.downloadSignatureFile')}
                    </button>
                  )}
                </div>
              )}
              {signature.status === 'DECLINED' && signature.declineReason && (
                <p className="text-xs text-danger">{t('signature.declineReason', { reason: signature.declineReason })}</p>
              )}
              <p className="text-[11px] font-mono text-fg-muted mt-1 [overflow-wrap:anywhere]">
                SHA-256: {signature.documentHash}
              </p>
            </div>
          ))}
        </div>
      )}
    </section>
  )
}
