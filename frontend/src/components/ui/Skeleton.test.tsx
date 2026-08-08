import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render } from '@testing-library/react'
import { SkeletonCardGrid, SkeletonList } from './Skeleton'

afterEach(cleanup)

describe('SkeletonCardGrid', () => {
  it('renders the default count of cards', () => {
    const { container } = render(<SkeletonCardGrid />)
    expect(container.querySelectorAll(':scope > div > div').length).toBe(6)
  })

  it('renders a custom count of cards', () => {
    const { container } = render(<SkeletonCardGrid count={2} />)
    expect(container.querySelectorAll(':scope > div > div').length).toBe(2)
  })
})

describe('SkeletonList', () => {
  it('renders the default count of rows', () => {
    const { container } = render(<SkeletonList />)
    expect(container.querySelectorAll(':scope > div > div').length).toBe(4)
  })

  it('renders a custom count of rows', () => {
    const { container } = render(<SkeletonList count={1} />)
    expect(container.querySelectorAll(':scope > div > div').length).toBe(1)
  })
})
