import { afterEach, beforeAll, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import i18n from '../../i18n'
import {
  ApplicationStatusBadge,
  Badge,
  CASE_STATUS_ORDER,
  CASE_STATUS_VARIANT,
  CaseStatusBadge,
  DocumentStatusBadge,
  caseStatusLabel,
} from './Badge'

describe('Badge', () => {
  beforeAll(async () => {
    await i18n.changeLanguage('ru')
  })

  afterEach(cleanup)

  it('renders children inside a variant-styled span', () => {
    render(<Badge variant="success">Готово</Badge>)
    const badge = screen.getByText('Готово')
    expect(badge.className).toContain('bg-success-soft')
  })

  it('maps each case status to its localized label', () => {
    for (const status of CASE_STATUS_ORDER) {
      expect(caseStatusLabel(status)).toBe(i18n.t(`status.case.${status}`))
    }
  })

  it('renders CaseStatusBadge with the variant from CASE_STATUS_VARIANT', () => {
    render(<CaseStatusBadge status="CLOSED_LOST" />)
    const badge = screen.getByText(i18n.t('status.case.CLOSED_LOST'))
    expect(CASE_STATUS_VARIANT.CLOSED_LOST).toBe('danger')
    expect(badge.className).toContain('bg-danger-soft')
  })

  it('renders ApplicationStatusBadge label per status', () => {
    render(<ApplicationStatusBadge status="APPROVED" />)
    expect(screen.getByText(i18n.t('status.application.APPROVED'))).toBeInTheDocument()
  })

  it('renders DocumentStatusBadge label per status', () => {
    render(<DocumentStatusBadge status="FAILED" />)
    expect(screen.getByText(i18n.t('status.document.FAILED'))).toBeInTheDocument()
  })
})
