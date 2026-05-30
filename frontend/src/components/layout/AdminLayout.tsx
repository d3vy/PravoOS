import { NavLink, Outlet } from 'react-router-dom'
import { Navbar } from './Navbar'

interface NavItem {
  path: string
  label: string
}

const navItems: NavItem[] = [
  { path: '/admin/applications', label: 'Заявки' },
  { path: '/admin/documents', label: 'Документы' },
  { path: '/chat', label: 'AI-ассистент' },
]

export function AdminLayout(): JSX.Element {
  return (
    <div className="min-h-screen bg-light-bg dark:bg-dark-bg">
      <Navbar />
      <div className="flex min-h-[calc(100vh-64px)]">
        <aside className="hidden md:flex flex-col w-56 shrink-0 border-r border-light-border dark:border-dark-border bg-light-surface dark:bg-dark-surface">
          <nav className="p-4 flex flex-col gap-1 pt-6">
            <p className="text-xs font-semibold uppercase tracking-widest text-light-secondary dark:text-dark-secondary px-3 mb-3">
              Управление
            </p>
            {navItems.map((item) => (
              <NavLink
                key={item.path}
                to={item.path}
                className={({ isActive }) =>
                  `px-3 py-2.5 rounded-lg text-sm font-medium transition-colors ${
                    isActive
                      ? 'bg-light-accent/10 dark:bg-dark-accent/10 text-light-accent dark:text-dark-accent'
                      : 'text-light-secondary dark:text-dark-secondary hover:text-light-text dark:hover:text-dark-text hover:bg-light-bg dark:hover:bg-dark-bg'
                  }`
                }
              >
                {item.label}
              </NavLink>
            ))}
          </nav>
        </aside>

        <main className="flex-1 overflow-auto">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
