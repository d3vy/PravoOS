import { useState, type FormEvent } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { usersApi } from '../../api/users'
import type {
  LawyerProfileResponse,
  MfaSetupResponse,
  SessionResponse,
  TelegramLinkResponse,
  UpdateProfileRequest,
} from '../../types'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'
import { Spinner } from '../../components/ui/Spinner'
import { PageHeader } from '../../components/ui/PageHeader'

export default function ProfilePage(): JSX.Element {
  const { t } = useTranslation()
  const queryClient = useQueryClient()

  const { data: profile, isLoading } = useQuery<LawyerProfileResponse>({
    queryKey: ['profile'],
    queryFn: usersApi.getProfile,
  })

  if (isLoading) {
    return (
      <div className="bg-bg">
        <div className="flex justify-center py-24">
          <Spinner size="lg" />
        </div>
      </div>
    )
  }

  if (!profile) {
    return (
      <div className="bg-bg">
        <div className="page-container py-16 text-center">
          <p className="text-fg-muted">{t('profile.notFound')}</p>
        </div>
      </div>
    )
  }

  return (
    <div className="bg-bg">
      <div className="page-container py-8 max-w-lg">
        <PageHeader title={t('profile.title')} />
        <ProfileForm profile={profile} queryClient={queryClient} />
        <div className="mt-10 pt-8 border-t border-line">
          <TelegramSection profile={profile} queryClient={queryClient} />
        </div>
        <div className="mt-10 pt-8 border-t border-line">
          <MfaSection queryClient={queryClient} />
        </div>
        <div className="mt-10 pt-8 border-t border-line">
          <SessionsSection />
        </div>
      </div>
    </div>
  )
}

function MfaSection({
  queryClient,
}: {
  queryClient: ReturnType<typeof useQueryClient>
}): JSX.Element {
  const { t } = useTranslation()
  const [setup, setSetup] = useState<MfaSetupResponse | null>(null)
  const [code, setCode] = useState('')
  const [disableCode, setDisableCode] = useState('')
  const [actionError, setActionError] = useState<string | null>(null)

  const { data: status, isLoading } = useQuery({
    queryKey: ['mfa-status'],
    queryFn: usersApi.getMfaStatus,
  })

  const setupMutation = useMutation({
    mutationFn: usersApi.setupMfa,
    onSuccess: (data) => {
      setSetup(data)
      setCode('')
      setActionError(null)
    },
    onError: () => setActionError(t('profile.mfaSetupFailed')),
  })

  const enableMutation = useMutation({
    mutationFn: () => usersApi.enableMfa(code),
    onSuccess: () => {
      setSetup(null)
      setCode('')
      setActionError(null)
      queryClient.invalidateQueries({ queryKey: ['mfa-status'] })
    },
    onError: () => setActionError(t('profile.mfaInvalidCode')),
  })

  const disableMutation = useMutation({
    mutationFn: () => usersApi.disableMfa(disableCode),
    onSuccess: () => {
      setDisableCode('')
      setActionError(null)
      queryClient.invalidateQueries({ queryKey: ['mfa-status'] })
    },
    onError: () => setActionError(t('profile.mfaDisableFailed')),
  })

  if (isLoading || !status) {
    return <Spinner size="sm" />
  }

  return (
    <div className="flex flex-col gap-4">
      <div>
        <h2 className="text-xl font-semibold text-fg">
          {t('profile.mfaTitle')}
        </h2>
        <p className="text-sm text-fg-muted mt-1">
          {t('profile.mfaDesc')}
          {status.mandatory && t('profile.mfaMandatory')}
        </p>
      </div>

      {status.enabled ? (
        <div className="flex flex-col gap-3">
          <p className="text-sm text-success">{t('profile.mfaEnabled')}</p>
          {status.mandatory ? (
            <p className="text-sm text-fg-muted">
              {t('profile.mfaDisableUnavailable')}
            </p>
          ) : (
            <div className="flex flex-col gap-2 max-w-xs">
              <Input
                label={t('profile.mfaDisableCodeLabel')}
                inputMode="numeric"
                placeholder="000000"
                value={disableCode}
                onChange={(e) => setDisableCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
              />
              <div>
                <Button
                  variant="secondary"
                  loading={disableMutation.isPending}
                  disabled={disableCode.length !== 6}
                  onClick={() => disableMutation.mutate()}
                >
                  {t('profile.mfaDisable')}
                </Button>
              </div>
            </div>
          )}
        </div>
      ) : setup ? (
        <div className="flex flex-col gap-3 rounded-lg border border-line p-4 max-w-md">
          <p className="text-sm text-fg">
            {t('profile.mfaSetupInstruction')}
          </p>
          <div>
            <p className="text-xs text-fg-muted mb-1">{t('profile.mfaSecretKey')}</p>
            <code className="font-mono text-sm break-all text-fg">{setup.secret}</code>
          </div>
          <Input
            label={t('profile.mfaConfirmCode')}
            inputMode="numeric"
            placeholder="000000"
            value={code}
            onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
          />
          <div className="flex flex-wrap gap-2">
            <Button
              variant="primary"
              loading={enableMutation.isPending}
              disabled={code.length !== 6}
              onClick={() => enableMutation.mutate()}
            >
              {t('profile.mfaEnable')}
            </Button>
            <Button variant="secondary" onClick={() => setSetup(null)}>
              {t('common.cancel')}
            </Button>
          </div>
        </div>
      ) : (
        <div>
          <Button
            variant="primary"
            loading={setupMutation.isPending}
            onClick={() => setupMutation.mutate()}
          >
            {t('profile.mfaSetup')}
          </Button>
        </div>
      )}

      {actionError && <p className="text-sm text-danger">{actionError}</p>}
    </div>
  )
}

function SessionsSection(): JSX.Element {
  const { t } = useTranslation()
  const queryClient = useQueryClient()

  const { data: sessions, isLoading } = useQuery({
    queryKey: ['sessions'],
    queryFn: usersApi.listSessions,
  })

  const revokeMutation = useMutation({
    mutationFn: (sessionId: string) => usersApi.revokeSession(sessionId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['sessions'] }),
  })

  return (
    <div className="flex flex-col gap-4">
      <div>
        <h2 className="text-xl font-semibold text-fg">{t('profile.sessionsTitle')}</h2>
        <p className="text-sm text-fg-muted mt-1">
          {t('profile.sessionsDesc')}
        </p>
      </div>

      {isLoading ? (
        <Spinner size="sm" />
      ) : !sessions || sessions.length === 0 ? (
        <p className="text-sm text-fg-muted">{t('profile.noSessions')}</p>
      ) : (
        <ul className="flex flex-col gap-3">
          {sessions.map((session) => (
            <SessionRow
              key={session.id}
              session={session}
              onRevoke={() => revokeMutation.mutate(session.id)}
              revoking={revokeMutation.isPending && revokeMutation.variables === session.id}
            />
          ))}
        </ul>
      )}
    </div>
  )
}

