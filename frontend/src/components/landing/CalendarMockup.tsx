import { useTranslation } from 'react-i18next'
import { AppWindow } from './AppWindow'

type DayEvent = 'hearing' | 'deadline' | 'task'

const EVENTS: Record<number, DayEvent> = {
  6: 'task',
  11: 'deadline',
  14: 'hearing',
  20: 'task',
  22: 'deadline',
  27: 'hearing',
}

const DOT_TONE: Record<DayEvent, string> = {
  hearing: 'bg-[#B91C1C]',
  deadline: 'bg-[#F59E0B]',
  task: 'bg-[#3B82F6]',
}

export function CalendarMockup(): JSX.Element {
  const { t } = useTranslation()
  const days = Array.from({ length: 35 }, (_, i) => i - 2)
  const weekdays = [
    t('landing.calWeekdayMon'),
    t('landing.calWeekdayTue'),
    t('landing.calWeekdayWed'),
    t('landing.calWeekdayThu'),
    t('landing.calWeekdayFri'),
    t('landing.calWeekdaySat'),
    t('landing.calWeekdaySun'),
  ]

  return (
    <AppWindow title="app.pravoos.ru/calendar">
      <div className="p-5">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-lg font-bold text-[#26251E]">{t('landing.calMonth')}</h3>
          <div className="flex items-center gap-3 text-[12px] text-[#26251E]/50">
            <Legend tone="bg-[#B91C1C]" label={t('landing.calLegendHearings')} />
            <Legend tone="bg-[#F59E0B]" label={t('landing.calLegendDeadlines')} />
            <Legend tone="bg-[#3B82F6]" label={t('landing.calLegendTasks')} />
          </div>
        </div>

        <div className="grid grid-cols-7 gap-1 mb-1">
          {weekdays.map((day) => (
            <div key={day} className="text-center text-[11px] text-[#26251E]/50 py-1">
              {day}
            </div>
          ))}
        </div>

        <div className="grid grid-cols-7 gap-1.5">
          {days.map((day, index) => {
            const valid = day >= 1 && day <= 31
            const event = valid ? EVENTS[day] : undefined
            return (
              <div
                key={index}
                className={`aspect-square rounded-xl border flex flex-col items-center justify-center gap-1.5 ${
                  valid ? 'border-[#DDDCD8]' : 'border-transparent'
                }`}
              >
                {valid && (
                  <>
                    <span className="text-[12px] text-[#26251E]">{day}</span>
                    {event && <span className={`w-1.5 h-1.5 rounded-full ${DOT_TONE[event]}`} />}
                  </>
                )}
              </div>
            )
          })}
        </div>
      </div>
    </AppWindow>
  )
}

function Legend({ tone, label }: { tone: string; label: string }): JSX.Element {
  return (
    <span className="inline-flex items-center gap-1.5">
      <span className={`w-1.5 h-1.5 rounded-full ${tone}`} />
      {label}
    </span>
  )
}
