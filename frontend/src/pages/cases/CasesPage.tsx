import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { motion, AnimatePresence } from 'framer-motion'
import { casesApi } from '../../api/cases'
import { clientsApi } from '../../api/clients'
import type { CaseResponse, ClientResponse } from '../../types'
import { Navbar } from '../../components/layout/Navbar'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'
import { Spinner } from '../../components/ui/Spinner'

export default function CasesPage(): JSX.Element {
  const [showForm, setShowForm] = useState(false)
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [clientId, setClientId] = useState('')
  const [formError, setFormError] = useState<string | null>(null)
  const queryClient = useQueryClient()

  const { data: cases = [], isLoading } = useQuery<CaseResponse[]>({
    queryKey: ['cases'],
    queryFn: casesApi.getAll,
  })

  const { data: clients = [] } = useQuery<ClientResponse[]>({
    queryKey: ['clients'],
    queryFn: clientsApi.getAll,
  })

  const createMutation = useMutation({
    mutationFn: casesApi.create,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['cases'] })
      setShowForm(false)
      setTitle('')
      setDescription('')
      setClientId('')
      setFormError(null)
    },
    onError: () => setFormError('Не удалось создать дело. Попробуйте снова.'),
  })

  const handleSubmit = (e: React.FormEvent): void => {
    e.preventDefault()
    if (!title.trim()) {
      setFormError('Укажите название дела')
      return
    }
    createMutation.mutate({
      title: title.trim(),
      description: description.trim() || undefined,
      clientId: clientId || undefined,
    })
  }

  return (
    <div className="min-h-screen bg-light-bg dark:bg-dark-bg">
      <Navbar />
      <div className="page-container py-8">
        <div className="flex items-center justify-between mb-8">
          <div>
            <h1 className="text-3xl font-semibold text-light-text dark:text-dark-text mb-1">
              Дела
            </h1>
            <p className="text-sm text-light-secondary dark:text-dark-secondary">
              Банкротные дела: документы, AI-анализ и история заключений
            </p>
          </div>
          <Button variant="primary" onClick={() => setShowForm((v) => !v)}>
            {showForm ? 'Отмена' : 'Новое дело'}
          </Button>
        </div>

        <AnimatePresence>
          {showForm && (
            <motion.form
              initial={{ opacity: 0, height: 0 }}
              animate={{ opacity: 1, height: 'auto' }}
              exit={{ opacity: 0, height: 0 }}
              onSubmit={handleSubmit}
              className="mb-8 p-6 rounded-xl bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border overflow-hidden"
            >
              <div className="flex flex-col gap-4">
                <Input
                  label="Название дела"
                  placeholder="Например: Банкротство ООО «Рассвет», дело № А40-..."
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  maxLength={500}
                />
                <div>
                  <label className="block text-sm font-medium text-light-text dark:text-dark-text mb-1.5">
                    Описание <span className="text-light-secondary dark:text-dark-secondary font-normal">(опционально)</span>
                  </label>
                  <textarea
                    value={description}
                    onChange={(e) => setDescription(e.target.value)}
                    rows={3}
                    maxLength={5000}
                    placeholder="Краткое описание дела, ключевые обстоятельства"
                    className="w-full px-3 py-2.5 rounded-lg border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg text-light-text dark:text-dark-text text-sm placeholder:text-light-secondary/60 dark:placeholder:text-dark-secondary/60 focus:outline-none focus:ring-2 focus:ring-light-accent dark:focus:ring-dark-accent resize-none"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-light-text dark:text-dark-text mb-1.5">
                    Клиент <span className="text-light-secondary dark:text-dark-secondary font-normal">(опционально)</span>
                  </label>
                  <select
                    value={clientId}
                    onChange={(e) => setClientId(e.target.value)}
                    className="w-full px-3 py-2.5 rounded-lg border border-light-border dark:border-dark-border bg-light-bg dark:bg-dark-bg text-light-text dark:text-dark-text text-sm focus:outline-none focus:ring-2 focus:ring-light-accent dark:focus:ring-dark-accent"
                  >
                    <option value="">Без клиента</option>
                    {clients.map((client) => (
                      <option key={client.id} value={client.id}>
                        {client.name} ({client.typeName})
                      </option>
                    ))}
                  </select>
                </div>
                {formError && <p className="text-sm text-red-600 dark:text-red-400">{formError}</p>}
                <div>
                  <Button type="submit" variant="primary" loading={createMutation.isPending}>
                    Создать дело
                  </Button>
                </div>
              </div>
            </motion.form>
          )}
        </AnimatePresence>

        {isLoading ? (
          <div className="flex justify-center py-16">
            <Spinner size="lg" />
          </div>
        ) : cases.length === 0 ? (
          <div className="text-center py-16 rounded-xl border border-dashed border-light-border dark:border-dark-border">
            <p className="text-light-secondary dark:text-dark-secondary text-sm">
              Пока нет дел. Создайте первое дело, чтобы загрузить документы и запустить AI-анализ.
            </p>
          </div>
        ) : (
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {cases.map((caseItem, index) => (
              <motion.div
                key={caseItem.id}
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.2, delay: index * 0.03 }}
              >
                <Link
                  to={`/cases/${caseItem.id}`}
                  className="block h-full p-5 rounded-xl bg-light-surface dark:bg-dark-surface border border-light-border dark:border-dark-border hover:border-light-accent/50 dark:hover:border-dark-accent/50 transition-colors"
                >
                  <h3 className="font-medium text-light-text dark:text-dark-text mb-2 line-clamp-2">
                    {caseItem.title}
                  </h3>
                  {caseItem.clientName && (
                    <p className="text-xs text-light-accent dark:text-dark-accent mb-2 truncate">
                      {caseItem.clientName}
                    </p>
                  )}
                  {caseItem.description && (
                    <p className="text-sm text-light-secondary dark:text-dark-secondary line-clamp-2 mb-3">
                      {caseItem.description}
                    </p>
                  )}
                  <p className="text-xs text-light-secondary dark:text-dark-secondary mt-auto">
                    {new Date(caseItem.createdAt).toLocaleDateString('ru-RU')}
                  </p>
                </Link>
              </motion.div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
