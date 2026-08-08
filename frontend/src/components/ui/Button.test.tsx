import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Button } from './Button'

afterEach(cleanup)

describe('Button', () => {
  it('is enabled by default and fires onClick', async () => {
    const onClick = vi.fn()
    render(<Button onClick={onClick}>Save</Button>)
    const button = screen.getByRole('button', { name: 'Save' })
    expect(button).not.toBeDisabled()
    await userEvent.click(button)
    expect(onClick).toHaveBeenCalledTimes(1)
  })

  it('disables the button and sets aria-busy while loading', () => {
    render(<Button loading>Save</Button>)
    const button = screen.getByRole('button')
    expect(button).toBeDisabled()
    expect(button).toHaveAttribute('aria-busy', 'true')
  })

  it('respects the disabled prop independent of loading', () => {
    render(<Button disabled>Save</Button>)
    const button = screen.getByRole('button')
    expect(button).toBeDisabled()
    expect(button).toHaveAttribute('aria-busy', 'false')
  })

  it('hides children visually but keeps them in the DOM while loading', () => {
    render(<Button loading>Save</Button>)
    const label = screen.getByText('Save')
    expect(label.className).toContain('invisible')
  })
})
