import { render } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { LegalMarkdown } from './LegalMarkdown'

function renderMarkdown(source: string): HTMLElement {
  return render(
    <MemoryRouter>
      <LegalMarkdown source={source} />
    </MemoryRouter>
  ).container
}

describe('LegalMarkdown', () => {
  it('turns a reference to a published document into a route link', () => {
    const container = renderMarkdown('См. [Политику cookie](politika-cookie.md).')

    const link = container.querySelector('a')
    expect(link?.textContent).toBe('Политику cookie')
    expect(link).toHaveAttribute('href', '/legal/cookies')
  })

  it('renders a reference to an internal document as plain text', () => {
    const container = renderMarkdown('См. [Реестр обработчиков](reestr-obrabotchikov.md).')

    expect(container.querySelector('a')).toBeNull()
    expect(container.textContent).toContain('Реестр обработчиков')
  })

  it('renders consecutive quote lines as a single blockquote', () => {
    const container = renderMarkdown('> Первая строка\n> вторая строка\n\nОбычный абзац.')

    const quotes = container.querySelectorAll('blockquote')
    expect(quotes).toHaveLength(1)
    expect(quotes[0].textContent).toBe('Первая строка вторая строка')
    expect(container.querySelector('p')?.textContent).toBe('Обычный абзац.')
  })
})
