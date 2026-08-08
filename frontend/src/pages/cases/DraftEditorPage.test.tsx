import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import DraftEditorPage from './DraftEditorPage'
import { casesApi } from '../../api/cases'
import type { CaseDraftDto, CaseDraftVersionDto } from '../../types'

vi.mock('../../api/cases', () => ({
  casesApi: {
    getDraft: vi.fn(),
    updateDraft: vi.fn(),
    refineDraft: vi.fn(),
    downloadDraft: vi.fn(),
    getDraftVersions: vi.fn(),
    restoreDraftVersion: vi.fn(),
  },
}))

function renderPage(caseId = 'case-1', draftId = 'draft-1'): void {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/cases/${caseId}/drafts/${draftId}`]}>
        <Routes>
          <Route path="/cases/:caseId/drafts/:draftId" element={<DraftEditorPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  )
}

function makeDraft(overrides: Partial<CaseDraftDto> = {}): CaseDraftDto {
  return {
    id: 'draft-1',
    caseId: 'case-1',
    draftType: 'CLAIM',
    draftTypeName: 'Claim',
    title: 'Исковое заявление',
    content: 'Исходный текст черновика.',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: null,
    ...overrides,
  }
}

function makeVersion(overrides: Partial<CaseDraftVersionDto> = {}): CaseDraftVersionDto {
  return {
    id: 'version-1',
    versionNo: 1,
    note: null,
    content: 'Старая версия',
    createdAt: '2026-01-02T00:00:00Z',
    ...overrides,
  }
}

describe('DraftEditorPage', () => {
  beforeEach(() => {
    vi.mocked(casesApi.getDraft).mockReset()
    vi.mocked(casesApi.updateDraft).mockReset()
    vi.mocked(casesApi.refineDraft).mockReset()
    vi.mocked(casesApi.downloadDraft).mockReset().mockResolvedValue(undefined)
    vi.mocked(casesApi.getDraftVersions).mockReset().mockResolvedValue([])
    vi.mocked(casesApi.restoreDraftVersion).mockReset()
  })

  afterEach(() => {
    cleanup()
  })

  it('shows a not-found message when the draft does not exist', async () => {
    vi.mocked(casesApi.getDraft).mockRejectedValue(new Error('404'))
    renderPage()
    expect(await screen.findByText('Draft not found')).toBeInTheDocument()
    expect(screen.getByText('← To case')).toHaveAttribute('href', '/cases/case-1')
  })

  it('loads the draft content into the editor and disables save until dirty', async () => {
    vi.mocked(casesApi.getDraft).mockResolvedValue(makeDraft())
    renderPage()

    const textarea = await screen.findByDisplayValue('Исходный текст черновика.')
    expect(screen.getByRole('button', { name: 'Save version' })).toBeDisabled()

    await userEvent.type(textarea, ' доп.')
    expect(screen.getByRole('button', { name: 'Save version' })).toBeEnabled()
    expect(screen.getByText('You have unsaved changes.')).toBeInTheDocument()
  })

  it('saves a new version with a note and clears the note field', async () => {
    vi.mocked(casesApi.getDraft).mockResolvedValue(makeDraft())
    vi.mocked(casesApi.updateDraft).mockResolvedValue(makeDraft({ content: 'Изменённый текст.' }))
    const user = userEvent.setup()
    renderPage()

    const textarea = await screen.findByDisplayValue('Исходный текст черновика.')
    await user.clear(textarea)
    await user.type(textarea, 'Изменённый текст.')
    await user.type(screen.getByPlaceholderText('Edit description (optional)'), 'fix typo')
    await user.click(screen.getByRole('button', { name: 'Save version' }))

    await waitFor(() =>
      expect(casesApi.updateDraft).toHaveBeenCalledWith('draft-1', { content: 'Изменённый текст.', note: 'fix typo' })
    )
    expect(screen.getByPlaceholderText('Edit description (optional)')).toHaveValue('')
  })

  it('shows a save-error message when saving fails', async () => {
    vi.mocked(casesApi.getDraft).mockResolvedValue(makeDraft())
    vi.mocked(casesApi.updateDraft).mockRejectedValue(new Error('boom'))
    const user = userEvent.setup()
    renderPage()

    const textarea = await screen.findByDisplayValue('Исходный текст черновика.')
    await user.type(textarea, '!')
    await user.click(screen.getByRole('button', { name: 'Save version' }))

    expect(await screen.findByText('Failed to save. Please try again.')).toBeInTheDocument()
  })

  it('downloads the draft as docx', async () => {
    vi.mocked(casesApi.getDraft).mockResolvedValue(makeDraft())
    const user = userEvent.setup()
    renderPage()
    await screen.findByDisplayValue('Исходный текст черновика.')

    await user.click(screen.getByRole('button', { name: 'Download .docx' }))

    await waitFor(() => expect(casesApi.downloadDraft).toHaveBeenCalledWith('draft-1', 'Исковое заявление'))
  })

  it('runs an AI preset refinement over the whole document and applies the suggestion', async () => {
    vi.mocked(casesApi.getDraft).mockResolvedValue(makeDraft())
    vi.mocked(casesApi.refineDraft).mockResolvedValue({ revisedText: 'Усиленный текст.' } as never)
    const user = userEvent.setup()
    renderPage()
    await screen.findByDisplayValue('Исходный текст черновика.')

    await user.click(screen.getByRole('button', { name: 'Strengthen wording' }))

    expect(await screen.findByText('Усиленный текст.')).toBeInTheDocument()
    expect(casesApi.refineDraft).toHaveBeenCalledWith('draft-1', {
      instruction:
        'Strengthen the legal wording, make the position more convincing and defensible while preserving the original meaning.',
      selectedText: undefined,
    })

    await user.click(screen.getByRole('button', { name: 'Apply' }))
    expect(await screen.findByDisplayValue('Усиленный текст.')).toBeInTheDocument()
  })

  it('rejects an AI suggestion without changing the editor content', async () => {
    vi.mocked(casesApi.getDraft).mockResolvedValue(makeDraft())
    vi.mocked(casesApi.refineDraft).mockResolvedValue({ revisedText: 'Другой вариант.' } as never)
    const user = userEvent.setup()
    renderPage()
    await screen.findByDisplayValue('Исходный текст черновика.')

    await user.click(screen.getByRole('button', { name: 'Simplify language' }))
    await screen.findByText('Другой вариант.')
    await user.click(screen.getByRole('button', { name: 'Reject' }))

    expect(screen.queryByText('Другой вариант.')).not.toBeInTheDocument()
    expect(screen.getByDisplayValue('Исходный текст черновика.')).toBeInTheDocument()
  })

  it('shows a refine-error message when the AI request fails', async () => {
    vi.mocked(casesApi.getDraft).mockResolvedValue(makeDraft())
    vi.mocked(casesApi.refineDraft).mockRejectedValue(new Error('boom'))
    const user = userEvent.setup()
    renderPage()
    await screen.findByDisplayValue('Исходный текст черновика.')

    await user.click(screen.getByRole('button', { name: 'Strengthen wording' }))

    expect(await screen.findByText('Failed to get the edit. Please try again.')).toBeInTheDocument()
  })

  it('sends a custom instruction only once typed', async () => {
    vi.mocked(casesApi.getDraft).mockResolvedValue(makeDraft())
    vi.mocked(casesApi.refineDraft).mockResolvedValue({ revisedText: 'Кастомный ответ.' } as never)
    const user = userEvent.setup()
    renderPage()
    await screen.findByDisplayValue('Исходный текст черновика.')

    expect(screen.getByRole('button', { name: 'Apply instruction' })).toBeDisabled()

    await user.type(
      screen.getByPlaceholderText(/Your own instruction/i),
      'Добавь пункт о неустойке'
    )
    expect(screen.getByRole('button', { name: 'Apply instruction' })).toBeEnabled()
    await user.click(screen.getByRole('button', { name: 'Apply instruction' }))

    await waitFor(() =>
      expect(casesApi.refineDraft).toHaveBeenCalledWith('draft-1', {
        instruction: 'Добавь пункт о неустойке',
        selectedText: undefined,
      })
    )
  })

  it('shows the version history panel and restores a version', async () => {
    vi.mocked(casesApi.getDraft).mockResolvedValue(makeDraft())
    vi.mocked(casesApi.getDraftVersions).mockResolvedValue([makeVersion()])
    vi.mocked(casesApi.restoreDraftVersion).mockResolvedValue(makeDraft({ content: 'Восстановленный текст.' }))
    const user = userEvent.setup()
    renderPage()
    await screen.findByDisplayValue('Исходный текст черновика.')

    await user.click(screen.getByRole('button', { name: 'Version history' }))
    expect(await screen.findByText('Version #1')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Restore' }))

    await waitFor(() => expect(casesApi.restoreDraftVersion).toHaveBeenCalledWith('draft-1', 'version-1'))
    expect(await screen.findByDisplayValue('Восстановленный текст.')).toBeInTheDocument()
  })

  it('shows the empty-versions placeholder', async () => {
    vi.mocked(casesApi.getDraft).mockResolvedValue(makeDraft())
    vi.mocked(casesApi.getDraftVersions).mockResolvedValue([])
    const user = userEvent.setup()
    renderPage()
    await screen.findByDisplayValue('Исходный текст черновика.')

    await user.click(screen.getByRole('button', { name: 'Version history' }))
    expect(await screen.findByText('No versions yet.')).toBeInTheDocument()
  })
})
