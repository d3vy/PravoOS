import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery, keepPreviousData } from '@tanstack/react-query'
import { invoicesApi } from '../../api/invoices'
import { DEFAULT_PAGE_SIZE, type Page } from '../../api/pagination'
import type { InvoiceStatus, InvoiceSummary } from '../../types'
import { Spinner } from '../../components/ui/Spinner'
import { Pagination } from '../../components/ui/Pagination'
import { InvoiceStatusBadge } from '../../components/invoices/InvoiceStatusBadge'
import { formatMoney } from '../../utils/billing'

export default function InvoicesPage(): JSX.Element {
  const [page, setPage] = useState(0)

  const { data, isLoading } = useQuery<Page<InvoiceSummary>>({
    queryKey: ['invoices', page],
    queryFn: () => invoicesApi.list(undefined, page),
    placeholderData: keepPreviousData,
  })

  const invoices = data?.items ?? []
  const total = data?.total ?? 0

  return (
    <div className="bg-light-bg dark:bg-dark-bg">
      <div className="page-container py-8">
        <div className="mb-8">
          <h1 className="text-3xl font-semibold text-light-text dark:text-dark-text mb-1">Счета</h1>
          <p className="text-sm text-light-secondary dark:text-dark-secondary">
            Счета за услуги — формируются из учёта времени по делам
          </p>
        </div>

        {isLoading ? (
          <div className="flex justify-center py-16">
            <Spinner />
          </div>
        ) : invoices.length === 0 ? (
          <p className="text-sm text-light-secondary dark:text-dark-secondary">
            Счетов пока нет. Откройте дело, спишите время и нажмите «Выставить счёт».
          </p>
        ) : (
          <div className="flex flex-col gap-2">
            {invoices.map((invoice) => (
              <Link
                key={invoice.id}
                to={`/invoices/${invoice.id}`}
                className="flex items-center gap-4 p-4 rounded-xl bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border hover:border-light-accent dark:hover:border-dark-accent transition-colors"
              >
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-light-text dark:text-dark-text">
                    {invoice.number}
                  </p>
                  <span className="text-xs text-light-secondary dark:text-dark-secondary">
                    {invoice.clientName ?? 'Клиент удалён'} ·{' '}
                    {new Date(invoice.issueDate).toLocaleDateString('ru-RU')}
                  </span>
                </div>
                <InvoiceStatusBadge status={invoice.status as InvoiceStatus} label={invoice.statusLabel} />
                <span className="text-sm font-semibold text-light-text dark:text-dark-text tabular-nums">
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
