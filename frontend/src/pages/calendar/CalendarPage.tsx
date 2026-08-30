import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import i18n from '../../i18n'
import { calendarApi } from '../../api/calendar'
import { clientsApi } from '../../api/clients'
import { casesApi } from '../../api/cases'
import { Button } from '../../components/ui/Button'
import { QueryState } from '../../components/ui/QueryState'
import type { CalendarEvent, CalendarEventType } from '../../types'
import { PageHeader } from '../../components/ui/PageHeader'

type ViewMode = 'month' | 'week'

const EVENT_STYLE: Record<CalendarEventType, string> = {
  DEADLINE: 'bg-red-100 text-red-700 dark:bg-red-500/15 dark:text-red-300',
  HEARING: 'bg-blue-100 text-blue-700 dark:bg-blue-500/15 dark:text-blue-300',
  TASK: 'bg-amber-100 text-amber-700 dark:bg-amber-500/15 dark:text-amber-300',
}

const EVENT_DOT: Record<CalendarEventType, string> = {
  DEADLINE: 'bg-red-500',
  HEARING: 'bg-blue-500',
  TASK: 'bg-amber-500',
}

function pad(value: number): string {
  return String(value).padStart(2, '0')
}

function toIso(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function addDays(date: Date, amount: number): Date {
  const copy = new Date(date)
  copy.setDate(copy.getDate() + amount)
  return copy
}

function startOfWeekMonday(date: Date): Date {
  const copy = new Date(date.getFullYear(), date.getMonth(), date.getDate())
  const offset = (copy.getDay() + 6) % 7
  copy.setDate(copy.getDate() - offset)
  return copy
}

function buildGrid(anchor: Date, view: ViewMode): Date[] {
  const start =
    view === 'week'
      ? startOfWeekMonday(anchor)
      : startOfWeekMonday(new Date(anchor.getFullYear(), anchor.getMonth(), 1))
  const length = view === 'week' ? 7 : 42
  return Array.from({ length }, (_, index) => addDays(start, index))
}

export default function CalendarPage(): JSX.Element {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const weekdays = [
    t('calendar.weekdayMon'), t('calendar.weekdayTue'), t('calendar.weekdayWed'),
    t('calendar.weekdayThu'), t('calendar.weekdayFri'), t('calendar.weekdaySat'), t('calendar.weekdaySun'),
  ]
  const months = [
    t('calendar.monthJanuary'), t('calendar.monthFebruary'), t('calendar.monthMarch'), t('calendar.monthApril'),
    t('calendar.monthMay'), t('calendar.monthJune'), t('calendar.monthJuly'), t('calendar.monthAugust'),
    t('calendar.monthSeptember'), t('calendar.monthOctober'), t('calendar.monthNovember'), t('calendar.monthDecember'),
  ]
  const [anchor, setAnchor] = useState<Date>(() => new Date())
  const [view, setView] = useState<ViewMode>('month')
  const [clientId, setClientId] = useState('')
  const [caseId, setCaseId] = useState('')

  const grid = useMemo(() => buildGrid(anchor, view), [anchor, view])
  const from = toIso(grid[0])
  const to = toIso(grid[grid.length - 1])
  const filters = useMemo(
    () => ({ clientId: clientId || undefined, caseId: caseId || undefined }),
    [clientId, caseId],
  )

  const { data: clients } = useQuery({ queryKey: ['clients', 'all'], queryFn: clientsApi.getAll })
  const { data: casesPage } = useQuery({
    queryKey: ['cases', 'calendar-filter'],
    queryFn: () => casesApi.list(undefined, undefined, 0, 100),
  })

  const { data: events, isLoading, isError } = useQuery({
    queryKey: ['calendar', from, to, filters.clientId, filters.caseId],
    queryFn: () => calendarApi.list(from, to, filters),
  })

  const eventsByDate = useMemo(() => {
    const map = new Map<string, CalendarEvent[]>()
    for (const event of events ?? []) {
      const bucket = map.get(event.date)
      if (bucket) bucket.push(event)
      else map.set(event.date, [event])
    }
    return map
  }, [events])

  const todayIso = toIso(new Date())
  const currentMonth = anchor.getMonth()

  const shift = (direction: -1 | 1): void => {
    setAnchor((previous) => {
      if (view === 'week') return addDays(previous, direction * 7)
      return new Date(previous.getFullYear(), previous.getMonth() + direction, 1)
    })
  }

  const title =
    view === 'week'
      ? `${toDisplay(grid[0])} — ${toDisplay(grid[6])}`
      : `${months[currentMonth]} ${anchor.getFullYear()}`

  const handleExport = (): void => {
    void calendarApi.exportIcs(from, to, filters)
  }

  const openEvent = (event: CalendarEvent): void => {
    navigate(`/cases/${event.caseId}`)
  }

  return (
    <div className="bg-bg">
      <div className="page-container py-8">
        <PageHeader
          eyebrow={t('calendar.eyebrow')}
          title={t('calendar.title')}
          className="mb-6"
          actions={
            <Button variant="secondary" size="sm" onClick={handleExport}>
              {t('calendar.exportIcal')}
            </Button>
          }
        />

        <div className="card-elevated p-4 mb-6 flex flex-wrap items-center gap-3">
          <div className="flex items-center gap-1 rounded-lg border border-line p-1">
            <ViewButton active={view === 'month'} onClick={() => setView('month')}>
              {t('calendar.viewMonth')}
            </ViewButton>
            <ViewButton active={view === 'week'} onClick={() => setView('week')}>
              {t('calendar.viewWeek')}
            </ViewButton>
          </div>

          <div className="flex items-center gap-2">
            <Button variant="ghost" size="sm" onClick={() => shift(-1)}>
              ←
            </Button>
            <Button variant="ghost" size="sm" onClick={() => setAnchor(new Date())}>
              {t('calendar.today')}
            </Button>
            <Button variant="ghost" size="sm" onClick={() => shift(1)}>
              →
            </Button>
          </div>

          <span className="text-sm font-medium text-fg min-w-0">{title}</span>

          <div className="ml-auto flex flex-wrap items-center gap-2">
            <select
              value={clientId}
              onChange={(e) => setClientId(e.target.value)}
              className="px-3 py-2 rounded-lg border border-line bg-surface text-fg text-sm focus:outline-none focus:ring-2 focus:ring-accent"
            >
              <option value="">{t('calendar.allClients')}</option>
              {clients?.map((client) => (
                <option key={client.id} value={client.id}>
                  {client.name}
                </option>
              ))}
            </select>
            <select
              value={caseId}
              onChange={(e) => setCaseId(e.target.value)}
              className="px-3 py-2 rounded-lg border border-line bg-surface text-fg text-sm focus:outline-none focus:ring-2 focus:ring-accent"
            >
              <option value="">{t('calendar.allCases')}</option>
              {casesPage?.items.map((caseItem) => (
                <option key={caseItem.id} value={caseItem.id}>
                  {caseItem.title}
                </option>
              ))}
            </select>
          </div>
        </div>

        <div className="flex flex-wrap gap-4 mb-4 text-xs text-fg-muted">
          <Legend type="DEADLINE" label={t('calendar.legendDeadline')} />
          <Legend type="HEARING" label={t('calendar.legendHearing')} />
          <Legend type="TASK" label={t('calendar.legendTask')} />
        </div>

        <QueryState
          isLoading={isLoading}
          isError={isError}
          errorMessage={t('calendar.loadError')}
        />

        {!isLoading && !isError && (
          <AgendaList
            days={grid.filter((day) => view === 'week' || day.getMonth() === currentMonth)}
            eventsByDate={eventsByDate}
            todayIso={todayIso}
            onOpen={openEvent}
          />
        )}

        {!isLoading && !isError && (
          <div className="hidden sm:block card-elevated overflow-hidden">
            <div className="grid grid-cols-7 border-b border-line">
              {weekdays.map((day) => (
                <div
                  key={day}
                  className="px-2 py-2 text-center text-xs font-medium text-fg-muted"
                >
                  {day}
                </div>
              ))}
            </div>
            <div className="grid grid-cols-7">
              {grid.map((day) => {
                const iso = toIso(day)
                const dayEvents = eventsByDate.get(iso) ?? []
                const outside = view === 'month' && day.getMonth() !== currentMonth
                return (
                  <DayCell
                    key={iso}
                    day={day}
                    isToday={iso === todayIso}
                    outside={outside}
                    tall={view === 'week'}
                    events={dayEvents}
                    onOpen={openEvent}
                  />
                )
              })}
            </div>
          </div>
        )}
      </div>
    </div>
  )
}

function toDisplay(date: Date): string {
  return `${pad(date.getDate())}.${pad(date.getMonth() + 1)}`
}

function toAgendaHeading(date: Date): string {
  const locale = i18n.language.startsWith('ru') ? 'ru-RU' : 'en-US'
  return date.toLocaleDateString(locale, { day: 'numeric', month: 'long', weekday: 'short' })
}

function AgendaList({
  days,
  eventsByDate,
  todayIso,
  onOpen,
}: {
  days: Date[]
  eventsByDate: Map<string, CalendarEvent[]>
  todayIso: string
  onOpen: (event: CalendarEvent) => void
}): JSX.Element {
  const { t } = useTranslation()
  const daysWithEvents = days.filter((day) => (eventsByDate.get(toIso(day)) ?? []).length > 0)

  if (daysWithEvents.length === 0) {
    return (
      <div className="sm:hidden card-elevated p-6 text-sm text-fg-muted">
        {t('calendar.noEvents')}
      </div>
    )
  }

  return (
    <div className="sm:hidden flex flex-col gap-3">
      {daysWithEvents.map((day) => {
        const iso = toIso(day)
        const isToday = iso === todayIso
        return (
          <div key={iso} className="card-elevated p-4">
            <p
              className={`text-sm font-medium mb-3 ${
                isToday
                  ? 'text-accent'
                  : 'text-fg'
              }`}
            >
              {toAgendaHeading(day)}
              {isToday && t('calendar.todaySuffix')}
            </p>
            <div className="flex flex-col gap-2">
              {(eventsByDate.get(iso) ?? []).map((event) => (
                <button
                  key={event.id}
                  type="button"
                  onClick={() => onOpen(event)}
                  className={`w-full text-left px-3 py-2 rounded-lg ${EVENT_STYLE[event.type]}`}
                >
                  <span className="block text-sm font-medium">{event.title}</span>
                  <span className="block text-xs opacity-80 truncate">
                    {event.caseTitle}
                    {event.detail ? ` · ${event.detail}` : ''}
                  </span>
                </button>
              ))}
            </div>
          </div>
        )
      })}
    </div>
  )
}

function ViewButton({
  active,
  onClick,
  children,
}: {
  active: boolean
  onClick: () => void
  children: string
}): JSX.Element {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`px-3 py-1.5 rounded-md text-sm font-medium transition-colors ${
        active
          ? 'bg-accent-solid text-accent-fg'
          : 'text-fg-muted hover:text-fg'
      }`}
    >
      {children}
    </button>
  )
}

