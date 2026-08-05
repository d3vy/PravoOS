import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import i18n from '../i18n'
import { ApplicationForm, type ApplicationFormData } from './ApplicationForm'

const FILLED_FORM: ApplicationFormData = {
  fullName: 'Иванов Иван Иванович',
  email: 'ivanov@lawfirm.ru',
  password: 'secret123',
  specialization: 'Корпоративное право',
  phone: '+7 (999) 000-00-00',
  personalDataConsent: false,
  crossBorderConsent: false,
  marketingConsent: false,
}

function renderForm(onSubmit: (data: ApplicationFormData) => Promise<void>): void {
  render(
    <MemoryRouter>
      <ApplicationForm
        initialValues={FILLED_FORM}
        passwordRequired
        consentsRequired
        submitLabel="Отправить"
        onSubmit={onSubmit}
      />
    </MemoryRouter>,
  )
}

describe('ApplicationForm consents', () => {
  beforeAll(async () => {
    await i18n.changeLanguage('ru')
  })

  afterEach(cleanup)

  it('blocks submission until mandatory consents are given', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    renderForm(onSubmit)

    await userEvent.click(screen.getByRole('button', { name: 'Отправить' }))

    expect(onSubmit).not.toHaveBeenCalled()
    expect(
      screen.getByText(i18n.t('applicationForm.consentPersonalDataRequired')),
    ).toBeInTheDocument()
    expect(
      screen.getByText(i18n.t('applicationForm.consentCrossBorderRequired')),
    ).toBeInTheDocument()
  })

  it('submits consent flags once both mandatory boxes are ticked', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    renderForm(onSubmit)

    await userEvent.click(screen.getByRole('checkbox', { name: /персональных данных/i }))
    await userEvent.click(screen.getByRole('checkbox', { name: /трансграничную передачу/i }))
    await userEvent.click(screen.getByRole('button', { name: 'Отправить' }))

    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1))
    expect(onSubmit.mock.calls[0][0]).toMatchObject({
      personalDataConsent: true,
      crossBorderConsent: true,
      marketingConsent: false,
    })
  })

  it('hides consents when the form is used for editing an application', async () => {
    render(
      <MemoryRouter>
        <ApplicationForm
          initialValues={FILLED_FORM}
          passwordRequired={false}
          submitLabel="Сохранить"
          onSubmit={vi.fn()}
        />
      </MemoryRouter>,
    )

    expect(screen.queryByRole('checkbox')).toBeNull()
  })
})
