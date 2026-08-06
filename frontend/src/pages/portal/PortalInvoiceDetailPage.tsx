import { useParams } from 'react-router-dom'
import { useMutation, useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useState } from 'react'
import { PortalLayout } from '../../components/layout/PortalLayout'
import { InvoiceStatusBadge } from '../../components/invoices/InvoiceStatusBadge'
import { Button } from '../../components/ui/Button'
import { Spinner } from '../../components/ui/Spinner'
import { portalApi } from '../../api/portal'
import { formatDuration, formatMoney } from '../../utils/billing'
import type { InvoiceResponse } from '../../types'
import { PageHeader } from '../../components/ui/PageHeader'

export default function PortalInvoiceDetailPage(): JSX.Element {
  const { t, i18n } = useTranslation()
  const locale = i18n.language.startsWith('ru') ? 'ru-RU' : 'en-US'
  const { invoiceId = '' } = useParams()
  const [payError, setPayError] = useState(false)

  const { data: invoice, isLoading } = useQuery<InvoiceResponse>({
    queryKey: ['portal', 'invoice', invoiceId],
    queryFn: () => portalApi.getInvoice(invoiceId),
    enabled: invoiceId !== '',
  })

  const payMutation = useMutation({
    mutationFn: () => portalApi.payInvoice(invoiceId),
    onSuccess: (response) => {
      window.location.href = response.confirmationUrl
    },
    onError: () => setPayError(true),
  })

  if (isLoading || !invoice) {
    return (
      <PortalLayout>
        <div className="flex justify-center py-16">
          <Spinner />
        </div>
      </PortalLayout>
    )
  }

  return (
    <PortalLayout>
      <PageHeader
        size="md"
        className="mb-6"
        breadcrumbs={[
          { label: t('portalInvoices.title'), to: '/portal/invoices' },
          { label: t('invoices.invoiceNumber', { number: invoice.number }) },
        ]}
        title={t('invoices.invoiceNumber', { number: invoice.number })}
        titleSuffix={<InvoiceStatusBadge status={invoice.status} />}
        description={
          <>
            {new Date(invoice.issueDate).toLocaleDateString(locale)}
            {invoice.dueDate &&
              ` · ${t('invoices.dueBy', { date: new Date(invoice.dueDate).toLocaleDateString(locale) })}`}
          </>
        }
        actions={
          invoice.status === 'ISSUED' && (
            <Button variant="primary" size="sm" loading={payMutation.isPending} onClick={() => payMutation.mutate()}>
              {t('portalInvoices.pay')}
            </Button>
          )
        }
      />

      {payError && (
        <div className="rounded-xl border border-danger/40 bg-danger/10 text-danger text-sm px-4 py-3 mb-6">
          {t('portalInvoices.payError')}
        </div>
      )}

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
        <p className="text-sm text-fg-muted whitespace-pre-wrap">{invoice.notes}</p>
      )}
    </PortalLayout>
  )
}
