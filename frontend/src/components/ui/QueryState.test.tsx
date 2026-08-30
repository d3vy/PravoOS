import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { QueryState } from './QueryState'

afterEach(cleanup)

describe('QueryState', () => {
  it('renders the spinner while loading', () => {
    render(<QueryState isLoading />)
    expect(screen.getByRole('status')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('renders the error message instead of the spinner when the query failed', () => {
    render(<QueryState isError errorMessage="Не удалось загрузить дела" />)
    expect(screen.getByRole('alert')).toHaveTextContent('Не удалось загрузить дела')
    expect(screen.queryByRole('status')).toBeNull()
  })

  it('prefers the loading branch when both flags are set', () => {
    render(<QueryState isLoading isError errorMessage="Не удалось загрузить дела" />)
    expect(screen.getByRole('status')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('falls back to the shared error text when no message is given', () => {
    render(<QueryState isError />)
    expect(screen.getByRole('alert')).toHaveTextContent('common.loadError')
  })

  it('renders nothing once the query settled', () => {
    const { container } = render(<QueryState />)
    expect(container).toBeEmptyDOMElement()
  })

  it('applies the requested spinner size', () => {
    render(<QueryState isLoading spinnerSize="lg" />)
    expect(screen.getByRole('status').className).toContain('w-10')
  })
})
