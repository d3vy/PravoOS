import { forwardRef } from 'react'
import { useTranslation } from 'react-i18next'
import { motion } from 'framer-motion'
import { useAiChatStore } from '../../store/aiChatStore'

export const AiWidgetLauncher = forwardRef<HTMLButtonElement>(function AiWidgetLauncher(
  _props,
  ref
): JSX.Element {
  const { t } = useTranslation()
  const openWidget = useAiChatStore((state) => state.openWidget)
  const unread = useAiChatStore((state) => state.unread)

  return (
    <motion.button
      ref={ref}
      type="button"
      onClick={() => openWidget()}
      initial={{ opacity: 0, scale: 0.8 }}
      animate={{ opacity: 1, scale: 1 }}
      exit={{ opacity: 0, scale: 0.8 }}
      transition={{ duration: 0.15, ease: 'easeOut' }}
      aria-label={t('aiWidget.open')}
      title={t('aiWidget.open')}
      className="fixed right-6 bottom-[calc(4.5rem+env(safe-area-inset-bottom))] md:bottom-6 z-[120] w-14 h-14 rounded-full bg-accent-solid text-accent-fg shadow-card flex items-center justify-center text-sm font-semibold tracking-wide hover:bg-accent-solid-hover focus:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-bg transition-colors"
    >
      AI
      {unread && (
        <span
          className="absolute top-1 right-1 w-3 h-3 rounded-full bg-danger border-2 border-bg"
          aria-hidden="true"
        />
      )}
    </motion.button>
  )
})
