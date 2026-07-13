import { useState, type FormEvent } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
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

export default function ProfilePage(): JSX.Element {
  const queryClient = useQueryClient()

  const { data: profile, isLoading } = useQuery<LawyerProfileResponse>({
    queryKey: ['profile'],
    queryFn: usersApi.getProfile,
  })

  if (isLoading) {
    return (
      <div className="bg-light-bg dark:bg-dark-bg">
        <div className="flex justify-center py-24">
          <Spinner size="lg" />
        </div>
      </div>
    )
  }

  if (!profile) {
    return (
      <div className="bg-light-bg dark:bg-dark-bg">
        <div className="page-container py-16 text-center">
          <p className="text-light-secondary dark:text-dark-secondary">Профиль не найден</p>
        </div>
      </div>
    )
  }

  return (
    <div className="bg-light-bg dark:bg-dark-bg">
      <div className="page-container py-8 max-w-lg">
        <h1 className="text-3xl font-semibold text-light-text dark:text-dark-text mb-8">
          Профиль
        </h1>
        <ProfileForm profile={profile} queryClient={queryClient} />
        <div className="mt-10 pt-8 border-t border-light-border dark:border-dark-border">
          <TelegramSection profile={profile} queryClient={queryClient} />
        </div>
        <div className="mt-10 pt-8 border-t border-light-border dark:border-dark-border">
          <MfaSection queryClient={queryClient} />
        </div>
        <div className="mt-10 pt-8 border-t border-light-border dark:border-dark-border">
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
    onError: () => setActionError('Не удалось начать настройку. Попробуйте снова.'),
  })

  const enableMutation = useMutation({
    mutationFn: () => usersApi.enableMfa(code),
    onSuccess: () => {
      setSetup(null)
      setCode('')
      setActionError(null)
      queryClient.invalidateQueries({ queryKey: ['mfa-status'] })
    },
    onError: () => setActionError('Неверный код. Проверьте приложение и попробуйте снова.'),
  })

  const disableMutation = useMutation({
    mutationFn: () => usersApi.disableMfa(disableCode),
    onSuccess: () => {
      setDisableCode('')
      setActionError(null)
      queryClient.invalidateQueries({ queryKey: ['mfa-status'] })
    },
    onError: () => setActionError('Не удалось отключить. Проверьте код и попробуйте снова.'),
  })

  if (isLoading || !status) {
    return <Spinner size="sm" />
  }

  return (
    <div className="flex flex-col gap-4">
      <div>
        <h2 className="text-xl font-semibold text-light-text dark:text-dark-text">
          Двухфакторная аутентификация
        </h2>
        <p className="text-sm text-light-secondary dark:text-dark-secondary mt-1">
          Дополнительный код из приложения-аутентификатора при входе (Google Authenticator, 1Password и др.).
          {status.mandatory && ' Обязательна для администраторов.'}
        </p>
      </div>

      {status.enabled ? (
        <div className="flex flex-col gap-3">
          <p className="text-sm text-green-600 dark:text-green-400">Двухфакторная аутентификация включена</p>
          {status.mandatory ? (
            <p className="text-sm text-light-secondary dark:text-dark-secondary">
              Отключение недоступно для администраторов.
            </p>
          ) : (
            <div className="flex flex-col gap-2 max-w-xs">
              <Input
                label="Код для отключения"
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
                  Отключить 2FA
                </Button>
              </div>
            </div>
          )}
        </div>
      ) : setup ? (
        <div className="flex flex-col gap-3 rounded-lg border border-light-border dark:border-dark-border p-4 max-w-md">
          <p className="text-sm text-light-text dark:text-dark-text">
            Добавьте ключ в приложение-аутентификатор, затем введите код для подтверждения.
          </p>
          <div>
            <p className="text-xs text-light-secondary dark:text-dark-secondary mb-1">Секретный ключ:</p>
            <code className="font-mono text-sm break-all text-light-text dark:text-dark-text">{setup.secret}</code>
          </div>
          <Input
            label="Код подтверждения"
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
              Включить 2FA
            </Button>
            <Button variant="secondary" onClick={() => setSetup(null)}>
              Отмена
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
            Настроить 2FA
          </Button>
        </div>
      )}

      {actionError && <p className="text-sm text-red-600 dark:text-red-400">{actionError}</p>}
    </div>
  )
}

