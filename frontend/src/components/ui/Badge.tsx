import type { ReactNode } from 'react'
import type { ApplicationStatus, CaseStatus, DocumentStatus } from '../../types'

type BadgeVariant = 'success' | 'warning' | 'danger' | 'neutral' | 'info'

export const CASE_STATUS_CONFIG: Record<CaseStatus, { variant: BadgeVariant; label: string }> = {
  INTAKE: { variant: 'neutral', label: 'Приём' },
  IN_PROGRESS: { variant: 'info', label: 'В работе' },
  SUBMITTED: { variant: 'warning', label: 'Подано в суд' },
  CLOSED_WON: { variant: 'success', label: 'Выиграно' },
  CLOSED_LOST: { variant: 'danger', label: 'Проиграно' },
}

export const CASE_STATUS_ORDER: CaseStatus[] = [
  'INTAKE',
  'IN_PROGRESS',
  'SUBMITTED',
  'CLOSED_WON',
  'CLOSED_LOST',
]

interface BadgeProps {
  variant: BadgeVariant
  children: ReactNode
}

const variantClasses: Record<BadgeVariant, string> = {
  success: 'bg-emerald-100 text-emerald-800 dark:bg-emerald-900/30 dark:text-emerald-400',
  warning: 'bg-amber-100 text-amber-800 dark:bg-amber-900/30 dark:text-amber-400',
  danger: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400',
  neutral: 'bg-light-bg text-light-secondary dark:bg-dark-bg dark:text-dark-secondary',
  info: 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400',
}

export function Badge({ variant, children }: BadgeProps): JSX.Element {
  return (
    <span
      className={`
        inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium
        ${variantClasses[variant]}
      `}
    >
      {children}
    </span>
  )
}

export function ApplicationStatusBadge({ status }: { status: ApplicationStatus }): JSX.Element {
  const config: Record<ApplicationStatus, { variant: BadgeVariant; label: string }> = {
    PENDING: { variant: 'warning', label: 'Ожидает' },
    APPROVED: { variant: 'success', label: 'Одобрена' },
    REJECTED: { variant: 'danger', label: 'Отклонена' },
  }
  const { variant, label } = config[status]
  return <Badge variant={variant}>{label}</Badge>
}

export function DocumentStatusBadge({ status }: { status: DocumentStatus }): JSX.Element {
  const config: Record<DocumentStatus, { variant: BadgeVariant; label: string }> = {
    PROCESSING: { variant: 'warning', label: 'Обработка...' },
    READY: { variant: 'success', label: 'Готов' },
    FAILED: { variant: 'danger', label: 'Ошибка' },
  }
  const { variant, label } = config[status]
  return <Badge variant={variant}>{label}</Badge>
}

export function CaseStatusBadge({ status }: { status: CaseStatus }): JSX.Element {
  const { variant, label } = CASE_STATUS_CONFIG[status]
  return <Badge variant={variant}>{label}</Badge>
}
