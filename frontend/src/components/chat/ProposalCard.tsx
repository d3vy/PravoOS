import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation } from '@tanstack/react-query'
import { chatApi } from '../../api/chat'
import type { AiActionProposal } from '../../types'
import { Spinner } from '../ui/Spinner'

const STATUS_TONE: Record<string, string> = {
  APPROVED: 'text-success',
  REJECTED: 'text-fg-muted',
  EXPIRED: 'text-fg-muted',
  FAILED: 'text-danger',
}

function ProposalOutcome({ proposal }: { proposal: AiActionProposal }): JSX.Element {
  const { t } = useTranslation()
  const tone = STATUS_TONE[proposal.status] ?? 'text-fg-muted'
  const detail =
    proposal.status === 'FAILED'
      ? proposal.failureReason
      : proposal.status === 'APPROVED'
        ? proposal.result
        : null

  return (
    <div className={`flex flex-col gap-0.5 text-xs ${tone}`}>
      <span>{t(`chat.proposal.status.${proposal.status}`)}</span>
      {detail && <span className="text-fg-muted [overflow-wrap:anywhere]">{detail}</span>}
    </div>
  )
}

export function ProposalCard({
  proposal,
  onDecided,
}: {
  proposal: AiActionProposal
  onDecided?: (decided: AiActionProposal) => void
}): JSX.Element {
  const { t } = useTranslation()
  const [alwaysAllow, setAlwaysAllow] = useState(false)

  const decideMutation = useMutation({
    mutationFn: (decision: 'approve' | 'reject') =>
      decision === 'approve'
        ? chatApi.approveProposal(proposal.id, alwaysAllow)
        : chatApi.rejectProposal(proposal.id),
    onSuccess: (decided) => onDecided?.(decided),
  })

  const current = decideMutation.data ?? proposal
  const isPending = current.status === 'PENDING'

  return (
    <div className="w-full max-w-md rounded-xl border border-accent/40 bg-surface p-3 flex flex-col gap-2">
      <div className="flex items-center gap-1.5 text-xs text-fg-muted">
        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
          <path d="M12 9v4M12 17h.01" />
          <path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
        </svg>
        <span>{t('chat.proposal.heading')}</span>
        <span aria-hidden="true">·</span>
        <span>{t(`chat.toolStep.${current.toolName}`, { defaultValue: current.toolName })}</span>
      </div>

      <p className="text-sm text-fg [overflow-wrap:anywhere]">{current.title}</p>

      {isPending ? (
        <div className="flex flex-col gap-2">
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => decideMutation.mutate('approve')}
              disabled={decideMutation.isPending}
              className="inline-flex items-center gap-1.5 text-xs px-3 py-1.5 rounded-lg bg-accent-solid text-accent-fg hover:bg-accent-solid-hover transition-colors disabled:opacity-50"
            >
              {decideMutation.isPending && <Spinner size="sm" />}
              {t('chat.proposal.approve')}
            </button>
            <button
              type="button"
              onClick={() => decideMutation.mutate('reject')}
              disabled={decideMutation.isPending}
              className="text-xs px-3 py-1.5 rounded-lg border border-line text-fg-muted hover:text-fg transition-colors disabled:opacity-50"
            >
              {t('chat.proposal.reject')}
            </button>
          </div>
          <label className="flex items-center gap-1.5 text-xs text-fg-muted cursor-pointer select-none">
            <input
              type="checkbox"
              checked={alwaysAllow}
              onChange={(event) => setAlwaysAllow(event.target.checked)}
              disabled={decideMutation.isPending}
              className="rounded border-line accent-accent-solid"
            />
            {t('chat.proposal.alwaysAllow')}
          </label>
        </div>
      ) : (
        <ProposalOutcome proposal={current} />
      )}

      {decideMutation.isError && <p className="text-xs text-danger">{t('chat.proposal.error')}</p>}
    </div>
  )
}
