import apiClient from './client'
import type { PushConfigResponse, PushSubscriptionRequest } from '../types'

export const pushApi = {
  config: async (): Promise<PushConfigResponse> => {
    const response = await apiClient.get<PushConfigResponse>('/api/user/push/config')
    return response.data
  },

  subscribe: async (subscription: PushSubscriptionRequest): Promise<void> => {
    await apiClient.post('/api/user/push/subscriptions', subscription)
  },

  unsubscribe: async (endpoint: string): Promise<void> => {
    await apiClient.post('/api/user/push/subscriptions/remove', { endpoint })
  },
}
