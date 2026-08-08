import { afterEach, beforeAll, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import i18n from '../../i18n'
import { SignatureStatusBadge, SIGNATURE_STATUS_CLASS } from './SignatureStatusBadge'
import type { SignatureStatus } from '../../types'

describe('SignatureStatusBadge', () => {
  beforeAll(async () => {
    await i18n.changeLanguage('ru')
  })

  afterEach(cleanup)

  it.each(Object.keys(SIGNATURE_STATUS_CLASS) as SignatureStatus[])(
    'renders the localized label and tone class for %s',
    (status) => {
      render(<SignatureStatusBadge status={status} />)
      const badge = screen.getByText(i18n.t(`status.signature.${status}`))
      for (const cls of SIGNATURE_STATUS_CLASS[status].split(' ')) {
        expect(badge.className).toContain(cls)
      }
    }
  )
})
