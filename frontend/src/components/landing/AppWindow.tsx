import type { ReactNode } from 'react'

interface AppWindowProps {
  title?: string
  children: ReactNode
  className?: string
}

export function AppWindow({ title = 'app.pravoos.ru', children, className = '' }: AppWindowProps): JSX.Element {
  return (
    <div
      className={`rounded-2xl border border-light-border dark:border-dark-border bg-light-surface dark:bg-dark-surface shadow-2xl shadow-black/10 dark:shadow-black/40 overflow-hidden ${className}`}
    >
      <div className="flex items-center gap-2 px-4 h-10 border-b border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg">
        <span className="w-3 h-3 rounded-full bg-red-400/70" />
        <span className="w-3 h-3 rounded-full bg-amber-400/70" />
        <span className="w-3 h-3 rounded-full bg-emerald-400/70" />
        <span className="ml-3 text-xs text-light-secondary dark:text-dark-secondary truncate">{title}</span>
      </div>
      <div className="bg-light-bg dark:bg-dark-bg">{children}</div>
    </div>
  )
}
