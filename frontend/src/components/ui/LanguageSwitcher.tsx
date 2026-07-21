import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useLanguage } from '../../hooks/useLanguage'
import { AppLanguage, LANGUAGE_LABELS } from '../../i18n/config'

export function LanguageSwitcher({ className = '' }: { className?: string }): JSX.Element {
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
        className="inline-flex items-center gap-1.5 h-9 px-2.5 rounded-lg text-light-secondary dark:text-dark-secondary hover:text-light-text dark:hover:text-dark-text hover:bg-light-bg dark:hover:bg-dark-bg transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-light-accent dark:focus-visible:ring-dark-accent"
      >
        <GlobeIcon />
        <span className="text-sm font-medium uppercase">{language}</span>
      </button>

      {open && (
        <div
          role="menu"
          className="absolute right-0 mt-2 w-40 rounded-xl border border-light-border dark:border-dark-border bg-light-surface dark:bg-dark-surface shadow-lg py-2 z-50"
        >
          {supportedLanguages.map((code) => (
            <button
              key={code}
              type="button"
              role="menuitemradio"
              aria-checked={code === language}
              onClick={() => select(code)}
              className={`flex w-full items-center justify-between px-4 py-2.5 text-sm transition-colors ${
                code === language
                  ? 'text-light-accent dark:text-dark-accent'
                  : 'text-light-text dark:text-dark-text hover:bg-light-bg dark:hover:bg-dark-bg'
              }`}
            >
              {LANGUAGE_LABELS[code]}
              {code === language && <CheckIcon />}
            </button>
          ))}
        </div>
      )}
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
