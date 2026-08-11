import apiClient, { multipartRequest, refreshSession } from './client'
import { MAX_PAGE_SIZE } from './pagination'
import { useAuthStore } from '../store/authStore'
import i18n from '../i18n'
import type {
  ChatRequest,
  ChatResponse,
  ConversationResponse,
  DocumentResponse,
  DocumentUploadResponse,
  MessageResponse,
  RateRequest,
} from '../types'

const baseURL = import.meta.env.VITE_API_URL || ''
const genericStreamError = (): string => i18n.t('chat.streamError')

export interface ChatStreamCallbacks {
  onToken: (token: string) => void
  onDone: (data: ChatResponse) => void
  onError: (message: string) => void
}

export async function streamMessage(
  data: ChatRequest,
  callbacks: ChatStreamCallbacks,
  signal?: AbortSignal
): Promise<void> {
  try {
    await runStream(data, callbacks, false, signal)
  } catch {
    if (signal?.aborted) return
    callbacks.onError(genericStreamError())
  }
}

async function runStream(
  data: ChatRequest,
  callbacks: ChatStreamCallbacks,
  isRetry: boolean,
  signal?: AbortSignal
): Promise<void> {
  const token = useAuthStore.getState().accessToken
  const response = await fetch(`${baseURL}/api/ai/chat/stream`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(data),
    signal,
  })

  if (response.status === 401 && !isRetry) {
    await refreshSession()
    await runStream(data, callbacks, true, signal)
    return
  }

  if (!response.ok || !response.body) {
    callbacks.onError(await extractErrorMessage(response))
    return
  }

  await consumeEventStream(response.body, callbacks, signal)
}

async function extractErrorMessage(response: Response): Promise<string> {
  try {
    const body = await response.json()
    if (body && typeof body.message === 'string') return body.message
  } catch {
    /* non-JSON body */
  }
  return genericStreamError()
}

async function consumeEventStream(
  body: ReadableStream<Uint8Array>,
  callbacks: ChatStreamCallbacks,
  signal?: AbortSignal
): Promise<void> {
  const reader = body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  for (;;) {
    if (signal?.aborted) {
      await reader.cancel().catch(() => undefined)
      return
    }
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    let separatorIdx: number
    while ((separatorIdx = buffer.indexOf('\n\n')) !== -1) {
      const rawEvent = buffer.slice(0, separatorIdx)
      buffer = buffer.slice(separatorIdx + 2)
      dispatchEvent(rawEvent, callbacks)
    }
  }
}

function dispatchEvent(rawEvent: string, callbacks: ChatStreamCallbacks): void {
  let eventName = 'message'
  const dataLines: string[] = []
  for (const line of rawEvent.split('\n')) {
    if (line.startsWith('event:')) eventName = line.slice('event:'.length).trim()
    else if (line.startsWith('data:')) dataLines.push(line.slice('data:'.length).replace(/^ /, ''))
  }
  if (dataLines.length === 0) return

  const payload = dataLines.join('\n')
  try {
    if (eventName === 'token') callbacks.onToken((JSON.parse(payload) as { content: string }).content)
    else if (eventName === 'done') callbacks.onDone(JSON.parse(payload) as ChatResponse)
    else if (eventName === 'error') callbacks.onError((JSON.parse(payload) as { message: string }).message)
  } catch {
    callbacks.onError(genericStreamError())
  }
}

export const chatApi = {
  sendMessage: async (data: ChatRequest): Promise<ChatResponse> => {
    const response = await apiClient.post<ChatResponse>('/api/ai/chat', data)
    return response.data
  },

  getConversations: async (
    q?: string,
    caseId?: string,
    documentId?: string
  ): Promise<ConversationResponse[]> => {
    const response = await apiClient.get<ConversationResponse[]>('/api/ai/conversations', {
      params: {
        ...(q ? { q } : {}),
        ...(caseId ? { caseId } : {}),
        ...(documentId ? { documentId } : {}),
        page: 0,
        size: MAX_PAGE_SIZE,
      },
    })
    return response.data
  },

  getMessages: async (conversationId: string): Promise<MessageResponse[]> => {
    const response = await apiClient.get<MessageResponse[]>(
      `/api/ai/conversations/${conversationId}/messages`,
      { params: { page: 0, size: MAX_PAGE_SIZE } }
    )
    return response.data
  },

  rateMessage: async (messageId: string, data: RateRequest): Promise<MessageResponse> => {
    const response = await apiClient.post<MessageResponse>(`/api/ai/messages/${messageId}/rate`, data)
    return response.data
  },

  getAttachments: async (): Promise<DocumentResponse[]> => {
    const response = await apiClient.get<DocumentResponse[]>('/api/ai/chat/attachments')
    return response.data
  },

  uploadAttachment: async (file: File): Promise<DocumentUploadResponse> => {
    const formData = new FormData()
    formData.append('file', file)
    const response = await apiClient.post<DocumentUploadResponse>('/api/ai/chat/attachments', formData, multipartRequest)
    return response.data
  },
}
