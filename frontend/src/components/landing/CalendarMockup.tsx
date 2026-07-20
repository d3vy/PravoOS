import { AppWindow } from './AppWindow'

const WEEKDAYS = ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб', 'Вс']

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
  hearing: 'bg-red-500',
  deadline: 'bg-amber-500',
  task: 'bg-blue-500',
}

export function CalendarMockup(): JSX.Element {
  const days = Array.from({ length: 35 }, (_, i) => i - 2)

  return (
    <AppWindow title="app.pravoos.ru/calendar">
      <div className="p-5">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-base font-semibold text-light-text dark:text-dark-text">Август 2026</h3>
          <div className="flex items-center gap-3 text-[11px] text-light-secondary dark:text-dark-secondary">
            <Legend tone="bg-red-500" label="Заседания" />
            <Legend tone="bg-amber-500" label="Сроки" />
            <Legend tone="bg-blue-500" label="Задачи" />
          </div>
        </div>

        <div className="grid grid-cols-7 gap-1 mb-1">
          {WEEKDAYS.map((day) => (
            <div key={day} className="text-center text-[10px] text-light-secondary dark:text-dark-secondary py-1">
              {day}
            </div>
          ))}
        </div>

        <div className="grid grid-cols-7 gap-1">
          {days.map((day, index) => {
            const valid = day >= 1 && day <= 31
            const event = valid ? EVENTS[day] : undefined
            return (
              <div
                key={index}
                className={`aspect-square rounded-lg border flex flex-col items-center justify-center gap-1 ${
                  valid
                    ? 'border-light-border dark:border-dark-border'
                    : 'border-transparent'
                }`}
              >
                {valid && (
                  <>
                    <span className="text-[11px] text-light-text dark:text-dark-text">{day}</span>
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
    <span className="inline-flex items-center gap-1">
      <span className={`w-1.5 h-1.5 rounded-full ${tone}`} />
      {label}
    </span>
  )
}
