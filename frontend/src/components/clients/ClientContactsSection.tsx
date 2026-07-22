import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { clientsApi } from '../../api/clients'
import { Button } from '../ui/Button'
import { Spinner } from '../ui/Spinner'
import i18n from '../../i18n'
import type { ContactType } from '../../types'

function todayIso(): string {
  return new Date().toISOString().slice(0, 10)
}

export function ClientContactsSection({ clientId }: { clientId: string }): JSX.Element {
  const { t } = useTranslation()
  const contactTypes: { value: ContactType; label: string }[] = [
    { value: 'CALL', label: t('clientContacts.typeCall') },
    { value: 'MEETING', label: t('clientContacts.typeMeeting') },
    { value: 'LETTER', label: t('clientContacts.typeLetter') },
    { value: 'EMAIL', label: t('clientContacts.typeEmail') },
    { value: 'MESSENGER', label: t('clientContacts.typeMessenger') },
  ]
  const queryClient = useQueryClient()
  const [type, setType] = useState<ContactType>('CALL')
  const [contactDate, setContactDate] = useState(todayIso())
  const [notes, setNotes] = useState('')
  const [error, setError] = useState<string | null>(null)

  const { data: contacts, isLoading } = useQuery({
    queryKey: ['client-contacts', clientId],
    queryFn: () => clientsApi.getContacts(clientId),
    enabled: clientId !== '',
  })

  const invalidate = (): void => {
    void queryClient.invalidateQueries({ queryKey: ['client-contacts', clientId] })
  }

  const createMutation = useMutation({
    mutationFn: () => clientsApi.createContact(clientId, { type, contactDate, notes: notes.trim() || undefined }),
    onSuccess: () => {
      invalidate()
      setNotes('')
      setType('CALL')
      setContactDate(todayIso())
      setError(null)
    },
    onError: () => setError(t('clientContacts.addError')),
  })

  const deleteMutation = useMutation({
    mutationFn: (contactId: string) => clientsApi.deleteContact(clientId, contactId),
    onSuccess: invalidate,
    onError: () => setError(t('clientContacts.deleteError')),
  })

  const handleAdd = (e: React.FormEvent): void => {
    e.preventDefault()
    if (!contactDate) {
      setError(t('clientContacts.dateRequired'))
      return
    }
    setError(null)
    createMutation.mutate()
  }

  return (
    <section className="mb-10">
      <h2 className="text-sm font-semibold text-fg mb-3">
        {t('clientContacts.title')}
        {contacts && contacts.length > 0 && (
          <span className="font-normal text-fg-muted"> ({contacts.length})</span>
        )}
      </h2>

      <form
        onSubmit={handleAdd}
        className="mb-4 p-4 rounded-xl bg-surface border border-line flex flex-col gap-3"
      >
        <div className="flex flex-col sm:flex-row gap-3">
          <select
            value={type}
            onChange={(e) => setType(e.target.value as ContactType)}
            className="px-3 py-2.5 rounded-lg border border-line bg-bg text-fg text-sm focus:outline-none focus:ring-2 focus:ring-accent"
          >
            {contactTypes.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
          <input
            type="date"
            value={contactDate}
            onChange={(e) => setContactDate(e.target.value)}
            className="px-3 py-2.5 rounded-lg border border-line bg-bg text-fg text-sm focus:outline-none focus:ring-2 focus:ring-accent"
          />
        </div>
        <textarea
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          placeholder={t('clientContacts.notesPlaceholder')}
          rows={2}
          className="w-full px-3 py-2.5 rounded-lg border border-line bg-bg text-fg text-sm focus:outline-none focus:ring-2 focus:ring-accent resize-none"
        />
        {error && <p className="text-sm text-danger">{error}</p>}
        <div>
          <Button type="submit" size="sm" loading={createMutation.isPending}>
            {t('clientContacts.addSubmit')}
          </Button>
        </div>
      </form>

      {isLoading ? (
        <div className="flex justify-center py-6">
          <Spinner />
        </div>
      ) : !contacts || contacts.length === 0 ? (
        <p className="text-sm text-fg-muted">{t('clientContacts.empty')}</p>
      ) : (
        <ul className="flex flex-col gap-2">
          {contacts.map((contact) => (
            <li
              key={contact.id}
              className="p-4 rounded-lg bg-surface border border-line"
            >
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <div className="flex items-center gap-2 mb-1">
                    <span className="text-xs px-2 py-0.5 rounded-full bg-bg border border-line text-fg-muted">
                      {contact.typeName}
                    </span>
                    <span className="text-xs text-fg-muted">
                      {new Date(contact.contactDate).toLocaleDateString(i18n.language.startsWith('ru') ? 'ru-RU' : 'en-US')}
                    </span>
                  </div>
                  {contact.notes && (
                    <p className="text-sm text-fg whitespace-pre-wrap">{contact.notes}</p>
                  )}
                </div>
                <button
                  onClick={() => deleteMutation.mutate(contact.id)}
                  className="shrink-0 text-xs text-fg-muted hover:text-red-600 dark:hover:text-red-400"
                >
                  {t('clientContacts.delete')}
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
