import { afterEach, beforeAll, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import i18n from '../../i18n'
import { CitationList, citationSummary } from './CitationList'
import type { CitationCheck, CitationCheckResult } from '../../types'

function citation(overrides: Partial<CitationCheck>): CitationCheck {
  return {
    raw: 'ст. 15 ГК РФ',
    type: 'STATUTE',
    normalized: 'ст. 15 ГК РФ',
    status: 'VERIFIED',
    detail: 'Действует',
    ...overrides,
  }
}

describe('citationSummary', () => {
  beforeAll(async () => {
    await i18n.changeLanguage('ru')
  })

  it('returns the empty-state message when total is 0', () => {
    const result: CitationCheckResult = {
      citations: [],
      total: 0,
      verified: 0,
      notFound: 0,
      outdated: 0,
      unverified: 0,
    }
    expect(citationSummary(result)).toBe(i18n.t('citation.noneFound'))
  })

  it('returns the interpolated summary when there are citations', () => {
    const result: CitationCheckResult = {
      citations: [citation({})],
      total: 3,
      verified: 1,
      notFound: 1,
      outdated: 1,
      unverified: 0,
    }
    expect(citationSummary(result)).toBe(
      i18n.t('citation.summary', { total: 3, verified: 1, notFound: 1, outdated: 1, unverified: 0 })
    )
  })
})

describe('CitationList', () => {
  beforeAll(async () => {
    await i18n.changeLanguage('ru')
  })

  afterEach(cleanup)

  it('renders nothing when there are no citations', () => {
    const { container } = render(
      <CitationList
        result={{ citations: [], total: 0, verified: 0, notFound: 0, outdated: 0, unverified: 0 }}
      />
    )
    expect(container.firstChild).toBeNull()
  })

  it('does not show the unverified warning when every statute is verified', () => {
    const citations = [citation({ status: 'VERIFIED' })]
    render(
      <CitationList
        result={{ citations, total: 1, verified: 1, notFound: 0, outdated: 0, unverified: 0 }}
      />
    )
    expect(screen.queryByText(i18n.t('citation.unverifiedWarning'))).toBeNull()
  })

  it('shows the unverified warning when a statute citation is not verified', () => {
    const citations = [citation({ type: 'STATUTE', status: 'OUTDATED' })]
    render(
      <CitationList
        result={{ citations, total: 1, verified: 0, notFound: 0, outdated: 1, unverified: 0 }}
      />
    )
    expect(screen.getByText(i18n.t('citation.unverifiedWarning'))).toBeInTheDocument()
  })

  it('does not warn for an unverified court case citation (only statutes count)', () => {
    const citations = [citation({ type: 'COURT_CASE', status: 'NOT_FOUND' })]
    render(
      <CitationList
        result={{ citations, total: 1, verified: 0, notFound: 1, outdated: 0, unverified: 0 }}
      />
    )
    expect(screen.queryByText(i18n.t('citation.unverifiedWarning'))).toBeNull()
  })

  it('renders one row per citation with its raw text and detail', () => {
    const citations = [
      citation({ raw: 'ст. 15 ГК РФ', detail: 'Действует' }),
      citation({ raw: 'ст. 20 ГК РФ', detail: 'Утратила силу', status: 'OUTDATED' }),
    ]
    render(
      <CitationList
        result={{ citations, total: 2, verified: 1, notFound: 0, outdated: 1, unverified: 0 }}
      />
    )
    expect(screen.getByText(/ст\. 15 ГК РФ/)).toBeInTheDocument()
    expect(screen.getByText(/ст\. 20 ГК РФ/)).toBeInTheDocument()
    expect(screen.getByText('Действует')).toBeInTheDocument()
    expect(screen.getByText('Утратила силу')).toBeInTheDocument()
  })
})
