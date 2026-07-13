import { useEffect, useState } from 'react'
import { Outlet } from 'react-router-dom'
import { useMediaQuery } from '../../hooks/useMediaQuery'
import { NavTabs } from '../ui/NavTabs'
import { LAWYER_NAV_INDICATOR_ID, lawyerNavSections } from './lawyerNav'
import { CollapseIcon } from './navIcons'
import { Navbar } from './Navbar'

const SIDEBAR_COLLAPSED_KEY = 'pravoos.sidebar.collapsed'
const WIDE_SCREEN_QUERY = '(min-width: 1024px)'

function readCollapsedPreference(): boolean {
  return localStorage.getItem(SIDEBAR_COLLAPSED_KEY) === 'true'
}

export function LawyerLayout(): JSX.Element {
  const isWideScreen = useMediaQuery(WIDE_SCREEN_QUERY)
  const [collapsedByUser, setCollapsedByUser] = useState(readCollapsedPreference)

  useEffect(() => {
    localStorage.setItem(SIDEBAR_COLLAPSED_KEY, String(collapsedByUser))
  }, [collapsedByUser])

  const collapsed = !isWideScreen || collapsedByUser

  return (
    <div className="min-h-screen bg-light-bg dark:bg-dark-bg">
      <Navbar />

      <div className="flex min-h-[calc(100vh-64px)]">
        <aside
          className={`hidden md:flex flex-col shrink-0 sticky top-16 h-[calc(100vh-64px)] overflow-y-auto scrollbar-thin border-r border-light-border dark:border-dark-border bg-light-surface dark:bg-dark-surface transition-[width] duration-200 ease-out ${
            collapsed ? 'w-16' : 'w-56'
          }`}
        >
          <nav className="flex-1 p-3 pt-6 flex flex-col gap-6">
            {lawyerNavSections.map((section) => (
              <div key={section.title}>
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
              aria-label={collapsed ? 'Развернуть меню' : 'Свернуть меню'}
              title={collapsed ? 'Развернуть меню' : 'Свернуть меню'}
              className={`sticky bottom-0 flex items-center gap-2.5 m-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors text-light-secondary dark:text-dark-secondary hover:text-light-text dark:hover:text-dark-text hover:bg-light-bg dark:hover:bg-dark-bg ${
                collapsed ? 'justify-center' : ''
              }`}
            >
              <CollapseIcon collapsed={collapsed} />
              {!collapsed && <span>Свернуть</span>}
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
