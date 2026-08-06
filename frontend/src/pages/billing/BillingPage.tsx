import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import i18n from '../../i18n'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { billingApi } from '../../api/billing'
import { refreshSession } from '../../api/client'
import type { BillingPlan, BillingStatus, PaymentRecord, PaymentStatus, SubscriptionStatus } from '../../types'
import { Button } from '../../components/ui/Button'
import { Spinner } from '../../components/ui/Spinner'
import { useConfirm } from '../../hooks/useConfirm'
import { PageHeader } from '../../components/ui/PageHeader'

const STATUS_LABEL_KEY: Record<SubscriptionStatus, string> = {
  TRIALING: 'billing.statusTrialing',
  ACTIVE: 'billing.statusActive',
  PAST_DUE: 'billing.statusPastDue',
  CANCELED: 'billing.statusCanceled',
}

const PAYMENT_STATUS_LABEL_KEY: Record<PaymentStatus, string> = {
  PENDING: 'billing.payPending',
  SUCCEEDED: 'billing.paySucceeded',
  CANCELED: 'billing.payCanceled',
}

function locale(): string {
  return i18n.language.startsWith('ru') ? 'ru-RU' : 'en-US'
}

function formatPrice(kopecks: number): string {
  return `${(kopecks / 100).toLocaleString(locale())} ₽`
}

function formatDate(value: string | null): string {
  if (!value) return '—'
  return new Date(value).toLocaleDateString(locale(), { day: '2-digit', month: 'long', year: 'numeric' })
}

function formatTokens(tokens: number): string {
  return tokens > 0
    ? i18n.t('billing.tokensPerDay', { tokens: tokens.toLocaleString(locale()) })
    : i18n.t('billing.noTokenLimit')
}

