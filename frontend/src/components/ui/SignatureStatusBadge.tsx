import type { SignatureStatus } from '../../types'

interface StatusMeta {
  label: string
  className: string
}

export const SIGNATURE_STATUS_META: Record<SignatureStatus, StatusMeta> = {
  PENDING: {
    label: 'Ожидает подписи',
    className: 'border-amber-500/40 text-amber-600 dark:text-amber-400 bg-amber-500/10',
  },
  SIGNED: {
    label: 'Подписан',
    className: 'border-emerald-500/40 text-emerald-600 dark:text-emerald-400 bg-emerald-500/10',
  },
  DECLINED: {
    label: 'Отклонён',
    className: 'border-red-500/40 text-red-600 dark:text-red-400 bg-red-500/10',
  },
  CANCELED: {
    label: 'Отменён',
    className: 'border-light-border dark:border-dark-border text-light-secondary dark:text-dark-secondary',
  },
  EXPIRED: {
    label: 'Истёк',
    className: 'border-light-border dark:border-dark-border text-light-secondary dark:text-dark-secondary',
  },
}

export function SignatureStatusBadge({ status }: { status: SignatureStatus }): JSX.Element {
  const meta = SIGNATURE_STATUS_META[status]
  return (
    <span className={`text-xs px-2 py-1 rounded-md border whitespace-nowrap ${meta.className}`}>
      {meta.label}
    </span>
  )
}
