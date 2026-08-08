import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { MockInstance } from 'vitest'

const { getMock, postMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
  postMock: vi.fn(),
}))

vi.mock('./client', async () => ({
  ...(await vi.importActual<typeof import('./client')>('./client')),
  default: {
    get: getMock,
    post: postMock,
    patch: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}))

import { multipartRequest } from './client'
import { casesApi } from './cases'

describe('casesApi.list', () => {
  beforeEach(() => {
    getMock.mockReset()
    getMock.mockResolvedValue({ data: [{ id: '1' }], headers: { 'x-total-count': '5' } })
  })

  it('sends only page and size by default', async () => {
    await casesApi.list()
    expect(getMock).toHaveBeenCalledWith('/api/ai/cases', { params: { page: 0, size: 20 } })
  })

  it('includes status, trimmed query and orgId when provided', async () => {
    await casesApi.list('IN_PROGRESS', '  contract dispute  ', 2, 10, 'org-1')
    expect(getMock).toHaveBeenCalledWith('/api/ai/cases', {
      params: { page: 2, size: 10, status: 'IN_PROGRESS', q: 'contract dispute', orgId: 'org-1' },
    })
  })

  it('omits the q param when it is blank', async () => {
    await casesApi.list(undefined, '   ')
    expect(getMock).toHaveBeenCalledWith('/api/ai/cases', { params: { page: 0, size: 20 } })
  })

  it('maps the response into a Page with total from the header', async () => {
    const page = await casesApi.list()
    expect(page).toEqual({ items: [{ id: '1' }], total: 5 })
  })

  it('falls back total to items length when the header is missing', async () => {
    getMock.mockResolvedValue({ data: [{ id: '1' }, { id: '2' }], headers: {} })
    const page = await casesApi.list()
    expect(page.total).toBe(2)
  })
})

describe('casesApi.uploadDocument', () => {
  beforeEach(() => {
    postMock.mockReset()
    postMock.mockResolvedValue({ data: { id: 'doc-1' } })
  })

  it('sends the file and title as multipart form data', async () => {
    const file = new File(['content'], 'contract.pdf', { type: 'application/pdf' })
    await casesApi.uploadDocument('case-1', file, 'Contract')

    expect(postMock).toHaveBeenCalledWith(
      '/api/ai/cases/case-1/documents',
      expect.any(FormData),
      multipartRequest
    )
    const formData = postMock.mock.calls[0][1] as FormData
    expect(formData.get('file')).toBe(file)
    expect(formData.get('title')).toBe('Contract')
  })

  it('gives uploads a timeout long enough for a 50 MB file', async () => {
    expect(multipartRequest.headers['Content-Type']).toBe('multipart/form-data')
    expect(multipartRequest.timeout).toBeGreaterThan(60000)
  })
})

describe('blob download helpers', () => {
  const createObjectURL = vi.fn(() => 'blob:mock-url')
  const revokeObjectURL = vi.fn()
  let clickSpy: MockInstance<[], void>

  beforeEach(() => {
    getMock.mockReset()
    createObjectURL.mockClear()
    revokeObjectURL.mockClear()
    Object.defineProperty(window.URL, 'createObjectURL', { value: createObjectURL, configurable: true })
    Object.defineProperty(window.URL, 'revokeObjectURL', { value: revokeObjectURL, configurable: true })
    clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)
  })

  afterEach(() => {
    clickSpy.mockRestore()
  })

  it('downloadDraft requests a blob and triggers a .docx download', async () => {
    const blob = new Blob(['doc'])
    getMock.mockResolvedValue({ data: blob })

    await casesApi.downloadDraft('draft-1', 'my-draft')

    expect(getMock).toHaveBeenCalledWith('/api/ai/drafts/draft-1/download', { responseType: 'blob' })
    expect(createObjectURL).toHaveBeenCalledWith(blob)
    expect(clickSpy).toHaveBeenCalledTimes(1)
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:mock-url')
  })

  it('exportCase requests the given format and names the file accordingly', async () => {
    const blob = new Blob(['doc'])
    getMock.mockResolvedValue({ data: blob })

    await casesApi.exportCase('case-1', 'pdf', 'export-name')

    expect(getMock).toHaveBeenCalledWith('/api/ai/cases/case-1/export', {
      params: { format: 'pdf' },
      responseType: 'blob',
    })
    expect(createObjectURL).toHaveBeenCalledWith(blob)
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:mock-url')
  })
})
