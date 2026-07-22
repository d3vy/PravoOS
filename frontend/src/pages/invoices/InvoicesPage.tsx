import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery, keepPreviousData } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { invoicesApi } from '../../api/invoices'
import { DEFAULT_PAGE_SIZE, type Page } from '../../api/pagination'
import type { InvoiceStatus, InvoiceSummary } from '../../types'
import { SkeletonList } from '../../components/ui/Skeleton'
import { Pagination } from '../../components/ui/Pagination'
import { InvoiceStatusBadge } from '../../components/invoices/InvoiceStatusBadge'
import { formatMoney } from '../../utils/billing'

export default function InvoicesPage(): JSX.Element {
  const { t, i18n } = useTranslation()
  const locale = i18n.language.startsWith('ru') ? 'ru-RU' : 'en-US'
  const [page, setPage] = useState(0)

  const { data, isLoading } = useQuery<Page<InvoiceSummary>>({
    queryKey: ['invoices', page],
    queryFn: () => invoicesApi.list(undefined, page),
    placeholderData: keepPreviousData,
  })

  const invoices = data?.items ?? []
  const total = data?.total ?? 0

  return (
    <div className="bg-bg">
      <div className="page-container py-8">
        <div className="mb-8">
          <h1 className="text-3xl font-semibold text-fg mb-1">{t('invoices.title')}</h1>
          <p className="text-sm text-fg-muted">
            {t('invoices.subtitle')}
          </p>
        </div>

        {isLoading ? (
          <SkeletonList count={6} />
        ) : invoices.length === 0 ? (
          <p className="text-sm text-fg-muted">
            {t('invoices.emptyHint')}
          </p>
        ) : (
          <div className="flex flex-col gap-2">
            {invoices.map((invoice) => (
              <Link
                key={invoice.id}
                to={`/invoices/${invoice.id}`}
                className="flex items-center gap-4 p-4 rounded-xl bg-surface border border-line hover:border-accent transition-colors"
              >
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-fg">
                    {invoice.number}
                  </p>
                  <span className="text-xs text-fg-muted">
                    {invoice.clientName ?? t('invoices.clientDeleted')} ·{' '}
                    {new Date(invoice.issueDate).toLocaleDateString(locale)}
                  </span>
                </div>
                <InvoiceStatusBadge status={invoice.status as InvoiceStatus} />
                <span className="text-sm font-semibold text-fg tabular-nums">
                  {formatMoney(invoice.total, invoice.currency)}
                </span>
              </Link>
            ))}
          </div>
        )}

        <Pagination page={page} total={total} pageSize={DEFAULT_PAGE_SIZE} onPageChange={setPage} />
      </div>
    </div>
  )
}
