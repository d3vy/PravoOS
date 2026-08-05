import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import axios from 'axios'
import { privacyApi } from '../../api/privacy'
import type { ConsentPurpose, ConsentResponse, SubjectRequestResponse } from '../../types'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'
import { Spinner } from '../../components/ui/Spinner'
import { useToastStore } from '../../store/toastStore'
import { readCookieConsent, storeCookieConsent } from '../../utils/cookieConsent'
import { refreshErrorReportingConsent } from '../../lib/observability'

const CONSENT_ORDER: ConsentPurpose[] = ['PERSONAL_DATA', 'CROSS_BORDER_TRANSFER', 'MARKETING']

function formatDateTime(value: string | null): string {
  if (!value) return '—'
  return new Date(value).toLocaleString()
}

export function PrivacyTab(): JSX.Element {
  const { t } = useTranslation()

  return (
    <div className="flex flex-col gap-10">
      <ConsentsSection />
      <CookieSection />
      <ExportSection />
      <RequestsSection />
      <ErasureSection />
      <p className="text-xs text-fg-muted">
        {t('privacy.legalHint')}{' '}
        <Link to="/legal/privacy" className="text-accent hover:underline">
          {t('privacy.legalLink')}
        </Link>
      </p>
    </div>
  )
}

function ConsentsSection(): JSX.Element {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const pushToast = useToastStore((state) => state.push)

  const { data: consents, isLoading } = useQuery<ConsentResponse[]>({
    queryKey: ['privacy-consents'],
    queryFn: privacyApi.consents,
  })

  const invalidate = (): void => {
    void queryClient.invalidateQueries({ queryKey: ['privacy-consents'] })
  }

  const grantMutation = useMutation({
    mutationFn: privacyApi.grantConsent,
    onSuccess: invalidate,
  })

  const revokeMutation = useMutation({
    mutationFn: privacyApi.revokeConsent,
    onSuccess: invalidate,
    onError: (error) => {
      const code = axios.isAxiosError(error)
        ? (error.response?.data?.code as string | undefined)
        : undefined
      pushToast({
        variant: 'error',
        message:
          code === 'MANDATORY_CONSENT'
            ? t('privacy.mandatoryRevokeError')
            : t('privacy.revokeError'),
      })
    },
    meta: { suppressErrorToast: true },
  })

  if (isLoading) {
    return <Spinner size="sm" />
  }

  const byPurpose = new Map((consents ?? []).map((consent) => [consent.purpose, consent]))

  return (
    <section className="flex flex-col gap-4">
      <div>
        <h2 className="text-xl font-semibold text-fg">{t('privacy.consentsTitle')}</h2>
        <p className="mt-1 text-sm text-fg-muted">{t('privacy.consentsDesc')}</p>
      </div>

      <div className="flex flex-col gap-3">
        {CONSENT_ORDER.map((purpose) => {
          const consent = byPurpose.get(purpose)
          const active = consent != null && consent.revokedAt === null
          const mandatory = consent?.mandatory ?? purpose !== 'MARKETING'
          return (
            <div
              key={purpose}
              className="flex flex-col gap-2 rounded-xl border border-line bg-surface p-4 sm:flex-row sm:items-center sm:justify-between"
            >
              <div className="flex-1">
                <p className="text-sm font-medium text-fg">
                  {t(`privacy.purpose.${purpose}`)}
                  {mandatory && (
                    <span className="ml-2 text-xs font-normal text-fg-muted">
                      {t('privacy.mandatoryTag')}
                    </span>
                  )}
                </p>
                <p className="mt-1 text-xs text-fg-muted">
                  {active
                    ? t('privacy.grantedAt', {
                        date: formatDateTime(consent?.grantedAt ?? null),
                        version: consent?.policyVersion ?? '—',
                      })
                    : t('privacy.notGranted')}
                </p>
              </div>
              {active ? (
                <Button
                  variant="ghost"
                  size="sm"
                  loading={revokeMutation.isPending && revokeMutation.variables === purpose}
                  onClick={() => revokeMutation.mutate(purpose)}
                >
                  {t('privacy.revoke')}
                </Button>
              ) : (
                <Button
                  variant="secondary"
                  size="sm"
                  loading={grantMutation.isPending && grantMutation.variables === purpose}
                  onClick={() => grantMutation.mutate(purpose)}
                >
                  {t('privacy.grant')}
                </Button>
              )}
            </div>
          )
        })}
      </div>
    </section>
  )
}

