import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import i18n from '../../i18n'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { signaturesApi } from '../../api/signatures'
import type { DocumentResponse, SignatureRequestResponse } from '../../types'
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
      signaturesApi.create(caseId, { documentId, message: message.trim() || undefined }),
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

  const documentTitle = (id: string): string =>
    documents.find((doc) => doc.id === id)?.title ?? t('signature.documentFallback')

  return (
    <section className="mb-10 p-5 rounded-xl bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border">
      <h2 className="text-sm font-semibold text-light-text dark:text-dark-text mb-1">{t('signature.title')}</h2>
      <p className="text-xs text-light-secondary dark:text-dark-secondary mb-3">
        {t('signature.hint')}
      </p>

      {signableDocuments.length === 0 ? (
        <p className="text-sm text-light-secondary dark:text-dark-secondary">
          {t('signature.noDocsHint')}
        </p>
      ) : (
        <div className="flex flex-col gap-3">
          <div>
            <label className="block text-xs text-light-secondary dark:text-dark-secondary mb-1">{t('signature.documentLabel')}</label>
            <select
              value={documentId}
              onChange={(e) => setDocumentId(e.target.value)}
              className="w-full px-3 py-2.5 rounded-lg border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg text-light-text dark:text-dark-text text-sm focus:outline-none focus:ring-2 focus:ring-light-accent dark:focus:ring-dark-accent"
            >
              <option value="">{t('signature.selectDocument')}</option>
              {signableDocuments.map((doc) => (
                <option key={doc.id} value={doc.id}>{doc.title}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-xs text-light-secondary dark:text-dark-secondary mb-1">
              {t('signature.messageLabel')}
            </label>
            <input
              type="text"
              value={message}
              maxLength={1000}
              onChange={(e) => setMessage(e.target.value)}
              placeholder={t('signature.messagePlaceholder')}
              className="w-full px-3 py-2.5 rounded-lg border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg text-light-text dark:text-dark-text text-sm focus:outline-none focus:ring-2 focus:ring-light-accent dark:focus:ring-dark-accent"
            />
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

      {actionError && <p className="text-sm text-red-600 dark:text-red-400 mt-3">{actionError}</p>}

      {signatures.length > 0 && (
        <div className="flex flex-col gap-3 mt-5">
          {signatures.map((signature) => (
            <div
              key={signature.id}
              className="p-4 rounded-lg border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg"
            >
              <div className="flex items-center gap-3 mb-2">
                <p className="flex-1 min-w-0 text-sm font-medium text-light-text dark:text-dark-text truncate">
                  {documentTitle(signature.documentId)}
                </p>
                <SignatureStatusBadge status={signature.status} />
                {signature.status === 'PENDING' && (
                  <button
                    type="button"
                    onClick={() => cancelMutation.mutate(signature.id)}
                    disabled={cancelMutation.isPending}
                    className="text-xs px-2 py-1 rounded-md border border-light-border dark:border-dark-border text-light-secondary dark:text-dark-secondary hover:text-red-600 dark:hover:text-red-400 disabled:opacity-60"
                  >
                    {t('signature.cancel')}
                  </button>
                )}
              </div>
              {signature.message && (
                <p className="text-xs text-light-secondary dark:text-dark-secondary mb-1">{signature.message}</p>
              )}
              {signature.status === 'SIGNED' && (
                <p className="text-xs text-emerald-600 dark:text-emerald-400">
                  {t('signature.signedBy', { name: signature.signerName })}
                  {signature.signedAt && ` · ${new Date(signature.signedAt).toLocaleString(i18n.language.startsWith('ru') ? 'ru-RU' : 'en-US')}`}
                  {signature.signerIp && ` · IP ${signature.signerIp}`}
                </p>
              )}
              {signature.status === 'DECLINED' && signature.declineReason && (
                <p className="text-xs text-red-600 dark:text-red-400">{t('signature.declineReason', { reason: signature.declineReason })}</p>
              )}
              <p className="text-[11px] font-mono text-light-secondary dark:text-dark-secondary mt-1 [overflow-wrap:anywhere]">
                SHA-256: {signature.documentHash}
              </p>
            </div>
          ))}
        </div>
      )}
    </section>
  )
}
