import type { AiActionProposal, ChatResponse, ChatToolStep, MessageResponse } from '../../types'
import type { LocalMessage } from '../../components/chat/ChatMessageBubble'

export interface OptimisticPair {
  baseId: string
  streamingId: string
  userMessage: LocalMessage
  streamingMessage: LocalMessage
}

export function createOptimisticPair(text: string): OptimisticPair {
  const baseId = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
  const streamingId = `loading-${baseId}`
  return {
    baseId,
    streamingId,
    userMessage: { id: `user-${baseId}`, role: 'USER', content: text },
    streamingMessage: {
      id: streamingId,
      role: 'ASSISTANT',
      content: '',
      isStreaming: true,
      toolSteps: [],
      proposals: [],
    },
  }
}

export function applyToken(
  messages: LocalMessage[],
  streamingId: string,
  token: string
): LocalMessage[] {
  return messages.map((message) =>
    message.id === streamingId ? { ...message, content: message.content + token } : message
  )
}

export function applyToolStep(
  messages: LocalMessage[],
  streamingId: string,
  step: ChatToolStep
): LocalMessage[] {
  return messages.map((message) =>
    message.id === streamingId
      ? { ...message, toolSteps: mergeToolStep(message.toolSteps ?? [], step) }
      : message
  )
}

function mergeToolStep(steps: ChatToolStep[], step: ChatToolStep): ChatToolStep[] {
  if (step.status === 'RUNNING') return [...steps, step]
  let index = -1
  for (let position = steps.length - 1; position >= 0; position--) {
    if (steps[position].name === step.name && steps[position].status === 'RUNNING') {
      index = position
      break
    }
  }
  if (index === -1) return [...steps, step]
  return steps.map((candidate, position) => (position === index ? step : candidate))
}

export function applyProposal(
  messages: LocalMessage[],
  streamingId: string,
  proposal: AiActionProposal
): LocalMessage[] {
  return messages.map((message) =>
    message.id === streamingId
      ? { ...message, proposals: mergeProposal(message.proposals ?? [], proposal) }
      : message
  )
}

export function applyProposalDecision(
  messages: LocalMessage[],
  proposal: AiActionProposal
): LocalMessage[] {
  return messages.map((message) =>
    message.proposals?.some((candidate) => candidate.id === proposal.id)
      ? { ...message, proposals: mergeProposal(message.proposals, proposal) }
      : message
  )
}

export function attachPendingProposals(
  messages: LocalMessage[],
  proposals: AiActionProposal[]
): LocalMessage[] {
  if (proposals.length === 0) return messages

  const messageIds = new Set(messages.map((message) => message.id))
  const byMessageId = new Map<string, AiActionProposal[]>()
  const orphaned: AiActionProposal[] = []
  for (const proposal of proposals) {
    if (proposal.messageId && messageIds.has(proposal.messageId)) {
      const bucket = byMessageId.get(proposal.messageId) ?? []
      bucket.push(proposal)
      byMessageId.set(proposal.messageId, bucket)
    } else {
      orphaned.push(proposal)
    }
  }

  const target = orphaned.length > 0 ? lastSettledAssistant(messages) : null
  if (byMessageId.size === 0 && !target) return messages

  return messages.map((message) => {
    const attached = byMessageId.get(message.id)
    if (attached) return { ...message, proposals: attached }
    if (target && message.id === target.id) return { ...message, proposals: orphaned }
    return message
  })
}

function mergeProposal(
  proposals: AiActionProposal[],
  proposal: AiActionProposal
): AiActionProposal[] {
  const index = proposals.findIndex((candidate) => candidate.id === proposal.id)
  if (index === -1) return [...proposals, proposal]
  return proposals.map((candidate, position) => (position === index ? proposal : candidate))
}

export function applyDone(
  messages: LocalMessage[],
  streamingId: string,
  response: ChatResponse,
  baseId: string
): LocalMessage[] {
  return messages.map((message) =>
    message.id === streamingId
      ? {
          id: response.messageId ?? `assistant-${baseId}`,
          role: 'ASSISTANT' as const,
          content: response.answer,
          sources: response.sources,
          followUps: response.followUps ?? [],
          autoCheckCitations: true,
          toolSteps: (message.toolSteps ?? []).filter((step) => step.status !== 'RUNNING'),
          proposals: (response.proposals ?? []).reduce(mergeProposal, message.proposals ?? []),
        }
      : message
  )
}

export function applyError(
  messages: LocalMessage[],
  streamingId: string,
  errorText: string
): LocalMessage[] {
  return [
    ...messages.filter((message) => message.id !== streamingId),
    { id: `error-${Date.now()}`, role: 'ASSISTANT', content: errorText },
  ]
}

export function historyToLocal(history: MessageResponse[]): LocalMessage[] {
  return history.map((message) => ({
    id: message.id,
    role: message.role,
    content: message.content,
    sources: message.sources,
    rating: message.rating,
    toolSteps: (message.toolSteps ?? []).map((step) => ({
      name: step.name,
      status: step.status,
    })),
  }))
}

export function lastSettledAssistant(messages: LocalMessage[]): LocalMessage | null {
  for (let index = messages.length - 1; index >= 0; index--) {
    const message = messages[index]
    if (message.role === 'ASSISTANT' && !message.isStreaming) return message
  }
  return null
}

export function lastFollowUps(messages: LocalMessage[]): string[] {
  return lastSettledAssistant(messages)?.followUps ?? []
}

export function applyRating(
  messages: LocalMessage[],
  messageId: string,
  rating: number
): LocalMessage[] {
  return messages.map((message) => (message.id === messageId ? { ...message, rating } : message))
}

export function autosizeTextarea(textarea: HTMLTextAreaElement | null, maxHeight: number): void {
  if (!textarea) return
  textarea.style.height = 'auto'
  textarea.style.height = Math.min(textarea.scrollHeight, maxHeight) + 'px'
}

export function resetTextareaHeight(textarea: HTMLTextAreaElement | null): void {
  if (!textarea) return
  textarea.style.height = 'auto'
}
