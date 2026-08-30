import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import i18n from '../../i18n'
import { adminApi } from '../../api/admin'
import type { SubjectRequestResponse } from '../../types'
import { Button } from '../../components/ui/Button'
import { QueryState } from '../../components/ui/QueryState'
import { EmptyState } from '../../components/ui/EmptyState'
import { DataTable, type DataTableColumn } from '../../components/ui/DataTable'
import { PageHeader } from '../../components/ui/PageHeader'
import { Modal } from '../../components/ui/Modal'
import { PrivacyRequestStatusBadge } from '../../components/ui/Badge'

type Action = 'complete' | 'reject'

interface PendingAction {
  request: SubjectRequestResponse
  action: Action
}

export default function PrivacyRequestsPage(): JSX.Element {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [pendingAction, setPendingAction] = useState<PendingAction | null>(null)
  const [note, setNote] = useState('')

  const { data: requests, isLoading } = useQuery<SubjectRequestResponse[]>({
    queryKey: ['admin-privacy-requests'],
    queryFn: adminApi.getPendingPrivacyRequests,
  })

  const completeMutation = useMutation({
    mutationFn: ({ requestId, note: value }: { requestId: string; note?: string }) =>
      adminApi.completePrivacyRequest(requestId, value),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-privacy-requests'] })
      closeModal()
    },
  })

  const rejectMutation = useMutation({
    mutationFn: ({ requestId, reason }: { requestId: string; reason?: string }) =>
      adminApi.rejectPrivacyRequest(requestId, reason),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-privacy-requests'] })
      closeModal()
    },
  })

  const closeModal = (): void => {
    setPendingAction(null)
    setNote('')
  }

  const confirmAction = (): void => {
    if (!pendingAction) return
    const { request, action } = pendingAction
    if (action === 'complete') {
      completeMutation.mutate({ requestId: request.id, note: note.trim() || undefined })
    } else {
      rejectMutation.mutate({ requestId: request.id, reason: note.trim() || undefined })
    }
  }

  const dateLocale = i18n.language.startsWith('ru') ? 'ru-RU' : 'en-US'
  const isSubmitting = completeMutation.isPending || rejectMutation.isPending

  const columns = useMemo<DataTableColumn<SubjectRequestResponse>[]>(
    () => [
      {
        id: 'subjectRef',
        header: t('adminPrivacyRequests.columnSubject'),
        alwaysVisible: true,
        value: (row) => row.subjectRef,
      },
      {
        id: 'type',
        header: t('adminPrivacyRequests.columnType'),
        value: (row) => row.type,
        render: (row) => t(`adminPrivacyRequests.type.${row.type}`),
      },
      {
        id: 'status',
        header: t('adminPrivacyRequests.columnStatus'),
        value: (row) => row.status,
        render: (row) => <PrivacyRequestStatusBadge status={row.status} />,
      },
      {
        id: 'requestedAt',
        header: t('adminPrivacyRequests.columnRequested'),
        value: (row) => row.requestedAt,
        render: (row) => new Date(row.requestedAt).toLocaleString(dateLocale),
      },
      {
        id: 'dueAt',
        header: t('adminPrivacyRequests.columnDue'),
        value: (row) => row.dueAt,
        render: (row) => new Date(row.dueAt).toLocaleString(dateLocale),
      },
    ],
    [t, dateLocale]
  )

  return (
    <div className="p-6 lg:p-8">
      <PageHeader
        title={t('adminPrivacyRequests.title')}
        description={t('adminPrivacyRequests.subtitle')}
      />

      {isLoading ? (
        <QueryState isLoading spinnerSize="lg" />
      ) : (
        <DataTable
          rows={requests ?? []}
          columns={columns}
          rowId={(row) => row.id}
          rowActions={(request) => (
            <>
              <Button
                variant="primary"
                size="sm"
                onClick={() => setPendingAction({ request, action: 'complete' })}
              >
                {t('adminPrivacyRequests.complete')}
              </Button>
              <Button
                variant="danger"
                size="sm"
                onClick={() => setPendingAction({ request, action: 'reject' })}
              >
                {t('adminPrivacyRequests.reject')}
              </Button>
            </>
          )}
          emptyState={<EmptyState illustration="users" description={t('adminPrivacyRequests.empty')} />}
        />
      )}

      <Modal
        open={pendingAction !== null}
        onClose={closeModal}
        title={
          pendingAction?.action === 'complete'
            ? t('adminPrivacyRequests.completeTitle')
            : t('adminPrivacyRequests.rejectTitle')
        }
        footer={
          <>
            <Button variant="secondary" size="sm" onClick={closeModal}>
              {t('common.cancel')}
            </Button>
            <Button
              variant={pendingAction?.action === 'reject' ? 'danger' : 'primary'}
              size="sm"
              onClick={confirmAction}
              loading={isSubmitting}
              disabled={isSubmitting}
            >
              {pendingAction?.action === 'complete'
                ? t('adminPrivacyRequests.complete')
                : t('adminPrivacyRequests.reject')}
            </Button>
          </>
        }
      >
        <label className="block text-sm font-medium text-fg mb-2">
          {pendingAction?.action === 'complete'
            ? t('adminPrivacyRequests.noteLabel')
            : t('adminPrivacyRequests.reasonLabel')}
        </label>
        <textarea
          className="w-full rounded-lg border border-line bg-bg px-3 py-2 text-sm text-fg focus:outline-none focus:ring-2 focus:ring-primary"
          rows={3}
          value={note}
          onChange={(event) => setNote(event.target.value)}
        />
      </Modal>
    </div>
  )
}
