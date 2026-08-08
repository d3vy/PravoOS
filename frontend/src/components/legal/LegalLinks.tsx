import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useCookieBannerStore } from '../../store/cookieBannerStore'

type LegalLinksTone = 'default' | 'inverted'

interface LegalLinksProps {
  tone?: LegalLinksTone
  className?: string
}

const TONE_CLASS: Record<LegalLinksTone, string> = {
  default: 'text-fg-muted hover:text-fg',
  inverted: 'text-white/50 hover:text-white',
}

const LEGAL_ROUTES = [
  { to: '/legal/privacy', labelKey: 'legal.navPrivacy' },
  { to: '/legal/consent', labelKey: 'legal.navConsent' },
  { to: '/legal/cross-border', labelKey: 'legal.navCrossBorder' },
  { to: '/legal/cookies', labelKey: 'legal.navCookies' },
] as const

export function LegalLinks({ tone = 'default', className = '' }: LegalLinksProps): JSX.Element {
  const { t } = useTranslation()
  const reopen = useCookieBannerStore((state) => state.reopen)
  const itemClass = `text-sm transition-colors ${TONE_CLASS[tone]}`

  return (
    <nav
      className={`flex flex-wrap gap-x-4 gap-y-2 ${className}`.trim()}
      aria-label={t('legal.navLabel')}
    >
      {LEGAL_ROUTES.map((route) => (
        <Link key={route.to} to={route.to} className={itemClass}>
          {t(route.labelKey)}
        </Link>
      ))}
      <button type="button" onClick={reopen} className={itemClass}>
        {t('legal.navCookieSettings')}
      </button>
    </nav>
  )
}
