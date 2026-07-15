import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { portalApi } from '../../api/portal'
import type { DocumentResponse, SignatureRequestResponse } from '../../types'
import { Button } from '../ui/Button'
import { Spinner } from '../ui/Spinner'
import { SignatureStatusBadge } from '../ui/SignatureStatusBadge'

const errorMessage = (error: unknown): string => {
  const response = (error as { response?: { data?: { message?: string } } })?.response
  return response?.data?.message ?? 'Не удалось выполнить действие. Попробуйте снова.'
}

export function PortalSignatureSection({ caseId }: { caseId: string }): JSX.Element | null {
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
      <h2 className="text-lg font-semibold text-light-text dark:text-dark-text mb-3">На подпись</h2>
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
  const [signerName, setSignerName] = useState('')
  const [consent, setConsent] = useState(false)
  const [declining, setDeclining] = useState(false)
  const [reason, setReason] = useState('')
  const [downloading, setDownloading] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)

  const signMutation = useMutation({
    mutationFn: () => portalApi.signDocument(signature.id, signerName.trim()),
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
      setActionError('Не удалось скачать документ.')
    } finally {
      setDownloading(false)
    }
  }

  const canSign = signerName.trim().length > 0 && consent

  return (
    <div className="card-elevated rounded-xl p-4">
      <div className="flex items-center gap-3 mb-2">
        <span className="flex-1 min-w-0 text-sm font-medium text-light-text dark:text-dark-text truncate">
          {document?.title ?? 'Документ'}
        </span>
        <SignatureStatusBadge status={signature.status} />
      </div>

      {signature.message && (
        <p className="text-sm text-light-secondary dark:text-dark-secondary mb-2">{signature.message}</p>
      )}

      {document && (
        <button
          type="button"
          onClick={() => void handleDownload()}
          disabled={downloading}
          className="text-sm text-light-accent dark:text-dark-accent hover:underline disabled:opacity-60 mb-3 inline-flex items-center gap-1"
        >
          {downloading ? <Spinner size="sm" /> : 'Скачать и проверить документ'}
        </button>
      )}

      {signature.status === 'PENDING' && !declining && (
        <div className="flex flex-col gap-2 mt-1">
          <label className="block text-xs text-light-secondary dark:text-dark-secondary">
            ФИО подписанта
            <input
              type="text"
              value={signerName}
              maxLength={300}
              onChange={(e) => setSignerName(e.target.value)}
              placeholder="Иванов Иван Иванович"
              className="mt-1 w-full px-3 py-2.5 rounded-lg border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg text-light-text dark:text-dark-text text-sm focus:outline-none focus:ring-2 focus:ring-light-accent dark:focus:ring-dark-accent"
            />
          </label>
          <label className="flex items-start gap-2 text-xs text-light-secondary dark:text-dark-secondary">
            <input
              type="checkbox"
              checked={consent}
              onChange={(e) => setConsent(e.target.checked)}
              className="mt-0.5"
            />
            <span>
              Я подтверждаю согласие подписать документ простой электронной подписью в соответствии со ст. 5
              Федерального закона № 63-ФЗ «Об электронной подписи».
            </span>
          </label>
          <div className="flex gap-2">
            <Button
              variant="primary"
              disabled={!canSign}
              loading={signMutation.isPending}
              onClick={() => signMutation.mutate()}
            >
              Подписать
            </Button>
            <button
              type="button"
              onClick={() => setDeclining(true)}
              className="text-sm px-3 py-2 rounded-lg border border-light-border dark:border-dark-border text-light-secondary dark:text-dark-secondary hover:text-red-600 dark:hover:text-red-400"
            >
              Отклонить
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
            placeholder="Причина отказа (необязательно)"
            rows={2}
            className="w-full px-3 py-2.5 rounded-lg border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg text-light-text dark:text-dark-text text-sm focus:outline-none focus:ring-2 focus:ring-light-accent dark:focus:ring-dark-accent"
          />
          <div className="flex gap-2">
            <Button
              variant="secondary"
              loading={declineMutation.isPending}
              onClick={() => declineMutation.mutate()}
            >
              Подтвердить отказ
            </Button>
            <button
              type="button"
              onClick={() => setDeclining(false)}
              className="text-sm px-3 py-2 rounded-lg border border-light-border dark:border-dark-border text-light-secondary dark:text-dark-secondary"
            >
              Назад
            </button>
          </div>
        </div>
      )}

      {signature.status === 'SIGNED' && (
        <p className="text-xs text-emerald-600 dark:text-emerald-400">
          Подписано: {signature.signerName}
          {signature.signedAt && ` · ${new Date(signature.signedAt).toLocaleString('ru-RU')}`}
        </p>
      )}
      {signature.status === 'DECLINED' && (
        <p className="text-xs text-red-600 dark:text-red-400">
          Вы отклонили подписание{signature.declineReason ? `: ${signature.declineReason}` : ''}.
        </p>
      )}

      {actionError && <p className="text-sm text-red-600 dark:text-red-400 mt-2">{actionError}</p>}
    </div>
  )
}
