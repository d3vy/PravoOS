import { useTranslation } from 'react-i18next'
import { Badge } from '../ui/Badge'
import type { MailboxStatus } from '../../types'

const VARIANT: Record<MailboxStatus, 'success' | 'warning' | 'danger'> = {
  OK: 'success',
  PENDING: 'warning',
  ERROR: 'danger',
}

export function MailboxStatusBadge({ status }: { status: MailboxStatus }): JSX.Element {
  const { t } = useTranslation()
  return <Badge variant={VARIANT[status]}>{t(`mailboxes.status.${status}`)}</Badge>
}
