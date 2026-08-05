import { useTranslation } from 'react-i18next'
import { Link, Navigate, useParams } from 'react-router-dom'
import { LegalMarkdown } from '../components/legal/LegalMarkdown'
import { Logo } from '../components/ui/Logo'
import { ThemeToggle } from '../components/ui/ThemeToggle'
import cookiePolicy from '../legal/politika-cookie.md?raw'
import crossBorderConsent from '../legal/soglasie-transgranichnaya-peredacha.md?raw'
import personalDataConsent from '../legal/soglasie-na-obrabotku-pdn.md?raw'
import privacyPolicy from '../legal/politika-obrabotki-pdn.md?raw'

const DOCUMENTS: Record<string, string> = {
  privacy: privacyPolicy,
  consent: personalDataConsent,
  'cross-border': crossBorderConsent,
  cookies: cookiePolicy,
}

const NAV_ITEMS: { slug: string; labelKey: string }[] = [
  { slug: 'privacy', labelKey: 'legal.navPrivacy' },
  { slug: 'consent', labelKey: 'legal.navConsent' },
  { slug: 'cross-border', labelKey: 'legal.navCrossBorder' },
  { slug: 'cookies', labelKey: 'legal.navCookies' },
]

export default function LegalPage(): JSX.Element {
  const { t } = useTranslation()
  const { slug } = useParams<{ slug: string }>()
  const document = slug ? DOCUMENTS[slug] : undefined

  if (!document) {
    return <Navigate to="/legal/privacy" replace />
  }

  return (
    <div className="min-h-screen bg-bg flex flex-col">
      <header className="flex items-center justify-between px-6 py-4 border-b border-line bg-surface">
        <Link to="/" className="hover:opacity-80 transition-opacity">
          <Logo />
        </Link>
        <ThemeToggle />
      </header>

      <main className="page-container flex-1 py-10">
        <nav className="mb-8 flex flex-wrap gap-2" aria-label={t('legal.navLabel')}>
          {NAV_ITEMS.map((item) => (
            <Link
              key={item.slug}
              to={`/legal/${item.slug}`}
              className={`rounded-lg border px-3 py-1.5 text-sm transition-colors ${
                item.slug === slug
                  ? 'border-accent bg-accent/5 text-fg'
                  : 'border-line text-fg-muted hover:text-fg'
              }`}
            >
              {t(item.labelKey)}
            </Link>
          ))}
        </nav>

        <article className="max-w-3xl">
          <LegalMarkdown source={document} />
        </article>

        <p className="mt-10 max-w-3xl text-xs text-fg-muted">{t('legal.contactHint')}</p>
      </main>
    </div>
  )
}
