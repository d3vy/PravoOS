import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { formatDuration, formatMoney } from '../../../utils/billing'
import { widgetPadding, type DashboardWidgetProps } from './types'

export function MoneyOnTableWidget({ data, density }: DashboardWidgetProps): JSX.Element {
  const { t } = useTranslation()
  const { uninvoicedMinutes, uninvoicedAmount } = data.moneyOnTable

  return (
    <div className={`card-elevated ${widgetPadding(density)} h-full flex flex-col justify-between`}>
      <div>
        <p className="text-sm text-fg-muted mb-1">{t('dashboard.moneyOnTableTitle')}</p>
        <p className="text-3xl font-semibold text-fg">{formatMoney(uninvoicedAmount)}</p>
        <p className="mt-1 text-xs text-fg-muted">
          {uninvoicedMinutes > 0
            ? t('dashboard.moneyOnTableMinutes', { duration: formatDuration(uninvoicedMinutes) })
            : t('dashboard.moneyOnTableEmpty')}
        </p>
      </div>
      {uninvoicedMinutes > 0 && (
        <Link to="/invoices" className="mt-3 text-sm font-medium text-accent hover:underline self-start">
          {t('dashboard.moneyOnTableAction')}
        </Link>
      )}
    </div>
  )
}
