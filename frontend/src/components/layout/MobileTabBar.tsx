import { NavLink } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { timeApi } from '../../api/time'
import type { TimeEntryResponse } from '../../types'
import { useMoreSheetStore } from '../../store/moreSheetStore'
import { useMobileTimerSheetStore } from '../../store/mobileTimerSheetStore'
import { CalendarIcon, CasesIcon } from './navIcons'

function MoreIcon(): JSX.Element {
  return (
    <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="5" cy="12" r="1.5" />
      <circle cx="12" cy="12" r="1.5" />
      <circle cx="19" cy="12" r="1.5" />
    </svg>
  )
}

function TimerIcon(): JSX.Element {
  return (
    <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="9" />
      <polyline points="12 7 12 12 15 14" />
    </svg>
  )
}

const TAB_ITEM_CLASS =
  'flex flex-col items-center justify-center gap-0.5 flex-1 min-h-11 py-1.5 rounded-lg text-[11px] font-medium transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-accent'

export function MobileTabBar(): JSX.Element {
  const { t } = useTranslation()
  const toggleMore = useMoreSheetStore((state) => state.toggle)
  const toggleTimer = useMobileTimerSheetStore((state) => state.toggle)

  const { data: activeTimer } = useQuery<TimeEntryResponse | null>({
    queryKey: ['active-timer'],
    queryFn: () => timeApi.activeTimer(),
    refetchInterval: 30_000,
  })

  return (
    <nav
      className="md:hidden fixed bottom-0 inset-x-0 z-40 flex items-stretch gap-1 px-2 pt-1.5 border-t border-line bg-bg/95 backdrop-blur-md"
      style={{ paddingBottom: 'max(0.375rem, env(safe-area-inset-bottom))' }}
      aria-label={t('nav.mobileTabBar')}
    >
      <NavLink
        to="/cases"
        className={({ isActive }) =>
          `${TAB_ITEM_CLASS} ${isActive ? 'text-accent' : 'text-fg-muted hover:text-fg'}`
        }
      >
        <CasesIcon />
        {t('nav.cases')}
      </NavLink>
      <NavLink
        to="/calendar"
        className={({ isActive }) =>
          `${TAB_ITEM_CLASS} ${isActive ? 'text-accent' : 'text-fg-muted hover:text-fg'}`
        }
      >
        <CalendarIcon />
        {t('nav.calendar')}
      </NavLink>
      <button
        type="button"
        onClick={toggleTimer}
        className={`${TAB_ITEM_CLASS} relative ${activeTimer ? 'text-accent' : 'text-fg-muted hover:text-fg'}`}
      >
        {activeTimer && <span className="absolute top-1 right-1/2 translate-x-3 w-2 h-2 rounded-full bg-accent animate-pulse" aria-hidden="true" />}
        <TimerIcon />
        {t('globalTimer.idle')}
      </button>
      <button type="button" onClick={toggleMore} className={`${TAB_ITEM_CLASS} text-fg-muted hover:text-fg`}>
        <MoreIcon />
        {t('nav.more')}
      </button>
    </nav>
  )
}
