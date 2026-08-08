import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import '../../i18n'
import { PageHeader } from './PageHeader'

afterEach(cleanup)

function renderHeader(props: Parameters<typeof PageHeader>[0]) {
  return render(
    <MemoryRouter>
      <PageHeader {...props} />
    </MemoryRouter>
  )
}

describe('PageHeader', () => {
  it('renders the title and omits breadcrumb nav when none are given', () => {
    renderHeader({ title: 'Дела' })
    expect(screen.getByRole('heading', { name: 'Дела' })).toBeInTheDocument()
    expect(screen.queryByRole('navigation')).toBeNull()
  })

  it('renders each breadcrumb, linking all but the last', () => {
    renderHeader({
      title: 'Дело №123',
      breadcrumbs: [
        { label: 'Дела', to: '/cases' },
        { label: 'Дело №123' },
      ],
    })
    const link = screen.getByRole('link', { name: 'Дела' })
    expect(link).toHaveAttribute('href', '/cases')
    const current = screen.getByText('Дело №123', { selector: 'span' })
    expect(current).toHaveAttribute('aria-current', 'page')
    expect(screen.queryByRole('link', { name: 'Дело №123' })).toBeNull()
  })

  it('renders the last breadcrumb as plain text even when it has a "to"', () => {
    renderHeader({
      title: 'Дело №123',
      breadcrumbs: [{ label: 'Дело №123', to: '/cases/123' }],
    })
    expect(screen.queryByRole('link')).toBeNull()
    expect(screen.getByText('Дело №123', { selector: 'span' })).toHaveAttribute('aria-current', 'page')
  })

  it('renders description, eyebrow and actions when provided', () => {
    renderHeader({
      title: 'Дела',
      eyebrow: 'Обзор',
      description: 'Все активные дела',
      actions: <button type="button">Создать</button>,
    })
    expect(screen.getByText('Обзор')).toBeInTheDocument()
    expect(screen.getByText('Все активные дела')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Создать' })).toBeInTheDocument()
  })
})
