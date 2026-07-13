import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { billingApi } from '../../api/billing'
import { refreshSession } from '../../api/client'
import type { BillingPlan, BillingStatus, PaymentRecord, PaymentStatus, SubscriptionStatus } from '../../types'
import { Button } from '../../components/ui/Button'
import { Spinner } from '../../components/ui/Spinner'

const STATUS_LABEL: Record<SubscriptionStatus, string> = {
  TRIALING: 'Пробный период',
  ACTIVE: 'Активна',
  PAST_DUE: 'Ожидает оплаты',
  CANCELED: 'Отменена',
}

const PAYMENT_STATUS_LABEL: Record<PaymentStatus, string> = {
  PENDING: 'Ожидает оплаты',
  SUCCEEDED: 'Оплачен',
  CANCELED: 'Отменён',
}

function formatPrice(kopecks: number): string {
  return `${(kopecks / 100).toLocaleString('ru-RU')} ₽`
}

function formatDate(value: string | null): string {
  if (!value) return '—'
  return new Date(value).toLocaleDateString('ru-RU', { day: '2-digit', month: 'long', year: 'numeric' })
}

function formatTokens(tokens: number): string {
  return tokens > 0 ? `${tokens.toLocaleString('ru-RU')} токенов/день` : 'без лимита токенов'
}

export default function BillingPage(): JSX.Element {
  const queryClient = useQueryClient()
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
      setError('Платёжный провайдер не вернул ссылку на оплату.')
    },
    onError: () => setError('Не удалось создать платёж. Попробуйте позже.'),
  })

  const cancelMutation = useMutation({
    mutationFn: billingApi.cancel,
    onSuccess: () => {
      setError(null)
      queryClient.invalidateQueries({ queryKey: ['billing-status'] })
    },
    onError: () => setError('Не удалось отменить подписку.'),
  })

  const handleCancel = (): void => {
    if (window.confirm('Отменить подписку? Доступ сохранится до конца оплаченного периода.')) {
      cancelMutation.mutate()
    }
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
    <div className="bg-light-bg dark:bg-dark-bg">
      <div className="page-container py-8 max-w-4xl">
        <div className="mb-6">
          <h1 className="text-3xl font-semibold text-light-text dark:text-dark-text mb-1">Подписка</h1>
          <p className="text-sm text-light-secondary dark:text-dark-secondary">
            Тариф определяет дневные лимиты AI-запросов и число мест в организации
          </p>
        </div>

        {error && <p className="mb-4 text-sm text-red-600 dark:text-red-400">{error}</p>}

        {status && (
          <div className="mb-8 p-6 rounded-xl bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border">
            <div className="flex items-start justify-between gap-4 flex-wrap">
              <div>
                <p className="text-xs text-light-secondary dark:text-dark-secondary mb-1">Текущий тариф</p>
                <h2 className="text-xl font-semibold text-light-text dark:text-dark-text">
                  {status.planName} · {STATUS_LABEL[status.status]}
                </h2>
                <p className="text-sm text-light-secondary dark:text-dark-secondary mt-1">
                  {status.dailyRequests} запросов/день · {formatTokens(status.dailyTokens)} · мест: {status.seats}
                </p>
              </div>
              {canCancel && (
                <Button variant="secondary" size="sm" loading={cancelMutation.isPending} onClick={handleCancel}>
                  Отменить подписку
                </Button>
              )}
            </div>

            {status.status === 'TRIALING' && (
              <p className="mt-4 text-sm text-light-text dark:text-dark-text">
                Пробный период до {formatDate(status.trialEnd)}. Оплатите тариф, чтобы сохранить лимиты.
              </p>
            )}
            {status.status === 'PAST_DUE' && (
              <p className="mt-4 text-sm text-red-600 dark:text-red-400">
                Оплата не прошла. Продлите подписку, иначе лимиты снизятся до бесплатного тарифа.
              </p>
            )}
            {status.cancelAtPeriodEnd && (
              <p className="mt-4 text-sm text-light-text dark:text-dark-text">
                Подписка отменена и не будет продлена. Доступ сохраняется до {formatDate(status.currentPeriodEnd)}.
              </p>
            )}
            {status.status === 'ACTIVE' && !status.cancelAtPeriodEnd && status.currentPeriodEnd && (
              <p className="mt-4 text-sm text-light-secondary dark:text-dark-secondary">
                Следующее списание: {formatDate(status.currentPeriodEnd)}
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
                    ? 'border-light-text dark:border-dark-text bg-light-surface dark:bg-dark-surface'
                    : 'border-light-border dark:border-dark-border'
                }`}
              >
                <div className="flex items-baseline justify-between gap-2">
                  <h3 className="text-lg font-semibold text-light-text dark:text-dark-text">{plan.name}</h3>
                  <span className="text-sm text-light-text dark:text-dark-text">
                    {isFree ? 'бесплатно' : `${formatPrice(plan.priceKopecks)}/мес`}
                  </span>
                </div>
                <ul className="text-sm text-light-secondary dark:text-dark-secondary flex flex-col gap-1">
                  <li>{plan.dailyRequests} AI-запросов в день</li>
                  <li>{formatTokens(plan.dailyTokens)}</li>
                  <li>мест в организации: {plan.seats}</li>
                </ul>
                <div className="mt-auto pt-2">
                  {isCurrent ? (
                    <p className="text-xs text-light-secondary dark:text-dark-secondary">Ваш текущий тариф</p>
                  ) : (
                    <Button
                      size="sm"
                      disabled={isFree}
                      loading={subscribeMutation.isPending && subscribeMutation.variables === plan.code}
                      onClick={() => subscribeMutation.mutate(plan.code)}
                    >
                      {isFree ? 'Тариф по умолчанию' : 'Оплатить'}
                    </Button>
                  )}
                </div>
              </div>
            )
          })}
        </div>

        {payments.length > 0 && (
          <div>
            <h2 className="text-sm font-medium text-light-text dark:text-dark-text mb-2">История платежей</h2>
            <div className="flex flex-col divide-y divide-light-border dark:divide-dark-border">
              {payments.map((payment) => (
                <div key={payment.id} className="flex items-center justify-between gap-3 py-3 min-w-0">
                  <div className="min-w-0">
                    <p className="text-sm text-light-text dark:text-dark-text truncate">
                      {payment.planCode ?? '—'} · {formatPrice(payment.amountKopecks)}
                    </p>
                    <p className="text-xs text-light-secondary dark:text-dark-secondary">
                      {formatDate(payment.paidAt ?? payment.createdAt)}
                    </p>
                  </div>
                  <div className="flex items-center gap-3 shrink-0">
                    <span className="text-xs text-light-secondary dark:text-dark-secondary">
                      {PAYMENT_STATUS_LABEL[payment.status]}
                    </span>
                    {payment.status === 'PENDING' && payment.confirmationUrl && (
                      <a
                        href={payment.confirmationUrl}
                        className="text-xs text-light-text dark:text-dark-text underline"
                      >
                        Оплатить
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