function SessionRow({
  session,
  onRevoke,
  revoking,
}: {
  session: SessionResponse
  onRevoke: () => void
  revoking: boolean
}): JSX.Element {
  const { t, i18n } = useTranslation()
  const locale = i18n.language.startsWith('ru') ? 'ru-RU' : 'en-US'
  return (
    <li className="flex items-center justify-between gap-3 rounded-lg border border-line p-3">
      <div className="min-w-0">
        <p className="text-sm text-fg truncate">
          {session.userAgent ?? t('profile.unknownDevice')}
          {session.current && (
            <span className="ml-2 rounded-full bg-accent/10 px-2 py-0.5 text-xs text-accent">
              {t('profile.currentSession')}
            </span>
          )}
        </p>
        <p className="text-xs text-fg-muted truncate">
          IP: {session.ipAddress ?? '—'} · {t('profile.loginAt')} {new Date(session.createdAt).toLocaleString(locale)}
        </p>
      </div>
      <Button variant="secondary" loading={revoking} onClick={onRevoke}>
        {t('profile.revoke')}
      </Button>
    </li>
  )
}

function TelegramSection({
  profile,
  queryClient,
}: {
  profile: LawyerProfileResponse
  queryClient: ReturnType<typeof useQueryClient>
}): JSX.Element {
  const { t } = useTranslation()
  const [link, setLink] = useState<TelegramLinkResponse | null>(null)

  const linkMutation = useMutation({
    mutationFn: usersApi.createTelegramLinkCode,
    onSuccess: (data) => setLink(data),
  })

  const unlinkMutation = useMutation({
    mutationFn: usersApi.unlinkTelegram,
    onSuccess: () => {
      setLink(null)
      queryClient.invalidateQueries({ queryKey: ['profile'] })
    },
  })

  return (
    <div className="flex flex-col gap-4">
      <div>
        <h2 className="text-xl font-semibold text-fg">{t('profile.telegramTitle')}</h2>
        <p className="text-sm text-fg-muted mt-1">
          {t('profile.telegramDesc')}
        </p>
      </div>

      {profile.telegramLinked ? (
        <div className="flex flex-col gap-3">
          <p className="text-sm text-success">{t('settings.telegramLinked')}</p>
          <div>
            <Button
              variant="secondary"
              loading={unlinkMutation.isPending}
              onClick={() => unlinkMutation.mutate()}
            >
              {t('settings.unlinkTelegram')}
            </Button>
          </div>
          {unlinkMutation.isError && (
            <p className="text-sm text-danger">{t('settings.unlinkFailed')}</p>
          )}
        </div>
      ) : (
        <div className="flex flex-col gap-3">
          {!link && (
            <div>
              <Button
                variant="primary"
                loading={linkMutation.isPending}
                onClick={() => linkMutation.mutate()}
              >
                {t('settings.linkTelegram')}
              </Button>
            </div>
          )}

          {linkMutation.isError && (
            <p className="text-sm text-danger">{t('settings.linkCreateFailed')}</p>
          )}

          {link && (
            <div className="flex flex-col gap-3 rounded-lg border border-line p-4">
              <p className="text-sm text-fg">
                {t('settings.botInstruction')}
              </p>
              <div className="flex flex-wrap gap-2">
                <a href={link.deepLink} target="_blank" rel="noopener noreferrer">
                  <Button variant="primary">{t('settings.openTelegram')}</Button>
                </a>
                <Button
                  variant="secondary"
                  onClick={() => queryClient.invalidateQueries({ queryKey: ['profile'] })}
                >
                  {t('settings.iLinkedRefresh')}
                </Button>
              </div>
              <p className="text-xs text-fg-muted">
                {t('settings.botCommandHint')}{' '}
                <code className="font-mono">/start {link.code}</code>
              </p>
            </div>
          )}
        </div>
      )}
    </div>
  )
}

