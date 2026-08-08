import { motion, useReducedMotion } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import { AppWindow } from './AppWindow'

export function ChatDemo(): JSX.Element {
  const { t } = useTranslation()
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
      <div className="p-4 flex flex-col gap-4 min-h-[300px]">
        <motion.div {...reveal(0.1 * base)} className="self-end max-w-[85%]">
          <div className="px-4 py-3 rounded-2xl rounded-br-sm bg-[#745C44] text-white text-sm leading-relaxed">
            {t('landing.chatQuestion')}
          </div>
        </motion.div>

        <motion.div {...reveal(0.5 * base)} className="self-start w-full">
          <div className="flex items-center gap-2 mb-1.5">
            <span className="w-5 h-5 rounded-md bg-[#745C44]/20 text-[#745C44] text-[10px] font-bold flex items-center justify-center">
              AI
            </span>
            <span className="text-xs text-[#26251E]/50">PravoOS</span>
          </div>
          <div className="px-4 py-3 rounded-2xl rounded-bl-sm bg-[#F7F7F4] border border-[#E8E7E2] text-sm text-[#26251E] leading-relaxed">
            {t('landing.chatAnswer')}
          </div>
        </motion.div>

        <motion.div {...reveal(0.9 * base)} className="self-start w-full">
          <p className="text-[11px] font-semibold uppercase tracking-wide text-[#26251E] mb-2">
            {t('landing.chatSourcesLabel')}
          </p>
          <div className="flex flex-col gap-2">
            <SourceRow label={t('landing.chatSource1')} verified />
            <SourceRow label={t('landing.chatSource2')} verified />
          </div>
        </motion.div>
      </div>
    </AppWindow>
  )
}

function SourceRow({ label, verified }: { label: string; verified?: boolean }): JSX.Element {
  const { t } = useTranslation()
  return (
    <div className="flex items-center gap-2.5 px-3 py-2.5 rounded-xl bg-[#F7F7F4] border border-[#E8E7E2]">
      <svg className="w-4 h-4 shrink-0 text-[#26251E]/50" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
        <path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z" />
        <path d="M14 3v5h5" />
      </svg>
      <span className="flex-1 min-w-0 text-[13px] text-[#26251E] truncate">{label}</span>
      {verified && (
        <span className="inline-flex items-center gap-1 text-[12px] font-semibold text-[#1F8A65] shrink-0">
          <svg className="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
            <polyline points="20 6 9 17 4 12" />
          </svg>
          {t('landing.chatVerified')}
        </span>
      )}
    </div>
  )
}
