import { Outlet } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { NavTabs } from '../ui/NavTabs'
import type { NavTabItem } from '../ui/NavTabs'
import { Navbar } from './Navbar'

const GRAFANA_URL = '/grafana/'

export function AdminLayout(): JSX.Element {
  const { t } = useTranslation()
  const navItems: NavTabItem[] = [
    { to: '/admin/applications', label: t('admin.applications'), end: true },
    { to: '/admin/users', label: t('admin.lawyers'), end: true },
    { to: '/admin/documents', label: t('admin.documents'), end: true },
    { to: '/admin/ai-stats', label: t('admin.aiMetrics'), end: true },
  ]

  return (
    <div className="min-h-screen bg-bg">
      <Navbar />

      {/* Mobile section nav */}
      <nav className="md:hidden flex gap-1 overflow-x-auto scrollbar-thin px-4 py-3 border-b border-line bg-surface">
        <NavTabs items={navItems} indicatorId="admin-mobile-tab-indicator" />
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

        <main className="flex-1 overflow-auto">
          <Outlet />
        </main>
      </div>
    </div>
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
