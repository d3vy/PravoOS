import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import i18n from '../i18n'
import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import axios from 'axios'
import { authApi } from '../api/auth'
import { privacyApi } from '../api/privacy'
import type { ApplicationSubmissionResponse } from '../types'
import { ApplicationForm, type ApplicationFormData } from '../components/ApplicationForm'
import { Button } from '../components/ui/Button'
import { Logo } from '../components/ui/Logo'
import { LanguageSwitcher } from '../components/ui/LanguageSwitcher'

const EMPTY_FORM: ApplicationFormData = {
  fullName: '',
  email: '',
  password: '',
  specialization: '',
  phone: '',
  personalDataConsent: false,
  crossBorderConsent: false,
  marketingConsent: false,
}

function submissionErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const status = error.response?.status
    const message = error.response?.data?.message as string | undefined
    const code = error.response?.data?.code as string | undefined
    if (status === 409 && code === 'APPLICATION_PENDING') {
      return i18n.t('apply.errorPending')
    }
    if (status === 409 && code === 'EMAIL_EXISTS') {
      return i18n.t('apply.errorEmailExists')
    }
    if (status === 409) {
      return message ?? i18n.t('apply.errorConflict')
    }
    if (status === 400 && code === 'CONSENT_REQUIRED') {
      return i18n.t('apply.errorConsentRequired')
    }
    if (status === 400 && message) {
      return message
    }
  }
  return i18n.t('apply.errorGeneric')
}

function ApplicationField({
  label,
  value,
  highlight = false,
}: {
  label: string
  value: string
  highlight?: boolean
}): JSX.Element {
  return (
    <div className="flex items-baseline justify-between gap-4">
      <dt className="text-fg-muted shrink-0">{label}</dt>
      <dd
        className={`text-right break-all ${
          highlight
            ? 'font-semibold text-accent'
            : 'text-fg'
        }`}
      >
        {value}
      </dd>
    </div>
  )
}

export default function ApplyPage(): JSX.Element {
  const { t } = useTranslation()
  const [submission, setSubmission] = useState<ApplicationSubmissionResponse | null>(null)
  const [policyVersion, setPolicyVersion] = useState('')

  useEffect(() => {
    let active = true
    privacyApi
      .policy()
      .then((policy) => {
        if (active) setPolicyVersion(policy.policyVersion)
      })
      .catch(() => {
        if (active) setPolicyVersion('')
      })
    return () => {
      active = false
    }
  }, [])

  const handleSubmit = async (data: ApplicationFormData): Promise<void> => {
    try {
      const result = await authApi.apply({ ...data, consentPolicyVersion: policyVersion })
      setSubmission(result)
    } catch (error) {
      throw new Error(submissionErrorMessage(error))
    }
  }

  return (
    <div className="bg-bg min-h-screen flex flex-col">
      <header className="page-container flex items-center justify-between h-16 sm:h-20">
        <Link to="/" className="hover:opacity-80 transition-opacity">
          <Logo />
        </Link>
        <LanguageSwitcher />
      </header>

      <main className="flex-1 flex items-center justify-center px-4 py-12">
        {submission ? (
          <motion.div
            initial={{ opacity: 0, scale: 0.96 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ duration: 0.4 }}
            className="w-full max-w-md text-center"
          >
            <div className="bg-surface rounded-2xl p-10 shadow-card">
              <div className="w-16 h-16 rounded-full bg-success-soft flex items-center justify-center text-success mx-auto mb-6">
                <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                  <polyline points="20 6 9 17 4 12" />
                </svg>
              </div>
              <h2 className="font-display text-3xl text-fg mb-4">
                {t('apply.submitted')}
              </h2>

              <div className="text-left rounded-xl border border-accent/30 bg-accent/5 p-5 mb-6">
                <p className="font-semibold text-fg mb-1">
                  {t('apply.confirmEmail')}
                </p>
                <p className="text-sm text-fg-muted leading-relaxed">
                  {t('apply.sentEmailTo', { email: submission.application.email })}
                </p>
              </div>

              <p className="text-fg-muted leading-relaxed mb-6">
                {t('apply.adminReview')}
              </p>

              <div className="text-left rounded-xl border border-line bg-surface p-5 mb-4">
                <p className="text-xs font-medium uppercase tracking-wide text-fg-muted mb-3">
                  {t('apply.submittedData')}
                </p>
                <dl className="flex flex-col gap-2.5 text-sm">
                  <ApplicationField label={t('apply.fieldFullName')} value={submission.application.fullName} />
                  <ApplicationField label="Email" value={submission.application.email} highlight />
                  <ApplicationField label={t('apply.fieldPhone')} value={submission.application.phone} />
                  <ApplicationField label={t('apply.fieldSpecialization')} value={submission.application.specialization} />
                </dl>
              </div>

              <p className="text-xs text-fg-muted mb-4">
                {t('apply.editHint')}
              </p>

              <div className="text-left rounded-xl border border-line bg-surface p-4 mb-6">
                <p className="text-xs text-fg-muted mb-2">
                  {t('apply.saveLinkHint')}
                </p>
                <Link
                  to={`/application/${submission.statusToken}`}
                  className="text-sm font-medium text-accent hover:underline break-all"
                >
                  {t('apply.openStatus')}
                </Link>
              </div>

              <Link to="/">
                <Button variant="ghost" size="md" className="w-full">
                  {t('apply.backHome')}
                </Button>
              </Link>
            </div>
          </motion.div>
        ) : (
          <motion.div
            initial={{ opacity: 0, y: 24 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.4, ease: 'easeOut' }}
            className="w-full max-w-lg"
          >
            <div className="bg-surface rounded-2xl p-8 sm:p-10 shadow-card">
              <div className="mb-8">
                <h1 className="font-display text-3xl text-fg mb-2 tracking-tight">
                  {t('apply.title')}
                </h1>
                <p className="text-sm text-fg-muted">
                  {t('apply.subtitle')}
                </p>
              </div>

              <ApplicationForm
                initialValues={EMPTY_FORM}
                consentsRequired
                passwordRequired
                submitLabel={t('apply.submit')}
                onSubmit={handleSubmit}
              />

              <div className="mt-6 pt-6 border-t border-line text-center">
                <p className="text-sm text-fg-muted">
                  {t('apply.alreadyHaveAccess')}{' '}
                  <Link
                    to="/login"
                    className="text-accent hover:underline font-medium"
                  >
                    {t('apply.login')}
                  </Link>
                </p>
              </div>
            </div>
          </motion.div>
        )}
      </main>
    </div>
  )
}
