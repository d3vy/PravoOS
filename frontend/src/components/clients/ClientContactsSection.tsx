import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { clientsApi } from '../../api/clients'
import { Button } from '../ui/Button'
import { Spinner } from '../ui/Spinner'
import type { ContactType } from '../../types'

const CONTACT_TYPES: { value: ContactType; label: string }[] = [
  { value: 'CALL', label: 'Звонок' },
  { value: 'MEETING', label: 'Встреча' },
  { value: 'LETTER', label: 'Письмо' },
  { value: 'EMAIL', label: 'Эл. письмо' },
  { value: 'MESSENGER', label: 'Мессенджер' },
]

function todayIso(): string {
  return new Date().toISOString().slice(0, 10)
}

export function ClientContactsSection({ clientId }: { clientId: string }): JSX.Element {
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
    onError: () => setError('Не удалось добавить запись. Проверьте данные.'),
  })

  const deleteMutation = useMutation({
    mutationFn: (contactId: string) => clientsApi.deleteContact(clientId, contactId),
    onSuccess: invalidate,
    onError: () => setError('Не удалось удалить запись.'),
  })

  const handleAdd = (e: React.FormEvent): void => {
    e.preventDefault()
    if (!contactDate) {
      setError('Укажите дату контакта')
      return
    }
    setError(null)
    createMutation.mutate()
  }

  return (
    <section className="mb-10">
      <h2 className="text-sm font-semibold text-light-text dark:text-dark-text mb-3">
        История коммуникаций
        {contacts && contacts.length > 0 && (
          <span className="font-normal text-light-secondary dark:text-dark-secondary"> ({contacts.length})</span>
        )}
      </h2>

      <form
        onSubmit={handleAdd}
        className="mb-4 p-4 rounded-xl bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border flex flex-col gap-3"
      >
        <div className="flex flex-col sm:flex-row gap-3">
          <select
            value={type}
            onChange={(e) => setType(e.target.value as ContactType)}
            className="px-3 py-2.5 rounded-lg border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg text-light-text dark:text-dark-text text-sm focus:outline-none focus:ring-2 focus:ring-light-accent dark:focus:ring-dark-accent"
          >
            {CONTACT_TYPES.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
          <input
            type="date"
            value={contactDate}
            onChange={(e) => setContactDate(e.target.value)}
            className="px-3 py-2.5 rounded-lg border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg text-light-text dark:text-dark-text text-sm focus:outline-none focus:ring-2 focus:ring-light-accent dark:focus:ring-dark-accent"
          />
        </div>
        <textarea
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          placeholder="Заметки (опционально)"
          rows={2}
          className="w-full px-3 py-2.5 rounded-lg border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg text-light-text dark:text-dark-text text-sm focus:outline-none focus:ring-2 focus:ring-light-accent dark:focus:ring-dark-accent resize-none"
        />
        {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}
        <div>
          <Button type="submit" size="sm" loading={createMutation.isPending}>
            Добавить запись
          </Button>
        </div>
      </form>

      {isLoading ? (
        <div className="flex justify-center py-6">
          <Spinner />
        </div>
      ) : !contacts || contacts.length === 0 ? (
        <p className="text-sm text-light-secondary dark:text-dark-secondary">Записей о контактах пока нет.</p>
      ) : (
        <ul className="flex flex-col gap-2">
          {contacts.map((contact) => (
            <li
              key={contact.id}
              className="p-4 rounded-lg bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border"
            >
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <div className="flex items-center gap-2 mb-1">
                    <span className="text-xs px-2 py-0.5 rounded-full bg-light-bg dark:bg-dark-bg border border-light-border dark:border-dark-border text-light-secondary dark:text-dark-secondary">
                      {contact.typeName}
                    </span>
                    <span className="text-xs text-light-secondary dark:text-dark-secondary">
                      {new Date(contact.contactDate).toLocaleDateString('ru-RU')}
                    </span>
                  </div>
                  {contact.notes && (
                    <p className="text-sm text-light-text dark:text-dark-text whitespace-pre-wrap">{contact.notes}</p>
                  )}
                </div>
                <button
                  onClick={() => deleteMutation.mutate(contact.id)}
                  className="shrink-0 text-xs text-light-secondary dark:text-dark-secondary hover:text-red-600 dark:hover:text-red-400"
                >
                  Удалить
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
