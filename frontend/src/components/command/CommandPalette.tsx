import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { motion, AnimatePresence } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import { searchApi } from '../../api/search'
import { useCommandPaletteStore } from '../../store/commandPaletteStore'
import { useTheme } from '../../hooks/useTheme'
import { useLawyerAccountLinks, useLawyerNavSections } from '../layout/lawyerNav'
import { CaseStatusBadge } from '../ui/Badge'
import type { GlobalSearchResponse } from '../../types'

const MIN_SEARCH_LENGTH = 2

interface CommandItem {
  id: string
  label: string
  hint?: string
  icon?: ReactNode
  keywords?: string
  badge?: ReactNode
  perform: () => void
}

interface CommandGroup {
  title: string
  items: CommandItem[]
}

function matches(query: string, item: CommandItem): boolean {
  if (!query) return true
  const haystack = `${item.label} ${item.keywords ?? ''}`.toLowerCase()
  return query
    .toLowerCase()
    .split(/\s+/)
    .filter(Boolean)
    .every((token) => haystack.includes(token))
}

export function CommandPalette(): JSX.Element | null {
  const { t } = useTranslation()
  const { open, setOpen, toggle } = useCommandPaletteStore()
  const navigate = useNavigate()
  const { toggleTheme } = useTheme()
  const lawyerNavSections = useLawyerNavSections()
  const lawyerAccountLinks = useLawyerAccountLinks()

  const [query, setQuery] = useState('')
  const [debouncedQuery, setDebouncedQuery] = useState('')
  const [activeIndex, setActiveIndex] = useState(0)
  const inputRef = useRef<HTMLInputElement>(null)
  const activeItemRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    const handleHotkey = (event: KeyboardEvent): void => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault()
        toggle()
      }
    }
    window.addEventListener('keydown', handleHotkey)
    return () => window.removeEventListener('keydown', handleHotkey)
  }, [toggle])

  useEffect(() => {
    if (open) {
      setQuery('')
      setDebouncedQuery('')
      setActiveIndex(0)
      const focusTimer = setTimeout(() => inputRef.current?.focus(), 30)
      return () => clearTimeout(focusTimer)
    }
    return undefined
  }, [open])

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedQuery(query.trim()), 200)
    return () => clearTimeout(timer)
  }, [query])

  const searchEnabled = open && debouncedQuery.length >= MIN_SEARCH_LENGTH
  const { data: searchResults } = useQuery<GlobalSearchResponse>({
    queryKey: ['command-search', debouncedQuery],
    queryFn: () => searchApi.global(debouncedQuery, false),
    enabled: searchEnabled,
  })

  const close = (): void => setOpen(false)
  const run = (action: () => void): void => {
    setOpen(false)
    action()
  }

  const actionGroup = useMemo<CommandGroup>(() => {
    const sectionNavItems: CommandItem[] = lawyerNavSections.flatMap((section) =>
      section.items.map((item) => ({
        id: `nav:${item.to}`,
        label: item.label,
        hint: t('command.goTo'),
        icon: item.icon,
        keywords: `${section.title} ${item.to}`,
        perform: () => run(() => navigate(item.to)),
      }))
    )
    const accountNavItems: CommandItem[] = lawyerAccountLinks.map((item) => ({
      id: `nav:${item.to}`,
      label: item.label,
      hint: t('command.goTo'),
      icon: item.icon,
      keywords: `аккаунт account ${item.to}`,
      perform: () => run(() => navigate(item.to)),
    }))
    const navItems: CommandItem[] = [
      ...sectionNavItems,
      {
        id: 'nav:/messages',
        label: t('command.messages'),
        hint: t('command.goTo'),
        icon: <ChatIcon />,
        keywords: 'переписка клиенты сообщения messages inbox',
        perform: () => run(() => navigate('/messages')),
      },
      ...accountNavItems,
    ]
    const quickItems: CommandItem[] = [
      {
        id: 'action:new-case',
        label: t('command.newCase'),
        hint: t('command.create'),
        keywords: 'создать добавить дело case new',
        icon: <PlusIcon />,
        perform: () => run(() => navigate('/cases?new=1')),
      },
      {
        id: 'action:new-client',
        label: t('command.newClient'),
        hint: t('command.create'),
        keywords: 'создать добавить клиент client new',
        icon: <PlusIcon />,
        perform: () => run(() => navigate('/clients?new=1')),
      },
      {
        id: 'action:ask-ai',
        label: t('command.askAi'),
        hint: t('command.aiChat'),
        keywords: 'вопрос спросить ai чат chat ask',
        icon: <ChatIcon />,
        perform: () => run(() => navigate('/chat')),
      },
      {
        id: 'action:toggle-theme',
        label: t('command.toggleTheme'),
        hint: t('command.appearance'),
        keywords: 'тема тёмная светлая dark light theme',
        icon: <ThemeIcon />,
        perform: () => run(toggleTheme),
      },
    ]
    return { title: t('command.actions'), items: [...quickItems, ...navItems] }
  }, [navigate, toggleTheme, lawyerNavSections, lawyerAccountLinks, t])

  const groups = useMemo<CommandGroup[]>(() => {
    const filteredActions: CommandGroup = {
      title: actionGroup.title,
      items: actionGroup.items.filter((item) => matches(debouncedQuery, item)),
    }
    const result: CommandGroup[] = filteredActions.items.length > 0 ? [filteredActions] : []

    if (searchEnabled && searchResults) {
      if (searchResults.cases.length > 0) {
        result.push({
          title: t('command.cases'),
          items: searchResults.cases.map((hit) => ({
            id: `case:${hit.id}`,
            label: hit.title,
            hint: hit.clientName ?? undefined,
            icon: <CaseIcon />,
            badge: <CaseStatusBadge status={hit.status} />,
            perform: () => run(() => navigate(`/cases/${hit.id}`)),
          })),
        })
      }
      if (searchResults.conversations.length > 0) {
        result.push({
          title: t('command.conversations'),
          items: searchResults.conversations.map((hit) => ({
            id: `conversation:${hit.id}`,
            label: hit.title,
            icon: <ChatIcon />,
            perform: () => run(() => navigate(`/chat?conversation=${hit.id}`)),
          })),
        })
      }
      if (searchResults.documents.length > 0) {
        result.push({
          title: t('command.documents'),
          items: searchResults.documents.map((hit) => ({
            id: `document:${hit.id}`,
            label: hit.title,
            hint: hit.fileName,
            icon: <DocIcon />,
            perform: () => run(() => navigate(hit.caseId ? `/cases/${hit.caseId}` : '/search')),
          })),
        })
      }
    }
    return result
  }, [actionGroup, debouncedQuery, searchEnabled, searchResults, navigate, t])

  const flatItems = useMemo(() => groups.flatMap((group) => group.items), [groups])

  useEffect(() => {
    setActiveIndex((current) => (current >= flatItems.length ? 0 : current))
  }, [flatItems.length])

  useEffect(() => {
    activeItemRef.current?.scrollIntoView({ block: 'nearest' })
  }, [activeIndex])

  const handleKeyDown = (event: React.KeyboardEvent): void => {
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      setActiveIndex((current) => (flatItems.length === 0 ? 0 : (current + 1) % flatItems.length))
    } else if (event.key === 'ArrowUp') {
      event.preventDefault()
      setActiveIndex((current) =>
        flatItems.length === 0 ? 0 : (current - 1 + flatItems.length) % flatItems.length
      )
    } else if (event.key === 'Enter') {
      event.preventDefault()
      flatItems[activeIndex]?.perform()
    } else if (event.key === 'Escape') {
      event.preventDefault()
      close()
    }
  }

  let runningIndex = -1

  return createPortal(
    <AnimatePresence>
      {open && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.12 }}
          className="fixed inset-0 z-[100] flex items-start justify-center px-4 pt-[12vh] bg-black/40 backdrop-blur-sm"
          onMouseDown={close}
          role="dialog"
          aria-modal="true"
          aria-label={t('command.dialog')}
        >
          <motion.div
            initial={{ opacity: 0, y: -8, scale: 0.98 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -8, scale: 0.98 }}
            transition={{ duration: 0.15, ease: 'easeOut' }}
            onMouseDown={(event) => event.stopPropagation()}
            onKeyDown={handleKeyDown}
            className="w-full max-w-xl overflow-hidden rounded-2xl border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-surface shadow-card dark:shadow-card-dark"
          >
            <div className="flex items-center gap-3 px-4 border-b border-light-border dark:border-dark-border">
              <span className="text-light-secondary dark:text-dark-secondary">
                <SearchIcon />
              </span>
              <input
                ref={inputRef}
                value={query}
                onChange={(event) => {
                  setQuery(event.target.value)
                  setActiveIndex(0)
                }}
                placeholder={t('command.placeholder')}
                className="flex-1 bg-transparent py-4 text-sm text-light-text dark:text-dark-text placeholder:text-light-secondary/70 dark:placeholder:text-dark-secondary/70 focus:outline-none"
                aria-label={t('command.inputAria')}
              />
              <kbd className="hidden sm:inline-flex items-center rounded-md border border-light-border dark:border-dark-border px-1.5 py-0.5 text-[11px] font-medium text-light-secondary dark:text-dark-secondary">
                ESC
              </kbd>
            </div>

            <div className="max-h-[52vh] overflow-y-auto scrollbar-thin p-2">
              {flatItems.length === 0 ? (
                <p className="px-3 py-10 text-center text-sm text-light-secondary dark:text-dark-secondary">
                  {debouncedQuery.length >= MIN_SEARCH_LENGTH
                    ? t('command.nothingFound', { query: debouncedQuery })
                    : t('command.startTyping')}
                </p>
              ) : (
                groups.map((group) => (
                  <div key={group.title} className="mb-1 last:mb-0">
                    <p className="eyebrow px-3 pt-2 pb-1">{group.title}</p>
                    {group.items.map((item) => {
                      runningIndex += 1
                      const index = runningIndex
                      const isActive = index === activeIndex
                      return (
                        <button
                          key={item.id}
                          ref={isActive ? activeItemRef : undefined}
                          type="button"
                          onMouseMove={() => setActiveIndex(index)}
                          onClick={item.perform}
                          className={`flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left transition-colors ${
                            isActive
                              ? 'bg-light-surface dark:bg-dark-surface-elevated'
                              : 'hover:bg-light-surface dark:hover:bg-dark-surface-elevated'
                          }`}
                        >
                          {item.icon && (
                            <span className="shrink-0 text-light-secondary dark:text-dark-secondary">
                              {item.icon}
                            </span>
                          )}
                          <span className="min-w-0 flex-1 truncate text-sm text-light-text dark:text-dark-text">
                            {item.label}
                          </span>
                          {item.badge}
                          {item.hint && !item.badge && (
                            <span className="shrink-0 truncate text-xs text-light-secondary dark:text-dark-secondary max-w-[45%]">
                              {item.hint}
                            </span>
                          )}
                        </button>
                      )
                    })}
                  </div>
                ))
              )}
            </div>

            <div className="hidden sm:flex items-center gap-4 border-t border-light-border dark:border-dark-border px-4 py-2.5 text-[11px] text-light-secondary dark:text-dark-secondary">
              <HintKey combo="↑↓" label={t('command.hintNavigate')} />
              <HintKey combo="↵" label={t('command.hintSelect')} />
              <HintKey combo="esc" label={t('command.hintClose')} />
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>,
    document.body
  )
}