function SessionsSection(): JSX.Element {
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
        <h2 className="text-xl font-semibold text-light-text dark:text-dark-text">Активные сессии</h2>
        <p className="text-sm text-light-secondary dark:text-dark-secondary mt-1">
          Устройства с активным доступом к аккаунту. Завершите незнакомые сессии.
        </p>
      </div>

      {isLoading ? (
        <Spinner size="sm" />
      ) : !sessions || sessions.length === 0 ? (
        <p className="text-sm text-light-secondary dark:text-dark-secondary">Активных сессий нет.</p>
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
  return (
    <li className="flex items-center justify-between gap-3 rounded-lg border border-light-border dark:border-dark-border p-3">
      <div className="min-w-0">
        <p className="text-sm text-light-text dark:text-dark-text truncate">
          {session.userAgent ?? 'Неизвестное устройство'}
        </p>
        <p className="text-xs text-light-secondary dark:text-dark-secondary truncate">
          IP: {session.ipAddress ?? '—'} · вход {new Date(session.createdAt).toLocaleString('ru-RU')}
        </p>
      </div>
      <Button variant="secondary" loading={revoking} onClick={onRevoke}>
        Завершить
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
        <h2 className="text-xl font-semibold text-light-text dark:text-dark-text">Telegram-уведомления</h2>
        <p className="text-sm text-light-secondary dark:text-dark-secondary mt-1">
          Привяжите Telegram, чтобы получать напоминания о дедлайнах по делам.
        </p>
      </div>

      {profile.telegramLinked ? (
        <div className="flex flex-col gap-3">
          <p className="text-sm text-green-600 dark:text-green-400">Telegram привязан</p>
          <div>
            <Button
              variant="secondary"
              loading={unlinkMutation.isPending}
              onClick={() => unlinkMutation.mutate()}
            >
              Отвязать Telegram
            </Button>
          </div>
          {unlinkMutation.isError && (
            <p className="text-sm text-red-600 dark:text-red-400">Не удалось отвязать. Попробуйте снова.</p>
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
                Привязать Telegram
              </Button>
            </div>
          )}

          {linkMutation.isError && (
            <p className="text-sm text-red-600 dark:text-red-400">Не удалось создать ссылку. Попробуйте снова.</p>
          )}

          {link && (
            <div className="flex flex-col gap-3 rounded-lg border border-light-border dark:border-dark-border p-4">
              <p className="text-sm text-light-text dark:text-dark-text">
                Откройте бота и нажмите «Запустить» — привязка произойдёт автоматически.
              </p>
              <div className="flex flex-wrap gap-2">
                <a href={link.deepLink} target="_blank" rel="noopener noreferrer">
                  <Button variant="primary">Открыть Telegram</Button>
                </a>
                <Button
                  variant="secondary"
                  onClick={() => queryClient.invalidateQueries({ queryKey: ['profile'] })}
                >
                  Я привязал — обновить
                </Button>
              </div>
              <p className="text-xs text-light-secondary dark:text-dark-secondary">
                Если ссылка не сработала, отправьте боту команду:{' '}
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
        <label className="text-sm font-medium text-light-text dark:text-dark-text block mb-1.5">Email</label>
        <input
          type="email"
          value={profile.email}
          disabled
          className="input-base opacity-60 cursor-not-allowed w-full"
        />
      </div>

      <Input
        label="ФИО"
        value={fullName}
        onChange={(e) => setFullName(e.target.value)}
        required
        maxLength={255}
      />

      <Input
        label="Специализация"
        value={specialization}
        onChange={(e) => setSpecialization(e.target.value)}
        maxLength={255}
        placeholder="Необязательно"
      />

      <Input
        label="Телефон"
        type="tel"
        value={phone}
        onChange={(e) => setPhone(e.target.value)}
        maxLength={50}
        placeholder="Необязательно"
      />

      {updateMutation.isError && (
        <p className="text-sm text-red-600 dark:text-red-400">Ошибка сохранения. Попробуйте снова.</p>
      )}

      {success && (
        <p className="text-sm text-green-600 dark:text-green-400">Профиль сохранён</p>
      )}

      <div className="pt-2">
        <Button
          type="submit"
          variant="primary"
          loading={updateMutation.isPending}
          disabled={!fullName.trim()}
        >
          Сохранить
        </Button>
      </div>
    </form>
  )
}
