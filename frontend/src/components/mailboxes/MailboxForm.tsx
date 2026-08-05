import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '../ui/Button'
import { Input } from '../ui/Input'
import type {
  CreateMailboxRequest,
  MailHostPresetResponse,
  MailboxResponse,
  UpdateMailboxRequest,
} from '../../types'

const AUTO_PRESET = '__auto__'
const CUSTOM_PRESET = '__custom__'

function matchPreset(emailAddress: string, presets: MailHostPresetResponse[]): MailHostPresetResponse | null {
  const domain = emailAddress.split('@')[1]?.toLowerCase()
  if (!domain) return null
  return presets.find((preset) => preset.domains.some((d) => d.toLowerCase() === domain)) ?? null
}

interface MailboxFormProps {
  mode: 'create' | 'edit'
  initial?: MailboxResponse
  presets: MailHostPresetResponse[]
  isSubmitting: boolean
  error: string | null
  onCancel: () => void
  onSubmit: (data: CreateMailboxRequest | UpdateMailboxRequest) => void
}

export function MailboxForm({
  mode,
  initial,
  presets,
  isSubmitting,
  error,
  onCancel,
  onSubmit,
}: MailboxFormProps): JSX.Element {
  const { t } = useTranslation()
  const [emailAddress, setEmailAddress] = useState(initial?.emailAddress ?? '')
  const [password, setPassword] = useState('')
  const [presetId, setPresetId] = useState<string>(mode === 'edit' ? CUSTOM_PRESET : AUTO_PRESET)
  const [imapHost, setImapHost] = useState(initial?.imapHost ?? '')
  const [imapPort, setImapPort] = useState(String(initial?.imapPort ?? 993))
  const [imapSsl, setImapSsl] = useState(initial?.imapSsl ?? true)
  const [folder, setFolder] = useState(initial?.folder ?? 'INBOX')
  const [syncEnabled, setSyncEnabled] = useState(initial?.syncEnabled ?? true)

  const usingAutoPreset = mode === 'create' && presetId === AUTO_PRESET
  const usingManualHost = presetId === CUSTOM_PRESET

  const handlePresetChange = (value: string): void => {
    setPresetId(value)
    if (value === AUTO_PRESET || value === CUSTOM_PRESET) return
    const preset = presets.find((p) => p.id === value)
    if (!preset) return
    setImapHost(preset.imapHost)
    setImapPort(String(preset.imapPort))
    setImapSsl(preset.imapSsl)
  }

  const handleSubmit = (event: React.FormEvent): void => {
    event.preventDefault()
    if (mode === 'create') {
      const payload: CreateMailboxRequest = {
        emailAddress: emailAddress.trim(),
        password,
      }
      if (!usingAutoPreset) {
        payload.imapHost = imapHost.trim() || undefined
        payload.imapPort = imapPort ? Number(imapPort) : undefined
        payload.imapSsl = imapSsl
      }
      if (folder.trim() && folder.trim() !== 'INBOX') payload.folder = folder.trim()
      onSubmit(payload)
      return
    }

    const payload: UpdateMailboxRequest = {
      folder: folder.trim() || undefined,
      syncEnabled,
    }
    if (password.trim()) payload.password = password
    if (usingManualHost) {
      payload.imapHost = imapHost.trim() || undefined
      payload.imapPort = imapPort ? Number(imapPort) : undefined
      payload.imapSsl = imapSsl
    }
    onSubmit(payload)
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      {mode === 'create' ? (
        <Input
          label={t('mailboxes.emailLabel')}
          type="email"
          required
          value={emailAddress}
          onChange={(e) => {
            const value = e.target.value
            setEmailAddress(value)
            if (presetId === AUTO_PRESET) {
              const matched = matchPreset(value, presets)
              if (matched) {
                setImapHost(matched.imapHost)
                setImapPort(String(matched.imapPort))
                setImapSsl(matched.imapSsl)
              }
            }
          }}
          placeholder="name@yandex.ru"
        />
      ) : (
        <div>
          <p className="text-xs font-medium text-fg-muted mb-1">{t('mailboxes.emailLabel')}</p>
          <p className="text-sm text-fg">{initial?.emailAddress}</p>
        </div>
      )}

      <Input
        label={t('mailboxes.passwordLabel')}
        type="password"
        required={mode === 'create'}
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        placeholder={mode === 'edit' ? t('mailboxes.passwordKeepHint') : undefined}
      />
      <p className="text-xs text-fg-muted -mt-2">{t('mailboxes.appPasswordHint')}</p>

      <div>
        <label className="text-sm font-medium text-fg mb-1.5 block">{t('mailboxes.presetLabel')}</label>
        <select
          value={presetId}
          onChange={(e) => handlePresetChange(e.target.value)}
          className="input-base w-full"
        >
          {mode === 'create' && <option value={AUTO_PRESET}>{t('mailboxes.presetAuto')}</option>}
          {presets.map((preset) => (
            <option key={preset.id} value={preset.id}>
              {preset.displayName}
            </option>
          ))}
          <option value={CUSTOM_PRESET}>{t('mailboxes.presetCustom')}</option>
        </select>
      </div>

      {!usingAutoPreset && (
        <div className="flex gap-3">
          <Input
            label={t('mailboxes.hostLabel')}
            value={imapHost}
            onChange={(e) => setImapHost(e.target.value)}
            disabled={!usingManualHost}
            className="flex-1"
          />
          <Input
            label={t('mailboxes.portLabel')}
            type="number"
            value={imapPort}
            onChange={(e) => setImapPort(e.target.value)}
            disabled={!usingManualHost}
            className="w-24"
          />
        </div>
      )}

      {!usingAutoPreset && (
        <label className="flex items-center gap-2 text-sm text-fg select-none">
          <input
            type="checkbox"
            checked={imapSsl}
            disabled={!usingManualHost}
            onChange={(e) => setImapSsl(e.target.checked)}
            className="h-4 w-4 accent-accent"
          />
          {t('mailboxes.sslLabel')}
        </label>
      )}

      <Input
        label={t('mailboxes.folderLabel')}
        value={folder}
        onChange={(e) => setFolder(e.target.value)}
        placeholder="INBOX"
      />

      {mode === 'edit' && (
        <label className="flex items-center gap-2 text-sm text-fg select-none">
          <input
            type="checkbox"
            checked={syncEnabled}
            onChange={(e) => setSyncEnabled(e.target.checked)}
            className="h-4 w-4 accent-accent"
          />
          {t('mailboxes.syncEnabledLabel')}
        </label>
      )}

      {error && <p className="text-sm text-danger">{error}</p>}

      <div className="flex justify-end gap-2 pt-1">
        <Button type="button" variant="secondary" onClick={onCancel}>
          {t('common.cancel')}
        </Button>
        <Button type="submit" loading={isSubmitting}>
          {mode === 'create' ? t('mailboxes.addSubmit') : t('mailboxes.saveSubmit')}
        </Button>
      </div>
    </form>
  )
}