function HintKey({ combo, label }: { combo: string; label: string }): JSX.Element {
  return (
    <span className="inline-flex items-center gap-1.5">
      <kbd className="inline-flex items-center rounded border border-light-border dark:border-dark-border px-1.5 py-0.5 font-medium">
        {combo}
      </kbd>
      {label}
    </span>
  )
}

function IconWrapper({ children }: { children: ReactNode }): JSX.Element {
  return (
    <svg
      className="w-4 h-4 shrink-0"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      {children}
    </svg>
  )
}

function SearchIcon(): JSX.Element {
  return (
    <IconWrapper>
      <circle cx="11" cy="11" r="7" />
      <line x1="16.5" y1="16.5" x2="21" y2="21" />
    </IconWrapper>
  )
}

function PlusIcon(): JSX.Element {
  return (
    <IconWrapper>
      <line x1="12" y1="5" x2="12" y2="19" />
      <line x1="5" y1="12" x2="19" y2="12" />
    </IconWrapper>
  )
}

function ThemeIcon(): JSX.Element {
  return (
    <IconWrapper>
      <path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z" />
    </IconWrapper>
  )
}

function CaseIcon(): JSX.Element {
  return (
    <IconWrapper>
      <rect x="2" y="7" width="20" height="14" rx="2" />
      <path d="M9 7V5a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v2" />
    </IconWrapper>
  )
}

function ChatIcon(): JSX.Element {
  return (
    <IconWrapper>
      <path d="M21 11.5a8.4 8.4 0 0 1-9 8.4 9.9 9.9 0 0 1-4.2-.9L3 21l1.9-4.1A8.4 8.4 0 0 1 12 3.1a8.4 8.4 0 0 1 9 8.4z" />
    </IconWrapper>
  )
}

function DocIcon(): JSX.Element {
  return (
    <IconWrapper>
      <path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z" />
      <path d="M14 3v5h5" />
    </IconWrapper>
  )
}
