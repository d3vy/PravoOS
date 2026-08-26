import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import { LandingHeader } from '../components/landing/LandingHeader'
import { LandingLogo } from '../components/landing/LandingLogo'
import { LegalLinks } from '../components/legal/LegalLinks'

type PreviewTone = 'warm' | 'cool'

const HERO_CAPABILITY_KEYS = [
  'landing.capSearch',
  'landing.capBilling',
  'landing.capExport',
  'landing.capCitations',
  'landing.capCases',
]

const PRODUCT_CARDS = [
  {
    titleKey: 'landing.showcaseCaseTitle',
    descriptionKey: 'landing.showcaseCaseDesc',
    image: '/assets/landing/product-case.png',
    tone: 'warm' as PreviewTone,
  },
  {
    titleKey: 'landing.showcaseCalendarTitle',
    descriptionKey: 'landing.showcaseCalendarDesc',
    image: '/assets/landing/product-calendar.png',
    tone: 'cool' as PreviewTone,
  },
]

const PAIN_POINTS = [
  {
    titleKey: 'landing.pain1Title',
    problemKey: 'landing.pain1Problem',
    solutionKey: 'landing.pain1Solution',
    image: '/assets/landing/why-search.png',
    tone: 'warm' as PreviewTone,
  },
  {
    titleKey: 'landing.pain3Title',
    problemKey: 'landing.pain3Problem',
    solutionKey: 'landing.pain3Solution',
    image: '/assets/landing/why-draft.png',
    tone: 'warm' as PreviewTone,
  },
  {
    titleKey: 'landing.pain2Title',
    problemKey: 'landing.pain2Problem',
    solutionKey: 'landing.pain2Solution',
    image: '/assets/landing/why-risk.png',
    tone: 'cool' as PreviewTone,
  },
]

const STEPS = [
  { number: '01', titleKey: 'landing.step1Title', descriptionKey: 'landing.step1Desc' },
  { number: '02', titleKey: 'landing.step2Title', descriptionKey: 'landing.step2Desc' },
  { number: '03', titleKey: 'landing.step3Title', descriptionKey: 'landing.step3Desc' },
]

function SectionLabel({ children, light = false }: { children: string; light?: boolean }): JSX.Element {
  return (
    <p
      className={`font-script text-[16px] leading-[1.4] md:text-[44px] ${
        light ? 'text-white/50' : 'text-[#1A0F0A80]'
      }`}
    >
      {children}
    </p>
  )
}

function Preview({ image, tone, padding }: { image: string; tone: PreviewTone; padding: string }): JSX.Element {
  return (
    <div className={`rounded-lg ${padding} ${tone === 'cool' ? 'bg-[#B5B8BD]' : 'bg-[#D7D3CD]'}`}>
      <img src={image} alt="" className="h-full w-full rounded-xl object-contain" />
    </div>
  )
}

