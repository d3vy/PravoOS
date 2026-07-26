import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import i18n from '../../i18n'
import { DocumentInsightPanel } from './DocumentInsightPanel'
import { chatApi, streamMessage } from '../../api/chat'
import { documentsApi } from '../../api/documents'
import type { DocumentInsightResponse } from '../../types'

vi.mock('../../api/chat', () => ({
  chatApi: {
    getConversations: vi.fn(),
    getMessages: vi.fn(),
    rateMessage: vi.fn(),
  },
  streamMessage: vi.fn(),
}))

vi.mock('../../api/documents', () => ({
  documentsApi: {
    getInsight: vi.fn(),
    regenerateInsight: vi.fn(),
  },
}))

vi.mock('../../api/citations', () => ({
  citationsApi: { checkText: vi.fn() },
}))

const DOCUMENT_ID = 'doc-1'

const mockedChatApi = vi.mocked(chatApi)
const mockedStreamMessage = vi.mocked(streamMessage)
const mockedDocumentsApi = vi.mocked(documentsApi)

function insight(overrides: Partial<DocumentInsightResponse> = {}): DocumentInsightResponse {
  return {
    documentId: DOCUMENT_ID,
    title: 'Договор поставки',
    status: 'READY',
    summaryStatus: 'READY',
    summary: 'Договор поставки товара между ООО «А» и ООО «Б».',
    keyPoints: ['Срок поставки — 30 дней', 'Штраф 0,1% за день просрочки'],
    generatedAt: new Date().toISOString(),
    ...overrides,
  }
}

function renderPanel(): void {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <DocumentInsightPanel documentId={DOCUMENT_ID} />
    </QueryClientProvider>
  )
}

beforeEach(async () => {
  await i18n.changeLanguage('ru')
  mockedChatApi.getConversations.mockResolvedValue([])
  mockedChatApi.getMessages.mockResolvedValue([])
  mockedStreamMessage.mockResolvedValue(undefined)
  mockedDocumentsApi.getInsight.mockResolvedValue(insight())
})

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

describe('DocumentInsightPanel', () => {
  it('shows the summary and its key points', async () => {
    renderPanel()

    expect(await screen.findByText(/Договор поставки товара/)).toBeInTheDocument()
    expect(screen.getByText('Срок поставки — 30 дней')).toBeInTheDocument()
    expect(screen.getByText('Штраф 0,1% за день просрочки')).toBeInTheDocument()
  })

  it('offers generation when no summary exists yet', async () => {
    mockedDocumentsApi.getInsight.mockResolvedValue(
      insight({ summaryStatus: 'NONE', summary: null, keyPoints: [], generatedAt: null })
    )
    mockedDocumentsApi.regenerateInsight.mockResolvedValue(insight())

    renderPanel()

    const generateButton = await screen.findByRole('button', {
      name: i18n.t('documentSummary.generate'),
    })
    await userEvent.click(generateButton)

    await waitFor(() => {
      expect(mockedDocumentsApi.regenerateInsight).toHaveBeenCalledWith(DOCUMENT_ID)
    })
    expect(await screen.findByText(/Договор поставки товара/)).toBeInTheDocument()
  })

  it('reports a failed summary', async () => {
    mockedDocumentsApi.getInsight.mockResolvedValue(
      insight({ summaryStatus: 'FAILED', summary: null, keyPoints: [] })
    )

    renderPanel()

    expect(await screen.findByText(i18n.t('documentSummary.failed'))).toBeInTheDocument()
  })

  it('loads only conversations of this document', async () => {
    renderPanel()

    await waitFor(() => {
      expect(mockedChatApi.getConversations).toHaveBeenCalledWith(undefined, undefined, DOCUMENT_ID)
    })
  })

  it('sends the question scoped to the document', async () => {
    renderPanel()

    const input = await screen.findByPlaceholderText(i18n.t('documentChat.placeholder'))
    await userEvent.type(input, 'Какой срок поставки?')
    await userEvent.click(screen.getByRole('button', { name: i18n.t('documentChat.send') }))

    await waitFor(() => {
      expect(mockedStreamMessage).toHaveBeenCalledWith(
        expect.objectContaining({
          message: 'Какой срок поставки?',
          documentId: DOCUMENT_ID,
          caseId: undefined,
        }),
        expect.anything()
      )
    })
  })
})
