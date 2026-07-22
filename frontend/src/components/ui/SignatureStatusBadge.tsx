import { useTranslation } from 'react-i18next'
import type { SignatureStatus } from '../../types'

export const SIGNATURE_STATUS_CLASS: Record<SignatureStatus, string> = {
  PENDING: 'border-amber-500/40 text-warning bg-amber-500/10',
  SIGNED: 'border-emerald-500/40 text-success bg-emerald-500/10',
  DECLINED: 'border-red-500/40 text-danger bg-red-500/10',
  CANCELED: 'border-line text-fg-muted',
  EXPIRED: 'border-line text-fg-muted',
}

export function SignatureStatusBadge({ status }: { status: SignatureStatus }): JSX.Element {
  const { t } = useTranslation()
  return (
    <span className={`text-xs px-2 py-1 rounded-md border whitespace-nowrap ${SIGNATURE_STATUS_CLASS[status]}`}>
      {t(`status.signature.${status}`)}
    </span>
  )
}
