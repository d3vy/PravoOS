import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { motion } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import i18n from '../i18n'
import { authApi } from '../api/auth'
import type { ApplicationResponse, ApplicationStatus } from '../types'
import { Button } from '../components/ui/Button'
import { Logo } from '../components/ui/Logo'
import { ThemeToggle } from '../components/ui/ThemeToggle'

type LoadState = 'loading' | 'loaded' | 'notFound'

const STATUS_META: Record<ApplicationStatus, { labelKey: string; descriptionKey: string; className: string }> = {
  PENDING: {
    labelKey: 'applicationStatus.statusPendingLabel',
    descriptionKey: 'applicationStatus.statusPendingDesc',
    className: 'bg-amber-50 dark:bg-amber-900/20 text-amber-700 dark:text-amber-400',
  },
  APPROVED: {
    labelKey: 'applicationStatus.statusApprovedLabel',
    descriptionKey: 'applicationStatus.statusApprovedDesc',
    className: 'bg-emerald-50 dark:bg-emerald-900/20 text-emerald-700 dark:text-emerald-400',
  },
  REJECTED: {
    labelKey: 'applicationStatus.statusRejectedLabel',
    descriptionKey: 'applicationStatus.statusRejectedDesc',
    className: 'bg-red-50 dark:bg-red-900/20 text-red-700 dark:text-red-400',
  },
}

function formatDateTime(value: string | null): string {
  if (!value) return '—'
  const locale = i18n.language.startsWith('ru') ? 'ru-RU' : 'en-US'
  return new Date(value).toLocaleString(locale, {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function StatusField({ label, value, highlight = false }: { label: string; value: string; highlight?: boolean }): JSX.Element {
  return (
    <div className="flex items-baseline justify-between gap-4">
      <dt className="text-light-secondary dark:text-dark-secondary shrink-0">{label}</dt>
      <dd
        className={`text-right break-all ${
          highlight
            ? 'font-semibold text-light-accent dark:text-dark-accent'
            : 'text-light-text dark:text-dark-text'
        }`}
      >
        {value}
      </dd>
    </div>
  )
}

export default function ApplicationStatusPage(): JSX.Element {
  const { t } = useTranslation()
  const { token } = useParams<{ token: string }>()
  const [state, setState] = useState<LoadState>('loading')
  const [application, setApplication] = useState<ApplicationResponse | null>(null)

  useEffect(() => {
    if (!token) {
      setState('notFound')
      return
    }
    let active = true
    authApi
      .getApplicationStatus(token)
      .then((data) => {
        if (!active) return
        setApplication(data)
        setState('loaded')
      })
      .catch(() => {
        if (active) setState('notFound')
      })
    return () => {
      active = false
    }
  }, [token])

  const renderBody = (): JSX.Element => {
    if (state === 'loading') {
      return (
        <div className="card-elevated rounded-2xl p-10 text-center">
          <div className="w-10 h-10 mx-auto mb-6 border-2 border-light-border dark:border-dark-border border-t-light-text dark:border-t-dark-text rounded-full animate-spin" />
          <p className="text-light-secondary dark:text-dark-secondary">{t('applicationStatus.loading')}</p>
        </div>
      )
    }

    if (state === 'notFound' || !application) {
      return (
        <div className="card-elevated rounded-2xl p-10 text-center">
          <div className="w-16 h-16 rounded-full bg-amber-50 dark:bg-amber-900/20 flex items-center justify-center text-amber-600 dark:text-amber-400 mx-auto mb-6">
            <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="12" cy="12" r="10" />
              <line x1="12" y1="8" x2="12" y2="12" />
              <line x1="12" y1="16" x2="12.01" y2="16" />
            </svg>
          </div>
          <h2 className="text-2xl font-semibold text-light-text dark:text-dark-text mb-4">{t('applicationStatus.notFoundTitle')}</h2>
          <p className="text-light-secondary dark:text-dark-secondary leading-relaxed mb-8">
            {t('applicationStatus.notFoundDesc')}
          </p>
          <Link to="/apply">
            <Button variant="primary" size="md">{t('applicationStatus.submitApplication')}</Button>
          </Link>
        </div>
      )
    }

    const meta = STATUS_META[application.status]

    return (
      <div className="card-elevated rounded-2xl p-8">
        <div className="mb-6">
          <span className={`inline-flex items-center rounded-full px-3 py-1 text-sm font-medium ${meta.className}`}>
            {t(meta.labelKey)}
          </span>
        </div>

        <h1 className="text-2xl font-bold text-light-text dark:text-dark-text mb-2 tracking-tight">
          {t('applicationStatus.title')}
        </h1>
        <p className="text-sm text-light-secondary dark:text-dark-secondary leading-relaxed mb-6">
          {t(meta.descriptionKey)}
        </p>

        <div className="rounded-xl border border-light-border dark:border-dark-border bg-light-surface dark:bg-dark-surface p-5 mb-6">
          <dl className="flex flex-col gap-2.5 text-sm">
            <StatusField label={t('apply.fieldFullName')} value={application.fullName} />
            <StatusField label={t('applicationStatus.fieldEmail')} value={application.email} highlight />
            <StatusField
              label={t('applicationStatus.fieldEmailVerified')}
              value={application.emailVerified ? t('common.yes') : t('common.no')}
            />
            <StatusField label={t('apply.fieldPhone')} value={application.phone} />
            <StatusField label={t('apply.fieldSpecialization')} value={application.specialization} />
            <StatusField label={t('applicationStatus.fieldSubmitted')} value={formatDateTime(application.submittedAt)} />
            {application.reviewedAt && (
              <StatusField label={t('applicationStatus.fieldReviewed')} value={formatDateTime(application.reviewedAt)} />
            )}
          </dl>
        </div>

        {application.status === 'PENDING' && !application.emailVerified && (
          <p className="text-xs text-light-secondary dark:text-dark-secondary mb-6">
            {t('applicationStatus.verifyHintBefore')}
            <Link to="/verify-email" className="text-light-accent dark:text-dark-accent hover:underline">
              {t('applicationStatus.verifyHintLink')}
            </Link>
            {t('applicationStatus.verifyHintAfter')}
          </p>
        )}

        {application.status === 'PENDING' && (
          <Link to={`/application/${token}/edit`}>
            <Button variant="primary" size="md" className="w-full mb-3">{t('applicationStatus.editApplication')}</Button>
          </Link>
        )}

        {application.status === 'APPROVED' ? (
          <Link to="/login">
            <Button variant="primary" size="md" className="w-full">{t('applicationStatus.login')}</Button>
          </Link>
        ) : (
          <Link to="/">
            <Button variant="ghost" size="md" className="w-full">{t('apply.backHome')}</Button>
          </Link>
        )}
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-white/80 dark:bg-dark-bg/85 flex flex-col">
      <header className="flex items-center justify-between px-6 py-4 border-b border-light-border dark:border-dark-border bg-light-surface dark:bg-dark-surface">
        <Link to="/" className="hover:opacity-80 transition-opacity">
          <Logo />
        </Link>
        <ThemeToggle />
      </header>

      <main className="flex-1 flex items-center justify-center px-4 py-12">
        <motion.div
          initial={{ opacity: 0, y: 24 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4, ease: 'easeOut' }}
          className="w-full max-w-md"
        >
          {renderBody()}
        </motion.div>
      </main>
    </div>
  )
}
