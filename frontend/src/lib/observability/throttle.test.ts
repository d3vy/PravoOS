import { describe, expect, it } from 'vitest'
import { ReportThrottle } from './throttle'

describe('ReportThrottle', () => {
  it('drops duplicates inside the dedupe window', () => {
    let clock = 0
    const throttle = new ReportThrottle({ maxReports: 10, dedupeWindowMs: 1000, now: () => clock })

    expect(throttle.allow('boom')).toBe(true)
    clock = 999
    expect(throttle.allow('boom')).toBe(false)
    clock = 1000
    expect(throttle.allow('boom')).toBe(true)
  })

  it('lets distinct signatures through', () => {
    const throttle = new ReportThrottle({ maxReports: 10, dedupeWindowMs: 1000, now: () => 0 })

    expect(throttle.allow('a')).toBe(true)
    expect(throttle.allow('b')).toBe(true)
  })

  it('caps the number of reports per session', () => {
    let clock = 0
    const throttle = new ReportThrottle({ maxReports: 2, dedupeWindowMs: 0, now: () => (clock += 10) })

    expect(throttle.allow('a')).toBe(true)
    expect(throttle.allow('b')).toBe(true)
    expect(throttle.allow('c')).toBe(false)
  })
})
