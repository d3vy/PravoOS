import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import { AnimatedSection } from '../components/ui/AnimatedSection'
import { Button } from '../components/ui/Button'
import { Navbar } from '../components/layout/Navbar'

const painPoints = [
  {
    title: 'Часы на поиск прецедентов',
    problem: 'Поиск по правовым базам, судебной практике и нормативным актам занимает 2–4 часа на каждый запрос. Это время не приносит клиентам никакой ценности.',
    solution: 'PravoOS находит релевантную судебную практику и нормативную базу за секунды, сразу указывая источники.',
  },
  {
    title: 'Ручной анализ сотен страниц',
    problem: 'Изучение объёмных договоров, дел и регулятивных документов — одна из самых затратных операций. 40% рабочего времени юриста уходит на документарный анализ.',
    solution: 'AI мгновенно анализирует загруженные документы, выделяет ключевые положения и отвечает на конкретные вопросы по тексту.',
  },
  {
    title: 'Рутинные ответы клиентам',
    problem: 'Типовые разъяснения по стандартным ситуациям повторяются снова и снова. Каждый такой ответ требует проверки актуального законодательства.',
    solution: 'AI формирует проект ответа на основе актуальной правовой базы. Юрист проверяет и утверждает — вместо того чтобы писать с нуля.',
  },
]

const steps = [
  {
    number: '01',
    title: 'Загрузите документы',
    description: 'Загрузите нормативные акты, судебную практику, внутренние регламенты. Система векторизует документы и строит индекс для точного поиска.',
  },
  {
    number: '02',
    title: 'Задайте вопрос',
    description: 'Сформулируйте вопрос на естественном языке — так, как вы спросили бы коллегу. Никаких специальных запросов или синтаксиса.',
  },
  {
    number: '03',
    title: 'Получите ответ с источниками',
    description: 'Система даёт развёрнутый ответ со ссылками на конкретные документы и нормы. Вы видите, откуда взята каждая часть ответа.',
  },
]

const features = [
  {
    title: 'Ответы с источниками',
    description: 'Каждый ответ AI содержит ссылки на конкретные документы и нормативные акты. Никакой «галлюцинации» — только то, что есть в вашей базе знаний.',
  },
  {
    title: 'История консультаций',
    description: 'Все диалоги сохраняются. Вы можете вернуться к любому предыдущему запросу, продолжить беседу или поделиться контекстом с коллегой.',
  },
  {
    title: 'Ваши данные — ваши',
    description: 'Загруженные документы хранятся в изолированном пространстве. Никакого обучения на ваших данных, никакой передачи третьим лицам.',
  },
  {
    title: 'Российский правовой контекст',
    description: 'Система разработана с учётом структуры российского права: ГК, ГПК, АПК, федеральные законы, постановления Пленума ВС и ВАС.',
  },
]