export default function LandingPage(): JSX.Element {
  const { t } = useTranslation()

  return (
    <main className="landing min-h-screen bg-[#F7F7F4] text-[#1A0F0A] antialiased">
      <LandingHeader />

      <section className="landing-hero mx-auto w-full max-w-[1920px] px-5 pb-20 md:px-10 md:pb-40">
        <div className="landing-hero-card grid h-[737px] grid-rows-[355px_382px] overflow-hidden rounded-lg bg-[#F2F1ED] md:h-[871px] md:grid-cols-[minmax(0,1fr)_clamp(360px,32vw,600px)] md:grid-rows-1 xl:grid-cols-[minmax(0,1fr)_600px]">
          <div className="landing-hero-copy flex min-h-0 flex-col px-5 py-8 md:px-12 md:py-12 2xl:px-20 2xl:py-20">
            <div>
              <SectionLabel>{t('landing.heroEyebrow')}</SectionLabel>
              <motion.h1
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.55, ease: [0.16, 1, 0.3, 1] }}
                className="landing-hero-title font-display mt-3 max-w-[970px] text-[36px] leading-[0.86] md:mt-6 md:text-[80px] 2xl:mt-8 2xl:text-[120px]"
              >
                {t('landing.heroTitleLine1')}
                <br />
                {t('landing.heroTitleLine2')}
              </motion.h1>
              <p className="mt-5 max-w-[555px] text-[14px] leading-[1.4] text-[#1A0F0A80] md:mt-6 md:text-[16px] 2xl:mt-8 2xl:text-[18px]">
                {t('landing.heroSubtitle')}
              </p>
              <div className="mt-6 flex flex-wrap gap-2 md:mt-8">
                <Link
                  to="/apply"
                  className="rounded-full bg-[#1A0F0A] px-3 py-2 text-[13px] leading-none text-white transition-opacity hover:opacity-90 md:px-6 md:py-3 md:text-[16px] 2xl:px-8 2xl:py-4 2xl:text-[20px]"
                >
                  {t('landing.heroCtaPrimary')}
                </Link>
                <Link
                  to="/login"
                  className="hidden rounded-full bg-[#E6E5E0] px-6 py-3 text-[16px] leading-none transition-colors hover:bg-[#DDDCD8] md:inline-flex 2xl:px-8 2xl:py-4 2xl:text-[20px]"
                >
                  {t('landing.heroCtaSecondary')}
                </Link>
              </div>
            </div>

            <div className="landing-hero-chips mt-6 hidden max-w-[900px] flex-wrap gap-2 md:flex 2xl:mt-8">
              {HERO_CAPABILITY_KEYS.map((key) => (
                <span
                  key={key}
                  className="rounded-[10px] bg-[#E6E5E0] px-4 py-2 text-[16px] leading-none 2xl:px-5 2xl:py-2.5 2xl:text-[18px]"
                >
                  {t(key)}
                </span>
              ))}
            </div>
          </div>

          <div className="relative h-full overflow-hidden bg-[#D8D0BF]">
            <img
              src="/assets/landing/hero-background.png"
              alt=""
              className="absolute inset-0 h-full w-full object-cover"
            />
            <img
              src="/assets/landing/hero-chat.png"
              alt=""
              className="absolute bottom-5 left-1/2 h-auto w-[280px] max-w-[520px] -translate-x-1/2 object-contain md:bottom-16 md:w-[calc(100%_-_48px)] xl:bottom-[103px] xl:w-[520px]"
            />
          </div>
        </div>
      </section>

      <section
        id="product"
        className="landing-product mx-auto min-h-[1145px] w-full max-w-[1920px] px-5 pb-20 md:min-h-[1361px] md:px-10 md:pb-40"
      >
        <div className="mb-8 text-center md:mb-14">
          <SectionLabel>{t('landing.showcaseEyebrow')}</SectionLabel>
          <h2 className="font-display mx-auto mt-3 max-w-[1240px] text-[36px] leading-[0.86] md:text-[110px]">
            {t('landing.showcaseHeading')}
          </h2>
        </div>

        <div className="grid gap-5 md:grid-cols-2">
          {PRODUCT_CARDS.map((card) => (
            <article
              key={card.titleKey}
              className="landing-product-card rounded-lg bg-[#F2F1ED] p-3 md:h-[871px] md:p-10"
            >
              <h3 className="text-[16px] leading-[1.4] md:text-[32px]">{t(card.titleKey)}</h3>
              <p className="mt-2 max-w-[680px] text-[13px] leading-[1.4] text-[#1A0F0A80] md:mt-3 md:text-[18px]">
                {t(card.descriptionKey)}
              </p>
              <div className="mt-5 md:mt-10">
                <Preview image={card.image} tone={card.tone} padding="p-3 md:p-10" />
              </div>
            </article>
          ))}
        </div>
      </section>

      <section className="landing-why mx-auto min-h-[1449px] w-full max-w-[1920px] px-5 pb-20 md:min-h-[2024px] md:px-10 md:pb-40">
        <div className="mb-8 text-center md:mb-14">
          <SectionLabel>{t('landing.painEyebrow')}</SectionLabel>
          <h2 className="font-display mx-auto mt-3 max-w-[1390px] text-[36px] leading-[0.86] md:text-[110px]">
            {t('landing.painHeading')}
          </h2>
        </div>

        <div className="space-y-5">
          {PAIN_POINTS.map((point, index) => (
            <article
              key={point.titleKey}
              className="landing-why-card grid gap-6 rounded-lg bg-[#F2F1ED] p-3 md:h-[478px] md:grid-cols-[minmax(0,1fr)_minmax(360px,830px)] md:gap-12 md:p-10 xl:gap-20"
            >
              <div className={index === 2 ? 'md:order-2' : ''}>
                <h3 className="text-[16px] leading-[1.4] md:text-[32px]">{t(point.titleKey)}</h3>
                <p className="mt-2 max-w-[580px] text-[13px] leading-[1.4] text-[#1A0F0A80] md:mt-4 md:text-[18px]">
                  {t(point.problemKey)}
                </p>
                <div className="mt-5 flex max-w-[620px] gap-3 text-[13px] font-bold leading-[1.4] text-[#745C44] md:mt-16 md:text-[18px]">
                  <span className="mt-1 flex h-9 w-9 shrink-0 items-center justify-center rounded-full border border-[#745C44]">
                    <span className="h-1.5 w-1.5 rounded-full bg-[#745C44]" />
                  </span>
                  {t(point.solutionKey)}
                </div>
              </div>
              <Preview image={point.image} tone={point.tone} padding="p-5" />
            </article>
          ))}
        </div>
      </section>

      <section id="how" className="landing-how h-[1021px] bg-[#745C44] py-16 text-white md:h-[1253px] md:py-24">
        <div className="mx-auto flex h-full w-full max-w-[1920px] flex-col px-5 md:px-10">
          <div className="text-center">
            <SectionLabel light>{t('landing.stepsEyebrow')}</SectionLabel>
            <h2 className="font-display mx-auto mt-3 max-w-[1050px] text-[36px] leading-[0.86] md:text-[110px]">
              {t('landing.stepsHeading')}
            </h2>
          </div>
          <img
            src="/assets/landing/how-pen.png"
            alt=""
            className="mx-auto mt-10 h-14 w-[189px] object-cover md:mt-14 md:h-[165px] md:w-[557px]"
          />
          <div className="mt-10 grid gap-4 pb-1 md:mt-16 md:grid-cols-3 md:pb-0">
            {STEPS.map((step) => (
              <article
                key={step.number}
                className="min-h-[160px] rounded-lg border border-white/30 p-5 md:min-h-[300px] md:p-8"
              >
                <p className="font-display text-[28px] leading-[0.85] md:text-[46px]">{step.number}</p>
                <h3 className="mt-8 text-[18px] leading-[1.2] md:mt-16 md:text-[28px]">{t(step.titleKey)}</h3>
                <p className="mt-3 text-[13px] leading-[1.4] text-white/70 md:text-[18px]">
                  {t(step.descriptionKey)}
                </p>
              </article>
            ))}
          </div>
        </div>
      </section>

      <section
        id="early-access"
        className="landing-early mx-auto min-h-[698px] w-full max-w-[1920px] px-5 pb-20 md:min-h-0 md:px-10 md:py-24"
      >
        <div className="mx-auto grid max-w-[1429px] gap-0 md:grid-cols-[858px_1fr] md:items-center md:gap-8">
          <div className="h-[306px] overflow-hidden rounded-lg bg-[#1A0F0A] md:h-[822px]">
            <img src="/assets/landing/early-access.png" alt="" className="h-full w-full object-cover" />
          </div>
          <div className="text-center">
            <SectionLabel>{t('landing.earlyAccessBadge')}</SectionLabel>
            <blockquote className="font-display mt-2 text-[30px] leading-[1.05] md:mt-4 md:text-[67px]">
              {t('landing.earlyAccessQuote')}
            </blockquote>
            <div className="mt-4 flex flex-wrap justify-center gap-2 md:mt-6">
              <span className="rounded-full bg-[#E6E5E0] px-3 py-1.5 text-[13px] md:rounded-[10px] md:px-5 md:py-2.5 md:text-[18px]">
                {t('landing.earlyAccessBadgeTeam')}
              </span>
              <span className="rounded-full bg-[#E6E5E0] px-3 py-1.5 text-[13px] md:rounded-[10px] md:px-5 md:py-2.5 md:text-[18px]">
                {t('landing.earlyAccessBadgeCohort')}
              </span>
            </div>
          </div>
        </div>
      </section>

      <footer
        id="contacts"
        className="landing-footer bg-[#1A0F0A] px-5 pb-20 pt-16 text-white md:px-10 md:py-40"
      >
        <div className="mx-auto max-w-[924px] text-center">
          <SectionLabel light>{t('landing.ctaEyebrow')}</SectionLabel>
          <h2 className="font-display mt-4 text-[36px] leading-[0.86] text-[#FDC298] md:text-[96px]">
            {t('landing.ctaHeading')}
          </h2>
          <p className="mx-auto mt-4 max-w-[620px] text-[13px] leading-[1.4] text-white/50 md:text-[18px]">
            {t('landing.ctaSubtitle')}
          </p>
          <Link
            to="/apply"
            className="mt-6 inline-flex rounded-full bg-[#FDC298] px-3 py-2 text-[13px] leading-none text-[#1A0F0A] transition-opacity hover:opacity-90 md:px-8 md:py-4 md:text-[20px]"
          >
            {t('landing.ctaButton')}
          </Link>
          <img
            src="/assets/landing/cta-figure.png"
            alt=""
            className="landing-cta-figure mx-auto mt-8 h-[122px] w-[131px] object-contain md:mt-10 md:h-[298px] md:w-[321px]"
          />
        </div>

        <div className="mx-auto mt-10 max-w-[1840px] rounded-lg bg-[#21140E] p-5 md:mt-20 md:p-10">
          <div className="grid gap-8 md:grid-cols-[1.3fr_1fr_1fr_0.8fr]">
            <div>
              <LandingLogo inverted />
              <p className="mt-8 text-[13px] leading-[1.4] text-white/50 md:text-[18px]">
                {t('landing.footerCopyright', { year: new Date().getFullYear() })}
              </p>
            </div>
            <div>
              <h3 className="font-display text-[28px] leading-[1.05] text-white/50">
                {t('landing.footerSecurityTitle')}
              </h3>
              <p className="mt-4 text-[13px] leading-[1.4] md:text-[18px]">
                {t('landing.footerSecurityText')}
              </p>
            </div>
            <div>
              <h3 className="font-display text-[28px] leading-[1.05] text-white/50">
                {t('landing.footerContactsTitle')}
              </h3>
              <p className="mt-4 text-[13px] leading-[1.4] md:text-[18px]">
                {t('landing.footerContactsText')}
              </p>
            </div>
            <div className="flex flex-col gap-3 text-[13px] text-white/50 md:text-[18px]">
              <a href="#product" className="transition-colors hover:text-white">
                {t('landing.footerProductTitle')}
              </a>
              <Link to="/login" className="transition-colors hover:text-white">
                {t('landing.footerLogin')}
              </Link>
              <Link to="/apply" className="transition-colors hover:text-white">
                {t('landing.footerApply')}
              </Link>
            </div>
          </div>
          <LegalLinks tone="inverted" className="mt-8 md:gap-x-8" />
        </div>
      </footer>
    </main>
  )
}
