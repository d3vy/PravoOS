import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { Logo } from './Logo'

afterEach(cleanup)

describe('Logo', () => {
  it('renders the wordmark by default', () => {
    const { container } = render(<Logo />)
    expect(container.textContent).toBe('PravoOS')
    expect(screen.getByText('OS')).toBeInTheDocument()
  })

  it('hides the wordmark when withWordmark is false', () => {
    const { container } = render(<Logo withWordmark={false} />)
    expect(container.textContent).toBe('')
    expect(screen.queryByText('OS')).toBeNull()
  })
})
