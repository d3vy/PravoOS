import { useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { invoicesApi } from '../../api/invoices'
import type { InvoiceResponse, InvoiceStatus } from '../../types'
import { Button } from '../../components/ui/Button'
import { Spinner } from '../../components/ui/Spinner'
import { InvoiceStatusBadge } from '../../components/invoices/InvoiceStatusBadge'
import { formatDuration, formatMoney } from '../../utils/billing'

const NEXT_STATUS: Partial<Record<InvoiceStatus, { to: InvoiceStatus; label: string; variant: 'primary' | 'secondary' }[]>> = {
  DRAFT: [
    { to: 'ISSUED', label: 'Выставить', variant: 'primary' },
    { to: 'CANCELED', label: 'Отменить', variant: 'secondary' },
  ],
  ISSUED: [
    { to: 'PAID', label: 'Отметить оплаченным', variant: 'primary' },
    { to: 'CANCELED', label: 'Отменить', variant: 'secondary' },
  ],
}

export default function InvoiceDetailPage(): JSX.Element {
  const { invoiceId = '' } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const { data: invoice, isLoading } = useQuery<InvoiceResponse>({
    queryKey: ['invoice', invoiceId],
    queryFn: () => invoicesApi.get(invoiceId),
    enabled: invoiceId !== '',
  })

  const invalidate = (): void => {
    queryClient.invalidateQueries({ queryKey: ['invoice', invoiceId] })
    queryClient.invalidateQueries({ queryKey: ['invoices'] })
  }

  const statusMutation = useMutation({
    mutationFn: (status: InvoiceStatus) => invoicesApi.updateStatus(invoiceId, status),
    onSuccess: invalidate,
  })

  const deleteMutation = useMutation({
    mutationFn: () => invoicesApi.remove(invoiceId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['invoices'] })
      navigate('/invoices')
    },
  })

  if (isLoading || !invoice) {
    return (
      <div className="flex justify-center py-16">
        <Spinner />
      </div>
    )
  }

  const actions = NEXT_STATUS[invoice.status] ?? []

  return (
    <div className="bg-light-bg dark:bg-dark-bg">
      <div className="page-container py-8 max-w-3xl">
        <button
          onClick={() => navigate('/invoices')}
          className="text-sm text-light-secondary dark:text-dark-secondary hover:text-light-accent dark:hover:text-dark-accent mb-4"
        >
          ← Ко всем счетам
        </button>

        <div className="flex items-start justify-between gap-4 mb-6">
          <div>
            <div className="flex items-center gap-3 mb-1">
              <h1 className="text-2xl font-semibold text-light-text dark:text-dark-text">
                Счёт {invoice.number}
              </h1>
              <InvoiceStatusBadge status={invoice.status} label={invoice.statusLabel} />
            </div>
            <p className="text-sm text-light-secondary dark:text-dark-secondary">
              {invoice.clientName ?? 'Клиент удалён'} ·{' '}
              {new Date(invoice.issueDate).toLocaleDateString('ru-RU')}
              {invoice.dueDate && ` · оплатить до ${new Date(invoice.dueDate).toLocaleDateString('ru-RU')}`}
            </p>
          </div>
          <Button variant="secondary" size="sm" onClick={() => invoicesApi.exportPdf(invoiceId, invoice.number)}>
            Скачать PDF
          </Button>
        </div>

        <div className="rounded-xl border border-light-border dark:border-dark-border overflow-hidden mb-6">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-light-surface dark:bg-dark-surface text-left text-light-secondary dark:text-dark-secondary">
                <th className="px-4 py-2.5 font-medium">Описание</th>
                <th className="px-4 py-2.5 font-medium text-right">Время</th>
                <th className="px-4 py-2.5 font-medium text-right">Ставка</th>
                <th className="px-4 py-2.5 font-medium text-right">Сумма</th>
              </tr>
            </thead>
            <tbody>
              {invoice.lines.map((line) => (
                <tr key={line.id} className="border-t border-light-border dark:border-dark-border">
                  <td className="px-4 py-2.5 text-light-text dark:text-dark-text">{line.description}</td>
                  <td className="px-4 py-2.5 text-right text-light-secondary dark:text-dark-secondary tabular-nums">
                    {formatDuration(line.minutes)}
                  </td>
                  <td className="px-4 py-2.5 text-right text-light-secondary dark:text-dark-secondary tabular-nums">
                    {formatMoney(line.hourlyRate, invoice.currency)}
                  </td>
                  <td className="px-4 py-2.5 text-right text-light-text dark:text-dark-text tabular-nums">
                    {formatMoney(line.amount, invoice.currency)}
                  </td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr className="border-t border-light-border dark:border-dark-border bg-light-surface dark:bg-dark-surface">
                <td className="px-4 py-3 font-semibold text-light-text dark:text-dark-text" colSpan={3}>
                  Итого к оплате
                </td>
                <td className="px-4 py-3 text-right font-semibold text-light-text dark:text-dark-text tabular-nums">
                  {formatMoney(invoice.total, invoice.currency)}
                </td>
              </tr>
            </tfoot>
          </table>
        </div>

        {invoice.notes && (
          <p className="text-sm text-light-secondary dark:text-dark-secondary mb-6 whitespace-pre-wrap">
            {invoice.notes}
          </p>
        )}

        <div className="flex flex-wrap gap-2">
          {actions.map((action) => (
            <Button
              key={action.to}
              variant={action.variant}
              size="sm"
              loading={statusMutation.isPending}
              onClick={() => statusMutation.mutate(action.to)}
            >
              {action.label}
            </Button>
          ))}
          {invoice.status === 'DRAFT' && (
            <Button
              variant="danger"
              size="sm"
              loading={deleteMutation.isPending}
              onClick={() => deleteMutation.mutate()}
            >
              Удалить черновик
            </Button>
          )}
        </div>
      </div>
    </div>
  )
}
