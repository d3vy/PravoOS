import type { ReactNode } from 'react'

interface AppWindowProps {
  title?: string
  children: ReactNode
  className?: string
}

export function AppWindow({ title = 'app.pravoos.ru', children, className = '' }: AppWindowProps): JSX.Element {
  return (
    <div
      className={`rounded-xl bg-[#F2F1ED] overflow-hidden ${className}`}
    >
      <div className="relative flex items-center justify-center px-4 h-9 border-b border-[#E8E7E2]">
        <div className="absolute left-3 flex items-center gap-1.5">
          <span className="w-3 h-3 rounded-full bg-[#DDDCD8]" />
          <span className="w-3 h-3 rounded-full bg-[#DDDCD8]" />
          <span className="w-3 h-3 rounded-full bg-[#DDDCD8]" />
        </div>
        <span className="text-xs text-[#63625C] truncate">{title}</span>
      </div>
      <div>{children}</div>
    </div>
  )
}
