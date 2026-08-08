import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { NavTabs, type NavTabItem } from './NavTabs'

afterEach(cleanup)

const items: NavTabItem[] = [
  { to: '/cases', label: 'Дела', icon: <span data-testid="icon-cases" />, badge: 3 },
  { to: '/clients', label: 'Клиенты' },
]

describe('NavTabs', () => {
  it('marks the tab matching the current route as active', () => {
    render(
      <MemoryRouter initialEntries={['/cases']}>
        <NavTabs items={items} indicatorId="tabs" />
      </MemoryRouter>
    )
    expect(screen.getByRole('link', { name: /Дела/ })).toHaveClass('text-accent')
    expect(screen.getByRole('link', { name: 'Клиенты' })).not.toHaveClass('text-accent')
  })

  it('shows the badge count next to the label', () => {
    render(
      <MemoryRouter initialEntries={['/cases']}>
        <NavTabs items={items} indicatorId="tabs" />
      </MemoryRouter>
    )
    expect(screen.getByText('3')).toBeInTheDocument()
  })

  it('hides labels and shows only icons/title in iconsOnly mode', () => {
    render(
      <MemoryRouter initialEntries={['/cases']}>
        <NavTabs items={items} indicatorId="tabs" iconsOnly />
      </MemoryRouter>
    )
    expect(screen.queryByText('Дела')).toBeNull()
    const link = screen.getByRole('link', { name: 'Дела' })
    expect(link).toHaveAttribute('title', 'Дела')
  })

  it('calls onNavigate when a tab is clicked', async () => {
    const onNavigate = vi.fn()
    render(
      <MemoryRouter initialEntries={['/cases']}>
        <NavTabs items={items} indicatorId="tabs" onNavigate={onNavigate} />
      </MemoryRouter>
    )
    await userEvent.click(screen.getByRole('link', { name: 'Клиенты' }))
    expect(onNavigate).toHaveBeenCalledTimes(1)
  })

  it('does not render a badge dot for a zero badge value', () => {
    render(
      <MemoryRouter initialEntries={['/clients']}>
        <NavTabs
          items={[{ to: '/clients', label: 'Клиенты', badge: 0 }]}
          indicatorId="tabs"
        />
      </MemoryRouter>
    )
    expect(screen.queryByText('0')).toBeNull()
  })
})
