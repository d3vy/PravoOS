import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import { AnimatedSection } from '../components/ui/AnimatedSection'
import { LandingHeader } from '../components/landing/LandingHeader'
import { ChatDemo } from '../components/landing/ChatDemo'
import { CaseMockup } from '../components/landing/CaseMockup'
import { CalendarMockup } from '../components/landing/CalendarMockup'
import { useCookieBannerStore } from '../store/cookieBannerStore'

const reopenCookieBanner = (): void => useCookieBannerStore.getState().reopen()

export default function LandingPage(): JSX.Element {
  const { t } = useTranslation()

  const capabilities = [
    t('landing.capSearch'),
    t('landing.capCitations'),
    t('landing.capCases'),
    t('landing.capBilling'),
    t('landing.capExport'),
  ]

  const painPoints = [
    {
      title: t('landing.pain1Title'),
      problem: t('landing.pain1Problem'),
      solution: t('landing.pain1Solution'),
      number: '01',
      visual: <SearchVisual />,
    },
    {
      title: t('landing.pain2Title'),
      problem: t('landing.pain2Problem'),
      solution: t('landing.pain2Solution'),
      number: '02',
      visual: <RiskVisual />,
    },
    {
      title: t('landing.pain3Title'),
      problem: t('landing.pain3Problem'),
      solution: t('landing.pain3Solution'),
      number: '03',
      visual: <DraftVisual />,
    },
  ]

  const steps = [
    { number: '01', title: t('landing.step1Title'), description: t('landing.step1Desc') },
    { number: '02', title: t('landing.step2Title'), description: t('landing.step2Desc') },
    { number: '03', title: t('landing.step3Title'), description: t('landing.step3Desc') },
  ]

  const securityItems = [
    { number: '01', title: t('landing.sec1Title'), description: t('landing.sec1Desc') },
    { number: '02', title: t('landing.sec2Title'), description: t('landing.sec2Desc') },
    { number: '03', title: t('landing.sec3Title'), description: t('landing.sec3Desc') },
    { number: '04', title: t('landing.sec4Title'), description: t('landing.sec4Desc') },
    { number: '05', title: t('landing.sec5Title'), description: t('landing.sec5Desc') },
    { number: '06', title: t('landing.sec6Title'), description: t('landing.sec6Desc') },
  ]

  return (
    <div className="bg-[#F7F7F4] text-[#1A0F0A]">
      <LandingHeader />

      {/* Hero */}
      <section className="page-container pt-4 pb-8 sm:pt-6">
        <div className="relative overflow-hidden rounded-2xl bg-[#F2F1ED]">
          <div className="grid lg:grid-cols-[1fr_600px]">
            <div className="min-w-0 px-6 py-12 sm:px-10 sm:py-16 lg:py-20">
              <p className="font-script text-xl sm:text-2xl text-[#1A0F0A]/50 mb-4 tracking-wide">
                {t('landing.heroEyebrow')}
              </p>
              <motion.h1
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.55, ease: [0.16, 1, 0.3, 1] }}
                className="font-display text-5xl sm:text-6xl lg:text-[86px] leading-[0.95] tracking-tight mb-6 max-w-xl"
              >
                {t('landing.heroTitleLine1')} {t('landing.heroTitleLine2')}
              </motion.h1>
              <p className="text-[#1A0F0A]/50 text-base sm:text-lg leading-relaxed mb-8 sm:max-w-md">
                {t('landing.heroSubtitle')}
              </p>
              <div className="flex flex-wrap items-center gap-3 mb-10">
                <Link
                  to="/apply"
                  className="inline-flex items-center justify-center px-7 py-3.5 rounded-full bg-[#1A0F0A] text-white text-[15px] sm:text-base hover:opacity-90 transition-opacity"
                >
                  {t('landing.heroCtaPrimary')}
                </Link>
                <Link
                  to="/login"
                  className="inline-flex items-center justify-center gap-2 px-7 py-3.5 rounded-full bg-[#E6E5E0] text-[#1A0F0A] text-[15px] sm:text-base hover:bg-[#DDDCD8] transition-colors"
                >
                  {t('landing.heroCtaSecondary')}
                </Link>
              </div>
              <div className="flex flex-wrap gap-2">
                {capabilities.map((cap) => (
                  <span
                    key={cap}
                    className="inline-flex items-center px-4 py-2 rounded-xl bg-[#E6E5E0] text-[#1A0F0A] text-sm"
                  >
                    {cap}
                  </span>
                ))}
              </div>
            </div>

            <motion.div
              initial={{ opacity: 0, scale: 0.98 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={{ duration: 0.6, delay: 0.15, ease: [0.16, 1, 0.3, 1] }}
              className="min-w-0 flex items-center justify-center p-6 py-10 lg:p-8 bg-gradient-to-br from-[#745C44] to-[#3D2F22]"
            >
              <div className="w-full max-w-[460px] shadow-2xl shadow-black/30 rounded-xl">
                <ChatDemo />
              </div>
            </motion.div>
          </div>
        </div>
      </section>

      {/* Product */}
      <section id="product" className="page-container py-16 sm:py-24">
        <AnimatedSection className="mb-12 max-w-2xl">
          <p className="font-script text-xl text-[#1A0F0A]/50 mb-3 tracking-wide">{t('landing.showcaseEyebrow')}</p>
          <h2 className="font-display text-4xl sm:text-5xl lg:text-6xl leading-[0.95] tracking-tight">
            {t('landing.showcaseHeading')}
          </h2>
        </AnimatedSection>

        <div className="grid lg:grid-cols-2 gap-6">
          <AnimatedSection className="min-w-0 rounded-2xl bg-[#F2F1ED] p-4 sm:p-6">
            <div className="rounded-xl bg-[#D7D3CD] p-3 sm:p-5 mb-6">
              <CaseMockup />
            </div>
            <div className="px-2">
              <h3 className="text-xl sm:text-2xl font-medium tracking-tight mb-2">
                {t('landing.showcaseCaseTitle')}
              </h3>
              <p className="text-sm text-[#1A0F0A]/50 leading-relaxed max-w-sm">
                {t('landing.showcaseCaseDesc')}
              </p>
            </div>
          </AnimatedSection>

          <AnimatedSection delay={0.1} className="min-w-0 rounded-2xl bg-[#F2F1ED] p-4 sm:p-6">
            <div className="rounded-xl bg-[#B5B8BD] p-3 sm:p-5 mb-6">
              <CalendarMockup />
            </div>
            <div className="px-2">
              <h3 className="text-xl sm:text-2xl font-medium tracking-tight mb-2">
                {t('landing.showcaseCalendarTitle')}
              </h3>
              <p className="text-sm text-[#1A0F0A]/50 leading-relaxed max-w-sm">
                {t('landing.showcaseCalendarDesc')}
              </p>
            </div>
          </AnimatedSection>
        </div>
      </section>

      {/* Why */}
      <section className="page-container py-16 sm:py-24">
        <AnimatedSection className="mb-12 max-w-3xl">
          <p className="font-script text-xl text-[#1A0F0A]/50 mb-3 tracking-wide">{t('landing.painEyebrow')}</p>
          <h2 className="font-display text-4xl sm:text-5xl lg:text-6xl leading-[0.95] tracking-tight">
            {t('landing.painHeading')}
          </h2>
        </AnimatedSection>

        <div className="flex flex-col gap-5">
          {painPoints.map((point, index) => (
            <AnimatedSection key={point.title} delay={index * 0.05}>
              <div className="rounded-2xl bg-[#F2F1ED] p-6 sm:p-10 grid lg:grid-cols-2 gap-8 items-center">
                <div>
                  <span className="font-display text-3xl block mb-4">{point.number}</span>
                  <h3 className="text-xl sm:text-2xl font-medium tracking-tight mb-3">
                    {point.title}
                  </h3>
                  <p className="text-sm text-[#1A0F0A]/50 leading-relaxed mb-5 sm:max-w-md">
                    {point.problem}
                  </p>
                  <p className="text-sm font-semibold text-[#745C44] leading-relaxed sm:max-w-md">
                    {point.solution}
                  </p>
                </div>
                <div className="rounded-xl bg-[#D7D3CD] p-3 sm:p-5">{point.visual}</div>
              </div>
            </AnimatedSection>
          ))}
        </div>
      </section>

      {/* How it works */}
      <section className="bg-[#745C44] py-16 sm:py-24">
        <div className="page-container">
          <AnimatedSection className="mb-12 max-w-2xl text-center mx-auto">
            <p className="font-script text-xl text-white/50 mb-3 tracking-wide">{t('landing.stepsEyebrow')}</p>
            <h2 className="font-display text-4xl sm:text-5xl lg:text-6xl leading-[0.95] tracking-tight text-white">
              {t('landing.stepsHeading')}
            </h2>
          </AnimatedSection>

          <div className="grid md:grid-cols-3 gap-4">
            {steps.map((step, index) => (
              <AnimatedSection key={step.number} delay={index * 0.1}>
                <div className="rounded-xl border border-white/20 p-6 h-full">
                  <span className="font-display text-2xl text-white block mb-8">{step.number}</span>
                  <h3 className="text-lg font-medium tracking-tight text-white mb-2">
                    {step.title}
                  </h3>
                  <p className="text-sm text-white/50 leading-relaxed">
                    {step.description}
                  </p>
                </div>
              </AnimatedSection>
            ))}
          </div>
        </div>
      </section>

      {/* Security */}
      <section id="security" className="page-container py-16 sm:py-24">
        <AnimatedSection className="mb-12 max-w-2xl text-center mx-auto">
          <p className="font-script text-xl text-[#1A0F0A]/50 mb-3 tracking-wide">{t('landing.securityEyebrow')}</p>
          <h2 className="font-display text-4xl sm:text-5xl lg:text-6xl leading-[0.95] tracking-tight">
            {t('landing.securityHeading')}
          </h2>
        </AnimatedSection>

        <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {securityItems.map((item, index) => (
            <AnimatedSection key={item.title} delay={index * 0.05}>
              <div className="rounded-xl bg-[#F2F1ED] p-6 h-full">
                <span className="font-display text-2xl block mb-8">{item.number}</span>
                <h3 className="text-lg font-medium tracking-tight mb-1.5">
                  {item.title}
                </h3>
                <p className="text-sm text-[#1A0F0A]/50 leading-relaxed">
                  {item.description}
                </p>
              </div>
            </AnimatedSection>
          ))}
        </div>
      </section>

      {/* Early access */}
      <section id="early-access" className="page-container py-16 sm:py-24">
        <AnimatedSection>
          <div className="max-w-4xl mx-auto text-center">
            <p className="font-script text-xl text-[#1A0F0A]/50 mb-6 tracking-wide">{t('landing.earlyAccessBadge')}</p>
            <p className="font-display text-3xl sm:text-4xl lg:text-5xl leading-[1.05] tracking-tight mb-8">
              {t('landing.earlyAccessQuote')}
            </p>
            <div className="flex flex-wrap items-center justify-center gap-2">
              <span className="inline-flex items-center px-3 py-1.5 rounded-full bg-[#E6E5E0] text-[#1A0F0A] text-sm">
                {t('landing.earlyAccessBadgeTeam')}
              </span>
              <span className="inline-flex items-center px-3 py-1.5 rounded-full bg-[#E6E5E0] text-[#1A0F0A] text-sm">
                {t('landing.earlyAccessBadgeCohort')}
              </span>
            </div>
          </div>
        </AnimatedSection>
      </section>

      {/* Start working + footer */}
      <section id="contacts" className="bg-[#1A0F0A] text-white py-16 sm:py-24">
        <div className="page-container">
          <AnimatedSection>
            <div className="rounded-2xl bg-[#21140E] px-6 py-14 sm:px-10 sm:py-20 text-center mb-16">
              <p className="font-script text-xl text-white/50 mb-6 tracking-wide">{t('landing.ctaEyebrow')}</p>
              <h2 className="font-display text-5xl sm:text-6xl lg:text-8xl leading-[0.9] tracking-tight text-[#FDC298] mb-10 max-w-4xl mx-auto">
                {t('landing.ctaHeading')}
              </h2>
              <p className="text-white/50 text-base sm:text-lg leading-relaxed mb-10 max-w-xl mx-auto">
                {t('landing.ctaSubtitle')}
              </p>
              <Link
                to="/apply"
                className="inline-flex items-center justify-center px-8 py-4 rounded-full bg-[#FDC298] text-[#1A0F0A] text-base font-medium hover:opacity-90 transition-opacity"
              >
                {t('landing.ctaButton')}
              </Link>
            </div>
          </AnimatedSection>

          <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-10 mb-14">
            <div>
              <span className="font-display text-lg text-white">
                Pravo<span className="opacity-60">OS</span>
              </span>
              <p className="mt-3 text-sm text-white/50 leading-relaxed">
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
              <p className="font-display text-base text-white/60 mb-3">
                {t('landing.footerSecurityTitle')}
              </p>
              <p className="text-sm text-white/50 leading-relaxed">
                {t('landing.footerSecurityText')}
              </p>
            </div>
            <div>
              <p className="font-display text-base text-white/60 mb-3">
                {t('landing.footerContactsTitle')}
              </p>
              <p className="text-sm text-white/50 leading-relaxed">
                {t('landing.footerContactsText')}
              </p>
            </div>
          </div>

          <div className="pt-6 border-t border-white/10 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <p className="text-sm text-white/50">
              {t('landing.footerCopyright', { year: new Date().getFullYear() })}
            </p>
            <nav className="flex flex-wrap gap-x-4 gap-y-2" aria-label={t('legal.navLabel')}>
              <Link to="/legal/privacy" className="text-sm text-white/50 hover:text-white transition-colors">
                {t('legal.navPrivacy')}
              </Link>
              <Link to="/legal/consent" className="text-sm text-white/50 hover:text-white transition-colors">
                {t('legal.navConsent')}
              </Link>
              <Link to="/legal/cross-border" className="text-sm text-white/50 hover:text-white transition-colors">
                {t('legal.navCrossBorder')}
              </Link>
              <Link to="/legal/cookies" className="text-sm text-white/50 hover:text-white transition-colors">
                {t('legal.navCookies')}
              </Link>
              <button
                type="button"
                onClick={reopenCookieBanner}
                className="text-sm text-white/50 hover:text-white transition-colors"
              >
                {t('legal.navCookieSettings')}
              </button>
            </nav>
          </div>
        </div>
      </section>
    </div>
  )
}

