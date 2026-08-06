import { useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { invoicesApi } from '../../api/invoices'
import type { InvoiceResponse, InvoiceStatus } from '../../types'
import { Button } from '../../components/ui/Button'
import { Spinner } from '../../components/ui/Spinner'
import { InvoiceStatusBadge } from '../../components/invoices/InvoiceStatusBadge'
import { formatDuration, formatMoney } from '../../utils/billing'
import { PageHeader } from '../../components/ui/PageHeader'

const NEXT_STATUS: Partial<Record<InvoiceStatus, { to: InvoiceStatus; labelKey: string; variant: 'primary' | 'secondary' }[]>> = {
  DRAFT: [
    { to: 'ISSUED', labelKey: 'invoices.actionIssue', variant: 'primary' },
    { to: 'CANCELED', labelKey: 'invoices.actionCancel', variant: 'secondary' },
  ],
  ISSUED: [
    { to: 'PAID', labelKey: 'invoices.actionMarkPaid', variant: 'primary' },
    { to: 'CANCELED', labelKey: 'invoices.actionCancel', variant: 'secondary' },
  ],
}

export default function InvoiceDetailPage(): JSX.Element {
  const { t, i18n } = useTranslation()
  const locale = i18n.language.startsWith('ru') ? 'ru-RU' : 'en-US'
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
    onMutate: async (status) => {
      await queryClient.cancelQueries({ queryKey: ['invoice', invoiceId] })
      const previous = queryClient.getQueryData<InvoiceResponse>(['invoice', invoiceId])
      queryClient.setQueryData<InvoiceResponse>(['invoice', invoiceId], (old) =>
        old ? { ...old, status } : old
      )
      return { previous }
    },
    onError: (_err, _status, context) => {
      if (context?.previous) queryClient.setQueryData(['invoice', invoiceId], context.previous)
    },
    onSettled: invalidate,
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
    <div className="bg-bg">
      <div className="page-container py-8 max-w-3xl">
        <PageHeader
          size="md"
          className="mb-6"
          breadcrumbs={[
            { label: t('nav.invoices'), to: '/invoices' },
            { label: t('invoices.invoiceNumber', { number: invoice.number }) },
          ]}
          title={t('invoices.invoiceNumber', { number: invoice.number })}
          titleSuffix={<InvoiceStatusBadge status={invoice.status} />}
          description={
            <>
              {invoice.clientName ?? t('invoices.clientDeleted')} ·{' '}
              {new Date(invoice.issueDate).toLocaleDateString(locale)}
              {invoice.dueDate && ` · ${t('invoices.dueBy', { date: new Date(invoice.dueDate).toLocaleDateString(locale) })}`}
            </>
          }
          actions={
            <Button variant="secondary" size="sm" onClick={() => invoicesApi.exportPdf(invoiceId, invoice.number)}>
              {t('invoices.downloadPdf')}
            </Button>
          }
        />

        <div className="rounded-xl border border-line overflow-hidden mb-6">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-surface text-left text-fg-muted">
                <th className="px-4 py-2.5 font-medium">{t('invoices.colDescription')}</th>
                <th className="px-4 py-2.5 font-medium text-right">{t('invoices.colTime')}</th>
                <th className="px-4 py-2.5 font-medium text-right">{t('invoices.colRate')}</th>
                <th className="px-4 py-2.5 font-medium text-right">{t('invoices.colAmount')}</th>
              </tr>
            </thead>
            <tbody>
              {invoice.lines.map((line) => (
                <tr key={line.id} className="border-t border-line">
                  <td className="px-4 py-2.5 text-fg">{line.description}</td>
                  <td className="px-4 py-2.5 text-right text-fg-muted tabular-nums">
                    {formatDuration(line.minutes)}
                  </td>
                  <td className="px-4 py-2.5 text-right text-fg-muted tabular-nums">
                    {formatMoney(line.hourlyRate, invoice.currency)}
                  </td>
                  <td className="px-4 py-2.5 text-right text-fg tabular-nums">
                    {formatMoney(line.amount, invoice.currency)}
                  </td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr className="border-t border-line">
                <td className="px-4 py-2.5 text-fg-muted" colSpan={3}>
                  {t('invoices.subtotal')}
                </td>
                <td className="px-4 py-2.5 text-right text-fg-muted tabular-nums">
                  {formatMoney(invoice.subtotal, invoice.currency)}
                </td>
              </tr>
              <tr className="border-t border-line">
                <td className="px-4 py-2.5 text-fg-muted" colSpan={3}>
                  {invoice.vatRate
                    ? t('invoices.vatWithRate', { rate: invoice.vatRate })
                    : t('invoices.vatNone')}
                </td>
                <td className="px-4 py-2.5 text-right text-fg-muted tabular-nums">
                  {formatMoney(invoice.vatAmount, invoice.currency)}
                </td>
              </tr>
              <tr className="border-t border-line bg-surface">
                <td className="px-4 py-3 font-semibold text-fg" colSpan={3}>
                  {t('invoices.totalDue')}
                </td>
                <td className="px-4 py-3 text-right font-semibold text-fg tabular-nums">
                  {formatMoney(invoice.total, invoice.currency)}
                </td>
              </tr>
            </tfoot>
          </table>
        </div>

        {invoice.notes && (
          <p className="text-sm text-fg-muted mb-6 whitespace-pre-wrap">
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
              {t(action.labelKey)}
            </Button>
          ))}
          {invoice.status === 'DRAFT' && (
            <Button
              variant="danger"
              size="sm"
              loading={deleteMutation.isPending}
              onClick={() => deleteMutation.mutate()}
            >
              {t('invoices.deleteDraft')}
            </Button>
          )}
        </div>
      </div>
    </div>
  )
}
