import { useEffect, useState } from 'react'
import { Trans, useTranslation } from 'react-i18next'
import { AnimatePresence, motion } from 'framer-motion'
import { Link } from 'react-router-dom'
import { Button } from './ui/Button'
import { readCookieConsent } from '../utils/cookieConsent'
import { applyCookieDecision } from '../utils/applyCookieDecision'
import { useCookieBannerStore } from '../store/cookieBannerStore'

export function CookieBanner(): JSX.Element | null {
  const { t } = useTranslation()
  const [undecided, setUndecided] = useState(false)
  const reopened = useCookieBannerStore((state) => state.reopened)
  const close = useCookieBannerStore((state) => state.close)

  useEffect(() => {
    setUndecided(readCookieConsent() === null)
  }, [])

  const decide = (analytics: boolean): void => {
    applyCookieDecision(analytics)
    setUndecided(false)
    close()
  }

  return (
    <AnimatePresence>
      {(undecided || reopened) && (
        <motion.div
          initial={{ opacity: 0, y: 24 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: 24 }}
          transition={{ duration: 0.25, ease: 'easeOut' }}
          role="dialog"
          aria-label={t('cookieBanner.title')}
          className="fixed inset-x-0 bottom-0 z-50 px-4 pb-4"
        >
          <div className="card-elevated mx-auto flex max-w-3xl flex-col gap-4 rounded-2xl border border-line p-5 sm:flex-row sm:items-center">
            <div className="flex-1">
              <p className="mb-1 text-sm font-semibold text-fg">{t('cookieBanner.title')}</p>
              <p className="text-xs leading-relaxed text-fg-muted">
                <Trans
                  i18nKey="cookieBanner.description"
                  components={[
                    <Link key="cookies" to="/legal/cookies" className="text-accent hover:underline" />,
                  ]}
                />
              </p>
            </div>
            <div className="flex shrink-0 gap-2">
              <Button variant="secondary" size="md" onClick={() => decide(false)}>
                {t('cookieBanner.decline')}
              </Button>
              <Button variant="secondary" size="md" onClick={() => decide(true)}>
                {t('cookieBanner.accept')}
              </Button>
            </div>
          </div>
        </motion.div>
      )}
    </AnimatePresence>
  )
}
