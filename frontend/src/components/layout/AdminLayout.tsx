import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { Navbar } from './Navbar'
import { useAuthStore } from '../../store/authStore'

interface NavItem {
  path: string
  label: string
}

const navItems: NavItem[] = [
  { path: '/admin/applications', label: 'Заявки' },
  { path: '/admin/users', label: 'Юристы' },
  { path: '/admin/documents', label: 'Документы' },
  { path: '/admin/ai-stats', label: 'AI-метрики' },
]

const GRAFANA_URL = '/grafana/'

const desktopLinkClass = ({ isActive }: { isActive: boolean }): string =>
  `px-3 py-2.5 rounded-lg text-sm font-medium transition-colors ${
    isActive
      ? 'bg-light-accent/10 dark:bg-dark-accent/10 text-light-accent dark:text-dark-accent'
      : 'text-light-secondary dark:text-dark-secondary hover:text-light-text dark:hover:text-dark-text hover:bg-light-bg dark:hover:bg-dark-bg'
  }`

const mobileLinkClass = ({ isActive }: { isActive: boolean }): string =>
  `shrink-0 px-4 py-2 rounded-lg text-sm font-medium whitespace-nowrap transition-colors ${
    isActive
      ? 'bg-light-accent/10 dark:bg-dark-accent/10 text-light-accent dark:text-dark-accent'
      : 'text-light-secondary dark:text-dark-secondary hover:text-light-text dark:hover:text-dark-text'
  }`

export function AdminLayout(): JSX.Element {
  const { toggleViewAsLawyer } = useAuthStore()
  const navigate = useNavigate()

  const handleSwitchToLawyer = (): void => {
    toggleViewAsLawyer()
    navigate('/chat')
  }

  return (
    <div className="min-h-screen bg-light-bg dark:bg-dark-bg">
      <Navbar />

      {/* Mobile section nav */}
      <nav className="md:hidden flex gap-1 overflow-x-auto scrollbar-thin px-4 py-3 border-b border-light-border dark:border-dark-border bg-light-surface dark:bg-dark-surface">
        {navItems.map((item) => (
          <NavLink key={item.path} to={item.path} end className={mobileLinkClass}>
            {item.label}
          </NavLink>
        ))}
        <a
          href={GRAFANA_URL}
          target="_blank"
          rel="noopener noreferrer"
          className="shrink-0 px-4 py-2 rounded-lg text-sm font-medium whitespace-nowrap transition-colors text-light-secondary dark:text-dark-secondary hover:text-light-text dark:hover:text-dark-text"
        >
          📊 Метрики
        </a>
        <button
          onClick={handleSwitchToLawyer}
          className="shrink-0 px-4 py-2 rounded-lg text-sm font-medium whitespace-nowrap transition-colors text-light-secondary dark:text-dark-secondary hover:text-light-text dark:hover:text-dark-text"
        >
          ⇄ Как юрист
        </button>
      </nav>

      <div className="flex min-h-[calc(100vh-64px)]">
        <aside className="hidden md:flex flex-col w-56 shrink-0 border-r border-light-border dark:border-dark-border bg-light-surface dark:bg-dark-surface">
          <nav className="p-4 flex flex-col gap-1 pt-6 flex-1">
            <p className="eyebrow px-3 mb-3">Управление</p>
            {navItems.map((item) => (
              <NavLink key={item.path} to={item.path} end className={desktopLinkClass}>
                {item.label}
              </NavLink>
            ))}
            <a
              href={GRAFANA_URL}
              target="_blank"
              rel="noopener noreferrer"
              className="px-3 py-2.5 rounded-lg text-sm font-medium transition-colors text-light-secondary dark:text-dark-secondary hover:text-light-text dark:hover:text-dark-text hover:bg-light-bg dark:hover:bg-dark-bg flex items-center gap-2"
            >
              <span>📊</span>
              <span>Метрики (Grafana)</span>
            </a>
          </nav>
          <div className="p-4 border-t border-light-border dark:border-dark-border">
            <button
              onClick={handleSwitchToLawyer}
              className="w-full flex items-center gap-2 px-3 py-2.5 rounded-lg text-sm font-medium text-light-secondary dark:text-dark-secondary hover:text-light-text dark:hover:text-dark-text hover:bg-light-bg dark:hover:bg-dark-bg transition-colors"
            >
              <span className="text-base">⇄</span>
              <span>Войти как юрист</span>
            </button>
          </div>
        </aside>

        <main className="flex-1 overflow-auto">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
