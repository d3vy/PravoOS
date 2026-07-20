import { motion, useReducedMotion } from 'framer-motion'
import { AppWindow } from './AppWindow'

const ANSWER_TEXT =
  'Срок исковой давности по оспариванию подозрительной сделки должника — один год с момента, когда управляющий узнал или должен был узнать об основаниях (п. 2 ст. 61.2 и ст. 61.9 127-ФЗ). Течение начинается не ранее введения процедуры.'

export function ChatDemo(): JSX.Element {
  const reduce = useReducedMotion()
  const base = reduce ? 0 : 1

  const reveal = (delay: number) => ({
    initial: reduce ? { opacity: 1 } : { opacity: 0, y: 10 },
    whileInView: { opacity: 1, y: 0 },
    viewport: { once: true, margin: '-40px' },
    transition: { duration: 0.4, delay: reduce ? 0 : delay },
  })

  return (
    <AppWindow title="app.pravoos.ru/chat">
      <div className="p-5 flex flex-col gap-4 min-h-[340px]">
        <motion.div {...reveal(0.1 * base)} className="self-end max-w-[80%]">
          <div className="px-4 py-2.5 rounded-2xl rounded-br-sm bg-light-accent dark:bg-dark-accent text-white dark:text-dark-bg text-sm">
            Какой срок давности для оспаривания подозрительной сделки должника?
          </div>
        </motion.div>

        <motion.div {...reveal(0.5 * base)} className="self-start max-w-[92%]">
          <div className="flex items-center gap-2 mb-1.5">
            <span className="w-5 h-5 rounded-md bg-light-accent/15 dark:bg-dark-accent/20 text-light-accent dark:text-dark-accent text-[10px] font-bold flex items-center justify-center">
              AI
            </span>
            <span className="text-xs text-light-secondary dark:text-dark-secondary">PravoOS</span>
          </div>
          <div className="px-4 py-3 rounded-2xl rounded-bl-sm bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border text-sm text-light-text dark:text-dark-text leading-relaxed">
            {ANSWER_TEXT}
          </div>
        </motion.div>

        <motion.div {...reveal(0.9 * base)} className="self-start w-full">
          <p className="text-[11px] font-semibold uppercase tracking-wide text-light-secondary dark:text-dark-secondary mb-2">
            Источники
          </p>
          <div className="flex flex-col gap-2">
            <SourceRow label="ст. 61.2 127-ФЗ «О несостоятельности»" verified />
            <SourceRow label="Анализ сделок должника.pdf · с. 12" verified />
          </div>
        </motion.div>
      </div>
    </AppWindow>
  )
}

function SourceRow({ label, verified }: { label: string; verified?: boolean }): JSX.Element {
  return (
    <div className="flex items-center gap-2.5 px-3 py-2 rounded-lg bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border">
      <svg className="w-3.5 h-3.5 shrink-0 text-light-secondary dark:text-dark-secondary" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z" />
        <path d="M14 3v5h5" />
      </svg>
      <span className="flex-1 min-w-0 text-xs text-light-text dark:text-dark-text truncate">{label}</span>
      {verified && (
        <span className="inline-flex items-center gap-1 text-[11px] font-medium text-emerald-600 dark:text-emerald-400 shrink-0">
          <svg className="w-3 h-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
            <polyline points="20 6 9 17 4 12" />
          </svg>
          проверено
        </span>
      )}
    </div>
  )
}
