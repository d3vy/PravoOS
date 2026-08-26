import { useEffect, useRef, useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import { useLanguage } from '../../hooks/useLanguage'
import { AppLanguage, LANGUAGE_LABELS } from '../../i18n/config'

type LanguageSwitcherTone = 'default' | 'landing'

interface LanguageSwitcherProps {
  className?: string
  tone?: LanguageSwitcherTone
}

const TRIGGER_CLASS: Record<LanguageSwitcherTone, string> = {
  default:
    'h-9 px-2.5 rounded-lg text-fg-muted hover:text-fg hover:bg-bg focus-visible:ring-accent',
  landing:
    'h-[42px] px-4 rounded-full border border-[#1A0F0A]/20 text-[#1A0F0A]/70 hover:text-[#1A0F0A] hover:bg-[#1A0F0A]/5 focus-visible:ring-[#745C44]',
}

const MENU_CLASS: Record<LanguageSwitcherTone, string> = {
  default: 'border-line bg-surface/90 shadow-lg',
  landing: 'border-[#1A0F0A]/10 bg-[#F7F7F4]/70 shadow-[0_12px_40px_-12px_rgba(26,15,10,0.35)]',
}

const ITEM_CLASS: Record<LanguageSwitcherTone, { active: string; idle: string }> = {
  default: { active: 'text-accent', idle: 'text-fg hover:bg-bg' },
  landing: { active: 'text-[#745C44]', idle: 'text-[#1A0F0A] hover:bg-[#1A0F0A]/5' },
}

export function LanguageSwitcher({ className = '', tone = 'default' }: LanguageSwitcherProps): JSX.Element {
  const { t } = useTranslation()
  const { language, supportedLanguages, setLanguage } = useLanguage()
  const [open, setOpen] = useState(false)
  const menuRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const handleClickOutside = (event: MouseEvent): void => {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    const handleEscape = (event: KeyboardEvent): void => {
      if (event.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', handleClickOutside)
    document.addEventListener('keydown', handleEscape)
    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
      document.removeEventListener('keydown', handleEscape)
    }
  }, [open])

  const select = (next: AppLanguage): void => {
    setLanguage(next)
    setOpen(false)
  }

  return (
    <div ref={menuRef} className={`relative ${className}`}>
      <button
        type="button"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={t('language.switchTo')}
        onClick={() => setOpen((current) => !current)}
        className={`inline-flex items-center gap-1.5 transition-colors focus:outline-none focus-visible:ring-2 ${TRIGGER_CLASS[tone]}`}
      >
        <GlobeIcon />
        <span className="text-sm font-medium uppercase">{language}</span>
      </button>

      <AnimatePresence>
        {open && (
          <motion.div
            role="menu"
            initial={{ opacity: 0, y: -6, scale: 0.96 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -6, scale: 0.96 }}
            transition={{ duration: 0.16, ease: [0.16, 1, 0.3, 1] }}
            style={{ transformOrigin: 'top right' }}
            className={`absolute right-0 z-50 mt-2 w-40 overflow-hidden rounded-xl border py-2 backdrop-blur-xl ${MENU_CLASS[tone]}`}
          >
            {supportedLanguages.map((code) => (
              <button
                key={code}
                type="button"
                role="menuitemradio"
                aria-checked={code === language}
                onClick={() => select(code)}
                className={`flex w-full items-center justify-between px-4 py-2.5 text-sm transition-colors ${
                  code === language ? ITEM_CLASS[tone].active : ITEM_CLASS[tone].idle
                }`}
              >
                {LANGUAGE_LABELS[code]}
                {code === language && <CheckIcon />}
              </button>
            ))}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}

function GlobeIcon(): JSX.Element {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx="12" cy="12" r="10" />
      <line x1="2" y1="12" x2="22" y2="12" />
      <path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z" />
    </svg>
  )
}

function CheckIcon(): JSX.Element {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <polyline points="20 6 9 17 4 12" />
    </svg>
  )
}
