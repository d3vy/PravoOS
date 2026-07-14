interface ThrottleOptions {
  maxReports: number
  dedupeWindowMs: number
  now?: () => number
}

export class ReportThrottle {
  private readonly maxReports: number
  private readonly dedupeWindowMs: number
  private readonly now: () => number
  private readonly recentSignatures = new Map<string, number>()
  private reportCount = 0

  constructor({ maxReports, dedupeWindowMs, now = Date.now }: ThrottleOptions) {
    this.maxReports = maxReports
    this.dedupeWindowMs = dedupeWindowMs
    this.now = now
  }

  allow(signature: string): boolean {
    if (this.reportCount >= this.maxReports) {
      return false
    }
    const timestamp = this.now()
    const lastSeen = this.recentSignatures.get(signature)
    if (lastSeen !== undefined && timestamp - lastSeen < this.dedupeWindowMs) {
      return false
    }
    this.recentSignatures.set(signature, timestamp)
    this.reportCount += 1
    return true
  }
}
