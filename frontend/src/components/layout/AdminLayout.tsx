import { useEffect } from 'react'
import { Outlet } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { NavTabs } from '../ui/NavTabs'
import type { NavTabItem } from '../ui/NavTabs'
import { Navbar } from './Navbar'
import { SkipLink } from '../ui/SkipLink'
import { AdminCommandPalette } from '../command/AdminCommandPalette'
import { useCommandPaletteStore } from '../../store/commandPaletteStore'

const GRAFANA_URL = '/grafana/'

function isTypingTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false
  const tag = target.tagName
  return tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || target.isContentEditable
}

export function AdminLayout(): JSX.Element {
  const { t } = useTranslation()
  const toggleCommandPalette = useCommandPaletteStore((state) => state.toggle)
  const navItems: NavTabItem[] = [
    { to: '/admin/applications', label: t('admin.applications'), end: true },
    { to: '/admin/users', label: t('admin.lawyers'), end: true },
    { to: '/admin/documents', label: t('admin.documents'), end: true },
    { to: '/admin/ai-stats', label: t('admin.aiMetrics'), end: true },
    { to: '/admin/ai-conversations', label: t('admin.aiConversations'), end: true },
    { to: '/admin/recycle-bin', label: t('admin.recycleBin'), end: true },
    { to: '/admin/privacy-requests', label: t('admin.privacyRequests'), end: true },
  ]

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent): void => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k' && !isTypingTarget(event.target)) {
        event.preventDefault()
        toggleCommandPalette()
      }
    }
    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [toggleCommandPalette])

  return (
    <div className="min-h-screen bg-bg">
      <SkipLink />
      <Navbar />
      <AdminCommandPalette />

      {/* Mobile section nav */}
      <nav className="md:hidden flex gap-1 overflow-x-auto scrollbar-thin px-4 py-3 border-b border-line bg-surface">
        <NavTabs items={navItems} indicatorId="admin-mobile-tab-indicator" />
        <button
          type="button"
          onClick={toggleCommandPalette}
          className="shrink-0 flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-medium whitespace-nowrap transition-colors text-fg-muted hover:text-fg"
        >
          <SearchIcon />
          <span>{t('nav.search')}</span>
        </button>
        <a
          href={GRAFANA_URL}
          target="_blank"
          rel="noopener noreferrer"
          className="shrink-0 flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-medium whitespace-nowrap transition-colors text-fg-muted hover:text-fg"
        >
          <ChartIcon />
          <span>{t('admin.metrics')}</span>
        </a>
      </nav>

      <div className="flex min-h-[calc(100vh-64px)]">
        <aside className="hidden md:flex flex-col w-56 shrink-0 border-r border-line bg-surface">
          <nav className="p-4 flex flex-col gap-1 pt-6 flex-1">
            <p className="eyebrow px-3 mb-3">{t('admin.management')}</p>
            <NavTabs items={navItems} indicatorId="admin-sidebar-tab-indicator" orientation="vertical" />
            <button
              type="button"
              onClick={toggleCommandPalette}
              className="px-3 py-2.5 rounded-lg text-sm font-medium transition-colors text-fg-muted hover:text-fg hover:bg-bg flex items-center gap-2"
            >
              <SearchIcon />
              <span>{t('nav.search')}</span>
              <kbd className="ml-auto inline-flex items-center rounded border border-line px-1.5 py-0.5 text-[11px] font-medium">
                ⌘K
              </kbd>
            </button>
            <a
              href={GRAFANA_URL}
              target="_blank"
              rel="noopener noreferrer"
              className="px-3 py-2.5 rounded-lg text-sm font-medium transition-colors text-fg-muted hover:text-fg hover:bg-bg flex items-center gap-2"
            >
              <ChartIcon />
              <span>{t('admin.metricsGrafana')}</span>
            </a>
          </nav>
        </aside>

        <main id="main-content" tabIndex={-1} className="flex-1 overflow-auto focus:outline-none">
          <Outlet />
        </main>
      </div>
    </div>
  )
}

function SearchIcon(): JSX.Element {
  return (
    <svg className="w-4 h-4 shrink-0" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="11" cy="11" r="7" />
      <line x1="16.5" y1="16.5" x2="21" y2="21" />
    </svg>
  )
}

function ChartIcon(): JSX.Element {
  return (
    <svg className="w-4 h-4 shrink-0" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <line x1="18" y1="20" x2="18" y2="10" />
      <line x1="12" y1="20" x2="12" y2="4" />
      <line x1="6" y1="20" x2="6" y2="14" />
    </svg>
  )
}