export default function LandingPage(): JSX.Element {
  return (
    <div className="min-h-screen bg-white/80 dark:bg-dark-bg/85">
      <Navbar />

      {/* Hero */}
      <section className="relative overflow-hidden">
        <div className="page-container pt-28 pb-32 md:pt-36 md:pb-44">
          <motion.div
            initial={{ opacity: 0, y: 24 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.55, ease: [0.16, 1, 0.3, 1] }}
            className="max-w-4xl"
          >
            <p className="eyebrow mb-7 tracking-[0.2em]">AI-платформа для юристов</p>

            <h1 className="font-sans text-5xl md:text-7xl lg:text-8xl font-black text-light-text dark:text-dark-text leading-[1.0] tracking-tight mb-8">
              Юрист должен<br />
              заниматься правом.
            </h1>

            <p className="text-lg md:text-xl text-light-secondary dark:text-dark-secondary leading-relaxed mb-12 max-w-xl font-light">
              PravoOS автоматизирует поиск по правовой базе, анализ документов
              и подготовку типовых ответов. Рутину берёт на себя AI.
            </p>

            <div className="flex flex-wrap items-center gap-4">
              <Link to="/apply">
                <Button variant="primary" size="lg">
                  Подать заявку
                </Button>
              </Link>
              <Link to="/login">
                <Button variant="ghost" size="lg">
                  Войти в систему →
                </Button>
              </Link>
            </div>

            <p className="mt-8 text-sm text-light-secondary dark:text-dark-secondary">
              Доступ по заявке — рассматривается в течение рабочего дня.
            </p>
          </motion.div>
        </div>
      </section>

      {/* Stats bar */}
      <section className="border-y border-light-border dark:border-dark-border">
        <div className="page-container py-12">
          <AnimatedSection className="grid grid-cols-2 md:grid-cols-4 gap-10">
            {[
              { value: '30–40%', label: 'рабочего времени — документный анализ' },
              { value: '~3 ч', label: 'в среднем на поиск одного прецедента' },
              { value: '2×', label: 'быстрее подготовка позиций с AI' },
              { value: '100%', label: 'ответов со ссылками на источники' },
            ].map((stat) => (
              <div key={stat.value}>
                <div className="font-sans text-3xl md:text-4xl font-black text-light-text dark:text-dark-text mb-2 tracking-tight">
                  {stat.value}
                </div>
                <div className="text-sm text-light-secondary dark:text-dark-secondary leading-snug font-light">
                  {stat.label}
                </div>
              </div>
            ))}
          </AnimatedSection>
        </div>
      </section>

      {/* Pain Points */}
      <section className="py-28">
        <div className="page-container">
          <AnimatedSection className="mb-16">
            <p className="eyebrow mb-5">Проблема</p>
            <h2 className="font-sans text-4xl md:text-5xl font-black text-light-text dark:text-dark-text max-w-2xl leading-tight tracking-tight">
              Где теряется время квалифицированного юриста
            </h2>
          </AnimatedSection>

          <div className="grid md:grid-cols-3 gap-6">
            {painPoints.map((point, index) => (
              <AnimatedSection key={point.title} delay={index * 0.08}>
                <div className="p-7 rounded-2xl border border-light-border dark:border-dark-border bg-light-surface dark:bg-dark-surface h-full flex flex-col gap-5">
                  <div className="w-8 h-8 rounded-lg bg-light-surface-elevated dark:bg-dark-surface-elevated border border-light-border dark:border-dark-border flex items-center justify-center text-light-secondary dark:text-dark-secondary">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round">
                      <line x1="18" y1="6" x2="6" y2="18" />
                      <line x1="6" y1="6" x2="18" y2="18" />
                    </svg>
                  </div>
                  <h3 className="font-sans font-semibold text-light-text dark:text-dark-text text-base tracking-tight">
                    {point.title}
                  </h3>
                  <p className="text-sm text-light-secondary dark:text-dark-secondary leading-relaxed flex-1 font-light">
                    {point.problem}
                  </p>
                  <div className="pt-4 border-t border-light-border dark:border-dark-border">
                    <div className="flex items-start gap-3">
                      <svg className="text-light-text dark:text-dark-text mt-0.5 shrink-0" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                        <polyline points="20 6 9 17 4 12" />
                      </svg>
                      <p className="text-sm text-light-text dark:text-dark-text leading-relaxed font-light">
                        {point.solution}
                      </p>
                    </div>
                  </div>
                </div>
              </AnimatedSection>
            ))}
          </div>
        </div>
      </section>

      {/* How it works */}
      <section className="py-28 bg-light-surface dark:bg-dark-surface border-y border-light-border dark:border-dark-border">
        <div className="page-container">
          <AnimatedSection className="mb-16">
            <p className="eyebrow mb-5">Как это работает</p>
            <h2 className="font-sans text-4xl md:text-5xl font-black text-light-text dark:text-dark-text max-w-2xl leading-tight tracking-tight">
              Три шага до ответа по правовой базе
            </h2>
          </AnimatedSection>

          <div className="grid md:grid-cols-3 gap-12 relative">
            {steps.map((step, index) => (
              <AnimatedSection key={step.number} delay={index * 0.12}>
                <div className="flex flex-col gap-4">
                  <div className="font-sans text-4xl font-black text-light-border dark:text-dark-border select-none tracking-tight">
                    {step.number}
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

      {/* Features */}
      <section className="py-28">
        <div className="page-container">
          <AnimatedSection className="mb-16">
            <p className="eyebrow mb-5">Возможности</p>
            <h2 className="font-sans text-4xl md:text-5xl font-black text-light-text dark:text-dark-text max-w-2xl leading-tight tracking-tight">
              Инструмент для правовой практики
            </h2>
          </AnimatedSection>

          <div className="grid md:grid-cols-2 gap-4">
            {features.map((feature, index) => (
              <AnimatedSection key={feature.title} delay={index * 0.07}>
                <div className="p-7 rounded-2xl border border-light-border dark:border-dark-border bg-light-surface dark:bg-dark-surface h-full">
                  <h3 className="font-sans font-semibold text-light-text dark:text-dark-text mb-3 tracking-tight">
                    {feature.title}
                  </h3>
                  <p className="text-sm text-light-secondary dark:text-dark-secondary leading-relaxed font-light">
                    {feature.description}
                  </p>
                </div>
              </AnimatedSection>
            ))}
          </div>
        </div>
      </section>

      {/* CTA */}
      <section className="py-28 border-t border-light-border dark:border-dark-border">
        <div className="page-container">
          <AnimatedSection>
            <div className="max-w-xl">
              <p className="eyebrow mb-6">Начать работу</p>
              <h2 className="font-sans text-4xl md:text-5xl font-black text-light-text dark:text-dark-text mb-6 leading-tight tracking-tight">
                Готовы освободиться от рутины?
              </h2>
              <p className="text-light-secondary dark:text-dark-secondary mb-10 text-lg leading-relaxed font-light">
                Подайте заявку — администратор платформы свяжется с вами в течение рабочего дня
                и предоставит доступ к системе.
              </p>
              <Link to="/apply">
                <Button variant="primary" size="lg">
                  Подать заявку на доступ
                </Button>
              </Link>
            </div>
          </AnimatedSection>
        </div>
      </section>

      {/* Footer */}
      <footer className="border-t border-light-border dark:border-dark-border">
        <div className="page-container py-8">
          <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
            <span className="font-sans text-sm font-semibold tracking-tight text-light-text dark:text-dark-text">
              Pravo<span className="font-light">OS</span>
            </span>
            <p className="text-sm text-light-secondary dark:text-dark-secondary font-light">
              © {new Date().getFullYear()} PravoOS. AI-платформа для юристов.
            </p>
          </div>
        </div>
      </footer>
    </div>
  )
}
