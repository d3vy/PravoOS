import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import type { ReactNode } from 'react'
import { AnimatedSection } from '../components/ui/AnimatedSection'
import { Button } from '../components/ui/Button'
import { Navbar } from '../components/layout/Navbar'
import { ChatDemo } from '../components/landing/ChatDemo'
import { CaseMockup } from '../components/landing/CaseMockup'
import { CalendarMockup } from '../components/landing/CalendarMockup'

const capabilities = [
  'RAG-поиск по вашей базе',
  'Ответы со ссылками на НПА',
  'Дела · сроки · клиенты',
  'Учёт времени и счета',
  'Экспорт в DOCX / PDF',
]

const painPoints: { title: string; problem: string; solution: string; visual: ReactNode }[] = [
  {
    title: 'Часы на поиск прецедентов',
    problem:
      'Поиск по правовым базам, судебной практике и нормативным актам занимает 2–4 часа на каждый запрос. Это время не приносит клиентам никакой ценности.',
    solution: 'PravoOS находит релевантную практику и нормы за секунды — сразу со ссылками на источник.',
    visual: <SearchVisual />,
  },
  {
    title: 'Ручной анализ сотен страниц',
    problem:
      'Изучение объёмных договоров, дел и регулятивных документов — одна из самых затратных операций рабочего дня юриста.',
    solution: 'AI анализирует загруженные документы, выделяет ключевые условия и риски и отвечает на вопросы по тексту.',
    visual: <RiskVisual />,
  },
  {
    title: 'Рутинные ответы и черновики',
    problem:
      'Типовые разъяснения и проекты документов повторяются снова и снова — каждый требует сверки с актуальным законом.',
    solution: 'AI готовит проект на основе актуальной базы. Юрист проверяет и утверждает — вместо написания с нуля.',
    visual: <DraftVisual />,
  },
]

const steps = [
  {
    number: '01',
    title: 'Загрузите базу',
    description: 'Нормативка, судебная практика, внутренние регламенты. Система строит векторный индекс для точного поиска.',
  },
  {
    number: '02',
    title: 'Спросите на обычном языке',
    description: 'Сформулируйте вопрос так, как спросили бы коллегу. Никакого специального синтаксиса.',
  },
  {
    number: '03',
    title: 'Получите ответ со ссылками',
    description: 'Развёрнутый ответ с указанием документов и норм. Видно, откуда взята каждая часть — с проверкой цитат.',
  },
]

const securityItems = [
  { title: 'Шифрование документов', description: 'Файлы хранятся зашифрованными at-rest (AES-256-GCM).' },
  { title: 'Антивирус загрузок', description: 'Каждый файл проверяется ClamAV до сохранения.' },
  { title: 'Строгий вход', description: 'JWT RS256, ротация сессий, 2FA (TOTP).' },
  { title: 'Изоляция данных', description: 'Каждый юрист и фирма видят только свои дела и документы.' },
  { title: 'Без обучения на ваших данных', description: 'Загруженное не уходит на обучение моделей и третьим лицам.' },
  { title: 'Аудит доступа', description: 'Действия с документами и клиентами фиксируются в журнале.' },
]

