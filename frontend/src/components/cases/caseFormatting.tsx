import type { ContractRiskLevel, DiffChangeType } from '../../types'

export const RISK_LEVEL_META: Record<ContractRiskLevel, { label: string; tone: string }> = {
  HIGH: { label: 'Высокий', tone: 'border-red-300 text-red-700 bg-red-50 dark:border-red-500/40 dark:text-red-400 dark:bg-red-500/10' },
  MEDIUM: { label: 'Средний', tone: 'border-amber-300 text-amber-700 bg-amber-50 dark:border-amber-500/40 dark:text-amber-400 dark:bg-amber-500/10' },
  LOW: { label: 'Низкий', tone: 'border-light-border text-light-secondary bg-light-bg dark:border-dark-border dark:text-dark-secondary dark:bg-dark-bg' },
}

export const DIFF_TYPE_META: Record<DiffChangeType, { label: string; tone: string }> = {
  ADDED: { label: 'Добавлено', tone: 'text-emerald-600 dark:text-emerald-400' },
  REMOVED: { label: 'Удалено', tone: 'text-red-600 dark:text-red-400' },
  MODIFIED: { label: 'Изменено', tone: 'text-amber-600 dark:text-amber-400' },
}

export function riskScoreTone(score: number): string {
  if (score >= 66) return 'text-red-600 dark:text-red-400'
  if (score >= 33) return 'text-amber-600 dark:text-amber-400'
  return 'text-emerald-600 dark:text-emerald-400'
}

export const ALLOWED_DOCUMENT_EXTENSIONS = ['.pdf', '.docx']
export const DOCUMENT_POLLING_INTERVAL_MS = 5000
