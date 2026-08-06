import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import i18n from '../../i18n'
import { CaseSignatureSection } from './CaseSignatureSection'
import { signaturesApi } from '../../api/signatures'
import { useAuthStore } from '../../store/authStore'
import type { DocumentResponse, SignatureRequestResponse } from '../../types'

vi.mock('../../api/signatures', () => ({
  signaturesApi: {
    listByCase: vi.fn(),
    create: vi.fn(),
    cancel: vi.fn(),
    sign: vi.fn(),
    signWithCms: vi.fn(),
    decline: vi.fn(),
    downloadProtocol: vi.fn(),
    downloadSignatureFile: vi.fn(),
  },
}))

const CASE_ID = 'case-1'
const LAWYER_ID = 'lawyer-1'

const mockedSignaturesApi = vi.mocked(signaturesApi)

function document(overrides: Partial<DocumentResponse> = {}): DocumentResponse {
  return {
    id: 'doc-1',
    title: 'Договор оказания услуг',
    status: 'READY',
    visibleToClient: false,
    ...overrides,
  } as DocumentResponse
}

function signature(overrides: Partial<SignatureRequestResponse> = {}): SignatureRequestResponse {
  return {
    id: 'sig-1',
    documentId: 'doc-1',
    caseId: CASE_ID,
    provider: 'SIMPLE',
    signerRole: 'LAWYER',
    signerLawyerId: LAWYER_ID,
    status: 'PENDING',
    documentHash: 'abc',
    message: null,
    signerName: null,
    signerIp: null,
    signedAt: null,
    declineReason: null,
    expiresAt: null,
    createdAt: new Date().toISOString(),
    certificateSubject: null,
    certificateIssuer: null,
    certificateSerial: null,
    certificateValidFrom: null,
    certificateValidTo: null,
    signatureAlgorithm: null,
    declaredSigningTime: null,
    hasSignatureFile: false,
    ...overrides,
  }
}

function renderSection(documents: DocumentResponse[] = [document()]): void {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <CaseSignatureSection caseId={CASE_ID} documents={documents} />
    </QueryClientProvider>
  )
}

beforeEach(async () => {
  await i18n.changeLanguage('ru')
  useAuthStore.setState({
    accessToken: 'token',
    user: { userId: LAWYER_ID, email: 'lawyer@pravoos.ru', role: 'LAWYER' },
  })
  mockedSignaturesApi.listByCase.mockResolvedValue([])
})

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

describe('CaseSignatureSection', () => {
  it('creates a lawyer-side request for a document hidden from the client', async () => {
    renderSection()
    const user = userEvent.setup()

    await user.selectOptions(
      screen.getByLabelText(i18n.t('signature.signerRoleLabel')),
      'LAWYER'
    )
    await user.selectOptions(screen.getByLabelText(i18n.t('signature.documentLabel')), 'doc-1')
    await user.click(screen.getByRole('button', { name: i18n.t('signature.createLawyerRequest') }))

    await waitFor(() => {
      expect(mockedSignaturesApi.create).toHaveBeenCalledWith(
        CASE_ID,
        expect.objectContaining({ documentId: 'doc-1', signerRole: 'LAWYER' })
      )
    })
  })

  it('hides documents invisible to the client when the client signs', async () => {
    renderSection()

    expect(screen.getByText(i18n.t('signature.noDocsHint'))).toBeInTheDocument()
  })

  it('lets the designated lawyer sign a pending request', async () => {
    mockedSignaturesApi.listByCase.mockResolvedValue([signature()])
    renderSection()
    const user = userEvent.setup()

    await user.type(
      await screen.findByLabelText(i18n.t('signature.signerNameLabel')),
      'Смирнова Анна'
    )
    await user.click(screen.getByLabelText(i18n.t('signature.lawyerConsent')))
    await user.click(screen.getByRole('button', { name: i18n.t('signature.signAction') }))

    await waitFor(() => {
      expect(mockedSignaturesApi.sign).toHaveBeenCalledWith(CASE_ID, 'sig-1', 'Смирнова Анна')
    })
  })

  it('does not offer signing for a request designated to another lawyer', async () => {
    mockedSignaturesApi.listByCase.mockResolvedValue([signature({ signerLawyerId: 'other' })])
    renderSection()

    expect(await screen.findByText(i18n.t('signature.signerRoleLawyerShort'))).toBeInTheDocument()
    expect(screen.queryByText(i18n.t('signature.yourTurn'))).not.toBeInTheDocument()
  })

  it('does not offer signing for a client-side request', async () => {
    mockedSignaturesApi.listByCase.mockResolvedValue([
      signature({ signerRole: 'CLIENT', signerLawyerId: null }),
    ])
    renderSection()

    expect(await screen.findByText(i18n.t('signature.signerRoleClientShort'))).toBeInTheDocument()
    expect(screen.queryByText(i18n.t('signature.yourTurn'))).not.toBeInTheDocument()
  })
})
