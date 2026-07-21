import type { ContractRiskLevel, DiffChangeType } from '../../types'

export const RISK_LEVEL_META: Record<ContractRiskLevel, { labelKey: string; tone: string }> = {
  HIGH: { labelKey: 'status.risk.HIGH', tone: 'border-red-300 text-red-700 bg-red-50 dark:border-red-500/40 dark:text-red-400 dark:bg-red-500/10' },
  MEDIUM: { labelKey: 'status.risk.MEDIUM', tone: 'border-amber-300 text-amber-700 bg-amber-50 dark:border-amber-500/40 dark:text-amber-400 dark:bg-amber-500/10' },
  LOW: { labelKey: 'status.risk.LOW', tone: 'border-light-border text-light-secondary bg-light-bg dark:border-dark-border dark:text-dark-secondary dark:bg-dark-bg' },
}

export const DIFF_TYPE_META: Record<DiffChangeType, { labelKey: string; tone: string }> = {
  ADDED: { labelKey: 'status.diff.ADDED', tone: 'text-emerald-600 dark:text-emerald-400' },
  REMOVED: { labelKey: 'status.diff.REMOVED', tone: 'text-red-600 dark:text-red-400' },
  MODIFIED: { labelKey: 'status.diff.MODIFIED', tone: 'text-amber-600 dark:text-amber-400' },
}

export function riskScoreTone(score: number): string {
  if (score >= 66) return 'text-red-600 dark:text-red-400'
  if (score >= 33) return 'text-amber-600 dark:text-amber-400'
  return 'text-emerald-600 dark:text-emerald-400'
}

export const ALLOWED_DOCUMENT_EXTENSIONS = ['.pdf', '.docx']
export const DOCUMENT_POLLING_INTERVAL_MS = 5000
