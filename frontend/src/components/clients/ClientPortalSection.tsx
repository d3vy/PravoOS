import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { clientsApi } from '../../api/clients'
import type { PortalInviteStatusResponse } from '../../types'
import { Button } from '../ui/Button'
import { Spinner } from '../ui/Spinner'
import { useConfirm } from '../../hooks/useConfirm'
import i18n from '../../i18n'

interface ClientPortalSectionProps {
  clientId: string
  email: string | null
}

export function ClientPortalSection({ clientId, email }: ClientPortalSectionProps): JSX.Element {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const confirm = useConfirm()
  const [actionError, setActionError] = useState<string | null>(null)

  const { data, isLoading } = useQuery<PortalInviteStatusResponse>({
    queryKey: ['client-portal', clientId],
    queryFn: () => clientsApi.getPortalStatus(clientId),
    enabled: clientId !== '',
  })

  const invalidate = (): void => {
    queryClient.invalidateQueries({ queryKey: ['client-portal', clientId] })
  }

  const inviteMutation = useMutation({
    mutationFn: () => clientsApi.invitePortal(clientId),
    onSuccess: () => {
      setActionError(null)
      invalidate()
    },
    onError: () => setActionError(t('clientPortal.inviteError')),
  })

  const revokeMutation = useMutation({
    mutationFn: () => clientsApi.revokePortal(clientId),
    onSuccess: () => {
      setActionError(null)
      invalidate()
    },
    onError: () => setActionError(t('clientPortal.revokeError')),
  })

  const isBusy = inviteMutation.isPending || revokeMutation.isPending
  const hasEmail = email !== null && email.trim() !== ''

  return (
    <section className="mb-10 p-6 rounded-xl bg-surface border border-line">
      <div className="flex items-center justify-between gap-4 mb-2">
        <h2 className="text-sm font-semibold text-fg">{t('clientPortal.title')}</h2>
        {!isLoading && data && <StatusBadge status={data.status} />}
      </div>

      {isLoading ? (
        <div className="py-2">
          <Spinner size="sm" />
        </div>
      ) : (
        <>
          <p className="text-sm text-fg-muted mb-4">
            {renderDescription(data?.status ?? 'NONE', data?.expiresAt ?? null, hasEmail)}
          </p>

          {actionError && <p className="text-sm text-danger mb-3">{actionError}</p>}

          <div className="flex flex-wrap gap-2">
            {data?.status === 'ACCEPTED' ? (
              <Button
                variant="ghost"
                size="sm"
                loading={revokeMutation.isPending}
                disabled={isBusy}
                onClick={async () => {
                  const confirmed = await confirm({
                    title: t('clientPortal.revokeAccess'),
                    description: t('clientPortal.revokeConfirm'),
                    confirmLabel: t('clientPortal.revokeAccess'),
                    danger: true,
                  })
                  if (confirmed) revokeMutation.mutate()
                }}
              >
                {t('clientPortal.revokeAccess')}
              </Button>
            ) : data?.status === 'PENDING' ? (
              <>
                <Button
                  variant="secondary"
                  size="sm"
                  loading={inviteMutation.isPending}
                  disabled={isBusy || !hasEmail}
                  onClick={() => inviteMutation.mutate()}
                >
                  {t('clientPortal.resend')}
                </Button>
                <Button
                  variant="ghost"
                  size="sm"
                  loading={revokeMutation.isPending}
                  disabled={isBusy}
                  onClick={() => revokeMutation.mutate()}
                >
                  {t('clientPortal.revoke')}
                </Button>
              </>
            ) : (
              <Button
                variant="primary"
                size="sm"
                loading={inviteMutation.isPending}
                disabled={isBusy || !hasEmail}
                onClick={() => inviteMutation.mutate()}
              >
                {t('clientPortal.invite')}
              </Button>
            )}
          </div>
        </>
      )}
    </section>
  )
}

function StatusBadge({ status }: { status: PortalInviteStatusResponse['status'] }): JSX.Element | null {
  const { t } = useTranslation()
  if (status === 'ACCEPTED') {
    return (
      <span className="text-xs px-2 py-0.5 rounded-full bg-success-soft text-success">
        {t('clientPortal.badgeAccepted')}
      </span>
    )
  }
  if (status === 'PENDING') {
    return (
      <span className="text-xs px-2 py-0.5 rounded-full bg-warning-soft text-warning">
        {t('clientPortal.badgePending')}
      </span>
    )
  }
  return null
}

function renderDescription(
  status: PortalInviteStatusResponse['status'],
  expiresAt: string | null,
  hasEmail: boolean,
): string {
  if (status === 'ACCEPTED') {
    return i18n.t('clientPortal.descAccepted')
  }
  if (status === 'PENDING') {
    const locale = i18n.language.startsWith('ru') ? 'ru-RU' : 'en-US'
    const until = expiresAt ? i18n.t('clientPortal.descPendingUntil', { date: new Date(expiresAt).toLocaleDateString(locale) }) : ''
    return `${i18n.t('clientPortal.descPending')}${until}`
  }
  if (!hasEmail) {
    return i18n.t('clientPortal.descNoEmail')
  }
  return i18n.t('clientPortal.descNone')
}
