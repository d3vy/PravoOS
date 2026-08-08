import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import i18n from '../../i18n'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { signaturesApi } from '../../api/signatures'
import { useAuthStore } from '../../store/authStore'
import type {
  DocumentResponse,
  SignatureProviderType,
  SignatureRequestResponse,
  SignatureSignerRole,
} from '../../types'
import { Button } from '../ui/Button'
import { SignatureStatusBadge } from '../ui/SignatureStatusBadge'

const errorMessage = (error: unknown): string => {
  const response = (error as { response?: { data?: { message?: string } } })?.response
  return response?.data?.message ?? i18n.t('signature.actionError')
}

const formatDateTime = (value: string): string =>
  new Date(value).toLocaleString(i18n.language.startsWith('ru') ? 'ru-RU' : 'en-US')

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
  const [signerRole, setSignerRole] = useState<SignatureSignerRole>('CLIENT')
  const [actionError, setActionError] = useState<string | null>(null)

  const readyDocuments = documents.filter((doc) => doc.status === 'READY')
  const signableDocuments =
    signerRole === 'CLIENT' ? readyDocuments.filter((doc) => doc.visibleToClient) : readyDocuments

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
      signaturesApi.create(caseId, {
        documentId,
        provider,
        signerRole,
        message: message.trim() || undefined,
      }),
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

  const changeSignerRole = (role: SignatureSignerRole): void => {
    setSignerRole(role)
    setDocumentId('')
  }

  return (
    <section className="mb-10 p-5 rounded-2xl bg-surface border border-line">
      <h2 className="text-sm font-semibold text-fg mb-1">{t('signature.title')}</h2>
      <p className="text-xs text-fg-muted mb-3">
        {t('signature.hint')}
      </p>

      <div className="mb-3">
        <label htmlFor="signature-signer-role" className="block text-xs text-fg-muted mb-1">
          {t('signature.signerRoleLabel')}
        </label>
        <select
          id="signature-signer-role"
          value={signerRole}
          onChange={(e) => changeSignerRole(e.target.value as SignatureSignerRole)}
          className="w-full px-3 py-2.5 rounded-lg border border-line bg-bg text-fg text-sm focus:outline-none focus:ring-2 focus:ring-accent"
        >
          <option value="CLIENT">{t('signature.signerRoleClient')}</option>
          <option value="LAWYER">{t('signature.signerRoleLawyer')}</option>
        </select>
        <p className="text-xs text-fg-muted mt-1">
          {signerRole === 'CLIENT'
            ? t('signature.signerRoleClientHint')
            : t('signature.signerRoleLawyerHint')}
        </p>
      </div>

      {signableDocuments.length === 0 ? (
        <p className="text-sm text-fg-muted">
          {signerRole === 'CLIENT' ? t('signature.noDocsHint') : t('signature.noLawyerDocsHint')}
        </p>
      ) : (
        <div className="flex flex-col gap-3">
          <div>
            <label htmlFor="signature-document" className="block text-xs text-fg-muted mb-1">
              {t('signature.documentLabel')}
            </label>
            <select
              id="signature-document"
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
              {signerRole === 'CLIENT'
                ? t('signature.messageLabel')
                : t('signature.messageLawyerLabel')}
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
              {signerRole === 'CLIENT'
                ? t('signature.sendForSignature')
                : t('signature.createLawyerRequest')}
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
                <span className="text-[11px] px-2 py-0.5 rounded-md border border-line text-fg-muted whitespace-nowrap">
                  {signature.signerRole === 'LAWYER'
                    ? t('signature.signerRoleLawyerShort')
                    : t('signature.signerRoleClientShort')}
                </span>
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
              <LawyerSigningPanel caseId={caseId} signature={signature} onChanged={invalidate} />
              {signature.status === 'SIGNED' && (
                <p className="text-xs text-success">
                  {t('signature.signedBy', { name: signature.signerName })}
                  {signature.signedAt && ` · ${formatDateTime(signature.signedAt)}`}
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

function LawyerSigningPanel({
  caseId,
  signature,
  onChanged,
}: {
  caseId: string
  signature: SignatureRequestResponse
  onChanged: () => void
}): JSX.Element | null {
  const { t } = useTranslation()
  const currentUserId = useAuthStore((state) => state.user?.userId)
  const [signerName, setSignerName] = useState('')
  const [consent, setConsent] = useState(false)
  const [declining, setDeclining] = useState(false)
  const [reason, setReason] = useState('')
  const [signatureFile, setSignatureFile] = useState<File | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  const signMutation = useMutation({
    mutationFn: () => signaturesApi.sign(caseId, signature.id, signerName.trim()),
    onSuccess: onChanged,
    onError: (error) => setActionError(errorMessage(error)),
  })

  const cmsMutation = useMutation({
    mutationFn: () => signaturesApi.signWithCms(caseId, signature.id, signatureFile as File),
    onSuccess: onChanged,
    onError: (error) => setActionError(errorMessage(error)),
  })

  const declineMutation = useMutation({
    mutationFn: () => signaturesApi.decline(caseId, signature.id, reason.trim()),
    onSuccess: onChanged,
    onError: (error) => setActionError(errorMessage(error)),
  })

  const isMyTurn =
    signature.status === 'PENDING' &&
    signature.signerRole === 'LAWYER' &&
    signature.signerLawyerId === currentUserId

  if (!isMyTurn) {
    return null
  }

  const requiresCms = signature.provider === 'DETACHED_CMS'
  const canSign = signerName.trim().length > 0 && consent

  return (
    <div className="flex flex-col gap-2 my-2 p-3 rounded-lg border border-line">
      <p className="text-xs font-medium text-fg">{t('signature.yourTurn')}</p>

      {!declining && requiresCms && (
        <>
          <p className="text-xs text-fg-muted">{t('signature.cmsHint')}</p>
          <label className="text-xs text-fg-muted">
            <span className="block mb-1">{t('signature.cmsChooseFile')}</span>
            <input
              type="file"
              accept=".sig,.p7s,.sgn,application/pkcs7-signature"
              onChange={(e) => setSignatureFile(e.target.files?.[0] ?? null)}
              className="block w-full text-sm text-fg"
            />
          </label>
          <div className="flex gap-2">
            <Button
              variant="primary"
              disabled={signatureFile === null}
              loading={cmsMutation.isPending}
              onClick={() => cmsMutation.mutate()}
            >
              {t('signature.cmsUpload')}
            </Button>
            <button
              type="button"
              onClick={() => setDeclining(true)}
              className="text-sm px-3 py-2 rounded-lg border border-line text-fg-muted hover:text-red-600 dark:hover:text-red-400"
            >
              {t('signature.declineAction')}
            </button>
          </div>
        </>
      )}

      {!declining && !requiresCms && (
        <>
          <label className="block text-xs text-fg-muted">
            {t('signature.signerNameLabel')}
            <input
              type="text"
              value={signerName}
              maxLength={300}
              onChange={(e) => setSignerName(e.target.value)}
              placeholder={t('signature.signerNamePlaceholder')}
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
            <span>{t('signature.lawyerConsent')}</span>
          </label>
          <div className="flex gap-2">
            <Button
              variant="primary"
              disabled={!canSign}
              loading={signMutation.isPending}
              onClick={() => signMutation.mutate()}
            >
              {t('signature.signAction')}
            </Button>
            <button
              type="button"
              onClick={() => setDeclining(true)}
              className="text-sm px-3 py-2 rounded-lg border border-line text-fg-muted hover:text-red-600 dark:hover:text-red-400"
            >
              {t('signature.declineAction')}
            </button>
          </div>
        </>
      )}

      {declining && (
        <>
          <textarea
            value={reason}
            maxLength={1000}
            onChange={(e) => setReason(e.target.value)}
            placeholder={t('signature.declineReasonPlaceholder')}
            rows={2}
            className="w-full px-3 py-2.5 rounded-lg border border-line bg-bg text-fg text-sm focus:outline-none focus:ring-2 focus:ring-accent"
          />
          <div className="flex gap-2">
            <Button
              variant="secondary"
              loading={declineMutation.isPending}
              onClick={() => declineMutation.mutate()}
            >
              {t('signature.confirmDecline')}
            </Button>
            <button
              type="button"
              onClick={() => setDeclining(false)}
              className="text-sm px-3 py-2 rounded-lg border border-line text-fg-muted"
            >
              {t('signature.back')}
            </button>
          </div>
        </>
      )}

      {actionError && <p className="text-sm text-danger">{actionError}</p>}
    </div>
  )
}