function CookieSection(): JSX.Element {
  const { t } = useTranslation()
  const [analytics, setAnalytics] = useState(false)
  const [decided, setDecided] = useState(false)

  useEffect(() => {
    const stored = readCookieConsent()
    setAnalytics(stored?.analytics ?? false)
    setDecided(stored !== null)
  }, [])

  const toggle = (value: boolean): void => {
    storeCookieConsent(value)
    refreshErrorReportingConsent()
    setAnalytics(value)
    setDecided(true)
  }

  return (
    <section className="flex flex-col gap-4">
      <div>
        <h2 className="text-xl font-semibold text-fg">{t('privacy.cookiesTitle')}</h2>
        <p className="mt-1 text-sm text-fg-muted">{t('privacy.cookiesDesc')}</p>
      </div>
      <label className="flex cursor-pointer items-start gap-3 rounded-xl border border-line bg-surface p-4">
        <input
          type="checkbox"
          checked={analytics}
          onChange={(e) => toggle(e.target.checked)}
          className="mt-0.5 h-4 w-4 shrink-0 rounded border border-line accent-accent"
        />
        <span className="text-sm text-fg-muted">
          {t('privacy.cookiesAnalytics')}
          {!decided && <span className="ml-2 text-xs">{t('privacy.cookiesUndecided')}</span>}
        </span>
      </label>
      <Link to="/legal/cookies" className="text-sm text-accent hover:underline">
        {t('privacy.cookiesPolicyLink')}
      </Link>
    </section>
  )
}

function ExportSection(): JSX.Element {
  const { t } = useTranslation()
  const pushToast = useToastStore((state) => state.push)

  const exportMutation = useMutation({
    mutationFn: privacyApi.exportData,
    onSuccess: (data) => {
      const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `pravoos-personal-data-${data.exportedAt.slice(0, 10)}.json`
      link.click()
      URL.revokeObjectURL(url)
      pushToast({ variant: 'success', message: t('privacy.exportDone') })
    },
  })

  return (
    <section className="flex flex-col gap-4">
      <div>
        <h2 className="text-xl font-semibold text-fg">{t('privacy.exportTitle')}</h2>
        <p className="mt-1 text-sm text-fg-muted">{t('privacy.exportDesc')}</p>
      </div>
      <div>
        <Button
          variant="secondary"
          size="md"
          loading={exportMutation.isPending}
          onClick={() => exportMutation.mutate()}
        >
          {t('privacy.exportAction')}
        </Button>
      </div>
    </section>
  )
}

function RequestsSection(): JSX.Element {
  const { t } = useTranslation()

  const { data: requests, isLoading } = useQuery<SubjectRequestResponse[]>({
    queryKey: ['privacy-requests'],
    queryFn: privacyApi.requests,
  })

  if (isLoading) {
    return <Spinner size="sm" />
  }

  return (
    <section className="flex flex-col gap-4">
      <div>
        <h2 className="text-xl font-semibold text-fg">{t('privacy.requestsTitle')}</h2>
        <p className="mt-1 text-sm text-fg-muted">{t('privacy.requestsDesc')}</p>
      </div>
      {requests && requests.length > 0 ? (
        <ul className="flex flex-col gap-2">
          {requests.map((request) => (
            <li
              key={request.id}
              className="flex flex-col gap-1 rounded-xl border border-line bg-surface p-4 text-sm sm:flex-row sm:items-center sm:justify-between"
            >
              <span className="text-fg">{t(`privacy.requestType.${request.type}`)}</span>
              <span className="text-xs text-fg-muted">
                {t('privacy.requestMeta', {
                  requested: formatDateTime(request.requestedAt),
                  due: formatDateTime(request.dueAt),
                })}
              </span>
              <span className="text-xs font-medium text-fg">
                {t(`privacy.requestStatus.${request.status}`)}
              </span>
            </li>
          ))}
        </ul>
      ) : (
        <p className="text-sm text-fg-muted">{t('privacy.requestsEmpty')}</p>
      )}
    </section>
  )
}

function ErasureSection(): JSX.Element {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const pushToast = useToastStore((state) => state.push)
  const [confirming, setConfirming] = useState(false)
  const [password, setPassword] = useState('')

  const eraseMutation = useMutation({
    mutationFn: privacyApi.erase,
    onSuccess: () => {
      setConfirming(false)
      setPassword('')
      void queryClient.invalidateQueries({ queryKey: ['privacy-requests'] })
      pushToast({ variant: 'success', message: t('privacy.eraseAccepted') })
    },
  })

  return (
    <section className="flex flex-col gap-4">
      <div>
        <h2 className="text-xl font-semibold text-danger">{t('privacy.eraseTitle')}</h2>
        <p className="mt-1 text-sm text-fg-muted">{t('privacy.eraseDesc')}</p>
      </div>

      {confirming ? (
        <div className="flex flex-col gap-3 rounded-xl border border-danger/30 bg-danger-soft p-4">
          <p className="text-sm text-fg">{t('privacy.eraseConfirmHint')}</p>
          <Input
            id="erasePassword"
            label={t('privacy.erasePasswordLabel')}
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
          <div className="flex gap-2">
            <Button
              variant="danger"
              size="md"
              disabled={password.length === 0}
              loading={eraseMutation.isPending}
              onClick={() => eraseMutation.mutate(password)}
            >
              {t('privacy.eraseConfirm')}
            </Button>
            <Button
              variant="ghost"
              size="md"
              onClick={() => {
                setConfirming(false)
                setPassword('')
              }}
            >
              {t('common.cancel')}
            </Button>
          </div>
        </div>
      ) : (
        <div>
          <Button variant="ghost" size="md" onClick={() => setConfirming(true)}>
            {t('privacy.eraseAction')}
          </Button>
        </div>
      )}
    </section>
  )
}
