import apiClient from './client'
import type {
  CreateInviteRequest,
  CreateOrganizationRequest,
  OrgInvite,
  OrgRole,
  Organization,
  OrganizationMember,
} from '../types'

export const organizationsApi = {
  list: async (): Promise<Organization[]> => {
    const response = await apiClient.get<Organization[]>('/api/user/org')
    return response.data
  },

  create: async (data: CreateOrganizationRequest): Promise<Organization> => {
    const response = await apiClient.post<Organization>('/api/user/org', data)
    return response.data
  },

  members: async (orgId: string): Promise<OrganizationMember[]> => {
    const response = await apiClient.get<OrganizationMember[]>(`/api/user/org/${orgId}/members`)
    return response.data
  },

  changeRole: async (orgId: string, userId: string, orgRole: OrgRole): Promise<OrganizationMember> => {
    const response = await apiClient.patch<OrganizationMember>(
      `/api/user/org/${orgId}/members/${userId}/role`,
      { orgRole }
    )
    return response.data
  },

  removeMember: async (orgId: string, userId: string): Promise<void> => {
    await apiClient.delete(`/api/user/org/${orgId}/members/${userId}`)
  },

  leave: async (orgId: string): Promise<void> => {
    await apiClient.post(`/api/user/org/${orgId}/leave`)
  },

  invites: async (orgId: string): Promise<OrgInvite[]> => {
    const response = await apiClient.get<OrgInvite[]>(`/api/user/org/${orgId}/invites`)
    return response.data
  },

  invite: async (orgId: string, data: CreateInviteRequest): Promise<OrgInvite> => {
    const response = await apiClient.post<OrgInvite>(`/api/user/org/${orgId}/invites`, data)
    return response.data
  },

  revokeInvite: async (orgId: string, inviteId: string): Promise<void> => {
    await apiClient.delete(`/api/user/org/${orgId}/invites/${inviteId}`)
  },

  acceptInvite: async (token: string): Promise<Organization> => {
    const response = await apiClient.post<Organization>('/api/user/org/invites/accept', { token })
    return response.data
  },
}
