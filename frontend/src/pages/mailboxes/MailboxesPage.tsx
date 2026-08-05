import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { mailboxesApi } from '../../api/mailboxes'
import type { CreateMailboxRequest, MailHostPresetResponse, MailboxResponse, UpdateMailboxRequest } from '../../types'
import { Button } from '../../components/ui/Button'
import { Modal } from '../../components/ui/Modal'
import { SkeletonList } from '../../components/ui/Skeleton'
import { EmptyState } from '../../components/ui/EmptyState'
import { useConfirm } from '../../hooks/useConfirm'
import { useToast } from '../../hooks/useToast'
import { MailboxStatusBadge } from '../../components/mailboxes/MailboxStatusBadge'
import { MailboxForm } from '../../components/mailboxes/MailboxForm'
import { UnlinkedEmailsSection } from '../../components/mailboxes/UnlinkedEmailsSection'

type Tab = 'mailboxes' | 'unlinked'

export default function MailboxesPage(): JSX.Element {
  const { t, i18n } = useTranslation()
  const queryClient = useQueryClient()
  const toast = useToast()
  const confirm = useConfirm()
  const [tab, setTab] = useState<Tab>('mailboxes')
  const [showCreate, setShowCreate] = useState(false)
  const [editing, setEditing] = useState<MailboxResponse | null>(null)
  const [formError, setFormError] = useState<string | null>(null)

  const locale = i18n.language.startsWith('ru') ? 'ru-RU' : 'en-US'

  const { data: mailboxes = [], isLoading } = useQuery<MailboxResponse[]>({
    queryKey: ['mailboxes'],
    queryFn: mailboxesApi.list,
  })

  const { data: presets = [] } = useQuery<MailHostPresetResponse[]>({
    queryKey: ['mailbox-presets'],
    queryFn: mailboxesApi.presets,
  })

  const invalidate = (): void => {
    queryClient.invalidateQueries({ queryKey: ['mailboxes'] })
  }

  const createMutation = useMutation({
    mutationFn: mailboxesApi.create,
    onSuccess: () => {
      invalidate()
      setShowCreate(false)
      setFormError(null)
    },
    onError: () => setFormError(t('mailboxes.createError')),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: Parameters<typeof mailboxesApi.update>[1] }) =>
      mailboxesApi.update(id, data),
    onSuccess: () => {
      invalidate()
      setEditing(null)
      setFormError(null)
    },
    onError: () => setFormError(t('mailboxes.updateError')),
  })

  const testMutation = useMutation({
    mutationFn: mailboxesApi.test,
    onSuccess: (result) => {
      invalidate()
      if (result.success) {
        toast.success(t('mailboxes.testSuccess'))
      } else {
        toast.error(result.message ?? t('mailboxes.testFailed'))
      }
    },
    onError: () => toast.error(t('mailboxes.testFailed')),
  })

  const syncMutation = useMutation({
    mutationFn: mailboxesApi.sync,
    onSuccess: (result) => {
      invalidate()
      if (result.success) {
        toast.success(t('mailboxes.syncSuccess', { saved: result.saved }))
      } else {
        toast.error(result.error ?? t('mailboxes.syncFailed'))
      }
    },
    onError: () => toast.error(t('mailboxes.syncFailed')),
  })

  const deleteMutation = useMutation({
    mutationFn: mailboxesApi.remove,
    onSuccess: invalidate,
  })

  const handleDelete = async (mailbox: MailboxResponse): Promise<void> => {
    const confirmed = await confirm({
      title: t('mailboxes.deleteTitle'),
      description: t('mailboxes.deleteConfirm', { email: mailbox.emailAddress }),
      danger: true,
    })
    if (confirmed) deleteMutation.mutate(mailbox.id)
  }

  const tabs: { id: Tab; label: string }[] = [
    { id: 'mailboxes', label: t('mailboxes.tabMailboxes') },
    { id: 'unlinked', label: t('mailboxes.tabUnlinked') },
  ]

  return (
    <div className="bg-bg">
      <div className="page-container py-8 max-w-3xl">
        <div className="flex items-start justify-between gap-4 mb-6">
          <div>
            <h1 className="text-3xl font-semibold text-fg mb-1">{t('mailboxes.title')}</h1>
            <p className="text-sm text-fg-muted">{t('mailboxes.subtitle')}</p>
          </div>
          {tab === 'mailboxes' && (
            <Button size="sm" onClick={() => setShowCreate(true)}>
              {t('mailboxes.addMailbox')}
            </Button>
          )}
        </div>

        <div className="flex gap-1 border-b border-line mb-6">
          {tabs.map((item) => (
            <button
              key={item.id}
              type="button"
              onClick={() => setTab(item.id)}
              className={`px-4 py-2.5 text-sm font-medium -mb-px border-b-2 transition-colors ${
                tab === item.id ? 'border-accent text-fg' : 'border-transparent text-fg-muted hover:text-fg'
              }`}
            >
              {item.label}
            </button>
          ))}
        </div>

        {tab === 'mailboxes' ? (
          isLoading ? (
            <SkeletonList count={3} />
          ) : mailboxes.length === 0 ? (
            <EmptyState
              description={t('mailboxes.empty')}
              action={{ label: t('mailboxes.addMailbox'), onClick: () => setShowCreate(true) }}
            />
          ) : (
            <div className="flex flex-col gap-3">
              {mailboxes.map((mailbox) => (
                <div key={mailbox.id} className="p-4 rounded-lg bg-surface border border-line">
                  <div className="flex items-start justify-between gap-3 mb-2">
                    <div className="min-w-0">
                      <p className="text-sm font-medium text-fg truncate">{mailbox.emailAddress}</p>
                      <p className="text-xs text-fg-muted">
                        {mailbox.imapHost}:{mailbox.imapPort} · {mailbox.folder}
                      </p>
                    </div>
                    <MailboxStatusBadge status={mailbox.status} />
                  </div>

                  {mailbox.status === 'ERROR' && mailbox.lastError && (
                    <p className="text-xs text-danger mb-2">{mailbox.lastError}</p>
                  )}

                  <p className="text-xs text-fg-muted mb-3">
                    {mailbox.lastSyncAt
                      ? t('mailboxes.lastSync', { date: new Date(mailbox.lastSyncAt).toLocaleString(locale) })
                      : t('mailboxes.neverSynced')}
                    {!mailbox.syncEnabled && ` · ${t('mailboxes.syncDisabled')}`}
                  </p>

                  <div className="flex flex-wrap gap-2">
                    <Button
                      size="sm"
                      variant="secondary"
                      loading={testMutation.isPending && testMutation.variables === mailbox.id}
                      onClick={() => testMutation.mutate(mailbox.id)}
                    >
                      {t('mailboxes.testButton')}
                    </Button>
                    <Button
                      size="sm"
                      variant="secondary"
                      disabled={!mailbox.syncEnabled}
                      loading={syncMutation.isPending && syncMutation.variables === mailbox.id}
                      onClick={() => syncMutation.mutate(mailbox.id)}
                    >
                      {t('mailboxes.syncButton')}
                    </Button>
                    <Button size="sm" variant="ghost" onClick={() => setEditing(mailbox)}>
                      {t('mailboxes.editButton')}
                    </Button>
                    <Button
                      size="sm"
                      variant="ghost"
                      loading={deleteMutation.isPending && deleteMutation.variables === mailbox.id}
                      onClick={() => void handleDelete(mailbox)}
                    >
                      {t('mailboxes.deleteButton')}
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          )
        ) : (
          <UnlinkedEmailsSection />
        )}
      </div>

      <Modal open={showCreate} onClose={() => setShowCreate(false)} title={t('mailboxes.addMailbox')}>
        <MailboxForm
          mode="create"
          presets={presets}
          isSubmitting={createMutation.isPending}
          error={formError}
          onCancel={() => setShowCreate(false)}
          onSubmit={(data) => createMutation.mutate(data as CreateMailboxRequest)}
        />
      </Modal>

      <Modal open={editing !== null} onClose={() => setEditing(null)} title={t('mailboxes.editMailbox')}>
        {editing && (
          <MailboxForm
            mode="edit"
            initial={editing}
            presets={presets}
            isSubmitting={updateMutation.isPending}
            error={formError}
            onCancel={() => setEditing(null)}
            onSubmit={(data) => updateMutation.mutate({ id: editing.id, data: data as UpdateMailboxRequest })}
          />
        )}
      </Modal>
    </div>
  )
}
