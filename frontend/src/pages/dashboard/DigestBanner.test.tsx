import { afterEach, beforeAll, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import i18n from '../../i18n'
import { DigestBanner, greeting } from './DashboardPage'
import type { DashboardResponse } from '../../types'

function makeDashboard(overrides: Partial<DashboardResponse>): DashboardResponse {
  return {
    pipeline: [],
    activeCases: 3,
    openTasks: 2,
    upcomingDeadlines: [],
    recentCases: [],
    moneyOnTable: { uninvoicedMinutes: 0, uninvoicedAmount: 0 },
    tasksToday: [],
    unpaidInvoices: { count: 0, totalAmount: 0, items: [] },
    ...overrides,
  }
}

function deadline(daysLeft: number): DashboardResponse['upcomingDeadlines'][number] {
  return {
    caseId: 'case-1',
    caseTitle: 'Дело',
    type: 'FILING_DEADLINE',
    typeName: 'Подача',
    date: new Date().toISOString(),
    daysLeft,
  }
}

function renderBanner(data: DashboardResponse): void {
  render(
    <MemoryRouter>
      <DigestBanner data={data} />
    </MemoryRouter>
  )
}

beforeAll(async () => {
  await i18n.changeLanguage('ru')
})

afterEach(() => {
  cleanup()
})

describe('greeting', () => {
  it('picks the time-of-day greeting bucket', () => {
    const t = ((key: string) => key) as never
    expect(greeting(8, t)).toBe('dashboard.greetingMorning')
    expect(greeting(14, t)).toBe('dashboard.greetingDay')
    expect(greeting(20, t)).toBe('dashboard.greetingEvening')
    expect(greeting(2, t)).toBe('dashboard.greetingNight')
  })
})

describe('DigestBanner', () => {
  it('shows the no-deadlines message when nothing is upcoming', () => {
    renderBanner(makeDashboard({ upcomingDeadlines: [] }))
    expect(screen.getByText('Всё под контролем — активных дедлайнов нет.')).toBeInTheDocument()
  })

  it('uses the singular plural form for one deadline due today', () => {
    renderBanner(makeDashboard({ upcomingDeadlines: [deadline(0)] }))
    expect(screen.getByText(/Сегодня к сроку/)).toBeInTheDocument()
    expect(screen.getByText('1 дедлайн')).toBeInTheDocument()
  })

  it('uses the few plural form for 2-4 deadlines due today', () => {
    renderBanner(makeDashboard({ upcomingDeadlines: [deadline(0), deadline(0), deadline(-1)] }))
    expect(screen.getByText('3 дедлайна')).toBeInTheDocument()
  })

  it('uses the many plural form for 5+ deadlines due today', () => {
    renderBanner(makeDashboard({ upcomingDeadlines: Array.from({ length: 5 }, () => deadline(0)) }))
    expect(screen.getByText('5 дедлайнов')).toBeInTheDocument()
  })

  it('falls back to the week summary when nothing is due today', () => {
    renderBanner(makeDashboard({ upcomingDeadlines: [deadline(3), deadline(5)] }))
    expect(screen.getByText(/Срочного на сегодня нет/)).toBeInTheDocument()
    expect(screen.getByText('2 дедлайна')).toBeInTheDocument()
  })

  it('renders the active cases, tasks and week pills from dashboard data', () => {
    renderBanner(makeDashboard({ activeCases: 7, openTasks: 4, upcomingDeadlines: [deadline(2)] }))
    expect(screen.getByText('7')).toBeInTheDocument()
    expect(screen.getByText('активных дел')).toBeInTheDocument()
    expect(screen.getByText('4')).toBeInTheDocument()
    expect(screen.getByText('задач')).toBeInTheDocument()
  })
})