export default function BillingPage(): JSX.Element {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const confirm = useConfirm()
  const [error, setError] = useState<string | null>(null)

  const { data: status, isLoading: statusLoading } = useQuery<BillingStatus>({
    queryKey: ['billing-status'],
    queryFn: billingApi.status,
  })

  const { data: plans = [], isLoading: plansLoading } = useQuery<BillingPlan[]>({
    queryKey: ['billing-plans'],
    queryFn: billingApi.plans,
  })

  const { data: payments = [] } = useQuery<PaymentRecord[]>({
    queryKey: ['billing-payments'],
    queryFn: billingApi.payments,
  })

  useEffect(() => {
    refreshSession()
      .then(() => queryClient.invalidateQueries({ queryKey: ['billing-status'] }))
      .catch(() => undefined)
  }, [queryClient])

  const subscribeMutation = useMutation({
    mutationFn: (planCode: string) => billingApi.subscribe(planCode),
    onSuccess: (checkout) => {
      if (checkout.confirmationUrl) {
        window.location.href = checkout.confirmationUrl
        return
      }
      setError(t('billing.noConfirmationUrl'))
    },
    onError: () => setError(t('billing.createError')),
  })

  const cancelMutation = useMutation({
    mutationFn: billingApi.cancel,
    onSuccess: () => {
      setError(null)
      queryClient.invalidateQueries({ queryKey: ['billing-status'] })
    },
    onError: () => setError(t('billing.cancelError')),
  })

  const handleCancel = async (): Promise<void> => {
    const confirmed = await confirm({
      title: t('billing.cancelSubscription'),
      description: t('billing.cancelConfirm'),
      danger: true,
    })
    if (confirmed) cancelMutation.mutate()
  }

  if (statusLoading || plansLoading) {
    return (
      <div className="flex justify-center py-16">
        <Spinner size="lg" />
      </div>
    )
  }

  const canCancel = status && !status.cancelAtPeriodEnd
    && (status.status === 'ACTIVE' || status.status === 'TRIALING')

  return (
    <div className="bg-bg">
      <div className="page-container py-8 max-w-4xl">
        <PageHeader
          title={t('billing.title')}
          description={t('billing.subtitle')}
          className="mb-6"
        />

        {error && <p className="mb-4 text-sm text-danger">{error}</p>}

        {status && (
          <div className="mb-8 p-6 rounded-xl bg-surface border border-line">
            <div className="flex items-start justify-between gap-4 flex-wrap">
              <div>
                <p className="text-xs text-fg-muted mb-1">{t('billing.currentPlan')}</p>
                <h2 className="text-xl font-semibold text-fg">
                  {status.planName} · {t(STATUS_LABEL_KEY[status.status])}
                </h2>
                <p className="text-sm text-fg-muted mt-1">
                  {t('billing.statusMeta', { requests: status.dailyRequests, tokens: formatTokens(status.dailyTokens), seats: status.seats })}
                </p>
              </div>
              {canCancel && (
                <Button variant="secondary" size="sm" loading={cancelMutation.isPending} onClick={handleCancel}>
                  {t('billing.cancelSubscription')}
                </Button>
              )}
            </div>

            {status.status === 'TRIALING' && (
              <p className="mt-4 text-sm text-fg">
                {t('billing.trialUntil', { date: formatDate(status.trialEnd) })}
              </p>
            )}
            {status.status === 'PAST_DUE' && (
              <p className="mt-4 text-sm text-danger">
                {t('billing.pastDueMsg')}
              </p>
            )}
            {status.cancelAtPeriodEnd && (
              <p className="mt-4 text-sm text-fg">
                {t('billing.canceledMsg', { date: formatDate(status.currentPeriodEnd) })}
              </p>
            )}
            {status.status === 'ACTIVE' && !status.cancelAtPeriodEnd && status.currentPeriodEnd && (
              <p className="mt-4 text-sm text-fg-muted">
                {t('billing.nextCharge', { date: formatDate(status.currentPeriodEnd) })}
              </p>
            )}
          </div>
        )}

        <div className="grid gap-4 sm:grid-cols-2 mb-10">
          {plans.map((plan) => {
            const isCurrent = plan.code === status?.planCode
            const isFree = plan.priceKopecks <= 0
            return (
              <div
                key={plan.code}
                className={`flex flex-col gap-3 p-5 rounded-xl border ${
                  isCurrent
                    ? 'border-fg bg-surface'
                    : 'border-line'
                }`}
              >
                <div className="flex items-baseline justify-between gap-2">
                  <h3 className="text-lg font-semibold text-fg">{plan.name}</h3>
                  <span className="text-sm text-fg">
                    {isFree ? t('billing.free') : t('billing.pricePerMonth', { price: formatPrice(plan.priceKopecks) })}
                  </span>
                </div>
                <ul className="text-sm text-fg-muted flex flex-col gap-1">
                  <li>{t('billing.planRequests', { count: plan.dailyRequests })}</li>
                  <li>{formatTokens(plan.dailyTokens)}</li>
                  <li>{t('billing.planSeats', { count: plan.seats })}</li>
                </ul>
                <div className="mt-auto pt-2">
                  {isCurrent ? (
                    <p className="text-xs text-fg-muted">{t('billing.currentPlanBadge')}</p>
                  ) : (
                    <Button
                      size="sm"
                      disabled={isFree}
                      loading={subscribeMutation.isPending && subscribeMutation.variables === plan.code}
                      onClick={() => subscribeMutation.mutate(plan.code)}
                    >
                      {isFree ? t('billing.defaultPlan') : t('billing.pay')}
                    </Button>
                  )}
                </div>
              </div>
            )
          })}
        </div>

        {payments.length > 0 && (
          <div>
            <h2 className="text-sm font-medium text-fg mb-2">{t('billing.paymentsHistory')}</h2>
            <div className="flex flex-col divide-y divide-line">
              {payments.map((payment) => (
                <div key={payment.id} className="flex items-center justify-between gap-3 py-3 min-w-0">
                  <div className="min-w-0">
                    <p className="text-sm text-fg truncate">
                      {payment.planCode ?? '—'} · {formatPrice(payment.amountKopecks)}
                    </p>
                    <p className="text-xs text-fg-muted">
                      {formatDate(payment.paidAt ?? payment.createdAt)}
                    </p>
                  </div>
                  <div className="flex items-center gap-3 shrink-0">
                    <span className="text-xs text-fg-muted">
                      {t(PAYMENT_STATUS_LABEL_KEY[payment.status])}
                    </span>
                    {payment.status === 'PENDING' && payment.confirmationUrl && (
                      <a
                        href={payment.confirmationUrl}
                        className="text-xs text-fg underline"
                      >
                        {t('billing.pay')}
                      </a>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
