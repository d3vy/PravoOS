import { describe, expect, it } from 'vitest'
import { readTotal } from './pagination'

describe('readTotal', () => {
  it('parses the x-total-count header', () => {
    expect(readTotal({ 'x-total-count': '42' }, 0)).toBe(42)
  })

  it('falls back when the header is missing', () => {
    expect(readTotal({}, 7)).toBe(7)
  })

  it('falls back when headers is undefined', () => {
    expect(readTotal(undefined, 7)).toBe(7)
  })

  it('falls back when the header value is not a number', () => {
    expect(readTotal({ 'x-total-count': 'not-a-number' }, 3)).toBe(3)
  })

  it('falls back when the header value is an empty string', () => {
    expect(readTotal({ 'x-total-count': '' }, 5)).toBe(5)
  })

  it('parses zero correctly instead of treating it as falsy', () => {
    expect(readTotal({ 'x-total-count': '0' }, 99)).toBe(0)
  })

  it('truncates a fractional value like Number.parseInt', () => {
    expect(readTotal({ 'x-total-count': '10.9' }, 0)).toBe(10)
  })
})
