import type { InvoiceStatus } from '../../types'

const styles: Record<InvoiceStatus, string> = {
  DRAFT: 'bg-slate-100 text-slate-600 dark:bg-slate-700/40 dark:text-slate-300',
  ISSUED: 'bg-blue-100 text-blue-700 dark:bg-blue-500/20 dark:text-blue-300',
  PAID: 'bg-green-100 text-green-700 dark:bg-green-500/20 dark:text-green-300',
  CANCELED: 'bg-red-100 text-red-700 dark:bg-red-500/20 dark:text-red-300',
}

export function InvoiceStatusBadge({
  status,
  label,
}: {
  status: InvoiceStatus
  label: string
}): JSX.Element {
  return (
    <span className={`shrink-0 px-2.5 py-1 rounded-full text-xs font-medium ${styles[status]}`}>
      {label}
    </span>
  )
}
