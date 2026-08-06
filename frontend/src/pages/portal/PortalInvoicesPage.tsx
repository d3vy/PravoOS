import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { PortalLayout } from '../../components/layout/PortalLayout'
import { InvoiceStatusBadge } from '../../components/invoices/InvoiceStatusBadge'
import { Spinner } from '../../components/ui/Spinner'
import { EmptyState } from '../../components/ui/EmptyState'
import { portalApi } from '../../api/portal'
import { formatMoney } from '../../utils/billing'
import i18n from '../../i18n'
import type { InvoiceSummary } from '../../types'
import { PageHeader } from '../../components/ui/PageHeader'

function formatDate(value: string | null): string {
  const locale = i18n.language.startsWith('ru') ? 'ru-RU' : 'en-US'
  return value ? new Date(value).toLocaleDateString(locale) : '—'
}

export default function PortalInvoicesPage(): JSX.Element {
  const { t } = useTranslation()
  const { data: invoices = [], isLoading, isError } = useQuery<InvoiceSummary[]>({
    queryKey: ['portal', 'invoices'],
    queryFn: portalApi.listInvoices,
  })

  return (
    <PortalLayout>
      <PageHeader size="md" title={t('portalInvoices.title')} className="mb-6" />

      {isLoading && (
        <div className="flex justify-center py-16">
          <Spinner />
        </div>
      )}

      {isError && (
        <div className="card-elevated rounded-xl p-8 text-center text-fg-muted">
          {t('portalInvoices.loadError')}
        </div>
      )}

      {!isLoading && !isError && invoices.length === 0 && (
        <EmptyState illustration="invoices" description={t('portalInvoices.empty')} />
      )}

      {!isLoading && !isError && invoices.length > 0 && (
        <ul className="space-y-3">
          {invoices.map((invoice) => (
            <li key={invoice.id}>
              <Link
                to={`/portal/invoices/${invoice.id}`}
                className="card-elevated rounded-xl p-5 flex items-start justify-between gap-4 hover:shadow-md transition-shadow"
              >
                <div className="min-w-0">
                  <p className="font-medium text-fg truncate">
                    {t('invoices.invoiceNumber', { number: invoice.number })}
                  </p>
                  <p className="text-sm text-fg-muted mt-1">
                    {formatDate(invoice.issueDate)}
                    {invoice.dueDate && ` · ${t('invoices.dueBy', { date: formatDate(invoice.dueDate) })}`}
                  </p>
                </div>
                <div className="flex flex-col items-end gap-2 shrink-0">
                  <InvoiceStatusBadge status={invoice.status} />
                  <span className="text-sm font-medium text-fg tabular-nums">
                    {formatMoney(invoice.total, invoice.currency)}
                  </span>
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </PortalLayout>
  )
}