export default function LandingPage(): JSX.Element {
  return (
    <div className="min-h-screen bg-white/80 dark:bg-dark-bg/85">
      <Navbar />

      {/* Hero */}
      <section className="relative overflow-hidden">
        <div
          aria-hidden="true"
          className="pointer-events-none absolute -top-40 right-0 w-[560px] h-[560px] rounded-full bg-light-accent/10 dark:bg-dark-accent/15 blur-3xl"
        />
        <div className="page-container relative pt-20 pb-24 md:pt-28 md:pb-32">
          <div className="grid lg:grid-cols-2 gap-14 lg:gap-10 items-center">
            <motion.div
              initial={{ opacity: 0, y: 24 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.55, ease: [0.16, 1, 0.3, 1] }}
            >
              <p className="eyebrow mb-6 tracking-[0.2em]">AI-платформа для юристов</p>
              <h1 className="font-sans text-4xl sm:text-5xl md:text-6xl font-black text-light-text dark:text-dark-text leading-[1.05] tracking-tight mb-6">
                Юрист должен<br />заниматься правом.
              </h1>
              <p className="text-lg text-light-secondary dark:text-dark-secondary leading-relaxed mb-8 max-w-lg font-light">
                Задайте вопрос — получите ответ со ссылками на ваши документы и актуальные нормы.
                Дела, сроки, клиенты и счета — в одном месте. Рутину берёт на себя AI.
              </p>
              <div className="flex flex-wrap items-center gap-4">
                <Link to="/apply">
                  <Button variant="primary" size="lg">
                    Получить доступ
                  </Button>
                </Link>
                <Link to="/login">
                  <Button variant="ghost" size="lg">
                    Войти в систему →
                  </Button>
                </Link>
              </div>
              <p className="mt-6 text-sm text-light-secondary dark:text-dark-secondary">
                Закрытый доступ по заявке · рассмотрим за рабочий день · без карты
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
      <section className="border-y border-light-border dark:border-dark-border bg-light-surface/50 dark:bg-dark-surface/50">
        <div className="page-container py-6">
          <div className="flex flex-wrap items-center gap-x-6 gap-y-3 justify-center md:justify-between">
            {capabilities.map((cap) => (
              <span key={cap} className="inline-flex items-center gap-2 text-sm text-light-secondary dark:text-dark-secondary">
                <span className="w-1.5 h-1.5 rounded-full bg-light-accent dark:bg-dark-accent" />
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
            <p className="eyebrow mb-5">Продукт</p>
            <h2 className="font-sans text-3xl sm:text-4xl md:text-5xl font-black text-light-text dark:text-dark-text leading-tight tracking-tight">
              Не на словах — посмотрите, как это выглядит
            </h2>
          </AnimatedSection>

          <div className="grid lg:grid-cols-2 gap-8 items-start">
            <AnimatedSection>
              <CaseMockup />
              <div className="mt-5 max-w-md">
                <h3 className="font-sans font-semibold text-light-text dark:text-dark-text mb-1.5">
                  Дело целиком — во вкладках
                </h3>
                <p className="text-sm text-light-secondary dark:text-dark-secondary leading-relaxed font-light">
                  Документы, AI-анализ, время и счета, задачи и переписка с клиентом. Ничего не теряется —
                  всё привязано к делу.
                </p>
              </div>
            </AnimatedSection>

            <AnimatedSection delay={0.1} className="lg:mt-16">
              <CalendarMockup />
              <div className="mt-5 max-w-md">
                <h3 className="font-sans font-semibold text-light-text dark:text-dark-text mb-1.5">
                  Единый календарь сроков
                </h3>
                <p className="text-sm text-light-secondary dark:text-dark-secondary leading-relaxed font-light">
                  Заседания из КАД.Арбитр, процессуальные сроки и задачи по делам — в одной сетке.
                  Напоминания за 7 / 3 / 1 день.
                </p>
              </div>
            </AnimatedSection>
          </div>
        </div>
      </section>

      {/* Pain → solution */}
      <section className="py-24 md:py-28 bg-light-surface dark:bg-dark-surface border-y border-light-border dark:border-dark-border">
        <div className="page-container">
          <AnimatedSection className="mb-16 max-w-2xl">
            <p className="eyebrow mb-5">Зачем это нужно</p>
            <h2 className="font-sans text-3xl sm:text-4xl md:text-5xl font-black text-light-text dark:text-dark-text leading-tight tracking-tight">
              Где теряется время квалифицированного юриста
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
                    <h3 className="font-sans text-xl font-semibold text-light-text dark:text-dark-text mb-3 tracking-tight">
                      {point.title}
                    </h3>
                    <p className="text-sm text-light-secondary dark:text-dark-secondary leading-relaxed font-light mb-4">
                      {point.problem}
                    </p>
                    <div className="flex items-start gap-3">
                      <svg
                        className="text-light-accent dark:text-dark-accent mt-0.5 shrink-0"
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
                      <p className="text-sm font-medium text-light-text dark:text-dark-text leading-relaxed">
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
            <p className="eyebrow mb-5">Как это работает</p>
            <h2 className="font-sans text-3xl sm:text-4xl md:text-5xl font-black text-light-text dark:text-dark-text leading-tight tracking-tight">
              Три шага до ответа по правовой базе
            </h2>
          </AnimatedSection>

          <div className="grid md:grid-cols-3 gap-10">
            {steps.map((step, index) => (
              <AnimatedSection key={step.number} delay={index * 0.1}>
                <div className="flex flex-col gap-4">
                  <div className="flex items-center gap-3">
                    <span className="font-sans text-2xl font-black text-light-accent dark:text-dark-accent tracking-tight">
                      {step.number}
                    </span>
                    <span className="flex-1 h-px bg-light-border dark:bg-dark-border" />
                  </div>
                  <h3 className="font-sans text-lg font-semibold text-light-text dark:text-dark-text tracking-tight">
                    {step.title}
                  </h3>
                  <p className="text-sm text-light-secondary dark:text-dark-secondary leading-relaxed font-light">
                    {step.description}
                  </p>
                </div>
              </AnimatedSection>
            ))}
          </div>
        </div>
      </section>

      {/* Security */}
      <section className="py-24 md:py-28 bg-light-surface dark:bg-dark-surface border-y border-light-border dark:border-dark-border">
        <div className="page-container">
          <AnimatedSection className="mb-14 max-w-2xl">
            <p className="eyebrow mb-5">Безопасность и данные</p>
            <h2 className="font-sans text-3xl sm:text-4xl md:text-5xl font-black text-light-text dark:text-dark-text leading-tight tracking-tight">
              Данные клиентов — под защитой
            </h2>
          </AnimatedSection>

          <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {securityItems.map((item, index) => (
              <AnimatedSection key={item.title} delay={index * 0.05}>
                <div className="p-6 rounded-2xl border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg h-full">
                  <div className="w-9 h-9 rounded-lg bg-light-accent/10 dark:bg-dark-accent/15 text-light-accent dark:text-dark-accent flex items-center justify-center mb-4">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                      <path d="M12 3l7 3v6c0 4.5-3 7.5-7 9-4-1.5-7-4.5-7-9V6z" />
                      <path d="M9 12l2 2 4-4" />
                    </svg>
                  </div>
                  <h3 className="font-sans font-semibold text-light-text dark:text-dark-text mb-1.5 tracking-tight">
                    {item.title}
                  </h3>
                  <p className="text-sm text-light-secondary dark:text-dark-secondary leading-relaxed font-light">
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
              <span className="inline-flex items-center gap-2 px-3 py-1 rounded-full border border-light-accent/40 dark:border-dark-accent/40 text-light-accent dark:text-dark-accent text-xs font-medium mb-6">
                <span className="w-1.5 h-1.5 rounded-full bg-light-accent dark:bg-dark-accent" />
                Ранний доступ
              </span>
              <p className="font-sans text-2xl md:text-3xl font-semibold text-light-text dark:text-dark-text leading-snug tracking-tight mb-4">
                «Мы делаем инструмент, которым хотели бы пользоваться сами — чтобы юрист занимался
                правом, а не искал документ в сотый раз».
              </p>
              <p className="text-sm text-light-secondary dark:text-dark-secondary">
                Команда PravoOS · закрытый набор практик и юрфирм
              </p>
            </div>
          </AnimatedSection>
        </div>
      </section>

      {/* CTA */}
      <section className="py-24 md:py-28 border-t border-light-border dark:border-dark-border">
        <div className="page-container">
          <AnimatedSection>
            <div className="max-w-xl">
              <p className="eyebrow mb-6">Начать работу</p>
              <h2 className="font-sans text-3xl sm:text-4xl md:text-5xl font-black text-light-text dark:text-dark-text mb-6 leading-tight tracking-tight">
                Освободите время для настоящей работы
              </h2>
              <p className="text-light-secondary dark:text-dark-secondary mb-10 text-lg leading-relaxed font-light">
                Оставьте заявку — откроем доступ вашей практике и поможем перенести базу.
                Отвечаем в течение рабочего дня.
              </p>
              <Link to="/apply">
                <Button variant="primary" size="lg">
                  Получить доступ для вашей практики
                </Button>
              </Link>
            </div>
          </AnimatedSection>
        </div>
      </section>

      {/* Footer */}
      <footer className="border-t border-light-border dark:border-dark-border">
        <div className="page-container py-12">
          <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-8 mb-10">
            <div>
              <span className="font-sans text-sm font-semibold tracking-tight text-light-text dark:text-dark-text">
                Pravo<span className="font-light">OS</span>
              </span>
              <p className="mt-2 text-sm text-light-secondary dark:text-dark-secondary font-light leading-relaxed">
                AI-платформа для юристов и юрфирм.
              </p>
            </div>
            <FooterColumn
              title="Продукт"
              links={[
                { label: 'Войти', to: '/login' },
                { label: 'Получить доступ', to: '/apply' },
              ]}
            />
            <div>
              <p className="text-xs font-semibold uppercase tracking-wide text-light-secondary dark:text-dark-secondary mb-3">
                Безопасность
              </p>
              <p className="text-sm text-light-secondary dark:text-dark-secondary font-light leading-relaxed">
                Шифрование at-rest, изоляция данных, аудит доступа. Не обучаемся на ваших данных.
              </p>
            </div>
            <div>
              <p className="text-xs font-semibold uppercase tracking-wide text-light-secondary dark:text-dark-secondary mb-3">
                Контакты
              </p>
              <p className="text-sm text-light-secondary dark:text-dark-secondary font-light leading-relaxed">
                По вопросам доступа — через форму заявки.
              </p>
            </div>
          </div>
          <div className="pt-6 border-t border-light-border dark:border-dark-border">
            <p className="text-sm text-light-secondary dark:text-dark-secondary font-light">
              © {new Date().getFullYear()} PravoOS. AI-платформа для юристов.
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
      <p className="text-xs font-semibold uppercase tracking-wide text-light-secondary dark:text-dark-secondary mb-3">
        {title}
      </p>
      <ul className="flex flex-col gap-2">
        {links.map((link) => (
          <li key={link.to}>
            <Link
              to={link.to}
              className="text-sm text-light-secondary dark:text-dark-secondary hover:text-light-text dark:hover:text-dark-text transition-colors font-light"
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
  return (
    <div className="p-5 rounded-2xl border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg">
      <div className="flex items-center gap-2 px-3 py-2 rounded-lg border border-light-border dark:border-dark-border mb-3">
        <svg className="w-4 h-4 text-light-secondary dark:text-dark-secondary" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <circle cx="11" cy="11" r="7" />
          <line x1="16.5" y1="16.5" x2="21" y2="21" />
        </svg>
        <span className="text-xs text-light-secondary dark:text-dark-secondary">субсидиарная ответственность КДЛ</span>
      </div>
      <div className="flex flex-col gap-2">
        {['Определение ВС РФ № 305-ЭС...', 'ст. 61.11 127-ФЗ', 'Пленум ВС № 53, п. 16'].map((r) => (
          <div key={r} className="flex items-center justify-between gap-2 px-3 py-2 rounded-lg bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border">
            <span className="text-xs text-light-text dark:text-dark-text truncate">{r}</span>
            <span className="text-[10px] text-light-secondary dark:text-dark-secondary shrink-0">0.9с</span>
          </div>
        ))}
      </div>
    </div>
  )
}

function RiskVisual(): JSX.Element {
  return (
    <div className="p-5 rounded-2xl border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg">
      <div className="flex items-center justify-between mb-3">
        <span className="text-xs font-medium text-light-text dark:text-dark-text">Договор поставки.pdf</span>
        <span className="text-xs font-semibold text-red-600 dark:text-red-400">риск 72/100</span>
      </div>
      <div className="flex flex-col gap-2">
        {[
          { level: 'Высокий', tone: 'border-red-300 text-red-700 dark:border-red-500/40 dark:text-red-400', text: 'Односторонний отказ без компенсации' },
          { level: 'Средний', tone: 'border-amber-300 text-amber-700 dark:border-amber-500/40 dark:text-amber-400', text: 'Неустойка выше обычая делового оборота' },
        ].map((f) => (
          <div key={f.text} className={`px-3 py-2 rounded-lg border ${f.tone}`}>
            <span className="text-[10px] font-semibold uppercase tracking-wide">{f.level} риск</span>
            <p className="text-xs text-light-text dark:text-dark-text mt-0.5">{f.text}</p>
          </div>
        ))}
      </div>
    </div>
  )
}

function DraftVisual(): JSX.Element {
  return (
    <div className="p-5 rounded-2xl border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg">
      <div className="flex items-center gap-2 mb-3">
        <span className="w-5 h-5 rounded-md bg-light-accent/15 dark:bg-dark-accent/20 text-light-accent dark:text-dark-accent text-[10px] font-bold flex items-center justify-center">
          AI
        </span>
        <span className="text-xs font-medium text-light-text dark:text-dark-text">Проект: претензия должнику</span>
      </div>
      <div className="flex flex-col gap-1.5">
        {[92, 76, 84, 60].map((w, i) => (
          <div key={i} className="h-2 rounded-full bg-light-surface dark:bg-dark-surface" style={{ width: `${w}%` }} />
        ))}
      </div>
      <div className="mt-3 flex items-center gap-2">
        <span className="text-[11px] px-2 py-0.5 rounded-md bg-light-accent/10 dark:bg-dark-accent/10 text-light-accent dark:text-dark-accent">Скачать .docx</span>
        <span className="text-[11px] px-2 py-0.5 rounded-md border border-light-border dark:border-dark-border text-light-secondary dark:text-dark-secondary">Редактировать</span>
      </div>
    </div>
  )
}