function Legend({ type, label }: { type: CalendarEventType; label: string }): JSX.Element {
  return (
    <span className="inline-flex items-center gap-1.5">
      <span className={`w-2.5 h-2.5 rounded-full ${EVENT_DOT[type]}`} />
      {label}
    </span>
  )
}

function DayCell({
  day,
  isToday,
  outside,
  tall,
  events,
  onOpen,
}: {
  day: Date
  isToday: boolean
  outside: boolean
  tall: boolean
  events: CalendarEvent[]
  onOpen: (event: CalendarEvent) => void
}): JSX.Element {
  const { t } = useTranslation()
  const visibleLimit = tall ? events.length : 3
  const visible = events.slice(0, visibleLimit)
  const overflow = events.length - visible.length

  return (
    <div
      className={`border-b border-r border-line p-1.5 flex flex-col gap-1 ${
        tall ? 'min-h-[9rem]' : 'min-h-[6.5rem]'
      } ${outside ? 'bg-bg/50' : ''}`}
    >
      <span
        className={`text-xs font-medium ${
          isToday
            ? 'inline-flex items-center justify-center w-6 h-6 rounded-full bg-accent-solid text-accent-fg'
            : outside
              ? 'text-fg-muted/50'
              : 'text-fg'
        }`}
      >
        {day.getDate()}
      </span>
      <div className="flex flex-col gap-1 min-w-0">
        {visible.map((event) => (
          <button
            key={event.id}
            type="button"
            title={`${event.caseTitle} · ${event.title}${event.detail ? ` · ${event.detail}` : ''}`}
            onClick={() => onOpen(event)}
            className={`text-left text-[11px] leading-tight px-1.5 py-1 rounded truncate ${EVENT_STYLE[event.type]}`}
          >
            {event.title}
          </button>
        ))}
        {overflow > 0 && (
          <span className="text-[11px] text-fg-muted px-1.5">
            {t('calendar.overflowMore', { count: overflow })}
          </span>
        )}
      </div>
    </div>
  )
}
