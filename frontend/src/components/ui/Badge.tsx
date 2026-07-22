import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import i18n from '../../i18n'
import type { ApplicationStatus, CaseStatus, DocumentStatus } from '../../types'

type BadgeVariant = 'success' | 'warning' | 'danger' | 'neutral' | 'info'

export const CASE_STATUS_VARIANT: Record<CaseStatus, BadgeVariant> = {
  INTAKE: 'neutral',
  IN_PROGRESS: 'info',
  SUBMITTED: 'warning',
  CLOSED_WON: 'success',
  CLOSED_LOST: 'danger',
}

export function caseStatusLabel(status: CaseStatus): string {
  return i18n.t(`status.case.${status}`)
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
  success: 'bg-success-soft text-success',
  warning: 'bg-warning-soft text-warning',
  danger: 'bg-danger-soft text-danger',
  neutral: 'bg-bg text-fg-muted',
  info: 'bg-info-soft text-info',
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

const APPLICATION_STATUS_VARIANT: Record<ApplicationStatus, BadgeVariant> = {
  PENDING: 'warning',
  APPROVED: 'success',
  REJECTED: 'danger',
}

export function ApplicationStatusBadge({ status }: { status: ApplicationStatus }): JSX.Element {
  const { t } = useTranslation()
  return <Badge variant={APPLICATION_STATUS_VARIANT[status]}>{t(`status.application.${status}`)}</Badge>
}

const DOCUMENT_STATUS_VARIANT: Record<DocumentStatus, BadgeVariant> = {
  PROCESSING: 'warning',
  READY: 'success',
  FAILED: 'danger',
}

export function DocumentStatusBadge({ status }: { status: DocumentStatus }): JSX.Element {
  const { t } = useTranslation()
  return <Badge variant={DOCUMENT_STATUS_VARIANT[status]}>{t(`status.document.${status}`)}</Badge>
}

export function CaseStatusBadge({ status }: { status: CaseStatus }): JSX.Element {
  const { t } = useTranslation()
  return <Badge variant={CASE_STATUS_VARIANT[status]}>{t(`status.case.${status}`)}</Badge>
}
