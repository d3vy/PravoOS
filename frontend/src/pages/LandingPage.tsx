import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { AnimatedSection } from '../components/ui/AnimatedSection'
import { Button } from '../components/ui/Button'
import { Navbar } from '../components/layout/Navbar'
import { ChatDemo } from '../components/landing/ChatDemo'
import { CaseMockup } from '../components/landing/CaseMockup'
import { CalendarMockup } from '../components/landing/CalendarMockup'

export default function LandingPage(): JSX.Element {
  const { t } = useTranslation()

  const capabilities = [
    t('landing.capSearch'),
    t('landing.capCitations'),
    t('landing.capCases'),
    t('landing.capBilling'),
    t('landing.capExport'),
  ]

  const painPoints: { title: string; problem: string; solution: string; visual: ReactNode }[] = [
    {
      title: t('landing.pain1Title'),
      problem: t('landing.pain1Problem'),
      solution: t('landing.pain1Solution'),
      visual: <SearchVisual />,
    },
    {
      title: t('landing.pain2Title'),
      problem: t('landing.pain2Problem'),
      solution: t('landing.pain2Solution'),
      visual: <RiskVisual />,
    },
    {
      title: t('landing.pain3Title'),
      problem: t('landing.pain3Problem'),
      solution: t('landing.pain3Solution'),
      visual: <DraftVisual />,
    },
  ]

  const steps = [
    { number: '01', title: t('landing.step1Title'), description: t('landing.step1Desc') },
    { number: '02', title: t('landing.step2Title'), description: t('landing.step2Desc') },
    { number: '03', title: t('landing.step3Title'), description: t('landing.step3Desc') },
  ]

  const securityItems = [
    { title: t('landing.sec1Title'), description: t('landing.sec1Desc') },
    { title: t('landing.sec2Title'), description: t('landing.sec2Desc') },
    { title: t('landing.sec3Title'), description: t('landing.sec3Desc') },
    { title: t('landing.sec4Title'), description: t('landing.sec4Desc') },
    { title: t('landing.sec5Title'), description: t('landing.sec5Desc') },
    { title: t('landing.sec6Title'), description: t('landing.sec6Desc') },
  ]

  return (
    <div className="auth-shell">
      <Navbar />

      {/* Hero */}
      <section className="relative overflow-hidden">
        <div
          aria-hidden="true"
          className="pointer-events-none absolute -top-40 right-0 w-[560px] h-[560px] rounded-full bg-accent/10 blur-3xl"
        />
        <div className="page-container relative pt-20 pb-24 md:pt-28 md:pb-32">
          <div className="grid lg:grid-cols-2 gap-14 lg:gap-10 items-center">
            <motion.div
              initial={{ opacity: 0, y: 24 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.55, ease: [0.16, 1, 0.3, 1] }}
            >
              <p className="eyebrow mb-6 tracking-[0.2em]">{t('landing.heroEyebrow')}</p>
              <h1 className="font-sans text-4xl sm:text-5xl md:text-6xl font-black text-fg leading-[1.05] tracking-tight mb-6">
                {t('landing.heroTitleLine1')}<br />{t('landing.heroTitleLine2')}
              </h1>
              <p className="text-lg text-fg-muted leading-relaxed mb-8 max-w-lg font-light">
                {t('landing.heroSubtitle')}
              </p>
              <div className="flex flex-wrap items-center gap-4">
                <Link to="/apply">
                  <Button variant="primary" size="lg">
                    {t('landing.heroCtaPrimary')}
                  </Button>
                </Link>
                <Link to="/login">
                  <Button variant="ghost" size="lg">
                    {t('landing.heroCtaSecondary')}
                  </Button>
                </Link>
              </div>
              <p className="mt-6 text-sm text-fg-muted">
                {t('landing.heroNote')}
              </p>
            </motion.div>

            <motion.div
              initial={{ opacity: 0, y: 30, scale: 0.98 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              transition={{ duration: 0.6, delay: 0.15, ease: [0.16, 1, 0.3, 1] }}
              className="lg:pl-4"
            >
              <ChatDemo />
            </motion.div>
          </div>
        </div>
      </section>

      {/* Capability strip */}
      <section className="border-y border-line bg-surface/50">
        <div className="page-container py-6">
          <div className="flex flex-wrap items-center gap-x-6 gap-y-3 justify-center md:justify-between">
            {capabilities.map((cap) => (
              <span key={cap} className="inline-flex items-center gap-2 text-sm text-fg-muted">
                <span className="w-1.5 h-1.5 rounded-full bg-accent-solid" />
                {cap}
              </span>
            ))}
          </div>
        </div>
      </section>

      {/* Product showcase */}
      <section className="py-24 md:py-28">
        <div className="page-container">
          <AnimatedSection className="mb-14 max-w-2xl">
            <p className="eyebrow mb-5">{t('landing.showcaseEyebrow')}</p>
            <h2 className="font-sans text-3xl sm:text-4xl md:text-5xl font-black text-fg leading-tight tracking-tight">
              {t('landing.showcaseHeading')}
            </h2>
          </AnimatedSection>

          <div className="grid lg:grid-cols-2 gap-8 items-start">
            <AnimatedSection>
              <CaseMockup />
              <div className="mt-5 max-w-md">
                <h3 className="font-sans font-semibold text-fg mb-1.5">
                  {t('landing.showcaseCaseTitle')}
                </h3>
                <p className="text-sm text-fg-muted leading-relaxed font-light">
                  {t('landing.showcaseCaseDesc')}
                </p>
              </div>
            </AnimatedSection>

            <AnimatedSection delay={0.1} className="lg:mt-16">
              <CalendarMockup />
              <div className="mt-5 max-w-md">
                <h3 className="font-sans font-semibold text-fg mb-1.5">
                  {t('landing.showcaseCalendarTitle')}
                </h3>
                <p className="text-sm text-fg-muted leading-relaxed font-light">
                  {t('landing.showcaseCalendarDesc')}
                </p>
              </div>
            </AnimatedSection>
          </div>
        </div>
      </section>

      {/* Pain → solution */}
      <section className="py-24 md:py-28 bg-surface border-y border-line">
        <div className="page-container">
          <AnimatedSection className="mb-16 max-w-2xl">
            <p className="eyebrow mb-5">{t('landing.painEyebrow')}</p>
            <h2 className="font-sans text-3xl sm:text-4xl md:text-5xl font-black text-fg leading-tight tracking-tight">
              {t('landing.painHeading')}
            </h2>
          </AnimatedSection>

          <div className="flex flex-col gap-14">
            {painPoints.map((point, index) => (
              <AnimatedSection key={point.title} delay={index * 0.05}>
                <div
                  className={`grid md:grid-cols-2 gap-8 md:gap-12 items-center ${
                    index % 2 === 1 ? 'md:[&>*:first-child]:order-2' : ''
                  }`}
                >
                  <div>
                    <h3 className="font-sans text-xl font-semibold text-fg mb-3 tracking-tight">
                      {point.title}
                    </h3>
                    <p className="text-sm text-fg-muted leading-relaxed font-light mb-4">
                      {point.problem}
                    </p>
                    <div className="flex items-start gap-3">
                      <svg
                        className="text-accent mt-0.5 shrink-0"
                        width="16"
                        height="16"
                        viewBox="0 0 24 24"
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="2.5"
                        strokeLinecap="round"
                        strokeLinejoin="round"
                      >
                        <polyline points="20 6 9 17 4 12" />
                      </svg>
                      <p className="text-sm font-medium text-fg leading-relaxed">
                        {point.solution}
                      </p>
                    </div>
                  </div>
                  <div>{point.visual}</div>
                </div>
              </AnimatedSection>
            ))}
          </div>
        </div>
      </section>

      {/* How it works */}
      <section className="py-24 md:py-28">
        <div className="page-container">
          <AnimatedSection className="mb-16 max-w-2xl">
            <p className="eyebrow mb-5">{t('landing.stepsEyebrow')}</p>
            <h2 className="font-sans text-3xl sm:text-4xl md:text-5xl font-black text-fg leading-tight tracking-tight">
              {t('landing.stepsHeading')}
            </h2>
          </AnimatedSection>

          <div className="grid md:grid-cols-3 gap-10">
            {steps.map((step, index) => (
              <AnimatedSection key={step.number} delay={index * 0.1}>
                <div className="flex flex-col gap-4">
                  <div className="flex items-center gap-3">
                    <span className="font-sans text-2xl font-black text-accent tracking-tight">
                      {step.number}
                    </span>
                    <span className="flex-1 h-px bg-line" />
                  </div>
                  <h3 className="font-sans text-lg font-semibold text-fg tracking-tight">
                    {step.title}
                  </h3>
                  <p className="text-sm text-fg-muted leading-relaxed font-light">
                    {step.description}
                  </p>
                </div>
              </AnimatedSection>
            ))}
          </div>
        </div>
      </section>

      {/* Security */}
      <section className="py-24 md:py-28 bg-surface border-y border-line">
        <div className="page-container">
          <AnimatedSection className="mb-14 max-w-2xl">
            <p className="eyebrow mb-5">{t('landing.securityEyebrow')}</p>
            <h2 className="font-sans text-3xl sm:text-4xl md:text-5xl font-black text-fg leading-tight tracking-tight">
              {t('landing.securityHeading')}
            </h2>
          </AnimatedSection>

          <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {securityItems.map((item, index) => (
              <AnimatedSection key={item.title} delay={index * 0.05}>
                <div className="p-6 rounded-2xl border border-line bg-bg h-full">
                  <div className="w-9 h-9 rounded-lg bg-accent/10 text-accent flex items-center justify-center mb-4">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                      <path d="M12 3l7 3v6c0 4.5-3 7.5-7 9-4-1.5-7-4.5-7-9V6z" />
                      <path d="M9 12l2 2 4-4" />
                    </svg>
                  </div>
                  <h3 className="font-sans font-semibold text-fg mb-1.5 tracking-tight">
                    {item.title}
                  </h3>
                  <p className="text-sm text-fg-muted leading-relaxed font-light">
                    {item.description}
                  </p>
                </div>
              </AnimatedSection>
            ))}
          </div>
        </div>
      </section>

      {/* Early access / founder note */}
      <section className="py-24 md:py-28">
        <div className="page-container">
          <AnimatedSection>
            <div className="max-w-3xl mx-auto text-center">
              <span className="inline-flex items-center gap-2 px-3 py-1 rounded-full border border-accent/40 text-accent text-xs font-medium mb-6">
                <span className="w-1.5 h-1.5 rounded-full bg-accent-solid" />
                {t('landing.earlyAccessBadge')}
              </span>
              <p className="font-sans text-2xl md:text-3xl font-semibold text-fg leading-snug tracking-tight mb-4">
                {t('landing.earlyAccessQuote')}
              </p>
              <p className="text-sm text-fg-muted">
                {t('landing.earlyAccessAttribution')}
              </p>
            </div>
          </AnimatedSection>
        </div>
      </section>

      {/* CTA */}
      <section className="py-24 md:py-28 border-t border-line">
        <div className="page-container">
          <AnimatedSection>
            <div className="max-w-xl">
              <p className="eyebrow mb-6">{t('landing.ctaEyebrow')}</p>
              <h2 className="font-sans text-3xl sm:text-4xl md:text-5xl font-black text-fg mb-6 leading-tight tracking-tight">
                {t('landing.ctaHeading')}
              </h2>
              <p className="text-fg-muted mb-10 text-lg leading-relaxed font-light">
                {t('landing.ctaSubtitle')}
              </p>
              <Link to="/apply">
                <Button variant="primary" size="lg">
                  {t('landing.ctaButton')}
                </Button>
              </Link>
            </div>
          </AnimatedSection>
        </div>
      </section>

      {/* Footer */}
      <footer className="border-t border-line">
        <div className="page-container py-12">
          <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-8 mb-10">
            <div>
              <span className="font-sans text-sm font-semibold tracking-tight text-fg">
                Pravo<span className="font-light">OS</span>
              </span>
              <p className="mt-2 text-sm text-fg-muted font-light leading-relaxed">
                {t('landing.footerTagline')}
              </p>
            </div>
            <FooterColumn
              title={t('landing.footerProductTitle')}
              links={[
                { label: t('landing.footerLogin'), to: '/login' },
                { label: t('landing.footerApply'), to: '/apply' },
              ]}
            />
            <div>
              <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted mb-3">
                {t('landing.footerSecurityTitle')}
              </p>
              <p className="text-sm text-fg-muted font-light leading-relaxed">
                {t('landing.footerSecurityText')}
              </p>
            </div>
            <div>
              <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted mb-3">
                {t('landing.footerContactsTitle')}
              </p>
              <p className="text-sm text-fg-muted font-light leading-relaxed">
                {t('landing.footerContactsText')}
              </p>
            </div>
          </div>
          <div className="pt-6 border-t border-line">
            <p className="text-sm text-fg-muted font-light">
              {t('landing.footerCopyright', { year: new Date().getFullYear() })}
            </p>
          </div>
        </div>
      </footer>
    </div>
  )
}

function FooterColumn({ title, links }: { title: string; links: { label: string; to: string }[] }): JSX.Element {
  return (
    <div>
      <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted mb-3">
        {title}
      </p>
      <ul className="flex flex-col gap-2">
        {links.map((link) => (
          <li key={link.to}>
            <Link
              to={link.to}
              className="text-sm text-fg-muted hover:text-fg transition-colors font-light"
            >
              {link.label}
            </Link>
          </li>
        ))}
      </ul>
    </div>
  )
}

function SearchVisual(): JSX.Element {
  const { t } = useTranslation()
  const results = [t('landing.visualSearchResult1'), t('landing.visualSearchResult2'), t('landing.visualSearchResult3')]
  return (
    <div className="p-5 rounded-2xl border border-line bg-bg">
      <div className="flex items-center gap-2 px-3 py-2 rounded-lg border border-line mb-3">
        <svg className="w-4 h-4 text-fg-muted" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <circle cx="11" cy="11" r="7" />
          <line x1="16.5" y1="16.5" x2="21" y2="21" />
        </svg>
        <span className="text-xs text-fg-muted">{t('landing.visualSearchQuery')}</span>
      </div>
      <div className="flex flex-col gap-2">
        {results.map((r) => (
          <div key={r} className="flex items-center justify-between gap-2 px-3 py-2 rounded-lg bg-surface border border-line">
            <span className="text-xs text-fg truncate">{r}</span>
            <span className="text-[10px] text-fg-muted shrink-0">{t('landing.visualSearchTime')}</span>
          </div>
        ))}
      </div>
    </div>
  )
}

function RiskVisual(): JSX.Element {
  const { t } = useTranslation()
  return (
    <div className="p-5 rounded-2xl border border-line bg-bg">
      <div className="flex items-center justify-between mb-3">
        <span className="text-xs font-medium text-fg">{t('landing.visualRiskFile')}</span>
        <span className="text-xs font-semibold text-danger">{t('landing.visualRiskScore')}</span>
      </div>
      <div className="flex flex-col gap-2">
        {[
          { level: t('landing.visualRiskHighLevel'), tone: 'border-red-300 text-red-700 dark:border-red-500/40 dark:text-red-400', text: t('landing.visualRiskHighText') },
          { level: t('landing.visualRiskMediumLevel'), tone: 'border-amber-300 text-amber-700 dark:border-amber-500/40 dark:text-amber-400', text: t('landing.visualRiskMediumText') },
        ].map((f) => (
          <div key={f.text} className={`px-3 py-2 rounded-lg border ${f.tone}`}>
            <span className="text-[10px] font-semibold uppercase tracking-wide">{f.level} {t('landing.visualRiskSuffix')}</span>
            <p className="text-xs text-fg mt-0.5">{f.text}</p>
          </div>
        ))}
      </div>
    </div>
  )
}

function DraftVisual(): JSX.Element {
  const { t } = useTranslation()
  return (
    <div className="p-5 rounded-2xl border border-line bg-bg">
      <div className="flex items-center gap-2 mb-3">
        <span className="w-5 h-5 rounded-md bg-accent/15 text-accent text-[10px] font-bold flex items-center justify-center">
          AI
        </span>
        <span className="text-xs font-medium text-fg">{t('landing.visualDraftTitle')}</span>
      </div>
      <div className="flex flex-col gap-1.5">
        {[92, 76, 84, 60].map((w, i) => (
          <div key={i} className="h-2 rounded-full bg-surface" style={{ width: `${w}%` }} />
        ))}
      </div>
      <div className="mt-3 flex items-center gap-2">
        <span className="text-[11px] px-2 py-0.5 rounded-md bg-accent/10 text-accent">{t('landing.visualDraftDownload')}</span>
        <span className="text-[11px] px-2 py-0.5 rounded-md border border-line text-fg-muted">{t('landing.visualDraftEdit')}</span>
      </div>
    </div>
  )
}
