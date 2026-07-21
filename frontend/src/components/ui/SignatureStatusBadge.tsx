import { useTranslation } from 'react-i18next'
import type { SignatureStatus } from '../../types'

export const SIGNATURE_STATUS_CLASS: Record<SignatureStatus, string> = {
  PENDING: 'border-amber-500/40 text-amber-600 dark:text-amber-400 bg-amber-500/10',
  SIGNED: 'border-emerald-500/40 text-emerald-600 dark:text-emerald-400 bg-emerald-500/10',
  DECLINED: 'border-red-500/40 text-red-600 dark:text-red-400 bg-red-500/10',
  CANCELED: 'border-light-border dark:border-dark-border text-light-secondary dark:text-dark-secondary',
  EXPIRED: 'border-light-border dark:border-dark-border text-light-secondary dark:text-dark-secondary',
}

export function SignatureStatusBadge({ status }: { status: SignatureStatus }): JSX.Element {
  const { t } = useTranslation()
  return (
    <span className={`text-xs px-2 py-1 rounded-md border whitespace-nowrap ${SIGNATURE_STATUS_CLASS[status]}`}>
      {t(`status.signature.${status}`)}
    </span>
  )
}
