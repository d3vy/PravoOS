import { useEffect } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { subscribeChatSync } from '../lib/chat/chatSync'

export function useAiChatListSync(): void {
  const queryClient = useQueryClient()

  useEffect(
    () =>
      subscribeChatSync((message) => {
        if (message.type !== 'list-changed') return
        queryClient.invalidateQueries({ queryKey: ['conversations'] })
      }),
    [queryClient]
  )
}
