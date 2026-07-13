import { motion, useReducedMotion } from 'framer-motion'
import type { ReactNode } from 'react'
import { NavLink } from 'react-router-dom'

export interface NavTabItem {
  to: string
  label: string
  icon?: ReactNode
  end?: boolean
}

type NavTabsOrientation = 'horizontal' | 'vertical'

interface NavTabsProps {
  items: NavTabItem[]
  indicatorId: string
  orientation?: NavTabsOrientation
  iconsOnly?: boolean
  className?: string
  onNavigate?: () => void
}

export function NavTabs({
  items,
  indicatorId,
  orientation = 'horizontal',
  iconsOnly = false,
  className = '',
  onNavigate,
}: NavTabsProps): JSX.Element {
  const prefersReducedMotion = useReducedMotion()
  const indicatorTransition = prefersReducedMotion
    ? { duration: 0 }
    : { type: 'spring' as const, stiffness: 420, damping: 38, mass: 0.7 }

  const isHorizontal = orientation === 'horizontal'

  const tabClass = ({ isActive }: { isActive: boolean }): string =>
    [
      'relative flex items-center gap-2.5 rounded-lg text-sm font-medium whitespace-nowrap transition-colors',
      'focus:outline-none focus-visible:ring-2 focus-visible:ring-light-text/30 dark:focus-visible:ring-dark-text/30',
      isHorizontal ? 'shrink-0 px-3 py-2' : 'px-3 py-2.5',
      iconsOnly ? 'justify-center' : '',
      isActive
        ? 'text-light-accent dark:text-dark-accent'
        : 'text-light-secondary dark:text-dark-secondary hover:text-light-text dark:hover:text-dark-text',
    ].join(' ')

  const indicatorClass = isHorizontal
    ? 'absolute left-2 right-2 bottom-0 h-0.5 rounded-full bg-light-accent dark:bg-dark-accent'
    : 'absolute inset-0 rounded-lg bg-light-accent/10 dark:bg-dark-accent/10'

  return (
    <div className={`flex ${isHorizontal ? 'items-center gap-1' : 'flex-col gap-1'} ${className}`}>
      {items.map((item) => (
        <NavLink
          key={item.to}
          to={item.to}
          end={item.end}
          onClick={onNavigate}
          title={iconsOnly ? item.label : undefined}
          aria-label={iconsOnly ? item.label : undefined}
          className={tabClass}
        >
          {({ isActive }) => (
            <>
              {isActive && (
                <motion.span
                  layoutId={indicatorId}
                  transition={indicatorTransition}
                  className={indicatorClass}
                  aria-hidden="true"
                />
              )}
              {item.icon && (
                <span className="relative z-10 shrink-0" aria-hidden="true">
                  {item.icon}
                </span>
              )}
              {!iconsOnly && <span className="relative z-10">{item.label}</span>}
            </>
          )}
        </NavLink>
      ))}
    </div>
  )
}
