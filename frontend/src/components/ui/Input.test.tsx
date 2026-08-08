import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { Input } from './Input'

afterEach(cleanup)

describe('Input', () => {
  it('associates the label with the input via a generated id', () => {
    render(<Input label="Email" />)
    const input = screen.getByLabelText('Email')
    expect(input).toBeInTheDocument()
  })

  it('uses the provided id instead of generating one', () => {
    render(<Input label="Email" id="email-field" />)
    expect(screen.getByLabelText('Email')).toHaveAttribute('id', 'email-field')
  })

  it('marks the input invalid and wires aria-describedby to the error message when error is set', () => {
    render(<Input label="Email" id="email-field" error="Обязательное поле" />)
    const input = screen.getByLabelText('Email')
    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(input).toHaveAttribute('aria-describedby', 'email-field-error')
    expect(screen.getByText('Обязательное поле')).toHaveAttribute('id', 'email-field-error')
  })

  it('has no aria-invalid or error text without an error', () => {
    render(<Input label="Email" />)
    const input = screen.getByLabelText('Email')
    expect(input).not.toHaveAttribute('aria-invalid')
    expect(input).not.toHaveAttribute('aria-describedby')
  })

  it('renders the rightElement and reserves space for it', () => {
    render(<Input label="Password" rightElement={<button type="button">show</button>} />)
    expect(screen.getByRole('button', { name: 'show' })).toBeInTheDocument()
    expect(screen.getByLabelText('Password').className).toContain('pr-11')
  })
})
