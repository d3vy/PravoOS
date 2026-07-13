import apiClient from './client'
import type { BillingPlan, BillingStatus, CheckoutResponse, PaymentRecord } from '../types'

export const billingApi = {
  status: async (): Promise<BillingStatus> => {
    const response = await apiClient.get<BillingStatus>('/api/user/billing')
    return response.data
  },

  plans: async (): Promise<BillingPlan[]> => {
    const response = await apiClient.get<BillingPlan[]>('/api/user/billing/plans')
    return response.data
  },

  subscribe: async (planCode: string): Promise<CheckoutResponse> => {
    const response = await apiClient.post<CheckoutResponse>('/api/user/billing/subscribe', { planCode })
    return response.data
  },

  cancel: async (): Promise<BillingStatus> => {
    const response = await apiClient.post<BillingStatus>('/api/user/billing/cancel')
    return response.data
  },

  payments: async (): Promise<PaymentRecord[]> => {
    const response = await apiClient.get<PaymentRecord[]>('/api/user/billing/payments')
    return response.data
  },
}
