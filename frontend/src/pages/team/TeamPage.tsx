import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { organizationsApi } from '../../api/organizations'
import { refreshSession } from '../../api/client'
import { useAuthStore } from '../../store/authStore'
import type { OrgInvite, OrgRole, Organization, OrganizationMember } from '../../types'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'
import { QueryState } from '../../components/ui/QueryState'
import { PageHeader } from '../../components/ui/PageHeader'

export default function TeamPage(): JSX.Element {
  const { t } = useTranslation()
  const ROLE_LABEL: Record<OrgRole, string> = {
    OWNER: t('team.roleOwner'),
    MANAGER: t('team.roleManager'),
    MEMBER: t('team.roleMember'),
  }
  const queryClient = useQueryClient()
  const currentUserId = useAuthStore((state) => state.user?.userId)
  const [selectedOrgId, setSelectedOrgId] = useState<string>('')
  const [orgName, setOrgName] = useState('')
  const [inviteEmail, setInviteEmail] = useState('')
  const [inviteRole, setInviteRole] = useState<OrgRole>('MEMBER')
  const [error, setError] = useState<string | null>(null)

  const { data: organizations = [], isLoading } = useQuery<Organization[]>({
    queryKey: ['organizations'],
    queryFn: organizationsApi.list,
  })

  useEffect(() => {
    if (organizations.length > 0 && !organizations.some((org) => org.id === selectedOrgId)) {
      setSelectedOrgId(organizations[0].id)
    }
  }, [organizations, selectedOrgId])

  const selectedOrg = organizations.find((org) => org.id === selectedOrgId)
  const canManage = selectedOrg && selectedOrg.myRole !== 'MEMBER'
  const isOwner = selectedOrg?.myRole === 'OWNER'

  const { data: members = [] } = useQuery<OrganizationMember[]>({
    queryKey: ['org-members', selectedOrgId],
    queryFn: () => organizationsApi.members(selectedOrgId),
    enabled: Boolean(selectedOrgId),
  })

  const { data: invites = [] } = useQuery<OrgInvite[]>({
    queryKey: ['org-invites', selectedOrgId],
    queryFn: () => organizationsApi.invites(selectedOrgId),
    enabled: Boolean(selectedOrgId) && Boolean(canManage),
  })

  const refreshOrgs = async (): Promise<void> => {
    await refreshSession()
    queryClient.invalidateQueries({ queryKey: ['organizations'] })
    queryClient.invalidateQueries({ queryKey: ['cases'] })
  }

  const createOrgMutation = useMutation({
    mutationFn: () => organizationsApi.create({ name: orgName.trim() }),
    onSuccess: async (org) => {
      setOrgName('')
      setError(null)
      await refreshOrgs()
      setSelectedOrgId(org.id)
    },
    onError: () => setError(t('team.createOrgError')),
  })

  const inviteMutation = useMutation({
    mutationFn: () => organizationsApi.invite(selectedOrgId, { email: inviteEmail.trim(), orgRole: inviteRole }),
    onSuccess: () => {
      setInviteEmail('')
      setInviteRole('MEMBER')
      setError(null)
      queryClient.invalidateQueries({ queryKey: ['org-invites', selectedOrgId] })
    },
    onError: () => setError(t('team.sendInviteError')),
  })

  const revokeInviteMutation = useMutation({
    mutationFn: (inviteId: string) => organizationsApi.revokeInvite(selectedOrgId, inviteId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['org-invites', selectedOrgId] }),
  })

  const changeRoleMutation = useMutation({
    mutationFn: ({ userId, role }: { userId: string; role: OrgRole }) =>
      organizationsApi.changeRole(selectedOrgId, userId, role),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['org-members', selectedOrgId] }),
  })

  const removeMemberMutation = useMutation({
    mutationFn: (userId: string) => organizationsApi.removeMember(selectedOrgId, userId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['org-members', selectedOrgId] }),
  })

  const leaveMutation = useMutation({
    mutationFn: () => organizationsApi.leave(selectedOrgId),
    onSuccess: async () => {
      setSelectedOrgId('')
      await refreshOrgs()
      queryClient.invalidateQueries({ queryKey: ['org-members'] })
    },
    onError: () => setError(t('team.leaveOrgError')),
  })

  return (
    <div className="bg-bg">
      <div className="page-container py-8 max-w-4xl">
        <PageHeader
          title={t('team.title')}
          description={t('team.subtitle')}
          className="mb-6"
        />

        {error && <p className="mb-4 text-sm text-danger">{error}</p>}

        {isLoading ? (
          <QueryState isLoading spinnerSize="lg" />
        ) : (
          <div className="flex flex-col gap-8">
            {organizations.length > 0 && (
              <div className="flex flex-wrap gap-2">
                {organizations.map((org) => (
                  <button
                    key={org.id}
                    type="button"
                    onClick={() => setSelectedOrgId(org.id)}
                    className={`px-4 py-2 rounded-lg text-sm font-medium border transition-colors ${
                      org.id === selectedOrgId
                        ? 'bg-fg text-bg border-transparent'
                        : 'border-line text-fg-muted hover:text-fg'
                    }`}
                  >
                    {org.name} · {ROLE_LABEL[org.myRole]}
                  </button>
                ))}
              </div>
            )}

            {selectedOrg && (
              <div className="flex flex-col gap-6 p-6 rounded-2xl bg-surface border border-line">
                <div className="flex items-center justify-between gap-4 flex-wrap">
                  <div>
                    <h2 className="text-xl font-semibold text-fg">{selectedOrg.name}</h2>
                    <p className="text-xs text-fg-muted">
                      {t('team.membersCount', { count: selectedOrg.memberCount, role: ROLE_LABEL[selectedOrg.myRole] })}
                    </p>
                  </div>
                  {!isOwner && (
                    <Button variant="secondary" size="sm" loading={leaveMutation.isPending} onClick={() => leaveMutation.mutate()}>
                      {t('team.leave')}
                    </Button>
                  )}
                </div>

                <div>
                  <h3 className="text-sm font-medium text-fg mb-2">{t('team.membersTitle')}</h3>
                  <div className="flex flex-col divide-y divide-line">
                    {members.map((member) => {
                      const isOrgOwner = member.userId === selectedOrg.ownerId
                      const isSelf = member.userId === currentUserId
                      return (
                        <div key={member.userId} className="flex items-center justify-between gap-3 py-3 min-w-0">
                          <div className="min-w-0">
                            <p className="text-sm text-fg truncate">
                              {member.fullName || member.email || member.userId}
                              {isSelf && <span className="text-fg-muted">{t('team.you')}</span>}
                            </p>
                            {member.email && (
                              <p className="text-xs text-fg-muted truncate">{member.email}</p>
                            )}
                          </div>
                          <div className="flex items-center gap-2 shrink-0">
                            {isOwner && !isOrgOwner ? (
                              <select
                                value={member.orgRole}
                                onChange={(e) => changeRoleMutation.mutate({ userId: member.userId, role: e.target.value as OrgRole })}
                                className="px-2 py-1 rounded-md border border-line bg-bg text-fg text-xs"
                              >
                                <option value="MEMBER">{ROLE_LABEL.MEMBER}</option>
                                <option value="MANAGER">{ROLE_LABEL.MANAGER}</option>
                              </select>
                            ) : (
                              <span className="text-xs text-fg-muted">{ROLE_LABEL[member.orgRole]}</span>
                            )}
                            {canManage && !isOrgOwner && !isSelf && (
                              <button
                                type="button"
                                onClick={() => removeMemberMutation.mutate(member.userId)}
                                className="text-xs text-danger hover:underline"
                              >
                                {t('team.delete')}
                              </button>
                            )}
                          </div>
                        </div>
                      )
                    })}
                  </div>
                </div>

                {canManage && (
                  <div>
                    <h3 className="text-sm font-medium text-fg mb-2">{t('team.inviteTitle')}</h3>
                    <div className="flex gap-2 flex-wrap items-end">
                      <div className="flex-1 min-w-[200px]">
                        <Input
                          type="email"
                          placeholder="email@example.com"
                          value={inviteEmail}
                          onChange={(e) => setInviteEmail(e.target.value)}
                        />
                      </div>
                      <select
                        value={inviteRole}
                        onChange={(e) => setInviteRole(e.target.value as OrgRole)}
                        className="px-3 py-2.5 rounded-lg border border-line bg-bg text-fg text-sm"
                      >
                        <option value="MEMBER">{ROLE_LABEL.MEMBER}</option>
                        {isOwner && <option value="MANAGER">{ROLE_LABEL.MANAGER}</option>}
                      </select>
                      <Button
                        variant="primary"
                        loading={inviteMutation.isPending}
                        disabled={!inviteEmail.trim()}
                        onClick={() => inviteMutation.mutate()}
                      >
                        {t('team.invite')}
                      </Button>
                    </div>

                    {invites.length > 0 && (
                      <div className="mt-4 flex flex-col divide-y divide-line">
                        {invites.map((invite) => (
                          <div key={invite.id} className="flex items-center justify-between gap-3 py-2 min-w-0">
                            <p className="text-sm text-fg-muted truncate">
                              {invite.email} · {ROLE_LABEL[invite.orgRole]}
                            </p>
                            <button
                              type="button"
                              onClick={() => revokeInviteMutation.mutate(invite.id)}
                              className="text-xs text-danger hover:underline shrink-0"
                            >
                              {t('team.revoke')}
                            </button>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                )}
              </div>
            )}

            <div className="p-6 rounded-2xl bg-surface border border-line">
              <h3 className="text-sm font-medium text-fg mb-2">{t('team.createOrgTitle')}</h3>
              <div className="flex gap-2 flex-wrap items-end">
                <div className="flex-1 min-w-[200px]">
                  <Input
                    placeholder={t('team.orgNamePlaceholder')}
                    value={orgName}
                    onChange={(e) => setOrgName(e.target.value)}
                    maxLength={200}
                  />
                </div>
                <Button
                  variant="primary"
                  loading={createOrgMutation.isPending}
                  disabled={!orgName.trim()}
                  onClick={() => createOrgMutation.mutate()}
                >
                  {t('team.create')}
                </Button>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
