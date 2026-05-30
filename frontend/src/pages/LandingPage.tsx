import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import { AnimatedSection } from '../components/ui/AnimatedSection'
import { Button } from '../components/ui/Button'
import { Navbar } from '../components/layout/Navbar'
import { Logo, ScalesIcon } from '../components/ui/Logo'

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
    <div className="min-h-screen bg-light-bg dark:bg-dark-bg">
      <Navbar />

      {/* Hero */}
      <section className="relative overflow-hidden">
        <div className="absolute inset-0 bg-gradient-to-br from-light-accent/5 via-transparent to-transparent dark:from-dark-accent/10" />
        <div className="page-container py-24 md:py-36 relative">
          <motion.div
            initial={{ opacity: 0, y: 32 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, ease: 'easeOut' }}
            className="max-w-3xl"
          >
            <div className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full border border-light-gold/30 dark:border-dark-gold/30 bg-light-gold/5 dark:bg-dark-gold/10 text-light-gold dark:text-dark-gold text-sm font-medium mb-8">
              <ScalesIcon className="w-4 h-4" />
              <span className="tracking-wide">AI-платформа для юристов</span>
            </div>

            <h1 className="font-display text-5xl md:text-7xl font-semibold text-light-text dark:text-dark-text leading-[1.05] mb-6">
              Юрист должен заниматься
              <br />
              <span className="text-light-accent dark:text-dark-accent">правом. Не рутиной.</span>
            </h1>

            <p className="text-lg md:text-xl text-light-secondary dark:text-dark-secondary leading-relaxed mb-10 max-w-2xl">
              PravoOS автоматизирует поиск по правовой базе, анализ документов и подготовку типовых ответов.
              Вы фокусируетесь на стратегии и клиентах — рутину берёт на себя AI.
            </p>

            <div className="flex flex-wrap gap-4">
              <Link to="/apply">
                <Button variant="primary" size="lg">
                  Подать заявку
                </Button>
              </Link>
              <Link to="/login">
                <Button variant="secondary" size="lg">
                  Войти в систему →
                </Button>
              </Link>
            </div>

            <p className="mt-6 text-sm text-light-secondary dark:text-dark-secondary">
              Доступ по заявке. Администратор рассматривает заявки в течение рабочего дня.
            </p>
          </motion.div>
        </div>
      </section>

      {/* Stats bar */}
      <section className="border-y border-light-border dark:border-dark-border bg-light-surface dark:bg-dark-surface">
        <div className="page-container py-8">
          <AnimatedSection className="grid grid-cols-2 md:grid-cols-4 gap-8">
            {[
              { value: '30–40%', label: 'рабочего времени юриста — документный анализ' },
              { value: '~3 ч', label: 'в среднем уходит на поиск одного прецедента' },
              { value: '2× быстрее', label: 'подготовка позиций с AI-ассистентом' },
              { value: '100%', label: 'ответов со ссылками на источники' },
            ].map((stat) => (
              <div key={stat.value} className="text-center">
                <div className="font-display text-3xl md:text-4xl font-semibold text-light-accent dark:text-dark-accent mb-1">
                  {stat.value}
                </div>
                <div className="text-sm text-light-secondary dark:text-dark-secondary leading-snug">
                  {stat.label}
                </div>
              </div>
            ))}
          </AnimatedSection>
        </div>
      </section>

      {/* Pain Points */}
      <section className="py-24">
        <div className="page-container">
          <AnimatedSection className="mb-16">
            <p className="eyebrow mb-4">
              Проблема
            </p>
            <h2 className="font-display text-4xl md:text-5xl font-semibold text-light-text dark:text-dark-text max-w-2xl leading-tight">
              Где теряется время квалифицированного юриста
            </h2>
          </AnimatedSection>

          <div className="grid md:grid-cols-3 gap-8">
            {painPoints.map((point, index) => (
              <AnimatedSection key={point.title} delay={index * 0.1}>
                <div className="p-6 rounded-xl border border-light-border dark:border-dark-border bg-light-surface dark:bg-dark-surface h-full flex flex-col gap-4">
                  <div className="w-10 h-10 rounded-lg bg-red-50 dark:bg-red-900/20 flex items-center justify-center text-red-500">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round">
                      <line x1="18" y1="6" x2="6" y2="18" />
                      <line x1="6" y1="6" x2="18" y2="18" />
                    </svg>
                  </div>
                  <h3 className="font-display font-semibold text-light-text dark:text-dark-text text-lg">
                    {point.title}
                  </h3>
                  <p className="text-sm text-light-secondary dark:text-dark-secondary leading-relaxed flex-1">
                    {point.problem}
                  </p>
                  <div className="pt-3 border-t border-light-border dark:border-dark-border">
                    <div className="flex items-start gap-2">
                      <svg className="text-emerald-500 mt-0.5 shrink-0" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round">
                        <polyline points="20 6 9 17 4 12" />
                      </svg>
                      <p className="text-sm text-light-text dark:text-dark-text leading-relaxed">
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
      <section className="py-24 bg-light-surface dark:bg-dark-surface border-y border-light-border dark:border-dark-border">
        <div className="page-container">
          <AnimatedSection className="mb-16">
            <p className="eyebrow mb-4">
              Как это работает
            </p>
            <h2 className="font-display text-4xl md:text-5xl font-semibold text-light-text dark:text-dark-text max-w-2xl leading-tight">
              Три шага до ответа по правовой базе
            </h2>
          </AnimatedSection>

          <div className="grid md:grid-cols-3 gap-8 relative">
            <div className="hidden md:block absolute top-8 left-[16.666%] right-[16.666%] h-px bg-gradient-to-r from-transparent via-light-border dark:via-dark-border to-transparent" />

            {steps.map((step, index) => (
              <AnimatedSection key={step.number} delay={index * 0.15}>
                <div className="flex flex-col gap-4">
                  <div className="font-display text-5xl font-semibold text-light-gold/70 dark:text-dark-gold/70 select-none">
                    {step.number}
                  </div>
                  <h3 className="font-display text-xl font-semibold text-light-text dark:text-dark-text">
                    {step.title}
                  </h3>
                  <p className="text-light-secondary dark:text-dark-secondary leading-relaxed">
                    {step.description}
                  </p>
                </div>
              </AnimatedSection>
            ))}
          </div>
        </div>
      </section>

      {/* Features */}
      <section className="py-24">
        <div className="page-container">
          <AnimatedSection className="mb-16">
            <p className="eyebrow mb-4">
              Возможности
            </p>
            <h2 className="font-display text-4xl md:text-5xl font-semibold text-light-text dark:text-dark-text max-w-2xl leading-tight">
              Инструмент, разработанный для правовой практики
            </h2>
          </AnimatedSection>

          <div className="grid md:grid-cols-2 gap-6">
            {features.map((feature, index) => (
              <AnimatedSection key={feature.title} delay={index * 0.08}>
                <div className="p-6 rounded-xl border border-light-border dark:border-dark-border bg-light-surface dark:bg-dark-surface shadow-card dark:shadow-card-dark flex gap-4 h-full">
                  <div className="w-1 rounded-full bg-gradient-to-b from-light-gold to-light-accent dark:from-dark-gold dark:to-dark-accent shrink-0" />
                  <div>
                    <h3 className="font-display font-semibold text-light-text dark:text-dark-text mb-2">
                      {feature.title}
                    </h3>
                    <p className="text-sm text-light-secondary dark:text-dark-secondary leading-relaxed">
                      {feature.description}
                    </p>
                  </div>
                </div>
              </AnimatedSection>
            ))}
          </div>
        </div>
      </section>

      {/* CTA */}
      <section className="py-24 border-t border-light-border dark:border-dark-border">
        <div className="page-container">
          <AnimatedSection>
            <div className="max-w-2xl mx-auto text-center">
              <div className="gold-rule mx-auto mb-6" />
              <h2 className="font-display text-4xl md:text-5xl font-semibold text-light-text dark:text-dark-text mb-6 leading-tight">
                Готовы освободиться от рутины?
              </h2>
              <p className="text-light-secondary dark:text-dark-secondary mb-10 text-lg leading-relaxed">
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
      <footer className="border-t border-light-border dark:border-dark-border bg-light-surface dark:bg-dark-surface">
        <div className="page-container py-8">
          <div className="flex flex-col sm:flex-row items-center justify-between gap-4">
            <Logo />
            <p className="text-sm text-light-secondary dark:text-dark-secondary">
              © {new Date().getFullYear()} PravoOS. AI-платформа для юристов.
            </p>
          </div>
        </div>
      </footer>
    </div>
  )
}
