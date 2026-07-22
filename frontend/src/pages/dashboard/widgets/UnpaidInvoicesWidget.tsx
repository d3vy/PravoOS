import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Badge } from '../../../components/ui/Badge'
import { formatMoney } from '../../../utils/billing'
import { widgetPadding, type DashboardWidgetProps } from './types'

export function UnpaidInvoicesWidget({ data, density }: DashboardWidgetProps): JSX.Element {
  const { t } = useTranslation()
  const { count, totalAmount, items } = data.unpaidInvoices

  return (
    <div className={`card-elevated ${widgetPadding(density)} h-full`}>
      <div className="flex items-center justify-between">
        <h2 className={density === 'compact' ? 'text-sm font-semibold text-fg' : 'text-lg font-semibold text-fg'}>
          {t('dashboard.unpaidInvoicesTitle')}
        </h2>
        {count > 0 && <span className="text-sm font-medium text-fg-muted">{formatMoney(totalAmount)}</span>}
      </div>
      {items.length === 0 ? (
        <p className="text-sm text-fg-muted mt-3">{t('dashboard.unpaidInvoicesEmpty')}</p>
      ) : (
        <ul className="space-y-2 mt-3">
          {items.map((invoice) => (
            <li key={invoice.id}>
              <Link
                to="/invoices"
                className="flex items-center justify-between gap-3 p-3 rounded-lg border border-line hover:bg-bg transition-colors"
              >
                <div className="min-w-0">
                  <p className="text-sm font-medium text-fg truncate">
                    {invoice.number} {invoice.clientName ? `· ${invoice.clientName}` : ''}
                  </p>
                  <p className="text-xs text-fg-muted">{formatMoney(invoice.total, invoice.currency)}</p>
                </div>
                {invoice.daysOverdue > 0 && (
                  <Badge variant="danger">{t('dashboard.unpaidInvoicesOverdue', { count: invoice.daysOverdue })}</Badge>
                )}
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
