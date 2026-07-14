import { describe, expect, it } from 'vitest'
import { scrubPii } from './scrub'

describe('scrubPii', () => {
  it('masks emails keeping the domain', () => {
    expect(scrubPii('failed for iliachuvikin@gmail.com')).toBe('failed for il***@gmail.com')
  })

  it('redacts jwt access tokens', () => {
    const jwt = 'eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxMjMifQ.c2lnbmF0dXJlLXZhbHVl'
    expect(scrubPii(`token=${jwt}`)).toBe('token=[redacted]')
  })

  it('redacts bearer headers and russian phones', () => {
    expect(scrubPii('Authorization: Bearer abcdef0123456789')).toBe('Authorization: Bearer [redacted]')
    expect(scrubPii('звонить +7 999 123-45-67')).toBe('звонить [redacted]')
  })

  it('keeps technical text intact', () => {
    const message = 'Request /api/ai/cases/3f2a1b8c-0000-4c1a-9f1e-0a0b0c0d0e0f failed with 500'
    expect(scrubPii(message)).toBe(message)
  })

  it('passes through empty values', () => {
    expect(scrubPii(undefined)).toBeUndefined()
    expect(scrubPii('')).toBe('')
  })
})