function ProfileForm({
  profile,
  queryClient,
}: {
  profile: LawyerProfileResponse
  queryClient: ReturnType<typeof useQueryClient>
}): JSX.Element {
  const { t } = useTranslation()
  const [fullName, setFullName] = useState(profile.fullName)
  const [specialization, setSpecialization] = useState(profile.specialization ?? '')
  const [phone, setPhone] = useState(profile.phone ?? '')
  const [success, setSuccess] = useState(false)

  const updateMutation = useMutation({
    mutationFn: (data: UpdateProfileRequest) => usersApi.updateProfile(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['profile'] })
      setSuccess(true)
      setTimeout(() => setSuccess(false), 3000)
    },
  })

  const handleSubmit = (e: FormEvent): void => {
    e.preventDefault()
    updateMutation.mutate({
      fullName,
      specialization: specialization || undefined,
      phone: phone || undefined,
    })
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <div>
        <label className="text-sm font-medium text-fg block mb-1.5">{t('common.email')}</label>
        <input
          type="email"
          value={profile.email}
          disabled
          className="input-base opacity-60 cursor-not-allowed w-full"
        />
      </div>

      <Input
        label={t('profile.fullNameLabel')}
        value={fullName}
        onChange={(e) => setFullName(e.target.value)}
        required
        maxLength={255}
      />

      <Input
        label={t('profile.specializationLabel')}
        value={specialization}
        onChange={(e) => setSpecialization(e.target.value)}
        maxLength={255}
        placeholder={t('profile.optional')}
      />

      <Input
        label={t('profile.phoneLabel')}
        type="tel"
        value={phone}
        onChange={(e) => setPhone(e.target.value)}
        maxLength={50}
        placeholder={t('profile.optional')}
      />

      {updateMutation.isError && (
        <p className="text-sm text-danger">{t('profile.saveError')}</p>
      )}

      {success && (
        <p className="text-sm text-success">{t('profile.saved')}</p>
      )}

      <div className="pt-2">
        <Button
          type="submit"
          variant="primary"
          loading={updateMutation.isPending}
          disabled={!fullName.trim()}
        >
          {t('common.save')}
        </Button>
      </div>
    </form>
  )
}
