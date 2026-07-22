import { useEffect, useState } from 'react'
import { Outlet } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { casesApi } from '../../api/cases'
import type { CaseThreadResponse } from '../../types'
import { useMediaQuery } from '../../hooks/useMediaQuery'
import { NavTabs } from '../ui/NavTabs'
import { LAWYER_NAV_INDICATOR_ID, useLawyerNavSections } from './lawyerNav'
import { CollapseIcon } from './navIcons'
import { Navbar } from './Navbar'
import { CommandPalette } from '../command/CommandPalette'
import { GlobalProgressBar } from '../ui/GlobalProgressBar'

const SIDEBAR_COLLAPSED_KEY = 'pravoos.sidebar.collapsed'
const WIDE_SCREEN_QUERY = '(min-width: 1024px)'

function readCollapsedPreference(): boolean {
  return localStorage.getItem(SIDEBAR_COLLAPSED_KEY) === 'true'
}

export function LawyerLayout(): JSX.Element {
  const { t } = useTranslation()
  const lawyerNavSections = useLawyerNavSections()
  const isWideScreen = useMediaQuery(WIDE_SCREEN_QUERY)
  const [collapsedByUser, setCollapsedByUser] = useState(readCollapsedPreference)

  useEffect(() => {
    localStorage.setItem(SIDEBAR_COLLAPSED_KEY, String(collapsedByUser))
  }, [collapsedByUser])

  const collapsed = !isWideScreen || collapsedByUser

  const { data: threads = [] } = useQuery<CaseThreadResponse[]>({
    queryKey: ['messageThreads'],
    queryFn: casesApi.listThreads,
    refetchInterval: 60000,
  })
  const unreadCount = threads.reduce((sum, thread) => sum + thread.unreadCount, 0)

  const navSections = lawyerNavSections.map((section) => ({
    ...section,
    items: section.items.map((item) =>
      item.to === '/cases' ? { ...item, badge: unreadCount } : item
    ),
  }))

  return (
    <div className="min-h-screen bg-bg">
      <GlobalProgressBar />
      <CommandPalette />
      <Navbar />

      <div className="flex min-h-[calc(100vh-64px)]">
        <aside
          className={`hidden md:flex flex-col shrink-0 sticky top-16 h-[calc(100vh-64px)] overflow-y-auto scrollbar-thin border-r border-line bg-surface transition-[width] duration-200 ease-out ${
            collapsed ? 'w-16' : 'w-56'
          }`}
        >
          <nav className="flex-1 p-3 pt-6 flex flex-col gap-6">
            {navSections.map((section) => (
              <div key={section.id}>
                {!collapsed && <p className="eyebrow px-3 mb-2">{section.title}</p>}
                <NavTabs
                  items={section.items}
                  indicatorId={LAWYER_NAV_INDICATOR_ID}
                  orientation="vertical"
                  iconsOnly={collapsed}
                />
              </div>
            ))}
          </nav>

          {isWideScreen && (
            <button
              type="button"
              onClick={() => setCollapsedByUser((current) => !current)}
              aria-label={collapsed ? t('nav.expandMenu') : t('nav.collapseMenu')}
              title={collapsed ? t('nav.expandMenu') : t('nav.collapseMenu')}
              className={`sticky bottom-0 flex items-center gap-2.5 m-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors text-fg-muted hover:text-fg hover:bg-bg ${
                collapsed ? 'justify-center' : ''
              }`}
            >
              <CollapseIcon collapsed={collapsed} />
              {!collapsed && <span>{t('nav.collapse')}</span>}
            </button>
          )}
        </aside>

        <main className="flex-1 min-w-0">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