function FooterColumn({ title, links }: { title: string; links: { label: string; to: string }[] }): JSX.Element {
  return (
    <div>
      <p className="font-display text-base text-white/60 mb-3">
        {title}
      </p>
      <ul className="flex flex-col gap-2">
        {links.map((link) => (
          <li key={link.to}>
            <Link
              to={link.to}
              className="text-sm text-white/50 hover:text-white transition-colors"
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
    <div className="rounded-xl bg-[#F2F1ED] p-5">
      <div className="flex items-center gap-2 px-3.5 py-3 rounded-xl bg-[#F7F7F4] border border-[#E8E7E2] mb-3">
        <svg className="w-4 h-4 text-[#8E8D89]" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
          <circle cx="11" cy="11" r="7" />
          <line x1="16.5" y1="16.5" x2="21" y2="21" />
        </svg>
        <span className="text-[13px] text-[#26251E]/60">{t('landing.visualSearchQuery')}</span>
      </div>
      <div className="flex flex-col gap-2">
        {results.map((r, i) => (
          <div key={r} className="flex items-center justify-between gap-2 px-3.5 py-3 rounded-xl bg-[#F7F7F4] border border-[#E8E7E2]">
            <span className="text-[13px] text-[#26251E] truncate">{r}</span>
            <span className="text-[12px] text-[#26251E]/60 shrink-0">
              {i === 0 ? t('landing.visualSearchTime') : '0.9с'}
            </span>
          </div>
        ))}
      </div>
    </div>
  )
}

function RiskVisual(): JSX.Element {
  const { t } = useTranslation()
  return (
    <div className="rounded-xl bg-[#F2F1ED] p-5">
      <div className="flex items-center justify-between mb-3">
        <span className="text-[15px] font-medium text-[#26251E]">{t('landing.visualRiskFile')}</span>
        <span className="text-[15px] font-semibold text-[#B91C1C]">{t('landing.visualRiskScore')}</span>
      </div>
      <div className="flex flex-col gap-3">
        {[
          { level: t('landing.visualRiskHighLevel'), tone: 'border-[#B91C1C] text-[#B91C1C]', text: t('landing.visualRiskHighText') },
          { level: t('landing.visualRiskMediumLevel'), tone: 'border-[#FCD34D] text-[#B45309]', text: t('landing.visualRiskMediumText') },
        ].map((f) => (
          <div key={f.text} className={`px-4 py-3 rounded-xl border ${f.tone}`}>
            <span className="text-[11px] font-semibold uppercase tracking-wide">{f.level} {t('landing.visualRiskSuffix')}</span>
            <p className="text-[13px] text-[#26251E] mt-1">{f.text}</p>
          </div>
        ))}
      </div>
    </div>
  )
}

function DraftVisual(): JSX.Element {
  const { t } = useTranslation()
  return (
    <div className="rounded-xl bg-[#F2F1ED] p-5">
      <div className="flex items-center gap-2 mb-4">
        <span className="w-5 h-5 rounded-md bg-[#745C44]/20 text-[#745C44] text-[10px] font-bold flex items-center justify-center">
          AI
        </span>
        <span className="text-[13px] font-medium text-[#26251E]">{t('landing.visualDraftTitle')}</span>
      </div>
      <div className="flex flex-col gap-2">
        {[92, 76, 84, 60].map((w, i) => (
          <div key={i} className="h-3.5 rounded-full bg-[#EAE8E0]" style={{ width: `${w}%` }} />
        ))}
      </div>
      <div className="mt-4 flex items-center gap-2">
        <span className="text-[12px] font-medium px-2.5 py-1 rounded-lg bg-[#745C44]/20 text-[#745C44]">{t('landing.visualDraftDownload')}</span>
        <span className="text-[12px] font-medium px-2.5 py-1.5 rounded-lg border border-[#26251E]/20 text-[#26251E]/50">{t('landing.visualDraftEdit')}</span>
      </div>
    </div>
  )
}
